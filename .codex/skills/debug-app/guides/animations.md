# 动画速度

```bash
.claude/skills/debug-app/scripts/debug-call.sh -s <device_id> -m animation.set_speed -a 0.2
.claude/skills/debug-app/scripts/debug-call.sh -s <device_id> -m animation.get_speed
.claude/skills/debug-app/scripts/debug-call.sh -s <device_id> -m animation.reset_speed
```

| method | 参数 | 作用 |
|---|---|---|
| `animation.set_speed` | 速度倍率（arg 或 `value` extra），范围 `0.05..1` | 实时调整本应用 Compose 动画速度 |
| `animation.get_speed` | 无 | 返回当前速度与对应的时长倍率 |
| `animation.reset_speed` | 无 | 恢复 `1x` 正常速度 |

`0.05x` 是二十分之一速度，`0.2x` 是五分之一速度，`1x` 是正常速度；范围内小数均可用。参数不合法时返回 `ok=false` 并保留原值，`0` 不作为关闭动画的档位。

速度通过窗口 Recomposer 的 `MotionDurationScale` 应用于 Compose 与 miuix 页面、菜单和弹窗动画；只在 debug 包的当前进程有效，进程重启恢复 `1x`。系统动画设置、网络请求、测速超时与业务定时不受影响。

慢放时，页面和菜单展开后等待动画结束再按测试 ID 操作；原本 `sleep 1` 的等待按速度倍率适当延长。验证完成后调用 `animation.reset_speed`。
