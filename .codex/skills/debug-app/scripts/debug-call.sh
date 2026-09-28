#!/usr/bin/env bash
# 调用 Stelliberty 的 debug 命令 Provider，按返回的 ok 反映退出码。
set -euo pipefail

DEVICE_ID=""
METHOD=""
ARG=""
EXTRAS=()

usage() {
    echo "Usage: $0 [-s <serial>] -m <domain.action> [-a <arg>] [-e <key:type:value>]..." >&2
    echo "  -e may repeat; type is s(tring) / b(oolean) / i(nt)" >&2
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -s|--serial) DEVICE_ID="$2"; shift 2 ;;
        -m|--method) METHOD="$2"; shift 2 ;;
        -a|--arg) ARG="$2"; shift 2 ;;
        -e|--extra) EXTRAS+=("$2"); shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; usage; exit 2 ;;
    esac
done

if [[ -z "$METHOD" ]]; then
    echo "Missing --method" >&2
    usage
    exit 2
fi

ADB=(adb)
if [[ -n "$DEVICE_ID" ]]; then
    ADB+=("-s" "$DEVICE_ID")
fi

# adb shell 把参数拼成一行交给设备上的 sh 执行，本机这层引号到不了那边。含空格或
# shell 元字符的值（节点名「香港 | 5」）必须按远端规则再引一次，否则竖线会被当成管道。
shquote() {
    printf "'%s'" "${1//\'/\'\\\'\'}"
}

REMOTE="content call --uri content://com.stelliberty.android.commands --method $(shquote "$METHOD")"
if [[ -n "$ARG" ]]; then
    REMOTE+=" --arg $(shquote "$ARG")"
fi
for extra in "${EXTRAS[@]+"${EXTRAS[@]}"}"; do
    REMOTE+=" --extra $(shquote "$extra")"
done

# MSYS_NO_PATHCONV：Git Bash 会把 content:// 之后的内容当路径改写
RESULT="$(MSYS_NO_PATHCONV=1 "${ADB[@]}" shell "$REMOTE")"
printf '%s\n' "$RESULT"

if [[ "$RESULT" != *"ok=true"* ]]; then
    exit 1
fi
