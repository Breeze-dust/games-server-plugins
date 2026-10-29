# ColorParty（色盲派对）—— 速查卡

> AI 先读这一段，能省掉读全部源码（5 个 Java 文件）

- **一句话职责**：每回合把**地板范围**按**形状拼布**刷成彩色混凝土（网格色块：每格一块底色，上面叠随机形状 圆环/方框/十字/叉叉/菱形/实心圆，形状用另一种候选色画；每种候选色至少占一格底色）、随机指定其中一种颜色并把**颜色面板**整块刷成该色，玩家看着面板找颜色站上去（指定色在场上=自己的底色格+散在别的格里的同色形状，几块区域都安全）；固定秒数倒计时结束后，**不是指定颜色的地板全部消失**，掉到淘汰线以下即淘汰；剩最后 1 人结算。颜色种数**随回合数增加**（默认第 1 回合 4 种、每回合 +2、封顶 16），指定颜色**不会连续两回合重复**，形状布局**每回合重新随机**。回合之间**不传送玩家**（原地跑向新颜色），中心出生点只在开局用。命令 `/cp`（别名 `/colorparty`）。
- **入口 / 主流程**：`ColorParty/src/main/java/com/colorparty/ColorPartyPlugin.java#onEnable` → `ColorPartyManager#load`（读 `arenas` + 快照文件 `arena-*.snap` + 战绩、40 tick 后兜底 `registerGame("色盲派对")` 并上报场地、启动 10 tick 一次的淘汰线兜底检测）→ 等待倒计时 `WaitCountdown` → 开局 `#start`（全员清背包 → 传**中心出生点** → 1 秒后 `#beginRound`）→ 回合引擎 `#beginRound`（守卫回合+1 → `#pickPool`/`#pickTarget` → `#paintFloor` 刷地板+面板 → 广播+标题 → 每秒动作栏倒计时）→ `#vanish`（地板里非指定色全部 `AIR`，物理更新关闭 → `round-gap-seconds` 后开下一回合）→ 淘汰 `#checkFall`（移动事件 + 10 tick 兜底任务，`Y < effectiveCutoff()` → `#eliminate`：旁观 + 传中心上空 + 广播）→ 结算 `#endGame`（冠/亚/季广播 → `#recordFirst` → `#restoreBlocks` 按快照重置地板+面板 → 全员回主城）。关服 `onDisable` → `#shutdown`（停任务 + `restoreAllBlocks` + `setup.exitAll`）+ `#saveAll`。
- **命令**：`/cp`（别名 `colorparty`）—— 玩家：`join <场名>`、`leave`、`list`、`stats [玩家]`、`help`；管理（OP 或 `cp.admin`）：`create <名>`、`remove <名>`、`edit <名>`、`rename <旧> <新>`、`setup <名>`、`setup-exit`、`pos1`、`pos2`（地板范围）、`panel1`、`panel2`（面板范围）、`spawn`（中心出生点）、`lobby`（等待点）、`cutoff`（淘汰线）、`save`（配置+快照）、`start`、`stats reset`。
- **建场道具**（`/cp setup`，公共库 SetupMode）：**木棍** 左/右键=地板角1/角2、**品红染料** 左/右键=面板角1/角2、**金锄头**=中心出生点、**纸**=等待点、**绿宝石**=保存（配置+快照）、**屏障**=退出。
- **配置**：`server/plugins/ColorParty/config.yml`（模板 `ColorParty/src/main/resources/config.yml`）—— `min-players: 2`、`max-players: 12`、`round-seconds: 5`（每回合倒计时）、`round-gap-seconds: 3`（塌陷→下一回合间隔）、`start-colors: 4`、`max-colors: 16`、`color-step: 2`、`arenas.<场名>.{pos1,pos2,spawn,lobby,panel-pos1,panel-pos2,cutoff-y}`。**cutoff-y 不设 = 地板最低点 - 2**（`ColorPartyArena.CUTOFF_UNSET` 哨兵，save 时未设置不落盘）。颜色种数公式：`clamp(start-colors + (回合-1)×color-step, 1, min(max-colors,16))`。
- **玩家记录**：命名空间 `colorparty`，YAML 结构 `games.colorparty.firsts.<uuid>`（冠军次数，只有冠军落盘，亚/季军只广播）；唯一文件 `server/plugins/MiniGameLib/players.yml`；清空命令 `/cp stats reset`（OP）。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 回合引擎（换色/倒计时/塌陷/淘汰/结算/广播文案） | `ColorParty/src/main/java/com/colorparty/ColorPartyManager.java`（16 色表 `COLORS`、颜色数公式 `colorsForRound` 也在这） |
  | 地板形状算法（网格切分/圆环/方框/十字/叉叉/菱形/实心圆） | `ColorParty/src/main/java/com/colorparty/ColorPartyFloor.java`（纯静态，`paint()` 一个入口；场地小到铺不满颜色数时自动裁颜色池保指定色） |
  | 场地字段（地板/面板/出生点/淘汰线）+ 地图快照读写 | `ColorParty/src/main/java/com/colorparty/ColorPartyArena.java` |
  | 事件入口（免伤/免饥饿/地板保护/道具/配置工具） | `ColorParty/src/main/java/com/colorparty/ColorPartyListener.java` |
  | 命令 / 权限 / tab 补全 | `ColorParty/src/main/java/com/colorparty/ColorPartyCommand.java` |
  | 显示名 / 图标 / 颜色（`minigame:` 段） | `ColorParty/src/main/resources/plugin.yml` |
  | 默认配置模板 / 线上配置 | `ColorParty/src/main/resources/config.yml` / `server/plugins/ColorParty/config.yml` |
  | 线上地图快照 | `server/plugins/ColorParty/arena-<场名>.snap`（文本 `x,y,z;blockdata`，地板+面板两个范围一起抓） |
- **注册名同步（一处改、三处改）**：`ColorPartyManager.GAME`（=`色盲派对`）＋ `plugin.yml` 的 `minigame.name` ＋ `server/plugins/HubCore/config.yml` 的 `games` 段与 `menu` 段（`action: game:色盲派对`，菜单位 `'25'`）；模板 `HubCore/src/main/resources/config.yml` 的 `games` 段同步。Manager `load()` 里有 `registerGame` 兜底，HubCore 配置漏改时 tab/菜单也能出现，但 alias 不一致会分叉分区。
- **常见坑**：
  - **面板（panel）可选但强烈建议设**：没面板时玩家只能靠聊天/标题里的颜色**名字**找色，难度大增；面板方块每回合整块刷成指定色，塌陷**只刷地板范围**，面板永不消失（面板若圈进地板范围，会被先刷随机色再刷成目标色，可以站）。
  - **对局中禁止抓快照**（`saveWithSnapshot` 会跳过）：会把彩色地板存成"原样"，之后每局重置成彩的。绿宝石/`/cp save` 在对局中只落配置。
  - **没快照的场**：每局结束后地板保持最后一回合的颜色不恢复（日志 warning）。必须用绿宝石/`/cp save` 认真保存一次生成 `arena-<场名>.snap`。
  - 关服 `saveAll()` 用内存回写 `config.yml`：运行中手改线上配置会被覆盖（与 TntRun/SkyPit 同款约定）。
  - 快照在**保存时**抓、每局结束重写"不一致"的格子；快照是文本文件，`pack-upload.ps1` 整包会自动带上。删场/改名连快照一起处理。
  - 所有事件监听都先判断"是不是我的玩家/我的场"再动手（铁律：同优先级拦截限定自己领域）；对局中地板/面板对**非 OP 路人**也是保护不可挖的（`inRunningArena`）。
  - 站进上一回合塌出的洞里不动 → 下回合重刷会把方块刷到脚下（免伤开着闷不死，移动会自己挤出来），下一轮塌陷照常判定；极端情况罕见，不值得为它加"跳过玩家所在格"的逻辑。
  - 淘汰判定只有"Y < 淘汰线"一条；对局玩家**免一切伤害**（摔落/窒息/火焰全取消），淘汰线没设就取地板最低点 - 2。
  - `round-seconds` 最小被钳到 2、`round-gap-seconds` 钳到 1（防配置写出 0 秒直接塌）。
- **验证方法**（游戏内，未实测，按此清单走）：
  1. `/cp create 彩色屋` → `/cp setup 彩色屋` → 快捷栏 6 件道具（木棍/品红染料/金锄头/纸/绿宝石/屏障）。
  2. 木棍左/右键圈**地板**（建议单层、水平 ≥ 10×10）；品红染料圈一块**面向地板的墙/柱**当面板；站地板正中央用金锄头设**中心出生点**；纸设等待点；绿宝石保存 → 期望「地图快照 N 格」，检查 `server\plugins\ColorParty\arena-彩色屋.snap` 存在。
  3. `/cp list` → 期望 `彩色屋 [WAITING] 已配置 面板已设 0人`；`/hub games` 应出现 **色盲派对**；主城菜单第 25 格出现品红混凝土图标。
  4. 两名玩家 `/cp join 彩色屋` → 等待倒计时或 OP 右键开始纸 → 期望全员传到中心出生点、背包只剩退出床（OP 另有开始纸在等待期）。
  5. 第 1 回合：地板按**形状拼布**出现 **4 种**颜色混凝土（可见圆环/方框/十字/叉叉等形状，同色聚成几块区域而不是噪点）、面板整块变指定色、聊天+标题报颜色名、动作栏倒数 5s → 5s 后**非指定色全部消失**。
  6. 站上正确颜色的玩家原地不动应存活；站错的脚下塌陷、掉出地板 2 格后广播「站错颜色掉下去了」并转旁观（传到中心上空）。
  7. 第 2 回合颜色应为 **6 种**（4+2）；指定色不应与上一回合相同；剩 1 人时广播冠军/亚军/季军、`/cp stats <玩家>` 冠军 +1、地板+面板回到保存前原样、全员回主城。
  8. 改 `round-seconds: 3` 后重启 → 倒计时变 3 秒（配置只在 `load()` 读一次，无 reload 子命令）。
- **未确认 / TODO**：
  - 未游戏内实测（本次只做了编译 + 冒烟启动验证插件 onEnable）。
  - 加入告示牌（joinsign）没做：加入靠主城菜单 / `/cp join`；需要再加（TntRun 有现成模式可抄）。
  - 回合重刷地板时若玩家恰好站在塌出的洞里，方块会刷进其脚下（见坑条目），实际手感未实测。
  - `WaitCountdown` 规则细节未读库源码核实（与 TntRun 同款调用，按其 README 口径）。
  - 面板与地板圈在**不同世界**时行为未定义（bounds() 会返回 null，等效于没设面板）。
