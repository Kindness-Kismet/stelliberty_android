#!/usr/bin/env bash
# 截图到 build/tmp/screenshots/。按项目规范先查 display id 再指定 id 截。
set -euo pipefail

DEVICE_ID=""
NAME="screen"

while [[ $# -gt 0 ]]; do
    case "$1" in
        -s|--serial) DEVICE_ID="$2"; shift 2 ;;
        -n|--name) NAME="$2"; shift 2 ;;
        -h|--help) echo "Usage: $0 [-s <serial>] [-n <name>]" >&2; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; exit 2 ;;
    esac
done

ADB=(adb)
if [[ -n "$DEVICE_ID" ]]; then
    ADB+=("-s" "$DEVICE_ID")
fi

# 仓库根：从 scripts/ 上溯四层（scripts → debug-app → skills → .claude 或 .codex）
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
OUTPUT_DIR="$ROOT/build/tmp/screenshots"
mkdir -p "$OUTPUT_DIR"

# 多屏设备（折叠屏、投屏）不指定 id 会截错屏；取第一个 HWC display
DISPLAY_ID="$("${ADB[@]}" shell dumpsys SurfaceFlinger --display-id | tr -d '\r' \
    | awk '/^Display [0-9-]+/ {print $2; exit}')"
if [[ -z "$DISPLAY_ID" ]]; then
    echo "Cannot resolve display id" >&2
    exit 1
fi

# 带时间戳：连续截图要能并排比对前后差异，固定文件名会把上一张覆盖掉
OUTPUT_FILE="$OUTPUT_DIR/${NAME}-$(date +%Y%m%d-%H%M%S).png"
MSYS_NO_PATHCONV=1 "${ADB[@]}" exec-out screencap -d "$DISPLAY_ID" -p > "$OUTPUT_FILE"

if [[ ! -s "$OUTPUT_FILE" ]]; then
    echo "Screenshot is empty; device may be locked or asleep" >&2
    rm -f "$OUTPUT_FILE"
    exit 1
fi

printf '%s\n' "$OUTPUT_FILE"
