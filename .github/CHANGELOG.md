- Added separate page preferences for opening the app from its launcher icon and long-pressing its Quick Settings tile, with all four bottom tabs available and the current page preserved when returning from recent apps
- Improved traffic notifications, which now show live speed and traffic totals in VPN, ROOT TUN, and ROOT TPROXY modes
- Added a Close action to proxy notifications that stops the proxy and removes the notification, and changed the small icon to the app foreground outline
- Improved background resource usage by pausing home traffic and memory updates while the app is in the background and stopping notification updates when the notification is hidden or disabled
- Fixed proxy shutdown during repeated restarts and after ROOT service recreation, so closing the notification stops the core without restarting it
- Removed redundant section headings from six settings pages, keeping related options grouped in cards
- Fixed IPv6 connections in ROOT TPROXY mode failing to reach the proxy when the upstream network had no IPv6 default route

---

- 新增了桌面图标打开应用和长按快捷磁贴的独立页面设置，均可选择底栏四个页面，从最近任务返回时保留当前位置
- 对流量通知进行了改善，现在 VPN、ROOT TUN 和 ROOT TPROXY 三种模式都能显示实时网速和累计流量
- 新增了代理通知的“关闭”按钮，可停止代理并移除通知，同时将通知小图标改为应用前景轮廓
- 对后台资源占用进行了改善，进入后台时暂停首页流量与内存更新，通知被隐藏或禁用时停止通知刷新
- 修复了连续重启和 ROOT 服务重建后的代理关闭问题，现在关闭通知能停止核心，且不会再次启动
- 移除了六个设置页中多余的分组小标题，相关选项仍按卡片分组
- 修复了 ROOT TPROXY 模式下上游网络缺少 IPv6 默认路由时，IPv6 连接无法进入代理的问题
