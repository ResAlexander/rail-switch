# Changelog

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)。发布到 Modrinth / CurseForge / GitHub Release 时，本文件对应版本的段落直接作为 changelog。

## 1.2.1

**内部命名空间更正**。行为与 1.2.0 完全一致，没有任何逻辑改动。

### 变更
- Java 包名 `com.resalexanderccho.railswitch` → **`io.github.resalexander.railswitch`**：改用 GitHub 登录名作为命名空间（原 `resalexanderccho` 是已弃用的旧名）。只影响源码目录与 jar 内的 class 名，**不影响 mod id、方块注册 id、存档、命令与配置**。
- README 安装示例中的 jar 文件名同步为 1.2.1。

（1.2.0 是首个公开发布版本，其内容见下一节。）

## 1.2.0

**铁轨样式改版 + 合规自检**。行为与 1.0.1 完全一致，改动只在贴图与工程侧。

### 变更
- 物品图标改为 **两层结构**：底层按 id 引用原版铁轨贴图（`minecraft:block/rail`，jar 内不打包原版素材），上层是自绘的**方块标记**——左开在左上角（蓝）、右开在右上角（橙），位置与颜色同时说明往哪边岔。
- 修正彩色旋钮被深色描边吃干的问题：16×16 下旋钮原本一个彩色像素都不剩，转辙机贴图左右看不出差别；改为无描边纯色块（左开 16 px 蓝 / 右开 16 px 橙）。
- 岔位时转辙机摇柄转向本岔的曲股方向（左开 +45°、右开 −45°），与旋钮颜色构成双重区分。
- 新增 `tools/check_compliance.py`：三项断言 —— ① jar 内不含 `assets/minecraft/**`；② 自带贴图与原版同尺寸全部贴图的非透明像素重合率为 0；③ 列出引用的原版资源并断言「引用了谁就没打包谁」。
- **许可证定为 PolyForm Noncommercial 1.0.0**：非商业用途免费（个人、教育、公益等），**商业用途需另行获得作者授权**，任何分发都必须保留 `Required Notice:` 署名行。
- 新增 `LICENSE`（PolyForm NC 1.0.0 + Required Notice）与 `CHANGELOG.md`。
- 仓库文档重构：`README.md` 改为**英文**用户文档（esp-claw 式的居中 Hero 区 + 徽章 + 卡片式特性表），中文版独立为 `README.zh-CN.md`，两文件顶部互相链接；开发、构建、代码规矩与测试清单移入 `DEVELOPING.md`。平台简介正文取自英文 README。
- **行为描述更正（用户 2026-09-22 实测指出）**：此前文档写「无输入则直行」不准确 —— 道岔**不会自动复位**，它会保持上一次被扳到的位置，没有输入时矿车按道岔**当时**的位置通过。已同步修正中英 README、`fabric.mod.json` 的描述、源码注释与教案。
- 素材来源写进 `DEVELOPING.md`（世界内铁轨按 id 引用原版贴图，jar 内不打包任何原版素材），README 只保留面向玩家的内容。
- 免责声明采用 Mojang 官方标准句式（两个 README 页脚）。

### 行为
- 与 1.0.1 相同：直行/岔位判定、进路提交与冻结、逆向汇合、配置项均未改动。

## 1.1.0

**发布准备：合规与元数据**（未单独发布，内容并入 1.2.0）。

### 变更
- 物品图标改为**完全自绘**，不再派生自原版贴图（合规要求：不打包 Mojang 素材）。
- 新增 256×256 模组图标（`assets/rail_switch/icon.png`），用于模组列表与平台项目页。
- 元数据：`fabric.mod.json` 补英文描述、中文描述、图标、源码/问题链接。

## 1.0.1

### 修复
- 修复「列车经过道岔时道岔高频抖动、矿车被推回反方向」：引入**进路提交 / 冻结**语义 —— 车已在道岔格上、车已进入提交距离、或锁已被提交时，任何逻辑都不再改形状；来向不属于本几何时同样不动。根因是车在格内转弯后速度方向已变为曲股方向，用速度反推来向会被误判成「从直股另一端来」，把形状改回直轨，而直轨会把带曲向速度的车推向排序靠前的出口（西），于是倒退。

### 新增
- 配置项 `debugLog`：打印 `set / freeze(on-switch) / freeze(committed) / skip(entry-not-in-geometry)`。

### 实测
- 220 bps（约 792 km/h）丝滑过岔；抖动与倒退消失。

## 1.0.0

### 新增
- 两个方块 `left_switch_rail` / `right_switch_rail`：骑手按住「向左/向右」（默认 A/D）时岔向曲股；从曲股方向来车自动汇入尖轨端。（无输入时的实际行为见 1.2.0 的文档更正。）
- 每 tick 提前量判定（`max(2, 速度×2+2)` 格）、按键记忆窗口（默认 10 tick）、进路冻结与冲突优先级。
- 与原版铁轨自动对接（进 `minecraft:rails` 标签 + 继承 `BaseRailBlock`）；零 mixin。
- 配置 `config/rail_switch.json`：`lookaheadBlocks`、`inputMemoryTicks`、`switchSound`、`requireNewPhysics`、`trailingBehavior`。
