- Fixed log streaming after proxy restarts and returning to the app, with accurate connection status and reliable automatic scrolling
- Added log export with timestamps, severity levels, and complete messages
- Added severity filtering for displayed and exported logs while preserving collected history
- Fixed node switching for group names containing special characters and prevented concurrent operations from overwriting selections or saving them to another subscription
- Added visible error messages when proxy requests fail
- Improved EasyTier connections with automatic retries after startup failures and automatic restarts when the instance stops

---

- 修复代理重启、返回应用后日志不再更新的问题，并修正连接状态显示与自动滚动
- 新增日志导出功能，包含时间、等级和完整消息
- 新增日志等级筛选，显示和导出均遵循所选等级，并保留已收集的历史记录
- 修复代理组名称含特殊字符时无法切换节点的问题，避免并发操作覆盖选择或将选择保存到其他订阅
- 新增代理请求失败提示
- 改善 EasyTier 连接恢复，启动失败后自动重试，实例停止后自动重启
