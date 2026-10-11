package main

/*
#include <stdlib.h>
*/
import "C"

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/metacubex/mihomo/component/age"
	"github.com/metacubex/mihomo/config"

	"stelliberty_core/overrides"
)

// age 订阅在磁盘上保持密文，变换只能作用于解密后的明文；没有 age 头的内容原样返回。
func decryptConfig(configBytes []byte, ageSecretKey string) ([]byte, error) {
	var keys []string
	if key := strings.TrimSpace(ageSecretKey); key != "" {
		keys = append(keys, key)
	}
	plain, err := age.DecryptBytes(configBytes, keys...)
	if err != nil {
		return nil, fmt.Errorf("decrypt config: %w", err)
	}
	return plain, nil
}

func applyTransformFile(configBytes []byte, transformPath, ageSecretKey string) ([]byte, error) {
	transform, err := overrides.LoadTransform(transformPath)
	if err != nil {
		return nil, err
	}
	plain, err := decryptConfig(configBytes, ageSecretKey)
	if err != nil {
		return nil, err
	}
	return transform.Apply(plain)
}

// transformPath 为空时只解密，不套变换。
func readTransformedConfig(workDir, transformPath, ageSecretKey string) ([]byte, error) {
	raw, err := os.ReadFile(filepath.Join(workDir, "config.yaml"))
	if err != nil {
		return nil, fmt.Errorf("read config: %w", err)
	}
	if transformPath == "" {
		return decryptConfig(raw, ageSecretKey)
	}
	return applyTransformFile(raw, transformPath, ageSecretKey)
}

type transformCheck struct {
	HasCycle bool `json:"hasCycle"`
}

// 与运行时同一套变换与解析流程：保存改动前先在应用内跑一遍，失败时不去重启代理。
//
//export stellibertyValidateTransform
func stellibertyValidateTransform(cWorkDir, cTransform, cKey *C.char) *C.char {
	return guardString(func() string {
		workDir := C.GoString(cWorkDir)
		// provider 校验同样需要订阅密钥，调用方的 processLock 保证全局密钥串行使用。
		age.SetGlobalSecretKeys(C.GoString(cKey))
		defer age.SetGlobalSecretKeys()
		out, err := readTransformedConfig(workDir, C.GoString(cTransform), C.GoString(cKey))
		if err != nil {
			return "error: " + err.Error()
		}
		rawCfg, err := config.UnmarshalRawConfig(out)
		if err != nil {
			return "error: unmarshal config: " + err.Error()
		}
		patchProvidersPath(rawCfg, filepath.Join(workDir, "providers"))
		cfg, err := config.ParseRawConfig(rawCfg)
		if err != nil {
			return "error: validate config: " + err.Error()
		}
		destroyProviders(cfg)
		data, _ := json.Marshal(transformCheck{HasCycle: overrides.HasChainCycle(out)})
		return string(data)
	})
}

// 链式代理页面以套完覆写后的配置为准，transform 只带覆写。
//
//export stellibertyChainProxyContext
func stellibertyChainProxyContext(cWorkDir, cTransform, cKey *C.char) *C.char {
	return guardString(func() string {
		out, err := readTransformedConfig(C.GoString(cWorkDir), C.GoString(cTransform), C.GoString(cKey))
		if err != nil {
			return "error: " + err.Error()
		}
		chainContext, err := overrides.ChainContextOf(out)
		if err != nil {
			return "error: " + err.Error()
		}
		data, err := json.Marshal(chainContext)
		if err != nil {
			return "error: " + err.Error()
		}
		return string(data)
	})
}

// 规则覆写页面以套完覆写与链式代理、尚未套规则覆写的配置为基线。
//
//export stellibertyRuleContext
func stellibertyRuleContext(cWorkDir, cTransform, cKey *C.char) *C.char {
	return guardString(func() string {
		out, err := readTransformedConfig(C.GoString(cWorkDir), C.GoString(cTransform), C.GoString(cKey))
		if err != nil {
			return "error: " + err.Error()
		}
		ruleContext, err := overrides.RuleContextOf(out)
		if err != nil {
			return "error: " + err.Error()
		}
		data, err := json.Marshal(ruleContext)
		if err != nil {
			return "error: " + err.Error()
		}
		return string(data)
	})
}

// 代理页预览与运行时同一份配置口径：套完覆写与链式代理；provider 成员从缓存文件补齐，读不到的跳过。
//
//export stellibertyProxyPreview
func stellibertyProxyPreview(cWorkDir, cTransform, cKey *C.char) *C.char {
	return guardString(func() string {
		out, err := readTransformedConfig(C.GoString(cWorkDir), C.GoString(cTransform), C.GoString(cKey))
		if err != nil {
			return "error: " + err.Error()
		}
		preview, err := overrides.ProxyPreviewOf(out, proxyProviderCacheContents(C.GoString(cWorkDir), out))
		if err != nil {
			return "error: " + err.Error()
		}
		data, err := json.Marshal(preview)
		if err != nil {
			return "error: " + err.Error()
		}
		return string(data)
	})
}

// provider 缓存路径的拼法只在 providerCachePath 一处成立，这里按名字把缓存内容交给 overrides。
// 配置本身解析失败时放弃补齐：预览是尽力而为，覆写页面已有独立的报错通道。
func proxyProviderCacheContents(workDir string, configBytes []byte) map[string][]byte {
	rawCfg, err := config.UnmarshalRawConfig(configBytes)
	if err != nil {
		return nil
	}
	contents := make(map[string][]byte, len(rawCfg.ProxyProvider))
	for name, provider := range rawCfg.ProxyProvider {
		path := providerCachePath(filepath.Join(workDir, "providers"), "proxies", provider)
		if path == "" {
			continue
		}
		data, err := os.ReadFile(path)
		if err != nil {
			continue
		}
		contents[name] = data
	}
	return contents
}
