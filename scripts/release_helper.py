#!/usr/bin/env python3
"""版本号升级与发布辅助（x.x.x.x 方案）。

规则（权威定义见 Version.md 一、二）：
  - versionName = X.Y.Z.W，W 为全局单调递增构建号；每次发版（含 RC）W += 1，永不重置。
  - versionCode = X*1_000_000 + Y*10_000 + Z*100 + W，严格单调。
  - 正式版 tag：vX.Y.Z.W；预发行 tag：vX.Y.Z.W-rcN（N 为该 X.Y.Z 下第 N 个 RC）。

子命令：
  current                      打印 build.gradle.kts 当前 versionName / versionCode
  plan --type T [--rc] [--dry-run]
                                计算下一版本：T ∈ major|minor|patch|build
                                写入 build.gradle.kts（--dry-run 仅打印不写）
  code --name X.Y.Z.W          打印该 versionName 对应的 versionCode
  rc-number --base X.Y.Z.W     打印该基线下下一个 RC 序号 N

例：
  python3 scripts/release_helper.py plan --type patch        # 计划正式版 0.1.5.1
  python3 scripts/release_helper.py plan --type patch --rc   # 计划 RC 0.1.5.1-rc1
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

BUILD_GRADLE = Path(__file__).resolve().parent.parent / "app" / "build.gradle.kts"

def _run(args: list[str]) -> str:
    return subprocess.run(args, capture_output=True, text=True).stdout


def read_current() -> tuple[str, int, str]:
    # 锚定到代码行（行首空白 + 赋值），避开注释里出现的 versionCode/versionName 字样。
    text = BUILD_GRADLE.read_text(encoding="utf-8")
    m = re.search(r'^\s*versionCode\s*=\s*(\d+)', text, re.M)
    n = re.search(r'^\s*versionName\s*=\s*"([^"]+)"', text, re.M)
    if not m or not n:
        raise SystemExit("无法在 app/build.gradle.kts 解析 versionCode/versionName")
    return n.group(1), int(m.group(1)), text


def bump(x: int, y: int, z: int, w: int, typ: str) -> tuple[int, int, int, int]:
    """语义段按 semver 规则递增；W 永远 +1（全局单调，不重置）。"""
    if typ == "major":
        return x + 1, 0, 0, w + 1
    if typ == "minor":
        return x, y + 1, 0, w + 1
    if typ == "patch":
        return x, y, z + 1, w + 1
    return x, y, z, w + 1  # build：仅构建号 +1


def parse_name(name: str) -> tuple[int, int, int, int]:
    parts = name.split(".")
    if len(parts) == 3:                       # 兼容旧三段式迁移
        x, y, z = (int(p) for p in parts)
        return x, y, z, 0
    if len(parts) == 4:
        return tuple(int(p) for p in parts)  # type: ignore[return-value]
    raise SystemExit(f"无法解析 versionName: {name!r}（需 X.Y.Z 或 X.Y.Z.W）")


def encode(x: int, y: int, z: int, w: int) -> int:
    return x * 1_000_000 + y * 10_000 + z * 100 + w


def next_rc_number(base: str) -> int:
    """base 形如 X.Y.Z.W；返回同 X.Y.Z 下下一个 RC 序号（已存在 rc tag 数 +1）。"""
    prefix = base.rsplit(".", 1)[0]           # X.Y.Z
    tags = _run(["git", "tag", "--list", f"v{prefix}.*-rc*"]).split()
    return len(tags) + 1


def write_version(text: str, name: str, code: int) -> None:
    # 同样锚定到代码行，避免误改注释。
    text = re.sub(r"(^\s*versionCode\s*=\s*)\d+", rf"\g<1>{code}", text, count=1, flags=re.M)
    text = re.sub(r'(^\s*versionName\s*=\s*")[^"]*(")', rf"\g<1>{name}\g<2>", text, count=1, flags=re.M)
    BUILD_GRADLE.write_text(text, encoding="utf-8")


def cmd_current(_args: argparse.Namespace | None = None) -> None:
    name, code, _ = read_current()
    print(f"versionName={name}  versionCode={code}")


def cmd_code(args: argparse.Namespace) -> None:
    print(encode(*parse_name(args.name)))


def cmd_rc(args: argparse.Namespace) -> None:
    print(next_rc_number(args.base))


def cmd_plan(args: argparse.Namespace) -> None:
    name, _code, text = read_current()
    x, y, z, w = parse_name(name)
    nx, ny, nz, nw = bump(x, y, z, w, args.type)
    nname = f"{nx}.{ny}.{nz}.{nw}"
    ncode = encode(nx, ny, nz, nw)
    if args.rc:
        n = next_rc_number(nname)
        tag = f"{nname}-rc{n}"
    else:
        tag = nname
    print(
        f"计划版本: versionName={tag}  versionCode={ncode}  tag=v{tag}"
        + ("  [DRY-RUN 未写入]" if args.dry_run else "")
    )
    if not args.dry_run:
        write_version(text, tag, ncode)
        print(f"已写入 {BUILD_GRADLE}")


def main() -> None:
    p = argparse.ArgumentParser(description="x.x.x.x 版本升级与发布辅助")
    sub = p.add_subparsers(dest="cmd", required=True)

    sub.add_parser("current", help="打印当前 versionName / versionCode")

    pc = sub.add_parser("code", help="打印 versionName 对应的 versionCode")
    pc.add_argument("--name", required=True, help="X.Y.Z.W")

    pr = sub.add_parser("rc-number", help="打印基线下下一个 RC 序号")
    pr.add_argument("--base", required=True, help="X.Y.Z.W")

    pp = sub.add_parser("plan", help="计算并写入下一版本")
    pp.add_argument("--type", required=True, choices=["major", "minor", "patch", "build"],
                    help="major|minor|patch|build")
    pp.add_argument("--rc", action="store_true", help="生成 RC 预发行版本（追加 -rcN）")
    pp.add_argument("--dry-run", action="store_true", help="只打印，不写 build.gradle.kts")

    args = p.parse_args()
    {"current": cmd_current, "code": cmd_code, "rc-number": cmd_rc, "plan": cmd_plan}[args.cmd](args)


if __name__ == "__main__":
    main()
