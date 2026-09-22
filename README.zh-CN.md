<div align="center">
  <img src="src/main/resources/assets/rail_switch/icon.png" width="160" alt="Rail Switch 图标">
  <h1>Rail Switch 道岔 🚂</h1>
  <h3>按键扳岔 · 原版铁轨自动对接 · 无 mixin · 220 bps 实测通过</h3>
  <p>
    <a href="https://github.com/ResAlexander/rail-switch/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/ResAlexander/rail-switch?style=flat-square&amp;label=release"></a>
    <img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-26.2-3C8527?style=flat-square">
    <img alt="Loader" src="https://img.shields.io/badge/Loader-Fabric-DBB69B?style=flat-square">
    <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/License-PolyForm%20NC%201.0.0-1a5fb4?style=flat-square"></a>
  </p>
  <p>
    <a href="README.md">English</a> ·
    <b>简体中文</b> ·
    <a href="DEVELOPING.md">开发说明</a> ·
    <a href="https://github.com/ResAlexander/rail-switch/issues">问题反馈</a>
  </p>
</div>

**Rail Switch** 为 Minecraft Java 26.2（Fabric）加入两种 Y 型道岔方块。骑矿车、在道岔前按住**向左**或**向右**（默认 `A` / `D`），矿车就岔向对应股道；没有输入时，矿车按道岔**当时**的位置通过。道岔方块满足原版的 `isRail` 判定，普通铁轨会自动与它对接。

模组**不含任何 mixin**：它不改写游戏自己的矿车代码，只写方块的形状状态、读取原版的玩家输入包。因此不存在与其它模组冲突的代码面，也不必担心兼容性。

## 🌟 主要特性

<table>
<tr>
<td width="50%" valign="top">

### 🚦 按键扳岔

`left_switch_rail` 在骑手按住**向左**（默认 `A`）时岔向左侧曲股；`right_switch_rail` 在按住**向右**（默认 `D`）时岔向右侧曲股。空车、非玩家骑手与未按键都不会替这辆车扳岔——矿车按道岔当时的位置通过。

</td>
<td width="50%" valign="top">

### 🔀 原版铁轨自动对接

方块满足原版 `isRail` 的两个条件并加入 `minecraft:rails` 标签，相邻的原版铁轨会自动调整形状与它衔接；支撑方块被破坏时像原版铁轨一样掉落。

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 🧩 无 mixin

不改写 `AbstractMinecart` / `NewMinecartBehavior`。拨岔纯粹是方块状态操作加读取原版玩家输入包，因此与物理模组叠加而不是打架。

</td>
<td width="50%" valign="top">

### ⚡ 高速可用

判定距离随车速自动放大，并有 0.5 秒按键记忆窗口，提前长按即可生效。与 highspeed-rail<sup>[1]</sup> 共存下 **220 bps** 实测通过。

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 🛤️ 逆向汇合不脱轨

从曲股方向驶来时，矿车自动汇入尖轨端，不脱轨、不停车（可配置为停车）。

</td>
<td width="50%" valign="top">

### 🎛️ 可配置

`config/rail_switch.json` 提供六项：提前判定距离、按键记忆、拨岔音效、旧物理行为、逆向行为、调试日志。

</td>
</tr>
</table>

## 📦 快速开始

### 前置条件

| 项目 | 要求 |
| --- | --- |
| 游戏 | Java 版 **26.2**，Fabric |
| Fabric Loader | `>= 0.19.0` |
| Fabric API | 必需 |
| Java | `>= 25` |
| 新矿车物理 | **必须开启**（`minecart_improvements`）；未开启时道岔退化为普通铁轨并记录一次警告 |
| 安装端 | **客户端与服务端都要装** |

### 安装

**下载**：[最新 Release](https://github.com/ResAlexander/rail-switch/releases/latest) 里的 jar；或自行构建，见 [DEVELOPING.md](DEVELOPING.md)。

模组新增了方块，属于**内容**——客户端缺它就会看到错误方块。两端必须装**同一版本**：

```text
<客户端配置目录>/mods/
├── fabric-api-*.jar
└── rail-switch-1.2.0-fabric-26.2.jar

<服务端>/mods/              （同一个文件）
├── fabric-api-*.jar
└── rail-switch-1.2.0-fabric-26.2.jar
```

* 单人游戏：放进客户端 `mods/` 即可（集成服务器同时加载）。
* 服务器：放进服务端 `mods/`，每个玩家客户端也放一份。
* **搬入已含道岔的存档前，服务端必须先装好同版本模组**，否则那些方块无法解析。

### 放置道岔

道岔需要**三个方向接轨**才当道岔用：直股两端 + 曲股一端。创造模式物品栏里有「Rail Switch」标签页（v1 有意不做合成配方）。

```text
        北（曲股）
        │
  ──────┼──────      直股：东西向
  西    │    东      曲股：北
        │
```

## 🎮 用法

1. 骑矿车，在道岔前**按住**向左或向右（默认 `A` / `D`）。
2. 对应的道岔方块会岔向曲股；**不按键则按道岔当时的位置通过**——道岔不会自动复位，它保持上一次被扳到的位置，直到下一辆车经过。
3. 松开后有 0.5 秒（10 tick）记忆窗口，但高速下请**提前按**：200 bps 时车一 tick 走 10 格，建议提前 20～60 格长按。

> 方向键默认没有绑定，且一个动作只能绑一个键。想用方向键请在「选项 → 控制」里把「向左 / 向右」改绑过去；模组读的是键位，改绑后照常生效。

## ⚙️ 配置

首次启动生成 `config/rail_switch.json`：

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `lookaheadBlocks` | `0` | 提前判定距离（格）；`0` = 按车速自动（速度×2 + 2，2～64） |
| `inputMemoryTicks` | `10` | 按键记忆窗口（tick） |
| `switchSound` | `true` | 拨岔音效 |
| `requireNewPhysics` | `true` | 旧物理维度是否停用道岔逻辑 |
| `trailingBehavior` | `merge` | 逆向过岔：`merge` 汇入尖轨端，`stop` 直接停车 |
| `debugLog` | `false` | 排查用：把每次拨岔/冻结写进日志 |

## 📄 许可证

本项目按 **PolyForm Noncommercial License 1.0.0**（SPDX 标识符 `PolyForm-Noncommercial-1.0.0`）授权。

* **以许可证全文为准**：[`LICENSE`](LICENSE)；官方正文 <https://polyformproject.org/licenses/noncommercial/1.0.0>。本节只是便于阅读的概述，不构成对许可证条款的修改或替代。
* **非商业用途免费**：个人游玩、学习研究、实验测试、教育机构、公益组织。
* **商业用途需另行获得作者授权**：请在 [Issues](https://github.com/ResAlexander/rail-switch/issues) 提出，或按作者 GitHub 主页上的邮箱联系。
* **分发义务**：任何形式的再分发都必须随附许可证全文（或其官方 URL），并保留下面这行署名。

```text
Required Notice: Copyright (c) 2026 Hardware Alexander (https://github.com/ResAlexander/rail-switch)
```

> 这是「源码可得」许可证，不是 OSI 认可的开源许可证，因此收费服务器与付费整合包不在免费使用范围内。

## 🛠️ 开发说明

开发细节详见 [DEVELOPING.md](DEVELOPING.md)。

---

<sup>[1]</sup> highspeed-rail：<https://modrinth.com/mod/highspeed-rail>

<div align="center">
  <p><sub>非官方 MINECRAFT 产品。未经 MOJANG 或 MICROSOFT 批准，亦与其无关。</sub></p>
</div>
