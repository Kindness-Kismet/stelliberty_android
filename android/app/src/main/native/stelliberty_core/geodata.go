package main

import "C"

import (
	"archive/zip"
	"bufio"
	"fmt"
	"io"
	"os"

	"github.com/ulikunitz/xz"
)

// 把 APK 里 xz 压缩的 entry 解到 dst，失败返回 "error: " 前缀；dst 的原子替换由调用方负责。
//export stellibertyExtractXzAsset
func stellibertyExtractXzAsset(cApk, cEntry, cDst *C.char) *C.char {
	return guardString(func() string {
		if err := extractXzAsset(C.GoString(cApk), C.GoString(cEntry), C.GoString(cDst)); err != nil {
			return "error: " + err.Error()
		}
		return ""
	})
}

func extractXzAsset(apkPath, entry, dst string) error {
	apk, err := zip.OpenReader(apkPath)
	if err != nil {
		return err
	}
	defer apk.Close()
	src, err := apk.Open(entry)
	if err != nil {
		return err
	}
	defer src.Close()
	// xz 按字节读取头部与块边界，不加缓冲会慢一个数量级。
	reader, err := xz.NewReader(bufio.NewReaderSize(src, 1<<20))
	if err != nil {
		return fmt.Errorf("%s: %w", entry, err)
	}
	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	writer := bufio.NewWriterSize(out, 1<<20)
	if _, err := io.Copy(writer, reader); err != nil {
		out.Close()
		return fmt.Errorf("%s: %w", entry, err)
	}
	if err := writer.Flush(); err != nil {
		out.Close()
		return err
	}
	return out.Close()
}
