- Added app update checks with stable and test channels, so you can now check for new versions on the About page and get notified at startup
- Fixed an issue with the default subscription User-Agent that previously made some panels drop nodes such as Hysteria2 and fail to import with a missing proxy group error
- Fixed an issue with updating subscriptions through the proxy that previously still downloaded subscriptions and providers directly
- Fixed an issue with provider caches that previously made the core download providers again before every start, especially in ROOT mode, and let files with the same name overwrite each other
- Fixed an issue with proxy startup that previously failed while the core was still downloading providers
- Improved startup error reports, which now keep the beginning of the core log so config parsing errors still show when there are many providers

---

- 新增了应用更新检查，现在可以在关于页面按稳定版或测试版渠道检查新版本，并在启动时收到新版本提醒
- 修复了订阅默认 User-Agent 的问题，该问题曾导致部分面板过滤掉 Hysteria2 等节点，导入时报代理组不存在
- 修复了通过代理更新订阅的问题，该问题曾导致订阅和 provider 实际上仍然直连下载
- 修复了 provider 缓存的问题，该问题曾导致每次启动前核心都要重新下载 provider（ROOT 模式尤为明显），同名文件还会相互覆盖
- 修复了启动代理的问题，该问题曾导致核心还在下载 provider 时就被判定启动失败
- 对启动失败的错误信息进行了改善，现在会保留核心日志的开头部分，provider 较多时也能看到配置解析阶段的错误
