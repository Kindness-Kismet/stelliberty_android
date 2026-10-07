package main

import (
	"fmt"
	"os"
	"sync/atomic"

	"github.com/metacubex/chi"
	"github.com/metacubex/http"
	"github.com/metacubex/mihomo/hub/route"
)

func registerRuntimeProbe() *atomic.Bool {
	ready := &atomic.Bool{}
	// 扩展路由沿用控制器鉴权；进程号区分使用相同密钥的内核，初始化期间的 503 也带上，
	// 应用据此区分「自己仍在加载 provider」与「端口被其他程序占用」。
	route.Register(func(router chi.Router) {
		router.Get("/stelliberty/runtime", func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			if !ready.Load() {
				w.WriteHeader(http.StatusServiceUnavailable)
			}
			fmt.Fprintf(w, `{"pid":%d}`, os.Getpid())
		})
	})
	return ready
}
