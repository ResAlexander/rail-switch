#!/usr/bin/env python3
"""生成 rail_switch 的资源文件与自绘贴图。

用法： python3 tools/gen_resources.py

合规要点：**不读取、不派生任何 Mojang 素材**。物品图标、转辙机贴图、模组图标
全部由本脚本用代码画出来，因此 jar 里不含他人受版权保护的像素。
"""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src" / "main" / "resources"
NS = "rail_switch"

# 左开 = 蓝，右开 = 橙（用户拍板的配色）
TYPES = [
    ("left_switch_rail", True, (64, 128, 255, 255)),
    ("right_switch_rail", False, (255, 152, 32, 255)),
]

RAIL_LIGHT = (168, 168, 176, 255)
RAIL_DARK = (92, 92, 100, 255)
SLEEPER = (104, 78, 58, 255)
SLEEPER_DARK = (68, 50, 36, 255)
PLATE = (72, 72, 80, 255)
PLATE_DARK = (34, 34, 40, 255)


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def save(image: Image.Image, out: Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    image.save(out)


# --------------------------------------------------------------------------- 贴图（全部自绘）
def make_stand_texture(color: tuple[int, int, int, int], out: Path) -> None:
    """16x16 转辙机：左上 8x8 是底座顶面（中心 4x4 彩色旋钮），右下竖条是摇柄。

    旋钮必须给足面积且不再描边——16x16 下描边会把彩色像素吃干，导致左右两色看不出来。
    """
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 7, 7], fill=PLATE, outline=PLATE_DARK)
    draw.rectangle([1, 1, 6, 6], fill=(142, 142, 150, 255))
    draw.rectangle([2, 2, 5, 5], fill=color)
    draw.rectangle([10, 0, 11, 15], fill=(58, 58, 64, 255))
    draw.rectangle([10, 0, 10, 15], fill=(158, 158, 166, 255))
    save(image, out)


def make_marker_texture(color: tuple[int, int, int, int], diverge_left: bool, out: Path) -> None:
    """16x16 透明底 + 一个彩色方块标记（沿用 1.0.1 的样式：深色托底 + 3x3 色块）。

    这块标记是**覆盖层**，贴在原版铁轨贴图上面（见 item 模型的两层结构）。
    位置说明岔向：左开在左上角，右开在右上角。
    """
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    x = 0 if diverge_left else 11
    draw.rectangle([x, 0, x + 4, 4], fill=(48, 48, 54, 255))
    draw.rectangle([x + 1, 1, x + 3, 3], fill=color)
    save(image, out)


def make_mod_icon(out: Path) -> None:
    """256x256 模组图标：Y 型道岔剪影 —— 直股 + 曲股 + 两台转辙机（蓝/橙）。"""
    image = Image.new("RGBA", (256, 256), (26, 28, 34, 255))
    draw = ImageDraw.Draw(image)

    # 地面阴影感的底纹
    draw.ellipse([16, 16, 240, 240], outline=(48, 52, 62, 255), width=4)

    # 直股（横线）
    draw.line([24, 176, 232, 176], fill=RAIL_DARK, width=20)
    draw.line([24, 170, 232, 170], fill=RAIL_LIGHT, width=8)
    # 曲股（斜线，向右上分出）
    draw.line([128, 176, 232, 96], fill=RAIL_DARK, width=18)
    draw.line([128, 170, 232, 90], fill=RAIL_LIGHT, width=8)
    # 枕木点缀
    for x in range(36, 232, 28):
        draw.line([x, 162, x, 184], fill=SLEEPER, width=6)

    # 两台转辙机：左开（蓝）在直股端，右开（橙）在曲股端
    def stand(cx: int, cy: int, color: tuple[int, int, int, int]) -> None:
        draw.rectangle([cx - 20, cy - 14, cx + 20, cy + 14], fill=PLATE, outline=PLATE_DARK, width=3)
        draw.ellipse([cx - 11, cy - 11, cx + 11, cy + 11], fill=color, outline=(18, 18, 22, 255), width=3)
        draw.line([cx, cy, cx + 30, cy - 30], fill=(220, 220, 228, 255), width=7)
        draw.ellipse([cx + 24, cy - 36, cx + 38, cy - 22], fill=color, outline=(18, 18, 22, 255), width=2)

    stand(72, 176, TYPES[0][2])
    stand(196, 118, TYPES[1][2])
    save(image, out)


# --------------------------------------------------------------------------- 模型
def rail_element(texture: str) -> dict:
    return {
        "from": [0, 1, 0],
        "to": [16, 1, 16],
        "faces": {
            "down": {"uv": [0, 16, 16, 0], "texture": texture},
            "up": {"uv": [0, 0, 16, 16], "texture": texture},
        },
    }


def stand_elements(lever_angle: float) -> list[dict]:
    base = {
        "from": [1, 1, 1],
        "to": [7, 3, 7],
        "faces": {
            "down": {"uv": [0, 0, 6, 6], "texture": "#stand"},
            "up": {"uv": [0, 0, 6, 6], "texture": "#stand"},
            "north": {"uv": [0, 0, 6, 2], "texture": "#stand"},
            "south": {"uv": [0, 0, 6, 2], "texture": "#stand"},
            "west": {"uv": [0, 0, 6, 2], "texture": "#stand"},
            "east": {"uv": [0, 0, 6, 2], "texture": "#stand"},
        },
    }
    lever_faces = {face: {"uv": [10, 0, 12, 8], "texture": "#stand"}
                   for face in ("down", "up", "north", "south", "west", "east")}
    lever = {
        "from": [3, 3, 3],
        "to": [5, 8, 5],
        "rotation": {"origin": [4, 3, 4], "axis": "y", "angle": lever_angle, "rescale": False},
        "faces": lever_faces,
    }
    return [base, lever]


def block_model(stand_texture: str, rail_texture: str, lever_angle: float) -> dict:
    return {
        "ambientocclusion": False,
        "textures": {
            "particle": rail_texture,
            "rail": rail_texture,
            "stand": stand_texture,
        },
        "elements": [rail_element("#rail")] + stand_elements(lever_angle),
    }


BLOCKSTATE_TEMPLATE = {
    "variants": {
        # 坡道形状按普通铁轨模型渲染（道岔逻辑不处理坡道）
        "shape=ascending_east": {"model": "minecraft:block/rail_raised_ne", "y": 90},
        "shape=ascending_north": {"model": "minecraft:block/rail_raised_ne"},
        "shape=ascending_south": {"model": "minecraft:block/rail_raised_sw"},
        "shape=ascending_west": {"model": "minecraft:block/rail_raised_sw", "y": 90},
        "shape=east_west": {"model": None, "y": 90},
        "shape=north_east": {"model": None, "y": 270},
        "shape=north_south": {"model": None},
        "shape=north_west": {"model": None, "y": 180},
        "shape=south_east": {"model": None},
        "shape=south_west": {"model": None, "y": 90},
    }
}


def build_blockstate(block_id: str) -> dict:
    state = json.loads(json.dumps(BLOCKSTATE_TEMPLATE))
    for key, variant in state["variants"].items():
        if variant["model"] is None:
            corner = key in ("shape=north_east", "shape=north_west",
                             "shape=south_east", "shape=south_west")
            variant["model"] = f"{NS}:block/{block_id}_{'corner' if corner else 'straight'}"
    return state


def main() -> None:
    for block_id, diverge_left, color in TYPES:
        side = "left" if diverge_left else "right"
        stand_texture = f"{NS}:block/switch_stand_{side}"

        make_stand_texture(color, RES / "assets" / NS / "textures/block" / f"switch_stand_{side}.png")
        make_marker_texture(color, diverge_left, RES / "assets" / NS / "textures/item" / f"switch_marker_{side}.png")

        # 直位 / 岔位 两套方块模型；岔位时摇柄转向本岔的曲股方向（左开 +45、右开 -45）
        write_json(RES / "assets" / NS / "models/block" / f"{block_id}_straight.json",
                   block_model(stand_texture, "minecraft:block/rail", 0.0))
        write_json(RES / "assets" / NS / "models/block" / f"{block_id}_corner.json",
                   block_model(stand_texture, "minecraft:block/rail_corner", 45.0 if diverge_left else -45.0))

        write_json(RES / "assets" / NS / "blockstates" / f"{block_id}.json", build_blockstate(block_id))

        write_json(RES / "assets" / NS / "items" / f"{block_id}.json",
                   {"model": {"type": "minecraft:model", "model": f"{NS}:item/{block_id}"}})
        # 两层：底层按 id 引用原版铁轨贴图（jar 内不打包原版素材），上层是本模组自绘的彩色圆球
        write_json(RES / "assets" / NS / "models/item" / f"{block_id}.json",
                   {"parent": "minecraft:item/generated",
                    "textures": {"layer0": "minecraft:block/rail",
                                 "layer1": f"{NS}:item/switch_marker_{side}"}})

        write_json(RES / "data" / NS / "loot_table/blocks" / f"{block_id}.json", {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [{"type": "minecraft:item", "name": f"{NS}:{block_id}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })

    make_mod_icon(RES / "assets" / NS / "icon.png")

    write_json(RES / "data/minecraft/tags/block/rails.json", {
        "replace": False,
        "values": [f"{NS}:left_switch_rail", f"{NS}:right_switch_rail"],
    })

    write_json(RES / "assets" / NS / "lang/en_us.json", {
        "itemGroup.rail_switch": "Rail Switch",
        "block.rail_switch.left_switch_rail": "Left Switch Rail",
        "block.rail_switch.right_switch_rail": "Right Switch Rail",
        "item.rail_switch.switch_rail.tooltip":
            "Works as a switch only when rails connect on three sides. A rider presses Left/Right (default A/D) to set it.",
    })
    write_json(RES / "assets" / NS / "lang/zh_cn.json", {
        "itemGroup.rail_switch": "道岔",
        "block.rail_switch.left_switch_rail": "左开道岔",
        "block.rail_switch.right_switch_rail": "右开道岔",
        "item.rail_switch.switch_rail.tooltip":
            "需要三个方向接轨才能当道岔用。骑手按「向左 / 向右」（默认 A / D）扳岔。",
    })

    write_json(RES / "pack.mcmeta", {
        "pack": {
            "description": "Rail Switch resources",
            "pack_format": 48,
            "supported_formats": [48, 101],
            "min_format": 48,
            "max_format": 101,
        },
    })

    write_text(RES / "fabric.mod.json", json.dumps({
        "schemaVersion": 1,
        "id": "${id}",
        "version": "${version}",
        "name": "${name}",
        "description": "Two Y-shaped switch rails: a rider sets the points with Left/Right (default A/D) and the minecart takes that branch; with no input it follows wherever the points currently are. It patches nothing (no mixins) - switching is plain block state, and the game's own new minecart physics does the turning. Requires the minecart_improvements new physics; install on both client and server.",
        # description 只能是字符串（loader 会拒收对象）；多语言说明放官方的 custom 里
        "custom": {
            "rail_switch:description_zh":  "两种 Y 型道岔方块：骑手按「向左/向右」（默认 A/D）扳岔，矿车在道岔处左/右变轨；没有输入时按道岔当时的位置通过。不改动原版物理（无 mixin），只是把车交给游戏自带的新矿车物理。需要开启 minecart_improvements 新物理；客户端与服务端都要安装。",
        },
        "authors": ["Hardware Alexander"],
        "license": "PolyForm-Noncommercial-1.0.0",
        "icon": "assets/rail_switch/icon.png",
        "environment": "*",
        "entrypoints": {"main": ["com.resalexanderccho.railswitch.RailSwitchMod"]},
        "contact": {
            "homepage": "https://github.com/ResAlexander/rail-switch",
            "sources": "https://github.com/ResAlexander/rail-switch",
            "issues": "https://github.com/ResAlexander/rail-switch/issues",
        },
        "depends": {
            "fabricloader": ">=0.19.0",
            "minecraft": ["${minecraft}"],
            "java": ">=25",
            "fabric-api": "*",
        },
    }, indent=2, ensure_ascii=False) + "\n")

    print("资源已生成到", RES)


if __name__ == "__main__":
    main()
