#!/usr/bin/env python3
"""合规自检：发布的 jar 里不得含 Minecraft 素材，也不得有原版贴图的近似复制。

用法：
    python3 tools/check_compliance.py              # 自动找 versions/*/build/libs/*.jar 里最新的
    python3 tools/check_compliance.py <jar>...     # 检查指定 jar

**审查范围（重要）**：本脚本只管「打进 jar 的文件」。
模组还有一半外观是**按 id 引用**原版资源实现的（世界内铁轨直接用 `minecraft:block/rail`），
那部分不进 jar、也就没有「打包他人素材」的问题 —— 但必须被明确列出来，
不能靠一个 0% 的数字把它盖过去。第 3 项就是这份清单。

检查项：
  1. jar 内 `assets/minecraft/**` 必须为 0 个条目（不打包任何原版素材）。
  2. jar 内每张 PNG，与本机原版贴图里**同尺寸**的**所有** PNG 逐一比对「非透明像素重合率」，
     必须低于阈值（默认 20%，实际应为 0%）。阈值可用 --max-overlap 调。

     为什么跟「所有」同尺寸贴图比、而不是只比铁轨：我们是不知道将来会抄到哪张的，
     只能全扫一遍取最高分。复制原版贴图会让重合率冲到 90%+，一眼可辨；
     而小贴图上偶然有 1~2 个像素颜色撞车（噪声）由 MIN_FAIL_PIXELS 兜住，不判失败。
  3. 列出模型 / blockstate 里引用的**原版资源**，并断言这些原始文件确实不在 jar 内。

原版素材从 loom 缓存的 merged jar 里读；找不到时跳过第 2 项并提示（CI 上可用 VANILLA_JAR 指定）。
"""
from __future__ import annotations

import argparse
import io
import json
import os
import re
import struct
import sys
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_MAX_OVERLAP = 0.20
MIN_OVERLAP_PIXELS = 4  # 不透明交集少于这么多像素就不算「可比对」，避免噪声
MIN_FAIL_PIXELS = 24  # 相同像素少于这么多就不判失败：小图（如一颗圆球）可能偶然撞色，复制必然远超此数


def find_jars() -> list[Path]:
    candidates = [p for p in ROOT.glob("versions/*/build/libs/*.jar") if "-sources" not in p.name]
    if not candidates:
        sys.exit("找不到构建产物，请先 ./gradlew build")
    return [max(candidates, key=lambda p: p.stat().st_mtime)]


def target_mc_version() -> str | None:
    """从 stonecutter.properties.toml 读本项目面向的 MC 版本，用来挑对应版本的原版 jar。"""
    props = ROOT / "stonecutter.properties.toml"
    if not props.exists():
        return None
    for line in props.read_text(encoding="utf-8").splitlines():
        if line.startswith("mod.mc_releases"):
            match = re.search(r'"([^"]+)"', line)
            return match.group(1) if match else None
    return None


def find_vanilla_jar() -> Path | None:
    env = os.environ.get("VANILLA_JAR")
    if env:
        return Path(env)
    base = Path.home() / ".gradle/caches/fabric-loom"
    found = [p for p in base.rglob("*merged-deobf*.jar") if "-sources" not in p.name]
    if not found:
        return None
    wanted = target_mc_version()
    matching = [p for p in found if wanted and wanted in str(p)]
    return max(matching or found, key=lambda p: p.stat().st_mtime)


def png_size(data: bytes) -> tuple[int, int] | None:
    """只读 PNG 头（IHDR），避免为了取尺寸解压整张图。"""
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", data[16:24])


def opaque_pixels(data: bytes) -> dict[int, tuple[int, int, int, int]]:
    image = Image.open(io.BytesIO(data)).convert("RGBA")
    return {i: px for i, px in enumerate(image.getdata()) if px[3] > 0}


def index_vanilla(path: Path) -> dict[tuple[int, int], list[str]]:
    """按尺寸给原版贴图建索引：{尺寸: [条目名...]}"""
    index: dict[tuple[int, int], list[str]] = {}
    with zipfile.ZipFile(path) as jar:
        for name in jar.namelist():
            if name.startswith("assets/minecraft/textures/") and name.endswith(".png"):
                with jar.open(name) as fh:
                    size = png_size(fh.read(24))
                if size:
                    index.setdefault(size, []).append(name)
    return index


def vanilla_references(jar_path: Path) -> tuple[dict[str, set[str]], set[str]]:
    """扫 jar 内我们自己的 JSON，列出引用的原版资源。

    返回 (资源 id -> 引用它的文件集合, 贴图类引用集合)。
    贴图类单独取出，用来断言「引用了谁就不能打包谁」。
    """
    refs: dict[str, set[str]] = {}
    textures: set[str] = set()

    def walk(node, source: str) -> None:
        if isinstance(node, str):
            if node.startswith(("minecraft:", "#minecraft:")):
                refs.setdefault(node.lstrip("#"), set()).add(source)
        elif isinstance(node, dict):
            for value in node.values():
                walk(value, source)
        elif isinstance(node, list):
            for value in node:
                walk(value, source)

    with zipfile.ZipFile(jar_path) as jar:
        for name in jar.namelist():
            if not (name.startswith("assets/rail_switch/") and name.endswith(".json")):
                continue
            data = json.loads(jar.read(name))
            walk(data, name.replace("assets/rail_switch/", ""))
            for value in (data.get("textures") or {}).values():
                if isinstance(value, str) and value.startswith("minecraft:"):
                    textures.add(value)
    return refs, textures


def max_overlap(data: bytes, vanilla_jar: Path,
                names: list[str]) -> tuple[float, int, int, str | None, int]:
    """返回 (最高重合率, 完全相同像素数, 不透明交集像素数, 最像的原版条目, 实际比对张数)。

    重合率 = 完全相同像素 / (双方都不透明的像素交集)。初始 best 用 -1 而不是 0，
    否则「恰好一个像素都不相同」这个最好的结果反而不会被记录。
    """
    ours = opaque_pixels(data)
    if not ours:
        return 0.0, 0, 0, None, 0
    best = (-1.0, 0, 0, None, 0)
    compared = 0
    with zipfile.ZipFile(vanilla_jar) as jar:
        for name in names:
            theirs = opaque_pixels(jar.read(name))
            if not theirs:
                continue
            shared = ours.keys() & theirs.keys()
            if len(shared) < MIN_OVERLAP_PIXELS:
                continue
            compared += 1
            same = sum(1 for i in shared if ours[i] == theirs[i])
            ratio = same / len(shared)
            if ratio > best[0]:
                best = (ratio, same, len(shared), name, compared)
    return best[0], best[1], best[2], best[3], compared


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("jars", nargs="*", type=Path)
    parser.add_argument("--max-overlap", type=float, default=DEFAULT_MAX_OVERLAP)
    args = parser.parse_args()

    jars = args.jars or find_jars()
    vanilla = find_vanilla_jar()
    if vanilla is None:
        print("⚠ 未找到原版 merged jar，跳过贴图比对（可设 VANILLA_JAR 指定）")
    else:
        print(f"比对基准：{vanilla.name}（本项目目标 MC {target_mc_version() or '未知'}）")

    failures = 0
    for jar_path in jars:
        print(f"\n=== {jar_path.name} ===")
        with zipfile.ZipFile(jar_path) as jar:
            names = jar.namelist()

            # 检查 1：不得打包原版素材
            vanilla_assets = [n for n in names if n.startswith("assets/minecraft/")]
            if vanilla_assets:
                failures += 1
                print(f"❌ jar 内含 {len(vanilla_assets)} 个 assets/minecraft/ 条目，例如：{vanilla_assets[:3]}")
            else:
                print("✅ 未打包任何 assets/minecraft/ 素材（世界内铁轨由模型按 id 引用原版贴图）")

            # 检查 3：把「引用的原版资源」摊开讲清楚，并断言引用了就没打包
            refs, tex_refs = vanilla_references(jar_path)
            print(f"   引用的原版资源 {len(refs)} 项（**未打包**，运行时由玩家自己的游戏提供）：")
            for ref in sorted(refs):
                kind = "贴图" if ref in tex_refs else ("模型" if ref.startswith("minecraft:block/") else "基础模型")
                print(f"     · [{kind}] {ref}  ← {len(refs[ref])} 个文件：{', '.join(sorted(refs[ref]))}")
            for ref in sorted(tex_refs):
                bundled = f"assets/minecraft/textures/{ref.split(':', 1)[1]}.png"
                if bundled in names:
                    failures += 1
                    print(f"     ❌ 引用了 {ref}，却又把它的原文件 {bundled} 打进了 jar")
            print("     结论：世界内铁轨外观 100% 来自原版贴图（按 id 引用，jar 内 0 字节）；"
                  "物品图标 = 原版铁轨（引用）+ 本模组自绘标记。")

            # 检查 2：自带贴图不得近似原版贴图
            textures = [n for n in names if n.endswith(".png")]
            print(f"   自带贴图 {len(textures)} 张：")
            if vanilla is None:
                for name in textures:
                    print(f"     · {name}")
                continue

            index = index_vanilla(vanilla)
            for name in textures:
                data = jar.read(name)
                size = png_size(data)
                if size is None:
                    failures += 1
                    print(f"     ❌ {name} 不是合法 PNG")
                    continue
                group = index.get(size, [])
                if not group:
                    print(f"     · {name}  {size[0]}x{size[1]}  无同尺寸原版贴图可比对")
                    continue
                ratio, same, shared, like, compared = max_overlap(data, vanilla, group)
                if like is None:  # 与所有同尺寸原版贴图都没有足够的不透明交集
                    print(f"     ✅ {name}  {size[0]}x{size[1]}  与原版无重叠区域（{size[0]}x{size[1]} 组 {len(group)} 张）")
                    continue
                ok = ratio < args.max_overlap or same < MIN_FAIL_PIXELS
                failures += 0 if ok else 1
                if same == 0:
                    verdict = f"与 {compared} 张同尺寸原版贴图逐像素比对，没有任何一个像素相同"
                elif same < MIN_FAIL_PIXELS:
                    # 撞色太少，点名某张原版贴图只会误导，只报数值
                    verdict = (f"最高重合率来自 {compared} 张里最像的一张，"
                               f"但只有 {same} px 撞色（交集 {shared} px），属噪声，不点名也不算复制")
                else:
                    verdict = (f"最像 {like.split('/')[-1]}：相同 {same} px / 非透明交集 {shared} px"
                               f"（共比对 {compared} 张）")
                print(f"     {'✅' if ok else '❌'} {name}  {size[0]}x{size[1]}  重合率 {ratio * 100:.1f}%  {verdict}")

    print(f"\n{'✅ 合规检查通过' if failures == 0 else f'❌ 合规检查失败（{failures} 项）'}")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
