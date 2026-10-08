package main

/*
#include <stdlib.h>
*/
import "C"

import (
	"fmt"
	"os"
	"strings"
	"sync"
	"unsafe"

	"github.com/metacubex/mihomo/component/age"
	"github.com/metacubex/mihomo/component/http"
	"github.com/metacubex/mihomo/constant"
)

// Go 的崩溃穿过 C 边界会直接终止宿主进程，而这些函数是在应用自己的进程里跑的，死的是整个应用。
// 所以在这里把崩溃收成一个错误字符串返回；只能覆盖同一条协程。
func guardString(fn func() string) (ret *C.char) {
	defer func() {
		if r := recover(); r != nil {
			ret = C.CString(fmt.Sprintf("error: panic: %v", r))
		}
	}()
	return C.CString(fn())
}

var (
	cancelRegistry sync.Map
	progressStore  sync.Map
	initOnce       sync.Once
)

//export stellibertyCoreInit
func stellibertyCoreInit(homeDir *C.char) {
	initOnce.Do(func() {
		constant.SetHomeDir(C.GoString(homeDir))
		// 面板按 UA 里的客户端名与版本裁剪节点（如 CMFA < 2.9.0 不下发 Hysteria2），版本必须如实；
		// 取内核 global-ua 默认值，与运行时刷新 provider 一致。
		http.SetUA("clash.meta/" + constant.Version)
	})
}

// Go 分配并返回的 C 字符串必须由 Go 这边释放，C 那边调 free 会破坏 Go 运行时的内存管理。
//export stellibertyFreeString
func stellibertyFreeString(s *C.char) {
	if s != nil {
		C.free(unsafe.Pointer(s))
	}
}

//export stellibertyCancel
func stellibertyCancel(token C.int) {
	if v, ok := cancelRegistry.Load(int32(token)); ok {
		if cancel, ok := v.(func()); ok {
			cancel()
		}
	}
}

//export stellibertyQueryProgress
func stellibertyQueryProgress(token C.int) *C.char {
	if v, ok := progressStore.Load(int32(token)); ok {
		if s, ok := v.(string); ok {
			return C.CString(s)
		}
	}
	return nil
}

//export stellibertySetAgeSecretKey
func stellibertySetAgeSecretKey(cKey *C.char) {
	key := strings.TrimSpace(C.GoString(cKey))
	if key == "" {
		age.SetGlobalSecretKeys()
	} else {
		age.SetGlobalSecretKeys(key)
	}
}

// PC 保存的是解密后的明文订阅，备份导出时用它解出明文；未加密的内容原样写到 dst。
//export stellibertyDecryptFile
func stellibertyDecryptFile(cSrc, cDst, cKey *C.char) *C.char {
	return guardString(func() string {
		data, err := os.ReadFile(C.GoString(cSrc))
		if err != nil {
			return "error: " + err.Error()
		}
		plain, err := age.DecryptBytes(data, strings.TrimSpace(C.GoString(cKey)))
		if err != nil {
			return "error: " + err.Error()
		}
		if err := os.WriteFile(C.GoString(cDst), plain, 0o600); err != nil {
			return "error: " + err.Error()
		}
		return ""
	})
}

//export stellibertyGenAgeKeyPair
func stellibertyGenAgeKeyPair() *C.char {
	return guardString(func() string {
		sk, pk, err := age.GenX25519KeyPair()
		if err != nil {
			return "error: " + err.Error()
		}
		return sk + "\n" + pk
	})
}

//export stellibertyGenAgeHybridKeyPair
func stellibertyGenAgeHybridKeyPair() *C.char {
	return guardString(func() string {
		sk, pk, err := age.GenHybridKeyPair()
		if err != nil {
			return "error: " + err.Error()
		}
		return sk + "\n" + pk
	})
}

func main() {}
