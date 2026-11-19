package main

/*
#include <stdlib.h>
*/
import "C"

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"mime"
	"net"
	"net/url"
	"os"
	"path"
	P "path"
	"reflect"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/metacubex/http"
	"github.com/metacubex/mihomo/component/ca"
	"github.com/metacubex/mihomo/component/dialer"
	clashHttp "github.com/metacubex/mihomo/component/http"
	"github.com/metacubex/mihomo/config"
	PProvider "github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/log"
	RP "github.com/metacubex/mihomo/rules/provider"
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

	client, err := newFetchClient(httpProxy)
	if err != nil {
		return nil, err
	}
	defer client.CloseIdleConnections()

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
		started := time.Now()
		if err := fetchURL(ctx, u, configPath, result, effectiveUA, client); err != nil {
			return nil, err
		}
		log.Infoln("Configuration fetch complete in %dms", time.Since(started).Milliseconds())
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
	seedRuleProviderCache(workDir, rawCfg)
	patchProvidersPath(rawCfg, providersDir)
	result.BuiltinChainProxyNames = builtinChainProxyNames(rawCfg.Proxy)

	if err := prefetchProviders(ctx, token, rawCfg, effectiveUA, client); err != nil {
		return nil, err
	}

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
	warmRuleProviders(ctx, rawCfg)
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

// 连接只在一次导入内复用，结束时关闭，下一次导入按最新代理设置重新拨号。
func newFetchClient(httpProxy string) (*http.Client, error) {
	tlsConfig, err := ca.GetTLSConfig(ca.Option{})
	if err != nil {
		return nil, err
	}
	dial := func(ctx context.Context, network, address string) (net.Conn, error) {
		return dialer.DialContext(ctx, network, address)
	}
	transport := &http.Transport{
		DialContext:           dial,
		TLSClientConfig:       tlsConfig,
		ForceAttemptHTTP2:     true,
		MaxIdleConns:          prefetchConcurrency,
		MaxIdleConnsPerHost:   prefetchConcurrency,
		IdleConnTimeout:       30 * time.Second,
		TLSHandshakeTimeout:   10 * time.Second,
		ExpectContinueTimeout: time.Second,
	}
	if httpProxy != "" {
		proxyURL, err := url.Parse(httpProxy)
		if err != nil {
			return nil, fmt.Errorf("parse http proxy: %w", err)
		}
		// HTTP 使用代理请求，HTTPS 才建立 CONNECT 隧道，兼容限制 CONNECT 端口的系统代理。
		transport.Proxy = http.ProxyURL(proxyURL)
	}
	return &http.Client{Transport: transport}, nil
}

func fetchRequest(ctx context.Context, client *http.Client, u *url.URL, userAgent string) (*http.Response, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u.String(), nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", userAgent)
	if user := u.User; user != nil {
		password, _ := user.Password()
		req.SetBasicAuth(user.Username(), password)
	}
	return client.Do(req)
}

func fetchURL(ctx context.Context, u *url.URL, dest string, result *FetchResult, userAgent string, client *http.Client) error {
	scheme := strings.ToLower(u.Scheme)
	if scheme != "http" && scheme != "https" {
		return fmt.Errorf("unsupported scheme %s", u.Scheme)
	}

	subCtx, cancel := context.WithTimeout(ctx, fetchTimeout)
	defer cancel()

	resp, err := fetchRequest(subCtx, client, u, userAgent)
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

// 规则集按自身更新间隔复用；来源和解析选项有变化时重新下载，节点订阅始终刷新。
func seedRuleProviderCache(workDir string, cfg *config.RawConfig) {
	previousDir := P.Join(workDir, "previous")
	defer os.RemoveAll(previousDir)
	raw, err := os.ReadFile(P.Join(previousDir, "config.yaml"))
	if err != nil {
		return
	}
	previous, err := config.UnmarshalRawConfig(raw)
	if err != nil {
		return
	}
	reused := 0
	for name, rule := range cfg.RuleProvider {
		if !reflect.DeepEqual(rule, previous.RuleProvider[name]) {
			continue
		}
		seconds, err := strconv.ParseInt(fmt.Sprint(rule["interval"]), 10, 64)
		if err != nil || seconds <= 0 {
			continue
		}
		source := providerCachePath(P.Join(previousDir, "providers"), "rules", rule)
		info, err := os.Stat(source)
		if err != nil || !info.Mode().IsRegular() || time.Since(info.ModTime()).Seconds() >= float64(seconds) {
			continue
		}
		dest := providerCachePath(P.Join(workDir, "providers"), "rules", rule)
		file, err := os.Open(source)
		if err != nil {
			continue
		}
		if err = os.MkdirAll(P.Dir(dest), 0700); err == nil {
			_, err = writeFileAtomic(dest, file)
		}
		file.Close()
		if err == nil {
			os.Chtimes(dest, info.ModTime(), info.ModTime())
			reused++
		}
	}
	log.Infoln("Reused %d unexpired rule provider caches", reused)
}

func prefetchProviders(ctx context.Context, token int32, cfg *config.RawConfig, userAgent string, client *http.Client) error {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	type item struct {
		key  string
		url  string
		dest string
		rule map[string]any
	}
	var items []item
	forEachProviders(cfg, func(_, _ int, key string, provider map[string]any, kind string) {
		vehicle, _ := provider["type"].(string)
		urlStr, _ := provider["url"].(string)
		dest, _ := provider["path"].(string)
		if vehicle != "http" || urlStr == "" || dest == "" {
			return
		}
		if _, err := os.Stat(dest); err == nil {
			return
		}
		it := item{key: key, url: urlStr, dest: dest}
		if kind == "rules" {
			it.rule = provider
		}
		items = append(items, it)
	})

	total := len(items)
	if total == 0 {
		return nil
	}
	started := time.Now()
	setProgress(token, FetchProgress{
		Action:      "FetchProviders",
		Args:        []string{items[0].key},
		Progress:    0,
		MaxProgress: total,
	})

	var done atomic.Int32
	var wg sync.WaitGroup
	var errorMu sync.Mutex
	var firstError error
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
			u, err := url.Parse(it.url)
			if err == nil {
				err = fetchProvider(ctx, u, it.dest, userAgent, client)
			}
			if err != nil {
				log.Warnln("Prefetch provider %s failed: %v", it.key, err)
				errorMu.Lock()
				if firstError == nil {
					firstError = fmt.Errorf("prefetch provider %s: %w", it.key, err)
					cancel()
				}
				errorMu.Unlock()
			} else if it.rule != nil && ctx.Err() == nil {
				warmRuleProvider(it.key, it.rule)
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
	log.Infoln("Provider prefetch complete: %d providers in %dms", total, time.Since(started).Milliseconds())
	return firstError
}

func warmRuleProviders(ctx context.Context, cfg *config.RawConfig) {
	for name, rule := range cfg.RuleProvider {
		if ctx.Err() != nil {
			return
		}
		warmRuleProvider(name, rule)
	}
}

func warmRuleProvider(name string, rule map[string]any) {
	defer func() {
		if err := recover(); err != nil {
			log.Debugln("Provider %s compiled cache preparation panic: %v", name, err)
		}
	}()
	if kind, _ := rule["type"].(string); kind != "http" {
		return
	}
	behaviorText, _ := rule["behavior"].(string)
	behavior, err := PProvider.ParseBehavior(behaviorText)
	if err != nil || behavior == PProvider.Classical {
		return
	}
	format := PProvider.YamlRule
	if text, _ := rule["format"].(string); text != "" {
		format, err = PProvider.ParseRuleFormat(text)
		if err != nil {
			return
		}
	}
	url, _ := rule["url"].(string)
	path, _ := rule["path"].(string)
	if err := RP.WarmCompiledRuleCache(name, url, path, behavior, format); err != nil {
		log.Debugln("Provider %s compiled cache preparation skipped: %v", name, err)
	}
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

func fetchProvider(ctx context.Context, u *url.URL, dest string, userAgent string, client *http.Client) error {
	subCtx, cancel := context.WithTimeout(ctx, fetchTimeout)
	defer cancel()
	resp, err := fetchRequest(subCtx, client, u, userAgent)
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
	n, err := writeFileAtomic(dest, resp.Body)
	if err != nil {
		return err
	}
	if n == 0 {
		return errors.New("empty response body")
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
