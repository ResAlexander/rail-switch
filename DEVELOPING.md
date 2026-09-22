# 开发说明（Rail Switch）

面向作者与后续 AI 会话：构建方式、自检脚本、代码约定、修复记录与测试清单。用户文档：[README.md](README.md)（English）· [README.zh-CN.md](README.zh-CN.md)（简体中文）。

## 构建

```bash
# 需要 JDK 25（Gradle 自身也要跑在 21+ 上）。把 JAVA_HOME 指向你的 JDK 25：
JAVA_HOME="<你的 JDK 25 路径>" ./gradlew build
# macOS 标准安装下可用：JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew build
# 产物：versions/26.2-fabric/build/libs/rail-switch-<版本>-fabric-26.2.jar
```

* 版本号在 `stonecutter.properties.toml` 的 `mod.version`。
* `versions/` 是 stonecutter 的按版本工作区，`build/` 已在 `.gitignore` 中。

## tools 三个脚本

| 脚本 | 作用 | 什么时候跑 |
| --- | --- | --- |
| `gen_resources.py` | 生成模型、blockstate、语言、标签、战利品表、物品定义与**全部贴图**（贴图是代码画出来的，不引用任何外部图片） | 改了资源需求后 |
| `validate_resources.py` | 引用自检：模型/贴图/物品定义是否都能落地 | 每次改资源后 |
| `check_compliance.py` | 合规断言三项（见下） | 发布前必跑 |

## 合规自检（`check_compliance.py`）

三项断言，失败退出码非 0：

1. jar 内 `assets/minecraft/**` 条目数 = 0；
2. 每张自带贴图与本机原版**同尺寸全部贴图**逐像素比对，非透明像素重合率低于阈值（实际为 0%，`MIN_FAIL_PIXELS` 兜住小图撞色噪声）；
3. 列出模型/blockstate 引用的**原版资源**，并断言「引用了谁就没打包谁」。

### 素材来源的边界（写清楚，别靠记忆）

* 世界内铁轨（直股 / 曲股）与物品图标底层：**按 id 引用** `minecraft:block/rail` / `minecraft:block/rail_corner`，**不进 jar**，运行时由游戏从玩家自己的安装读取。所以「看起来像原版」的原因是同一个文件，而不是复制。
* 随包发布的 5 张贴图全部自绘：`switch_stand_left/right.png`、`switch_marker_left/right.png`、`icon.png`。
* **反直觉但重要**：「引用原版贴图」比「自己画一张像原版的贴图」更安全 —— 后者是把模仿他人表达的作品打包分发，更容易被认定为演绎作品。所以「要原版外观」这个需求，正确解法是引用而非重绘。
* 副作用（通常是优点）：玩家用资源包换掉原版铁轨贴图时，道岔跟着一起变。

> 需要提醒的是：这里给的是「零分发」这一可验证的技术结论。至于「引用是否完全无争议」「像不像算不算侵权」属于法律判断，不是本项目能下的结论。

## 与 MC 版本相关的面

只有三处，升级 MC 时优先看这三处：

1. 读玩家输入 —— `logic/RiderInput`（用 `ServerPlayer#getLastClientInput`，**不要**用 `getLastClientMoveIntent`，那是视角换算过的世界方向）；
2. 改方块形状 —— `block/AbstractSwitchRailBlock`；
3. 解析轨道几何 —— `logic/SwitchGeometry`、`logic/RailPathWalker`。

判定新物理：`AbstractMinecart#useExperimentalMovement(level)` / `cart.getBehavior() instanceof NewMinecartBehavior`。

## 代码规矩（非必要不繁琐）

1. **死代码零容忍**：未用的字段、参数、导入、注释掉的旧实现一律删；历史交给 git，不用注释留档。
2. **不做「将来可能用到」的抽象**：扩展点写在 README / `PSEUDOCODE.md`，不写成空壳代码。
3. **一个类只做一件事**，公开面尽量小，跨类只留必要方法。
4. **数字与字符串集中**：常量放类顶、可调项进配置，别在逻辑里撒魔数。
5. **合并文件不等于简洁**：19 行的 `LeftSwitchRailBlock` / `RightSwitchRailBlock` 保留（两种业务对象）；而三处与 MC 版本相关的面要分开，方便升级。
6. **每次改动后跑**：`./gradlew build` + `python3 tools/validate_resources.py` + `python3 tools/check_compliance.py` + 未用 import 检查（见下）。
7. **LOC 记账**：当前 10 个文件 / 约 830 行；加功能前先想「能不能塞进现有类，能不能顺手删掉别的」。

```bash
# 未用 import 自检
python3 - <<'PY'
import re, pathlib
for f in sorted(pathlib.Path("src/main/java").rglob("*.java")):
    t = f.read_text(encoding="utf-8")
    body = re.sub(r'^import .*$', '', t, flags=re.M)
    for imp in re.findall(r'^import (?:static )?([\w.]+);', t, re.M):
        if not re.search(r'\b' + re.escape(imp.split('.')[-1]) + r'\b', body):
            print(f"{f}: 未用 {imp}")
PY
```

## 修复记录

### 1.2.0 —— 贴图改版 + 合规自检

* 物品图标沿用 1.0.1 样式（原版铁轨外观 + 方块标记）：左开保持左上角蓝方块，右开改为右上角橙方块；底层由「复制原版贴图」改为**引用**，解决 1.0.1 的合规问题。
* 修掉彩色旋钮被深色描边吃干的问题（16×16 下转辙机贴图曾一个彩色像素都没有，左右看不出差别）。
* 新增 `LICENSE`（PolyForm NC 1.0.0）、`CHANGELOG.md`、`tools/check_compliance.py`；发布者署名改为 `Hardware Alexander`。

### 1.0.1 —— 修「过岔时道岔高频抖动、矿车倒退」

* 症状：车经过道岔时形状在岔位/直位之间高频切换，车被原速推回反方向。
* 根因：车在道岔格内转弯后速度方向已变成曲股方向，而调度器每 tick 用速度反推「来向」，于是被误判成「从直股另一端来 → 恒直行」，形状被改回直轨；此时车带着曲股方向的速度落在直轨形状上，原版 `stepAlongTrack` 会把它推向两个出口里排序靠前的那个（西），车就以原速倒退，再触发下一轮误判。
* 修法：加入**进路提交 / 冻结**语义（真实道岔语义：车上了尖轨就不能再扳）：
  1. 车已经在道岔格上 → 绝不改形状；
  2. 车已进入提交距离（本 tick 必进格）→ 不再改；
  3. 被别的车锁住且该锁已提交 → 任何车都不许抢；
  4. 来向不属于本几何（车正在格内转弯的中间态）→ 不动形状。
* 另加 `debugLog` 开关：日志会打印 `set / freeze(on-switch) / freeze(committed) / skip(entry-not-in-geometry)`。
* **实测（2026-09-20）**：220 bps（约 792 km/h）丝滑过岔，抖动/倒车消失。

## 已知限制

1. 连接数不是 3（0/1/2/4 个方向接轨）时，方块退化为普通铁轨，不响应按键。
2. 坡道形状（`ascending_*`）不参与道岔逻辑，按普通铁轨渲染与行为。
3. 高速下「临门一脚」按来不及，属于物理必然（车已越过判定点）。
4. 同一 tick 多辆车抢一条道岔时：近者优先，同距时岔位请求优先；被挤掉的那辆车按当时的形状通过。
5. 若在车正过岔的瞬间有玩家在旁边放置铁轨，原版的邻居重连可能短暂改写道岔形状（极少见，会在下一 tick 被拨回）。

## 测试清单（游戏内验证用）

1. 8 bps：直行、按住 A 左岔、按住 D 右岔、提前松手。
2. 40 bps：同上三组。
3. 200 bps：提前 2 秒按住 / 提前 0.3 秒按 / 临门按。
4. 边界：空车、双乘客、反向来车（逆向汇合）、道岔前有弯、道岔后有弯、道岔放在坡上、刚放下就过车、刚加载区块就过车。
5. 共存：与 highspeed-rail 同时开；Carpet 假人（无键盘输入，应直行）。
