package main

/*
#include <stdlib.h>
*/
import "C"

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"mime"
	"net"
	"net/http"
	"net/netip"
	"net/url"
	"os"
	"path"
	P "path"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	clashHttp "github.com/metacubex/mihomo/component/http"
	"github.com/metacubex/mihomo/config"
	Const "github.com/metacubex/mihomo/constant"
)

const fetchTimeout = 60 * time.Second

const prefetchConcurrency = 5

type FetchProgress struct {
	Action      string   `json:"action"`
	Args        []string `json:"args"`
	Progress    int      `json:"progress"`
	MaxProgress int      `json:"max"`
}

type FetchResult struct {
	Upload   int64  `json:"upload"`
	Download int64  `json:"download"`
	Total    int64  `json:"total"`
	Expire   int64  `json:"expire"`
	FileName string `json:"fileName"`
	// 与 PC 相同，取自未套覆写的订阅原文。
	BuiltinChainProxyNames []string `json:"builtinChainProxyNames"`
}

//export stellibertyFetchAndValid
func stellibertyFetchAndValid(
	cWorkDir *C.char,
	cURL *C.char,
	force C.int,
	cHttpProxy *C.char,
	cUserAgent *C.char,
	token C.int,
) *C.char {
	return guardString(func() string {
		workDir := C.GoString(cWorkDir)
		rawURL := C.GoString(cURL)
		httpProxy := C.GoString(cHttpProxy)
		userAgent := C.GoString(cUserAgent)

		ctx, cancel := context.WithCancel(context.Background())
		cancelRegistry.Store(int32(token), func() { cancel() })
		defer cancelRegistry.Delete(int32(token))
		defer progressStore.Delete(int32(token))

		result, err := runFetchAndValid(ctx, int32(token), workDir, rawURL, force != 0, httpProxy, userAgent)
		if err != nil {
			return "error: " + err.Error()
		}
		payload, _ := json.Marshal(result)
		return string(payload)
	})
}

func runFetchAndValid(
	ctx context.Context,
	token int32,
	workDir, rawURL string,
	force bool,
	httpProxy string,
	userAgent string,
) (*FetchResult, error) {
	if err := os.MkdirAll(workDir, 0700); err != nil {
		return nil, fmt.Errorf("create workDir: %w", err)
	}
	effectiveUA := strings.TrimSpace(userAgent)
	if effectiveUA == "" {
		effectiveUA = clashHttp.UA()
	}

	var options []clashHttp.Option
	if httpProxy != "" {
		proxyURL, err := url.Parse(httpProxy)
		if err != nil {
			return nil, fmt.Errorf("parse http proxy: %w", err)
		}
		options = append(options, clashHttp.WithDialer(connectDialer{proxyAddr: proxyURL.Host}))
	}

	configPath := P.Join(workDir, "config.yaml")
	result := &FetchResult{}

	if force {
		_ = os.Remove(configPath)
	}
	if _, err := os.Stat(configPath); os.IsNotExist(err) {
		u, err := url.Parse(rawURL)
		if err != nil {
			return nil, fmt.Errorf("parse url: %w", err)
		}
		setProgress(token, FetchProgress{
			Action:      "FetchConfiguration",
			Args:        []string{u.Host},
			Progress:    -1,
			MaxProgress: -1,
		})
		if err := fetchURL(ctx, u, configPath, result, effectiveUA, options); err != nil {
			return nil, err
		}
	}

	raw, err := os.ReadFile(configPath)
	if err != nil {
		return nil, fmt.Errorf("read config: %w", err)
	}
	rawCfg, err := config.UnmarshalRawConfig(raw)
	if err != nil {
		return nil, fmt.Errorf("unmarshal config: %w", err)
	}

	providersDir := P.Join(workDir, "providers")
	if err := os.MkdirAll(providersDir, 0700); err != nil {
		return nil, fmt.Errorf("create providers dir: %w", err)
	}
	patchProvidersPath(rawCfg, providersDir)
	result.BuiltinChainProxyNames = builtinChainProxyNames(rawCfg.Proxy)

	prefetchProviders(ctx, token, rawCfg, effectiveUA, options)

	if ctx.Err() != nil {
		return nil, ctx.Err()
	}

	setProgress(token, FetchProgress{
		Action:      "Verifying",
		Args:        []string{},
		Progress:    0xffff,
		MaxProgress: 0xffff,
	})
	cfg, err := config.ParseRawConfig(rawCfg)
	if err != nil {
		return nil, fmt.Errorf("validate config: %w", err)
	}
	destroyProviders(cfg)

	return result, nil
}

// 带 dialer-proxy 的节点即订阅内置的链式代理。
func builtinChainProxyNames(proxies []map[string]any) []string {
	names := []string{}
	seen := make(map[string]bool)
	for _, proxy := range proxies {
		name, _ := proxy["name"].(string)
		dialer, _ := proxy["dialer-proxy"].(string)
		if strings.TrimSpace(name) == "" || strings.TrimSpace(dialer) == "" || seen[name] {
			continue
		}
		seen[name] = true
		names = append(names, name)
	}
	return names
}

func setProgress(token int32, p FetchProgress) {
	if bytes, err := json.Marshal(p); err == nil {
		progressStore.Store(token, string(bytes))
	}
}

func fetchURL(ctx context.Context, u *url.URL, dest string, result *FetchResult, userAgent string, options []clashHttp.Option) error {
	scheme := strings.ToLower(u.Scheme)
	if scheme != "http" && scheme != "https" {
		return fmt.Errorf("unsupported scheme %s", u.Scheme)
	}

	subCtx, cancel := context.WithTimeout(ctx, fetchTimeout)
	defer cancel()

	header := http.Header{"User-Agent": []string{userAgent}}
	resp, err := clashHttp.HttpRequest(subCtx, u.String(), http.MethodGet, header, nil, options...)
	if err != nil {
		return fmt.Errorf("http request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("http status %d", resp.StatusCode)
	}

	parseUserinfo(resp.Header.Get("subscription-userinfo"), result)
	result.FileName = dispositionFileName(resp.Header.Get("Content-Disposition"))

	if err := os.MkdirAll(P.Dir(dest), 0700); err != nil {
		return err
	}
	n, err := writeFileAtomic(dest, resp.Body)
	if err != nil {
		return err
	}
	if n == 0 {
		_ = os.Remove(dest)
		return errors.New("empty response body")
	}
	return nil
}

func dispositionFileName(header string) string {
	if header == "" {
		return ""
	}
	_, params, err := mime.ParseMediaType(header)
	if err != nil {
		return ""
	}
	name := strings.TrimSpace(params["filename"])
	if name == "" {
		return ""
	}
	if strings.Contains(name, "%") {
		if decoded, err := url.PathUnescape(name); err == nil && strings.TrimSpace(decoded) != "" {
			name = strings.TrimSpace(decoded)
		}
	}
	name = path.Base(strings.ReplaceAll(name, "\\", "/"))
	if name == "." || name == ".." || name == "/" {
		return ""
	}
	lower := strings.ToLower(name)
	for _, ext := range []string{".yaml", ".yml"} {
		if strings.HasSuffix(lower, ext) {
			name = name[:len(name)-len(ext)]
			break
		}
	}
	return strings.TrimSpace(name)
}

func parseUserinfo(header string, result *FetchResult) {
	if header == "" {
		return
	}
	for _, segment := range strings.Split(header, ";") {
		kv := strings.SplitN(strings.TrimSpace(segment), "=", 2)
		if len(kv) != 2 {
			continue
		}
		v, err := strconv.ParseInt(kv[1], 10, 64)
		if err != nil {
			continue
		}
		switch strings.ToLower(kv[0]) {
		case "upload":
			result.Upload = v
		case "download":
			result.Download = v
		case "total":
			result.Total = v
		case "expire":
			result.Expire = v
		}
	}
}

func prefetchProviders(ctx context.Context, token int32, cfg *config.RawConfig, userAgent string, options []clashHttp.Option) {
	type item struct {
		key  string
		url  string
		dest string
	}
	var items []item
	forEachProviders(cfg, func(_, _ int, key string, provider map[string]any, _ string) {
		vehicle, _ := provider["type"].(string)
		urlStr, _ := provider["url"].(string)
		dest, _ := provider["path"].(string)
		if vehicle != "http" || urlStr == "" || dest == "" {
			return
		}
		if _, err := os.Stat(dest); err == nil {
			return
		}
		items = append(items, item{key: key, url: urlStr, dest: dest})
	})

	total := len(items)
	if total == 0 {
		return
	}
	setProgress(token, FetchProgress{
		Action:      "FetchProviders",
		Args:        []string{items[0].key},
		Progress:    0,
		MaxProgress: total,
	})

	var done atomic.Int32
	var wg sync.WaitGroup
	sem := make(chan struct{}, prefetchConcurrency)
	for _, it := range items {
		if ctx.Err() != nil {
			break
		}
		wg.Add(1)
		go func(it item) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()
			if ctx.Err() != nil {
				return
			}
			if u, err := url.Parse(it.url); err == nil {
				_ = fetchProvider(ctx, u, it.dest, userAgent, options)
			}
			setProgress(token, FetchProgress{
				Action:      "FetchProviders",
				Args:        []string{it.key},
				Progress:    int(done.Add(1)),
				MaxProgress: total,
			})
		}(it)
	}
	wg.Wait()
}

func writeFileAtomic(dest string, src io.Reader) (int64, error) {
	tmp, err := os.CreateTemp(path.Dir(dest), path.Base(dest)+".tmp")
	if err != nil {
		return 0, err
	}
	tmpName := tmp.Name()
	fail := func(err error) (int64, error) {
		tmp.Close()
		os.Remove(tmpName)
		return 0, err
	}
	n, err := io.Copy(tmp, src)
	if err != nil {
		return fail(err)
	}
	if err := tmp.Sync(); err != nil {
		return fail(err)
	}
	if err := tmp.Close(); err != nil {
		os.Remove(tmpName)
		return 0, err
	}
	if err := os.Chmod(tmpName, 0600); err != nil {
		os.Remove(tmpName)
		return 0, err
	}
	if err := os.Rename(tmpName, dest); err != nil {
		os.Remove(tmpName)
		return 0, err
	}
	return n, nil
}

func fetchProvider(ctx context.Context, u *url.URL, dest string, userAgent string, options []clashHttp.Option) error {
	subCtx, cancel := context.WithTimeout(ctx, fetchTimeout)
	defer cancel()
	header := http.Header{"User-Agent": []string{userAgent}}
	resp, err := clashHttp.HttpRequest(subCtx, u.String(), http.MethodGet, header, nil, options...)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("status %d", resp.StatusCode)
	}
	if err := os.MkdirAll(path.Dir(dest), 0700); err != nil {
		return err
	}
	_, err = writeFileAtomic(dest, resp.Body)
	return err
}

// HttpRequest 的 Transport 不读代理环境变量，所以经代理下载只能换拨号器，用 HTTP CONNECT 建隧道。
type connectDialer struct {
	proxyAddr string
}

func (d connectDialer) DialContext(ctx context.Context, _, address string) (net.Conn, error) {
	var dialer net.Dialer
	conn, err := dialer.DialContext(ctx, "tcp", d.proxyAddr)
	if err != nil {
		return nil, err
	}
	// 握手期间取消或超时就关闭连接，阻塞的读写随之返回。
	stop := context.AfterFunc(ctx, func() { _ = conn.Close() })
	err = connectHandshake(conn, address)
	if !stop() {
		return nil, ctx.Err()
	}
	if err != nil {
		_ = conn.Close()
		return nil, err
	}
	return conn, nil
}

func (connectDialer) ListenPacket(context.Context, string, string, netip.AddrPort) (net.PacketConn, error) {
	return nil, errors.New("udp is not supported by http proxy")
}

var _ Const.Dialer = connectDialer{}

func connectHandshake(conn net.Conn, address string) error {
	req := &http.Request{
		Method: http.MethodConnect,
		URL:    &url.URL{Opaque: address},
		Host:   address,
		Header: http.Header{},
	}
	if err := req.Write(conn); err != nil {
		return err
	}
	// 隧道建立前对端不会先发数据，读取缓冲里不会多出属于隧道的字节。
	resp, err := http.ReadResponse(bufio.NewReader(conn), req)
	if err != nil {
		return err
	}
	_ = resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("proxy connect: %s", resp.Status)
	}
	return nil
}

func destroyProviders(cfg *config.Config) {
	for _, p := range cfg.Providers {
		if c, ok := any(p).(io.Closer); ok {
			_ = c.Close()
		}
	}
	for _, p := range cfg.RuleProviders {
		if c, ok := any(p).(io.Closer); ok {
			_ = c.Close()
		}
	}
}
