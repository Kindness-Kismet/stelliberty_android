package main

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	Const "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/hub/executor"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

func loadRuntimeConfig(configFile, transformPath, ageSecretKey string) ([]byte, error) {
	raw, err := os.ReadFile(configFile)
	if err != nil {
		return nil, err
	}
	if transformPath != "" {
		if _, err := os.Stat(transformPath); os.IsNotExist(err) {
			transformPath = ""
		}
	}
	if transformPath == "" {
		raw, err = decryptConfig(raw, ageSecretKey)
	} else {
		raw, err = applyTransformFile(raw, transformPath, ageSecretKey)
	}
	if err != nil {
		return nil, err
	}
	return patchProviderPathsYAML(raw, filepath.Join(Const.Path.HomeDir(), "providers"))
}

func reloadRuntimeConfig(load func() ([]byte, error)) error {
	started := time.Now()
	raw, err := load()
	if err != nil {
		return err
	}
	cfg, err := executor.ParseWithBytes(raw)
	if err != nil {
		return fmt.Errorf("validate config: %w", err)
	}
	// TUN 描述符、监听端口和控制器属于当前服务会话，订阅重载保留它们。
	cfg.General.Inbound = executor.GetGeneral().Inbound
	for name, previous := range tunnel.Proxies() {
		next := cfg.Proxies[name]
		if next == nil || next.Type() != previous.Type() {
			continue
		}
		selector, ok := next.Adapter().(outboundgroup.SelectAble)
		if !ok {
			continue
		}
		data, err := previous.MarshalJSON()
		if err != nil {
			continue
		}
		var selection struct {
			Now   string `json:"now"`
			Fixed string `json:"fixed"`
		}
		if json.Unmarshal(data, &selection) != nil {
			continue
		}
		if previous.Type() == Const.Selector {
			selector.ForceSet(selection.Now)
		} else {
			selector.ForceSet(selection.Fixed)
		}
	}
	executor.ApplyConfig(cfg, false)
	log.Infoln("Runtime configuration reloaded in %dms", time.Since(started).Milliseconds())
	return nil
}
