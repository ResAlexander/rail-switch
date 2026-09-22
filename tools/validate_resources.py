#!/usr/bin/env python3
"""静态自检：方块状态 / 模型 / 物品定义 / 贴图 / 标签 / 战利品表的引用是否都能落地。

用法： python3 tools/validate_resources.py
原版资源（minecraft: 开头）不在检查范围内。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src" / "main" / "resources"
NS = "rail_switch"
ASSETS = RES / "assets" / NS
DATA = RES / "data" / NS

errors: list[str] = []
checked = 0


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def check_model_ref(ref: str, where: str) -> None:
    global checked
    checked += 1
    if ref.startswith("minecraft:"):
        return
    namespace, _, path = ref.partition(":")
    if namespace != NS:
        errors.append(f"{where}: 未知命名空间 {ref}")
        return
    kind, _, name = path.partition("/")
    target = ASSETS / "models" / kind / f"{name}.json"
    if not target.exists():
        errors.append(f"{where}: 模型缺失 {ref} -> {target}")


def check_texture_ref(ref: str, where: str) -> None:
    global checked
    checked += 1
    if ref.startswith("#") or ref.startswith("minecraft:"):
        return
    namespace, _, path = ref.partition(":")
    if namespace != NS:
        errors.append(f"{where}: 未知命名空间 {ref}")
        return
    kind, _, name = path.partition("/")
    target = ASSETS / "textures" / kind / f"{name}.png"
    if not target.exists():
        errors.append(f"{where}: 贴图缺失 {ref} -> {target}")


# 方块状态 -> 模型
for blockstate in sorted((ASSETS / "blockstates").glob("*.json")):
    for variant_key, variant in load(blockstate)["variants"].items():
        check_model_ref(variant["model"], f"{blockstate.name}[{variant_key}]")

# 模型 -> 父模型 / 贴图
for model in sorted((ASSETS / "models").rglob("*.json")):
    data = load(model)
    if "parent" in data:
        check_model_ref(data["parent"], model.relative_to(RES).as_posix())
    for key, value in data.get("textures", {}).items():
        check_texture_ref(value, f"{model.relative_to(RES).as_posix()}[textures.{key}]")

# 物品定义 -> 物品模型
for item_def in sorted((ASSETS / "items").glob("*.json")):
    model = load(item_def)["model"]["model"]
    check_model_ref(model, item_def.name)

# 战利品表 -> 物品
for loot in sorted((DATA / "loot_table").rglob("*.json")):
    for pool in load(loot).get("pools", []):
        for entry in pool.get("entries", []):
            name = entry.get("name")
            if name and not name.startswith(f"{NS}:"):
                errors.append(f"{loot.name}: 战利品表物品名异常 {name}")

# rails 标签
tag = load(RES / "data" / "minecraft" / "tags" / "block" / "rails.json")
for value in tag["values"]:
    if not value.startswith(f"{NS}:"):
        errors.append(f"rails.json: 标签内容异常 {value}")

if errors:
    print("发现问题：")
    for line in errors:
        print(" -", line)
    sys.exit(1)

print(f"自检通过，共检查 {checked} 处引用。")
