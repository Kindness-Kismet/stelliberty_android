#!/usr/bin/env python3
"""编译 Stelliberty，默认 release，--dev 选择 debug，APK 保存到 build/apk。"""
import os
import sys

os.environ["PYTHONDONTWRITEBYTECODE"] = "1"
sys.dont_write_bytecode = True

import argparse
import re
import shutil
import subprocess
import time
from pathlib import Path

from prebuild import (
    PREBUILD_MARKER,
    Console,
    Toolchain,
    apply_native_patches,
    detect_toolchain,
    fail,
    gradle_root,
    missing_resources,
    resolve_project_root,
    run_gradle,
)

APK_OUTPUT_DIR = Path("build/apk")
DEFAULT_ABI = "arm64-v8a"
ABI_CHOICES = ("arm64-v8a", "x86_64")


def prepare_build(console: Console, project_root: Path) -> Toolchain:
    if not (project_root / PREBUILD_MARKER).is_file() or missing_resources(project_root):
        command = [sys.executable, str(project_root / "scripts/prebuild.py")]
        if console.verbose:
            command.append("--verbose")
        console.info("preparing build resources")
        if subprocess.run(command, cwd=project_root).returncode != 0:
            fail("prebuild failed")
    apply_native_patches(project_root)
    return detect_toolchain(console, project_root)


# === 工程信息 ===


def read_version_name(project_root: Path) -> str:
    config = gradle_root(project_root) / "buildSrc" / "src" / "main" / "kotlin" / "ProjectConfig.kt"
    match = re.search(r'VERSION_NAME\s*=\s*"([^"]+)"', config.read_text(encoding="utf-8"))
    if not match:
        fail(f"VERSION_NAME missing in {config.relative_to(project_root).as_posix()}")
    return match.group(1)


def read_app_name(project_root: Path) -> str:
    config = gradle_root(project_root) / "buildSrc" / "src" / "main" / "kotlin" / "ProjectConfig.kt"
    match = re.search(r'APP_NAME\s*=\s*"([^"]+)"', config.read_text(encoding="utf-8"))
    if not match:
        fail(f"APP_NAME missing in {config.relative_to(project_root).as_posix()}")
    return match.group(1)


def abi_task_suffix(abi: str) -> str:
    return abi.replace("-", "_")


def abi_args(abi: str) -> list[str]:
    """非默认 ABI 才传 property，保持默认调用与 CI 行为一致。"""
    return [] if abi == DEFAULT_ABI else [f"-Pstelliberty.abis={abi}"]


def version_args(version: str | None) -> list[str]:
    if version is None:
        return []
    if not re.fullmatch(r"(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)(?:-beta[1-9]\d*)?", version):
        fail("version must be major.minor.patch or major.minor.patch-betaN")
    return [f"-Pstelliberty.versionName={version}"]


# === 命令 ===


def collect_apk(console: Console, project_root: Path, build_type: str, abi: str) -> Path:
    source_dir = gradle_root(project_root) / "app" / "build" / "outputs" / "apk" / build_type
    if not source_dir.is_dir():
        fail(f"apk output dir missing: {source_dir.relative_to(project_root).as_posix()}")
    # 文件名由版本号与 ABI split 决定，只收集本次架构的安装包。
    matches = sorted(source_dir.glob(f"*{abi}*.apk"), key=lambda p: p.stat().st_mtime, reverse=True)
    if not matches:
        listed = ", ".join(sorted(p.name for p in source_dir.glob("*.apk"))) or "none"
        fail(f"no apk matching abi={abi}; found: {listed}")
    source = matches[0]
    output_dir = project_root / APK_OUTPUT_DIR
    output_dir.mkdir(parents=True, exist_ok=True)
    target = output_dir / source.name
    shutil.copy2(source, target)
    size_mb = target.stat().st_size / 1024 / 1024
    console.ok(f"apk={target.relative_to(project_root).as_posix()} size_mb={size_mb:.2f}")
    return target


def command_compile(args: argparse.Namespace) -> None:
    """只编 Kotlin，跳过 Go cgo——秒级，改 Kotlin 后的默认验证手段。"""
    start = time.monotonic()
    console = Console(args.verbose)
    project_root = resolve_project_root()
    toolchain = prepare_build(console, project_root)
    variant = args.build_type.capitalize()
    tasks = [
        f":app:compile{variant}Kotlin",
        "-x", f"buildMihomo_{abi_task_suffix(args.abi)}",
        *abi_args(args.abi),
        *version_args(args.version),
    ]
    if run_gradle(console, project_root, toolchain, tasks) != 0:
        fail("compile failed")
    console.ok(f"compile done ({time.monotonic() - start:.2f}s)")


def command_apk(args: argparse.Namespace) -> None:
    start = time.monotonic()
    console = Console(args.verbose)
    project_root = resolve_project_root()
    toolchain = prepare_build(console, project_root)

    console.info(
        f"app={read_app_name(project_root)} version={args.version or read_version_name(project_root)} "
        f"type={args.build_type} abi={args.abi} jdk={toolchain.jdk.major} go={toolchain.go.version}"
    )
    console.step(1, 2, "Build APK")
    tasks = (
        (["clean"] if args.clean else []) + [f"assemble{args.build_type.capitalize()}"]
        + abi_args(args.abi) + version_args(args.version)
    )
    if run_gradle(console, project_root, toolchain, tasks) != 0:
        fail("apk build failed")
    console.ok("apk build done")

    console.step(2, 2, "Collect APK")
    collect_apk(console, project_root, args.build_type, args.abi)
    console.ok(f"done ({time.monotonic() - start:.2f}s)")


def command_gradle(args: argparse.Namespace) -> None:
    tasks = args.task[1:] if args.task[:1] == ["--"] else args.task
    if not tasks:
        fail("a Gradle task is required")
    console = Console(args.verbose)
    project_root = resolve_project_root()
    toolchain = prepare_build(console, project_root)
    raise SystemExit(run_gradle(console, project_root, toolchain, tasks))


def build_parser() -> argparse.ArgumentParser:
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument(
        "-v", "--verbose", action="store_true", default=argparse.SUPPRESS,
        help="Print detection details and Gradle commands",
    )
    build_options = argparse.ArgumentParser(add_help=False)
    build_options.add_argument(
        "--version", default=argparse.SUPPRESS,
        help="Override the APK version without changing ProjectConfig.kt",
    )
    build_options.add_argument(
        "--abi", default=argparse.SUPPRESS, choices=ABI_CHOICES,
        help=f"Target ABI; default: {DEFAULT_ABI}",
    )
    variant = build_options.add_mutually_exclusive_group()
    variant.add_argument(
        "--dev", dest="build_type", action="store_const", const="debug",
        default=argparse.SUPPRESS, help="Build the development (debug) version",
    )
    variant.add_argument(
        "--build-type", default=argparse.SUPPRESS, choices=("debug", "release"),
        help="Gradle build type; default: release",
    )
    parser = argparse.ArgumentParser(
        prog="stelliberty-build",
        description="Compile Stelliberty; defaults to a release APK.",
        epilog="Development build: python scripts/build.py --dev",
        parents=[common, build_options],
    )
    parser.add_argument("--clean", action="store_true", help="Run the clean task first")
    sub = parser.add_subparsers(dest="command")

    compile_cmd = sub.add_parser(
        "compile", parents=[common, build_options],
        help="Compile Kotlin only, skipping the Go build",
    )
    compile_cmd.set_defaults(func=command_compile)

    apk = sub.add_parser(
        "apk", parents=[common, build_options], help="Build an APK into build/apk",
    )
    apk.add_argument("--clean", action="store_true", default=argparse.SUPPRESS, help="Run the clean task first")
    apk.set_defaults(func=command_apk)

    gradle = sub.add_parser("gradle", parents=[common], help="Run custom Gradle tasks")
    gradle.add_argument("task", nargs=argparse.REMAINDER, help="Tasks and flags forwarded to the wrapper")
    gradle.set_defaults(func=command_gradle)
    return parser


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    defaults = argparse.Namespace(
        verbose=False, build_type="release", abi=DEFAULT_ABI, version=None, clean=False, func=command_apk,
    )
    args = build_parser().parse_args(namespace=defaults)
    args.func(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
