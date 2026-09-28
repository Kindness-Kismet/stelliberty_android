#!/usr/bin/env bash
# 把 Stelliberty 拉到前台并切到指定页面。
# 必须先 am start：page.open 靠组合树内的 collector 落地，应用不在前台时指令返回 ok 但无效果。
set -euo pipefail

DEVICE_ID=""
PAGE_ID=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        -s|--serial) DEVICE_ID="$2"; shift 2 ;;
        -p|--page) PAGE_ID="$2"; shift 2 ;;
        -h|--help) echo "Usage: $0 [-s <serial>] -p <page_id>" >&2; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; exit 2 ;;
    esac
done

if [[ -z "$PAGE_ID" ]]; then
    echo "Missing --page <page_id>" >&2
    exit 2
fi

ADB=(adb)
if [[ -n "$DEVICE_ID" ]]; then
    ADB+=("-s" "$DEVICE_ID")
fi

"${ADB[@]}" shell input keyevent KEYCODE_WAKEUP
"${ADB[@]}" shell wm dismiss-keyguard
"${ADB[@]}" shell am start -n com.stelliberty.android/.MainActivity >/dev/null

# 不在这里 sleep：DebugNavBridge.request 自己等 collector 就位，超时才返回 ok=false
RESULT="$(MSYS_NO_PATHCONV=1 "${ADB[@]}" shell content call \
    --uri content://com.stelliberty.android.commands \
    --method page.open \
    --arg "$PAGE_ID")"

printf '%s\n' "$RESULT"

if [[ "$RESULT" != *"ok=true"* ]]; then
    exit 1
fi

# 等 pager / 压栈动画收敛，截图才拍到稳定画面
sleep 1
