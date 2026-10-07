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
	// 扩展路由沿用控制器鉴权；进程号区分使用相同密钥的内核。
	route.Register(func(router chi.Router) {
		router.Get("/stelliberty/runtime", func(w http.ResponseWriter, r *http.Request) {
			if !ready.Load() {
				w.WriteHeader(http.StatusServiceUnavailable)
				return
			}
			w.Header().Set("Content-Type", "application/json")
			fmt.Fprintf(w, `{"pid":%d}`, os.Getpid())
		})
	})
	return ready
}
