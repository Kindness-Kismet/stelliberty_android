package main

/*
#include <stdlib.h>
*/
import "C"

import (
	"context"
	"flag"
	"fmt"
	"net"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"unsafe"

	"github.com/metacubex/mihomo/component/age"
	"github.com/metacubex/mihomo/component/updater"
	"github.com/metacubex/mihomo/config"
	Const "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/hub"
	"github.com/metacubex/mihomo/hub/executor"
	"github.com/metacubex/mihomo/listener"
	"github.com/metacubex/mihomo/log"
)

//export mihomoEntry
func mihomoEntry(argc C.int, argv **C.char) (result C.int) {
	defer func() {
		if err := recover(); err != nil {
			fmt.Fprintln(os.Stderr, "mihomo runtime panic:", err)
			result = 1
		}
	}()
	args := make([]string, int(argc))
	if argc > 0 && argv != nil {
		arr := unsafe.Slice(argv, int(argc))
		for i, p := range arr {
			args[i] = C.GoString(p)
		}
	}
	os.Args = args
	return C.int(runMihomo())
}

func runMihomo() int {
	fs := flag.NewFlagSet("mihomo", flag.ExitOnError)
	var (
		homeDir            string
		configFile         string
		secret             string
		externalController string
		overrideJSON       string
		transformPath      string
		ageSecretKey       string
		readyFile          string
	)
	fs.StringVar(&homeDir, "d", "", "set configuration directory")
	fs.StringVar(&configFile, "f", "", "specify configuration file")
	fs.StringVar(&overrideJSON, "override-json", "", "path to a JSON file whose fields override the parsed RawConfig")
	fs.StringVar(&transformPath, "transform", "", "path to a JSON subscription transform (overrides, chain proxies) applied before parsing")
	fs.StringVar(&secret, "secret", "", "override RESTful API secret")
	fs.StringVar(&externalController, "ext-ctl", "", "override external controller address")
	fs.StringVar(&ageSecretKey, "age-secret-key", "", "age secret key to decrypt age-armor encrypted configuration")
	fs.StringVar(&readyFile, "ready-file", "", "signal completed runtime initialization to the parent process")
	if err := fs.Parse(os.Args[1:]); err != nil {
		return 2
	}

	net.DefaultResolver.PreferGo = true
	net.DefaultResolver.Dial = func(ctx context.Context, network, address string) (net.Conn, error) {
		fmt.Fprintln(os.Stderr, "panic: net.DefaultResolver.Dial should never be called")
		os.Exit(2)
		return nil, nil
	}

	if overrideJSON != "" {
		config.OverrideJSONPath = overrideJSON
	}

	if ageSecretKey != "" {
		age.SetGlobalSecretKeys(ageSecretKey)
	}

	if homeDir != "" {
		if !filepath.IsAbs(homeDir) {
			cwd, _ := os.Getwd()
			homeDir = filepath.Join(cwd, homeDir)
		}
		Const.SetHomeDir(homeDir)
	}

	if configFile == "" {
		configFile = filepath.Join(Const.Path.HomeDir(), Const.Path.Config())
	} else if !filepath.IsAbs(configFile) {
		cwd, _ := os.Getwd()
		configFile = filepath.Join(cwd, configFile)
	}
	Const.SetConfig(configFile)

	if err := config.Init(Const.Path.HomeDir()); err != nil {
		log.Fatalln("init config dir: %s", err.Error())
	}

	configBytes, err := os.ReadFile(configFile)
	if err != nil {
		log.Fatalln("read config: %s", err.Error())
	}
	// 与 PC 相同：订阅覆写与链式代理先于 override-json 等运行参数生效。
	if transformPath != "" {
		if configBytes, err = applyTransformFile(configBytes, transformPath, ageSecretKey); err != nil {
			log.Fatalln("apply transform: %s", err.Error())
		}
	}

	var tunEnabled bool
	options := []hub.Option{func(cfg *config.Config) { tunEnabled = cfg.General.Tun.Enable }}
	if externalController != "" {
		options = append(options, hub.WithExternalController(externalController))
	}
	if secret != "" {
		options = append(options, hub.WithSecret(secret))
	}

	if err := hub.Parse(configBytes, options...); err != nil {
		log.Fatalln("Parse config: %s", err.Error())
	}
	defer executor.Shutdown()
	if tunEnabled && !listener.GetTunConf().Enable {
		log.Errorln("Start TUN listening error: TUN listener is not active")
		return 1
	}

	if updater.GeoAutoUpdate() {
		updater.RegisterGeoUpdater()
	}

	termSig := make(chan os.Signal, 1)
	hupSig := make(chan os.Signal, 1)
	signal.Notify(termSig, syscall.SIGINT, syscall.SIGTERM)
	signal.Notify(hupSig, syscall.SIGHUP)
	// 控制接口先于隧道与 provider 创建；父进程预建文件，保留应用的所有权与读取权限。
	if readyFile != "" {
		if err := os.WriteFile(readyFile, []byte("ready"), 0600); err != nil {
			log.Errorln("write runtime ready signal: %s", err.Error())
			return 1
		}
	}

	for {
		select {
		case <-termSig:
			return 0
		case <-hupSig:
			if err := hub.Parse(configBytes, options...); err != nil {
				log.Errorln("Reload config: %s", err.Error())
			}
		}
	}
}
