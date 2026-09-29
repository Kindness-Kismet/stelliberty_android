#!/usr/bin/env python3
"""准备构建环境：子模块、Gradle 启动器、便携 JDK / Go 与 GeoIP 资源。"""
import os
import sys

os.environ["PYTHONDONTWRITEBYTECODE"] = "1"
sys.dont_write_bytecode = True

import argparse
import hashlib
import json
import platform
import re
import shutil
import subprocess
import tarfile
import tempfile
import urllib.request
import zipfile
from dataclasses import dataclass
from pathlib import Path

MIN_JDK = 26
JDK_DIR = Path("build/jdk")
GO_DIR = Path("build/go")
TMP_DIR = Path("build/tmp")
PREBUILD_MARKER = Path("build/prebuild.ready")
GEO_ASSETS = ("geoip.metadb.xz", "GeoIP.dat.xz", "geosite.dat.xz", "ASN.mmdb.xz")
GRADLE_WRAPPER_FILES = ("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar")
ADOPTIUM_URL = "https://api.adoptium.net/v3/binary/latest/{major}/ga/{os_name}/{arch}/jdk/hotspot/normal/eclipse"
GO_RELEASES_URL = "https://go.dev/dl/?mode=json"


@dataclass(frozen=True)
class Jdk:
    home: Path
    major: int


@dataclass(frozen=True)
class Go:
    home: Path
    version: str


@dataclass(frozen=True)
class Toolchain:
    jdk: Jdk
    go: Go
    sdk_dir: Path


class Console:
    def __init__(self, verbose: bool = False) -> None:
        self.verbose = verbose

    def info(self, message: str) -> None:
        print(f"[info] {message}")

    def ok(self, message: str) -> None:
        print(f"[ ok ] {message}")

    def step(self, index: int, total: int, message: str) -> None:
        print(f"[{index}/{total}] {message}")


def fail(message: str) -> None:
    raise SystemExit(f"error: {message}")


def resolve_project_root() -> Path:
    current = Path(__file__).resolve().parent
    for candidate in (current, *current.parents):
        if (candidate / ".git").exists() and (candidate / "android" / "settings.gradle.kts").is_file():
            return candidate
    fail("project root not found next to this script")


def read_java_major(java_home: Path) -> int | None:
    release = java_home / "release"
    version = None
    if release.is_file():
        match = re.search(r'^JAVA_VERSION="([^"]+)"', release.read_text(encoding="utf-8", errors="replace"), re.M)
        version = match.group(1) if match else None
    if version is None:
        launcher = java_home / "bin" / ("java.exe" if os.name == "nt" else "java")
        if not launcher.is_file():
            return None
        result = subprocess.run([str(launcher), "-version"], capture_output=True, text=True, errors="replace")
        match = re.search(r'version "([^"]+)"', result.stderr + result.stdout)
        version = match.group(1) if match else None
    if version is None:
        return None
    parts = version.split(".")
    major = parts[1] if parts[0] == "1" and len(parts) > 1 else parts[0]
    parsed = int(re.sub(r"\D.*$", "", major) or 0)
    return parsed or None


def _is_jdk_home(path: Path) -> bool:
    launcher = path / "bin" / ("java.exe" if os.name == "nt" else "java")
    return launcher.is_file() and (path / "release").is_file()


def find_jdk_home(root: Path) -> Path | None:
    if not root.is_dir():
        return None
    if _is_jdk_home(root):
        return root
    for child in sorted(p for p in root.iterdir() if p.is_dir()):
        if _is_jdk_home(child):
            return child
        # macOS 归档多一层 Contents/Home
        mac_home = child / "Contents" / "Home"
        if _is_jdk_home(mac_home):
            return mac_home
    return None


def current_portable_jdk(project_root: Path) -> Jdk | None:
    home = find_jdk_home(project_root / JDK_DIR)
    if home is None:
        return None
    major = read_java_major(home)
    if major is None or major < MIN_JDK:
        return None
    return Jdk(home, major)


def host_target() -> tuple[str, str]:
    machine = platform.machine().lower()
    if machine in ("amd64", "x86_64"):
        arch = "amd64"
    elif machine in ("arm64", "aarch64"):
        arch = "arm64"
    else:
        fail(f"unsupported CPU for portable toolchains: {machine}")
    if os.name == "nt":
        return "windows", arch
    if sys.platform == "darwin":
        return "darwin", arch
    return "linux", arch


def adoptium_target() -> tuple[str, str]:
    os_name, arch = host_target()
    return ("mac" if os_name == "darwin" else os_name), {"amd64": "x64", "arm64": "aarch64"}[arch]


def _archive_kind(path: Path) -> str:
    with path.open("rb") as handle:
        magic = handle.read(4)
    if magic[:2] == b"PK":
        return "zip"
    if magic[:2] == b"\x1f\x8b":
        return "tar.gz"
    fail("downloaded toolchain archive is not zip or tar.gz")


def _extract(archive: Path, dest: Path) -> None:
    if _archive_kind(archive) == "zip":
        with zipfile.ZipFile(archive) as zf:
            zf.extractall(dest)
        return
    with tarfile.open(archive) as tf:
        try:
            tf.extractall(dest, filter="data")
        except TypeError:
            tf.extractall(dest)


def _download(url: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": "stelliberty-prebuild"})
    with urllib.request.urlopen(request) as response, dest.open("wb") as out:
        shutil.copyfileobj(response, out)


def install_portable_jdk(project_root: Path, *, verbose: bool = False) -> Jdk:
    os_name, arch = adoptium_target()
    url = ADOPTIUM_URL.format(major=MIN_JDK, os_name=os_name, arch=arch)
    dest = project_root / JDK_DIR
    print(f"[info] downloading Temurin {MIN_JDK} {os_name}/{arch}")
    if verbose:
        print(f"[info] url: {url}")
    if dest.exists():
        shutil.rmtree(dest)
    dest.mkdir(parents=True)
    tmp_root = project_root / TMP_DIR
    tmp_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="stelliberty-jdk-", dir=tmp_root) as tmp:
        archive = Path(tmp) / "jdk.bin"
        _download(url, archive)
        _extract(archive, dest)
    home = find_jdk_home(dest)
    if home is None:
        fail(f"extracted archive has no JDK under {JDK_DIR.as_posix()}")
    major = read_java_major(home)
    if major is None or major < MIN_JDK:
        fail(f"portable JDK is {major}, need {MIN_JDK}+")
    print(f"[ ok ] portable jdk={home.relative_to(project_root).as_posix()} major={major}")
    return Jdk(home, major)


def go_version_key(version: str) -> tuple[int, ...]:
    return tuple(int(part) for part in version.removeprefix("go").split("."))


def read_portable_go(home: Path) -> Go | None:
    launcher = home / "bin" / ("go.exe" if os.name == "nt" else "go")
    version_file = home / "VERSION"
    if not launcher.is_file() or not version_file.is_file():
        return None
    version = version_file.read_text(encoding="utf-8").partition("\n")[0].strip()
    if not re.fullmatch(r"go\d+\.\d+(?:\.\d+)?", version):
        return None
    return Go(home, version)


def current_portable_go(project_root: Path) -> Go | None:
    installed = [go for path in (project_root / GO_DIR).glob("go*") if (go := read_portable_go(path))]
    return max(installed, key=lambda go: go_version_key(go.version), default=None)


def ensure_portable_go(project_root: Path, *, refresh: bool = False, verbose: bool = False) -> Go:
    current = current_portable_go(project_root)
    if current is not None and not refresh:
        if verbose:
            print(f"[info] portable Go ready: {current.home} ({current.version})")
        return current

    os_name, arch = host_target()
    print("[info] checking latest stable Go")
    with urllib.request.urlopen(GO_RELEASES_URL, timeout=60) as response:
        releases = json.load(response)
    release = max((item for item in releases if item["stable"]), key=lambda item: go_version_key(item["version"]))
    version = release["version"]
    if current is not None and current.version == version:
        print(f"[ ok ] portable Go is current: {version}")
        return current
    package = next((item for item in release["files"]
                    if item["os"] == os_name and item["arch"] == arch and item["kind"] == "archive"), None)
    if package is None:
        fail(f"Go {version} has no archive for {os_name}/{arch}")

    dest = project_root / GO_DIR / version
    if dest.exists():
        fail(f"incomplete portable Go directory; move {dest.relative_to(project_root).as_posix()} aside and retry")
    tmp_root = project_root / TMP_DIR
    tmp_root.mkdir(parents=True, exist_ok=True)
    print(f"[info] downloading {version} {os_name}/{arch}")
    with tempfile.TemporaryDirectory(prefix="stelliberty-go-", dir=tmp_root) as tmp:
        archive = Path(tmp) / package["filename"]
        _download(f"https://go.dev/dl/{package['filename']}", archive)
        if hashlib.sha256(archive.read_bytes()).hexdigest() != package["sha256"]:
            fail("Go archive checksum mismatch")
        _extract(archive, Path(tmp))
        extracted = read_portable_go(Path(tmp) / "go")
        if extracted is None or extracted.version != version:
            fail("Go archive version does not match the release metadata")
        dest.parent.mkdir(parents=True, exist_ok=True)
        extracted.home.rename(dest)
    print(f"[ ok ] portable go={dest.relative_to(project_root).as_posix()} version={version}")
    return Go(dest, version)


# === submodule ===


def _run_git(project_root: Path, *args: str, capture: bool = False) -> subprocess.CompletedProcess:
    """capture=True 时吞掉输出供解析；否则直通终端（clone 进度需要可见）。"""
    return subprocess.run(
        ["git", *args],
        cwd=project_root,
        capture_output=capture,
        text=True,
        errors="replace",
        check=not capture,
    )


def submodule_paths(project_root: Path) -> list[str]:
    gitmodules = project_root / ".gitmodules"
    if not gitmodules.is_file():
        return []
    return re.findall(r"^\s*path\s*=\s*(.+?)\s*$", gitmodules.read_text(encoding="utf-8"), re.M)


def apply_native_patches(project_root: Path) -> None:
    # 补丁随主仓库分发，本机与 CI 使用同一份内核源码；已应用的补丁直接跳过。
    for patch in sorted((project_root / "scripts/patches").glob("mihomo-*.patch")):
        command = ["git", "-C", str(project_root / "third_party/mihomo"), "apply", "--unidiff-zero"]
        applied = subprocess.run([*command, "--reverse", "--check", str(patch)], capture_output=True)
        if applied.returncode == 0:
            continue
        subprocess.run([*command, "--check", str(patch)], check=True)
        subprocess.run([*command, str(patch)], check=True)
        print(f"[ ok ] applied {patch.name}")


def _blocking_dirs(project_root: Path, paths: list[str]) -> list[str]:
    """非空但没有 .git 的 submodule 目录：git clone 会以「destination path already exists」失败。

    只报告不清理——删目录属破坏性操作，交给调用者确认。
    """
    blocked = []
    for rel in paths:
        target = project_root / rel
        if not target.is_dir():
            continue
        if (target / ".git").exists():
            continue
        if any(target.iterdir()):
            blocked.append(rel)
    return blocked


def ensure_submodules(project_root: Path, *, verbose: bool = False) -> None:
    paths = submodule_paths(project_root)
    if not paths:
        return
    if shutil.which("git") is None:
        fail("git not found in PATH; cannot fetch submodules")
    if not (project_root / ".git").exists():
        print("[warn] not a git checkout; skipping submodules")
        return

    blocked = _blocking_dirs(project_root, paths)
    if blocked:
        fail(
            "these submodule directories are non-empty but not git checkouts: "
            + ", ".join(blocked)
            + "; move them aside, then re-run"
        )

    status = _run_git(project_root, "submodule", "status", capture=True)
    # 前缀 '-' = 未初始化，'+' = 与父仓库记录的 commit 不一致
    stale = [
        line[1:].split()[1] if len(line.split()) > 1 else line
        for line in status.stdout.splitlines()
        if line[:1] in ("-", "+")
    ]
    if not stale:
        if verbose:
            print(f"[info] submodules up to date: {', '.join(paths)}")
        return

    print(f"[info] syncing submodules: {', '.join(paths)}")
    _run_git(project_root, "submodule", "update", "--init", "--recursive")
    print(f"[ ok ] submodules ready: {', '.join(paths)}")


def ensure_portable_jdk(project_root: Path, *, force: bool = False, verbose: bool = False) -> Jdk:
    if not force:
        found = current_portable_jdk(project_root)
        if found is not None:
            if verbose:
                print(f"[info] portable JDK ready: {found.home} (JDK {found.major})")
            return found
    print(f"[info] portable JDK {MIN_JDK}+ missing; downloading")
    return install_portable_jdk(project_root, verbose=verbose)


def gradle_root(project_root: Path) -> Path:
    """Gradle 根不等于仓库根：wrapper、settings、buildSrc 与两个模块都在 android/ 下。"""
    return project_root / "android"


def read_properties(path: Path) -> dict[str, str]:
    if not path.is_file():
        return {}
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith(("#", "!")) or "=" not in stripped:
            continue
        key, _, value = stripped.partition("=")
        # Java properties 会转义分隔符，还原成真实路径
        values[key.strip()] = value.strip().replace("\\:", ":").replace("\\\\", "\\")
    return values


def ensure_gradle_wrapper(project_root: Path) -> None:
    root = gradle_root(project_root)
    missing = [name for name in GRADLE_WRAPPER_FILES if not (root / name).is_file()]
    if not missing:
        return
    properties = read_properties(root / "gradle/wrapper/gradle-wrapper.properties")
    match = re.fullmatch(
        r"https://services\.gradle\.org/distributions/gradle-(\d+\.\d+(?:\.\d+)?)-(?:bin|all)\.zip",
        properties.get("distributionUrl", ""),
    )
    if not match:
        fail("unsupported Gradle distributionUrl; expected an official numbered release")
    version = match.group(1)
    tmp_root = project_root / TMP_DIR
    tmp_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="gradle-wrapper-", dir=tmp_root) as tmp:
        for name in missing:
            downloaded = Path(tmp) / Path(name).name
            _download(f"https://raw.githubusercontent.com/gradle/gradle/v{version}/{name}", downloaded)
            if name.endswith(".jar"):
                checksum = Path(tmp) / "wrapper.sha256"
                _download(
                    f"https://services.gradle.org/distributions/gradle-{version}-wrapper.jar.sha256",
                    checksum,
                )
                expected = checksum.read_text(encoding="utf-8").strip()
                if hashlib.sha256(downloaded.read_bytes()).hexdigest() != expected:
                    fail("Gradle wrapper checksum mismatch")
            target = root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            downloaded.replace(target)
            if name == "gradlew":
                target.chmod(0o755)
    print(f"[ ok ] Gradle {version} wrapper ready")


def sdk_dir_candidates(project_root: Path) -> list[tuple[str, Path]]:
    candidates: list[tuple[str, Path]] = []
    configured = read_properties(gradle_root(project_root) / "local.properties").get("sdk.dir")
    if configured:
        candidates.append(("local.properties", Path(configured)))
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        value = os.environ.get(key)
        if value:
            candidates.append((key, Path(value)))
    home = Path.home()
    if os.name == "nt":
        local_app_data = os.environ.get("LOCALAPPDATA", str(home / "AppData" / "Local"))
        candidates.append(("default", Path(local_app_data) / "Android" / "Sdk"))
    elif sys.platform == "darwin":
        candidates.append(("default", home / "Library" / "Android" / "sdk"))
    else:
        candidates.append(("default", home / "Android" / "Sdk"))
    return candidates


def detect_sdk(console: Console, project_root: Path) -> Path:
    for source, path in sdk_dir_candidates(project_root):
        expanded = path.expanduser()
        if (expanded / "platforms").is_dir() or (expanded / "platform-tools").is_dir():
            if console.verbose:
                console.info(f"sdk found from {source}: {expanded}")
            return expanded
    fail("Android SDK not found; set ANDROID_HOME or install it through Android Studio")


def detect_toolchain(console: Console, project_root: Path) -> Toolchain:
    sdk_dir = detect_sdk(console, project_root)
    jdk = current_portable_jdk(project_root)
    if jdk is None:
        fail("portable JDK missing; run python scripts/prebuild.py")
    go = current_portable_go(project_root)
    if go is None:
        fail("portable Go missing; run python scripts/prebuild.py")
    return Toolchain(jdk, go, sdk_dir)


def geo_assets_status(project_root: Path) -> list[str]:
    """缺失的 GeoIP 资源。构建时被打进 assets，运行时提取给 mihomo 用。"""
    assets = gradle_root(project_root) / "app" / "src" / "main" / "assets"
    return [name for name in GEO_ASSETS if not (assets / name).is_file() or (assets / name).stat().st_size == 0]


def gradlew_path(project_root: Path) -> Path:
    return gradle_root(project_root) / ("gradlew.bat" if os.name == "nt" else "gradlew")


def run_gradle(
    console: Console,
    project_root: Path,
    toolchain: Toolchain,
    args: list[str],
) -> int:
    # JDK 经 -D 传入而非写 local.properties（本仓约定）；daemon-jvm.properties 钉的是 21，
    # 这个开关压过它。ANDROID_HOME 走环境变量，AGP 与 GoBuildTask 都从那里找 SDK/NDK。
    env = {
        **os.environ,
        "JAVA_HOME": str(toolchain.jdk.home),
        "ANDROID_HOME": str(toolchain.sdk_dir),
        "GOROOT": str(toolchain.go.home),
        "GOTOOLCHAIN": "local",
        "PATH": str(toolchain.go.home / "bin") + os.pathsep + os.environ.get("PATH", ""),
        "PYTHONUTF8": "1",
    }
    env.pop("JDK_HOME", None)
    full_args = [
        f"-Dorg.gradle.java.home={toolchain.jdk.home}",
        f"-Pstelliberty.goVersion={toolchain.go.version}",
        *args,
    ]
    executable = gradlew_path(project_root)
    cmd = ["cmd", "/c", str(executable), *full_args] if os.name == "nt" else [str(executable), *full_args]
    if console.verbose:
        console.info(f"run: {' '.join(cmd)}")
    # cwd 必须是 Gradle 根：wrapper 自己能定位发行版，但 Gradle 从当前目录往上找 settings.gradle.kts，
    # 在仓库根跑会当成没有构建脚本的空项目，报的是「task 不存在」。
    return subprocess.run(cmd, cwd=gradle_root(project_root), env=env).returncode


def missing_resources(project_root: Path) -> list[str]:
    missing = []
    missing.extend(name for name in GRADLE_WRAPPER_FILES if not (gradle_root(project_root) / name).is_file())
    if current_portable_jdk(project_root) is None:
        missing.append(JDK_DIR.as_posix())
    if current_portable_go(project_root) is None:
        missing.append(GO_DIR.as_posix())
    missing.extend(
        path for path in submodule_paths(project_root)
        if not (project_root / path / ".git").exists()
    )
    missing.extend(geo_assets_status(project_root))
    return missing


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(
        prog="stelliberty-prebuild",
        description="Prepare submodules, portable JDK / Go toolchains and GeoIP assets.",
    )
    parser.add_argument("--force", action="store_true", help="Replace the existing portable JDK")
    parser.add_argument("--skip-submodules", action="store_true", help="Skip the submodule sync check")
    parser.add_argument("--refresh-geo", action="store_true", help="Download the latest GeoIP assets")
    parser.add_argument("--refresh-go", action="store_true", help="Update portable Go to the latest stable release")
    parser.add_argument("-v", "--verbose", action="store_true", help="Print extra detail")
    args = parser.parse_args()

    root = resolve_project_root()
    if not args.skip_submodules:
        ensure_submodules(root, verbose=args.verbose)
    apply_native_patches(root)
    ensure_gradle_wrapper(root)
    jdk = ensure_portable_jdk(root, force=args.force, verbose=args.verbose)
    print(f"[ ok ] JAVA_HOME={jdk.home.relative_to(root).as_posix()}")
    go = ensure_portable_go(root, refresh=args.refresh_go, verbose=args.verbose)
    print(f"[ ok ] GOROOT={go.home.relative_to(root).as_posix()} version={go.version}")
    console = Console(args.verbose)
    if args.refresh_geo or geo_assets_status(root):
        toolchain = Toolchain(jdk, go, detect_sdk(console, root))
        # 下载与编译分开调用，避免资源合并任务消费未声明的任务输出。
        if run_gradle(console, root, toolchain, [":app:downloadGeoFiles"]) != 0:
            fail("downloadGeoFiles failed")
    missing = missing_resources(root)
    if missing:
        fail(f"build resources missing: {', '.join(missing)}")
    marker = root / PREBUILD_MARKER
    marker.parent.mkdir(parents=True, exist_ok=True)
    marker.touch()
    console.ok("build resources ready")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
