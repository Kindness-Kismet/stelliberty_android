#!/usr/bin/env python3
"""从 MingCute 的 SVG 生成 Compose ImageVector。

一次性工具：改 ICONS 表后重跑，产物提交进仓库。运行时不依赖网络，也不引 MingCute 依赖——
只取用到的那二十几个图标，避免 material-icons-extended 那种「为 3 个图标拖进整个库」。

用法：
    python scripts/gen_icons.py            # 增量：SVG 已缓存则不重新下载
    python scripts/gen_icons.py --force    # 强制重新下载
"""

from __future__ import annotations

import argparse
import re
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_RAW = "https://raw.githubusercontent.com/Richard9394/MingCute/main/assets/svg/core"
SVG_CACHE = Path("build/tmp/mingcute/svg")
OUT_PACKAGE = "com.stelliberty.android.ui.icon"
OUT_DIR = Path("android/app/src/main/kotlin/com/stelliberty/android/ui/icon")
SVG_NS = "{http://www.w3.org/2000/svg}"

# AppIcons 属性名 → MingCute 的 <风格>/<分类>/<文件>.svg（风格取 regular / filled）。左侧对齐
# 调用点语义、右侧是原名；生成的 val 按右侧命名，语义名可共用一个。换图标只改右侧，调用点不动。
ICONS: dict[str, str] = {
    "Add": "regular/system/add.svg",
    "ArrowRight": "regular/arrow/arrow_right.svg",
    "Back": "regular/arrow/arrow_left.svg",
    "Backup": "regular/files/upload.svg",
    "Check": "regular/system/check.svg",
    "ChevronRight": "regular/arrow/right_small.svg",
    "Close": "regular/system/close.svg",
    "Delete": "regular/system/delete_2.svg",
    "Info": "regular/system/information.svg",
    "More": "regular/system/more_1.svg",
    "MoveDown": "regular/arrow/down_small.svg",
    "MoveUp": "regular/arrow/up_small.svg",
    "Pin": "regular/files/pin.svg",
    "Refresh": "regular/system/refresh_2.svg",
    "Restore": "regular/files/download.svg",
    "Search": "regular/files/search.svg",
    "SearchCleanup": "regular/system/close_circle.svg",
    "Sort": "regular/editing/sort_ascending.svg",
    "TestDelay": "regular/devices/dashboard_2.svg",
    "Unpin": "regular/system/close_circle.svg",
    "Wifi": "regular/devices/wifi.svg",
    # 四个 Tab 各一对：未选中用 regular，选中用 filled
    "NavHome": "regular/buildings/home_2.svg",
    "NavHomeActive": "filled/buildings/home_2.svg",
    "NavProxy": "regular/shapes/shield_shape.svg",
    "NavProxyActive": "filled/shapes/shield_shape.svg",
    "NavSubscription": "regular/weather/moon_cloudy.svg",
    "NavSubscriptionActive": "filled/weather/moon_cloudy.svg",
    "NavSettings": "regular/system/settings_3.svg",
    "NavSettingsActive": "filled/system/settings_3.svg",
}


def vector_name(rel: str) -> str:
    """`regular/system/close_circle.svg` → `MingCuteCloseCircle`；filled 风格加 `Filled` 后缀。"""
    style, _, tail = rel.partition("/")
    stem = tail.rsplit("/", 1)[-1].removesuffix(".svg")
    name = "MingCute" + "".join(part.capitalize() for part in stem.split("_"))
    return name + "Filled" if style == "filled" else name

CAP = {"butt": "Butt", "round": "Round", "square": "Square"}
JOIN = {"miter": "Miter", "round": "Round", "bevel": "Bevel"}


def project_root() -> Path:
    """按标志文件向上找仓库根，避免脚本里出现绝对路径。"""
    for candidate in [Path.cwd(), *Path(__file__).resolve().parents]:
        if (candidate / ".git").exists() and (candidate / "android" / "settings.gradle.kts").exists():
            return candidate
    sys.exit("error: 找不到仓库根（需同时存在 .git 与 android/settings.gradle.kts）")


def fetch(rel: str, dest: Path, force: bool) -> str:
    if dest.exists() and not force:
        return dest.read_text(encoding="utf-8")
    url = f"{REPO_RAW}/{rel}"
    try:
        with urllib.request.urlopen(url, timeout=30) as resp:
            if resp.status != 200:
                sys.exit(f"error: {url} 返回 {resp.status}")
            body = resp.read().decode("utf-8")
    except urllib.error.URLError as exc:
        sys.exit(f"error: 下载 {url} 失败：{exc}")
    if "<svg" not in body:
        sys.exit(f"error: {url} 不是 SVG（限流页面会被原样写入）")
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(body, encoding="utf-8")
    return body


def parse_paths(svg: str, rel: str) -> tuple[float, float, list[dict[str, str]]]:
    """抽出 viewBox 与各 <path>。只认 <path>——遇到 rect/circle 直接报错，
    不静默跳过：漏掉一段的图标看起来只是「画得不对」，追起来很费时间。"""
    root = ET.fromstring(svg)
    box = (root.get("viewBox") or "0 0 24 24").split()
    if len(box) != 4:
        sys.exit(f"error: {rel} 的 viewBox 非法：{root.get('viewBox')}")
    width, height = float(box[2]), float(box[3])

    paths: list[dict[str, str]] = []
    for node in root.iter():
        tag = node.tag.replace(SVG_NS, "")
        if tag in ("svg", "g", "defs", "title", "desc"):
            continue
        if tag != "path":
            sys.exit(f"error: {rel} 含不支持的元素 <{tag}>，需扩展生成器")
        data = node.get("d")
        if not data:
            sys.exit(f"error: {rel} 有 <path> 缺少 d")
        paths.append(
            {
                "d": re.sub(r"\s+", " ", data).strip(),
                "fill": (node.get("fill") or "none").strip(),
                "fill_rule": (node.get("fill-rule") or "nonzero").strip(),
                "stroke": (node.get("stroke") or "none").strip(),
                "stroke_width": (node.get("stroke-width") or "1").strip(),
                "linecap": (node.get("stroke-linecap") or "butt").strip(),
                "linejoin": (node.get("stroke-linejoin") or "miter").strip(),
            }
        )
    if not paths:
        sys.exit(f"error: {rel} 没有任何 <path>")
    return width, height, paths


def render_path(p: dict[str, str], rel: str) -> str:
    """描边与填充都落成 Color.Black；实际着色由调用点的 tint 覆盖，
    源 SVG 里的 #10161F 属于 MingCute 的预览色，不该带进来。"""
    has_fill = p["fill"] not in ("none", "")
    has_stroke = p["stroke"] not in ("none", "")
    if not has_fill and not has_stroke:
        sys.exit(f"error: {rel} 有既不填充也不描边的 path")
    if p["linecap"] not in CAP:
        sys.exit(f"error: {rel} 未知 stroke-linecap：{p['linecap']}")
    if p["linejoin"] not in JOIN:
        sys.exit(f"error: {rel} 未知 stroke-linejoin：{p['linejoin']}")

    fill_type = "EvenOdd" if p["fill_rule"] == "evenodd" else "NonZero"
    lines = [
        "        addPath(",
        f'            pathData = PathParser().parsePathString("{p["d"]}").toNodes(),',
        f"            pathFillType = PathFillType.{fill_type},",
        f"            fill = {'SolidColor(Color.Black)' if has_fill else 'null'},",
    ]
    if has_stroke:
        lines += [
            "            stroke = SolidColor(Color.Black),",
            f"            strokeLineWidth = {float(p['stroke_width'])}f,",
            f"            strokeLineCap = StrokeCap.{CAP[p['linecap']]},",
            f"            strokeLineJoin = StrokeJoin.{JOIN[p['linejoin']]},",
        ]
    lines.append("        )")
    return "\n".join(lines)


def render_icon(val: str, rel: str, width: float, height: float, paths: list[dict[str, str]]) -> str:
    body = "\n".join(render_path(p, rel) for p in paths)
    imports = [
        "androidx.compose.ui.graphics.Color",
        "androidx.compose.ui.graphics.PathFillType",
        "androidx.compose.ui.graphics.SolidColor",
        "androidx.compose.ui.graphics.vector.ImageVector",
        "androidx.compose.ui.graphics.vector.PathParser",
        "androidx.compose.ui.unit.dp",
    ]
    if any(p["stroke"] not in ("none", "") for p in paths):
        imports += [
            "androidx.compose.ui.graphics.StrokeCap",
            "androidx.compose.ui.graphics.StrokeJoin",
        ]
    import_block = "\n".join(f"import {i}" for i in sorted(imports))
    return f"""// 由 scripts/gen_icons.py 从 MingCute 生成，勿手改。源：{rel}
package {OUT_PACKAGE}

{import_block}

internal val {val}: ImageVector by lazy {{
    ImageVector.Builder(
        name = "{val.removeprefix("MingCute")}",
        defaultWidth = {width:g}.dp,
        defaultHeight = {height:g}.dp,
        viewportWidth = {width:g}f,
        viewportHeight = {height:g}f,
        autoMirror = false,
    ).apply {{
{body}
    }}.build()
}}
"""


def render_entry() -> str:
    props = "\n".join(
        f"    val {name}: ImageVector get() = {vector_name(rel)}" for name, rel in sorted(ICONS.items())
    )
    return f"""// 由 scripts/gen_icons.py 生成，勿手改。改图标请改脚本里的 ICONS 表后重跑。
package {OUT_PACKAGE}

import androidx.compose.ui.graphics.vector.ImageVector

/** 全应用图标入口，路径来自 MingCute。`Nav*Active` 是 filled 风格，其余为 regular（线性描边）。 */
object AppIcons {{
{props}
}}
"""


def main() -> int:
    parser = argparse.ArgumentParser(description="从 MingCute SVG 生成 Compose ImageVector")
    parser.add_argument("--force", action="store_true", help="忽略缓存重新下载")
    args = parser.parse_args()

    root = project_root()
    cache = root / SVG_CACHE
    out = root / OUT_DIR
    out.mkdir(parents=True, exist_ok=True)

    for rel in sorted(set(ICONS.values())):
        svg = fetch(rel, cache / rel, args.force)
        width, height, paths = parse_paths(svg, rel)
        val = vector_name(rel)
        (out / f"{val}.kt").write_text(render_icon(val, rel, width, height, paths), encoding="utf-8", newline="\n")
        aliases = sorted(n for n, r in ICONS.items() if r == rel)
        print(f"[ ok ] {val}.kt  <- {rel}  ({len(paths)} path)  {', '.join(aliases)}")

    (out / "AppIcons.kt").write_text(render_entry(), encoding="utf-8", newline="\n")
    print(f"[ ok ] AppIcons.kt  ({len(ICONS)} 个语义名 / {len(set(ICONS.values()))} 个 vector)")
    print(f"[info] 输出目录 {OUT_DIR}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
