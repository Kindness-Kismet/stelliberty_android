package main

import (
	"fmt"
	"os"
	"strconv"
	"sync/atomic"

	"github.com/metacubex/chi"
	"github.com/metacubex/http"
	"github.com/metacubex/mihomo/hub/route"
)

func registerRuntimeProbe(reload func() error) *atomic.Bool {
	ready := &atomic.Bool{}
	// 扩展路由沿用控制器鉴权；进程号区分使用相同密钥的内核，初始化期间的 503 也带上，
	// 应用据此区分「自己仍在加载 provider」与「端口被其他程序占用」。
	route.Register(func(router chi.Router) {
		router.Post("/stelliberty/reload", func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Query().Get("pid") != strconv.Itoa(os.Getpid()) {
				http.Error(w, "runtime identity mismatch", http.StatusConflict)
				return
			}
			if !ready.Load() {
				http.Error(w, "runtime is not ready", http.StatusServiceUnavailable)
				return
			}
			if err := reload(); err != nil {
				http.Error(w, err.Error(), http.StatusBadRequest)
				return
			}
			w.WriteHeader(http.StatusNoContent)
		})
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
