#!/usr/bin/env bash
# 按测试 ID 定位控件并操作：列出 / 断言存在 / 读文本 / 点击 / 输入。
# 点击走真实 input tap，经系统输入派发，因此能证明控件确实可点；这一点和直接调 onClick 不同。
set -euo pipefail

DEVICE_ID=""
ACTION=""
TEST_ID=""
VALUE=""

usage() {
    cat >&2 <<'EOF'
Usage: debug-control.sh [-s <serial>] -c <action> [-i <test_id>] [-v <value>]

  -c list           列出当前界面全部测试 ID
  -c text           列出当前界面全部可见文字
  -c exists -i ID   断言控件存在，不存在则退出码 1
  -c get -i ID      读控件文字
  -c click -i ID    点击控件中心
  -c input -i ID -v VALUE   点进控件后输入文本
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -s|--serial) DEVICE_ID="$2"; shift 2 ;;
        -c|--action) ACTION="$2"; shift 2 ;;
        -i|--id) TEST_ID="$2"; shift 2 ;;
        -v|--value) VALUE="$2"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; usage; exit 2 ;;
    esac
done

if [[ -z "$ACTION" ]]; then
    echo "Missing --action" >&2
    usage
    exit 2
fi

ADB=(adb)
if [[ -n "$DEVICE_ID" ]]; then
    ADB+=("-s" "$DEVICE_ID")
fi

# adb shell 把参数拼成一行交给设备上的 sh，本机这层引号到不了那边。测试 ID 里嵌着组名和
# 节点名（「Proxy.Node.Example 🔹 香港 | 5」），竖线不再引一次就会被当成管道。
shquote() {
    printf "'%s'" "${1//\'/\'\\\'\'}"
}

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
DUMP="$ROOT/build/tmp/ui-dump.xml"
mkdir -p "$(dirname "$DUMP")"

# Windows 版 python 认不了 Git Bash 的 /<盘符>/... 形式，转成 <盘符>:\... 再传进去
DUMP_FOR_PY="$DUMP"
if command -v cygpath >/dev/null 2>&1; then
    DUMP_FOR_PY="$(cygpath -w "$DUMP")"
fi

# 每次操作都重新 dump：上一次点击很可能已经改变了界面，复用旧快照会按过期坐标点。
# MSYS_NO_PATHCONV：Git Bash 会把设备路径 /sdcard/... 改写成 <Git 安装目录>/sdcard/...，
# dump 于是写到别处，本地只拿到 0 字节文件。exec-out 取内容避免 shell 层再插 CR。
MSYS_NO_PATHCONV=1 "${ADB[@]}" shell uiautomator dump /sdcard/debug-ui.xml >/dev/null 2>&1
MSYS_NO_PATHCONV=1 "${ADB[@]}" exec-out cat /sdcard/debug-ui.xml > "$DUMP" 2>/dev/null

if [[ ! -s "$DUMP" ]]; then
    echo "UI dump failed or empty" >&2
    exit 1
fi

case "$ACTION" in
    list|text)
        python - "$DUMP_FOR_PY" "$ACTION" <<'PY'
import html, re, sys
dump, action = sys.argv[1], sys.argv[2]
text = open(dump, encoding="utf-8", errors="replace").read()
field = "resource-id" if action == "list" else "text"
# uiautomator 把 emoji 写成 &#128313; 这种数字引用，要还原成字面字符再输出，
# 否则列出来的 ID 直接拿去 -i 定位会对不上（那条路径经 XML 解析、引用已被还原）。
values = [
    html.unescape(v) for v in re.findall(rf'{field}="([^"]*)"', text) if v.strip()
]
if action == "list":
    values = [v for v in values if not v.startswith("android:")]
for v in dict.fromkeys(values):
    print(v)
PY
        ;;

    exists|get|click|input)
        if [[ -z "$TEST_ID" ]]; then
            echo "Action '$ACTION' needs --id" >&2
            exit 2
        fi
        if [[ "$ACTION" == "input" && -z "$VALUE" ]]; then
            echo "Action 'input' needs --value" >&2
            exit 2
        fi

        NODE="$(python - "$DUMP_FOR_PY" "$TEST_ID" <<'PY'
import re, sys
from xml.etree import ElementTree

dump, wanted = sys.argv[1], sys.argv[2]
root = ElementTree.parse(dump).getroot()


def center(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


# 文字可能不在挂 testTag 的那个节点上（Compose 的按钮里文字是子节点），所以连子树一起找
def subtree_text(node):
    parts = []
    for n in node.iter():
        t = n.get("text", "").strip()
        if t:
            parts.append(t)
    return " ".join(parts)


for node in root.iter("node"):
    if node.get("resource-id") != wanted:
        continue
    point = center(node)
    if point is None:
        continue
    print(f"{point[0]} {point[1]} {subtree_text(node)}")
    break
PY
)"

        if [[ -z "$NODE" ]]; then
            echo "未找到控件: $TEST_ID" >&2
            exit 1
        fi

        CX="$(echo "$NODE" | awk '{print $1}')"
        CY="$(echo "$NODE" | awk '{print $2}')"
        LABEL="$(echo "$NODE" | cut -d' ' -f3-)"

        case "$ACTION" in
            exists) printf 'ok %s\n' "$TEST_ID" ;;
            get) printf '%s\n' "$LABEL" ;;
            click)
                "${ADB[@]}" shell input tap "$CX" "$CY"
                printf 'clicked %s at %s,%s\n' "$TEST_ID" "$CX" "$CY"
                ;;
            input)
                "${ADB[@]}" shell input tap "$CX" "$CY"
                sleep 1
                # input text 不认空格，用 %s 占位；其余标点仍要调用方自己转义
                "${ADB[@]}" shell "input text $(shquote "${VALUE// /%s}")"
                printf 'typed into %s\n' "$TEST_ID"
                ;;
        esac
        ;;

    *)
        echo "Unknown action: $ACTION" >&2
        usage
        exit 2
        ;;
esac
