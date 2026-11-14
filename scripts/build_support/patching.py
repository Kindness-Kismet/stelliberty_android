import subprocess
from pathlib import Path


def apply_native_patches(project_root: Path, *, verbose: bool = False) -> None:
    patch_dir = Path(__file__).resolve().parent / "patches"
    source_dir = project_root / "third_party/mihomo"
    command = ["git", "apply", "--whitespace=error"]

    for patch in sorted(patch_dir.glob("mihomo-*.patch")):
        # 二进制输入保留 LF，避免 Windows 把补丁上下文转换成 CRLF。
        content = patch.read_text(encoding="utf-8").encode("utf-8")
        options = {"cwd": source_dir, "input": content, "capture_output": True}
        # 已应用或被上游原样吸收的补丁直接跳过，保留子模块里的其他修改。
        applied = subprocess.run([*command, "--reverse", "--check"], **options)
        if applied.returncode == 0:
            if verbose:
                print(f"[info] already applied {patch.name}")
            continue

        checked = subprocess.run([*command, "--check"], **options)
        if checked.returncode != 0:
            detail = checked.stderr.decode("utf-8", errors="replace").strip()
            raise SystemExit(f"error: cannot apply {patch.name}; review the patch against the pinned source\n{detail}")
        subprocess.run(command, **options, check=True)
        print(f"[ ok ] applied {patch.name}")
