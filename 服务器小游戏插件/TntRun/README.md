# TntRun（雪塔大作战 — 原 TNT Run 跑酷）

> 速查卡（AI 先读这里，别先读源码）

- **一句话职责**：踩上雪块会消失，且消失后**沿下方雪柱继续排队消失（层层塌）**；掉到淘汰线以下即出局，最后站住的人是冠军。每人一把**效率 V + 不毁的铁铲**，挖掉一格雪块得 1 个雪球；**丢出的雪球砸中雪块 → 那一格只消失一格**（不连带塌），雪球也能砸到玩家（无伤害，只是普通雪球击退）。命令 `/tr`（别名 `/tntrun`）。
- **地图快照 + 开局圆阵（本次新增）**：建场时"圈地（pos1/pos2）+ 指南针选最上层中心"后按**保存**（绿宝石或 `/tr save`）＝ 配置落盘 **＋ 把 `pos1..pos2` 范围内的非空气方块抓成 `plugins/TntRun/arena-<场名>.snap`**；**每局结束（`endGame`）按这份快照把地图重置回保存那一刻**——塌掉的雪补回、本局新放的方块清成空气。开局时**以最上层中心为圆心、半径 5 格画圆（`SPAWN_RADIUS=5`），按在场人数把玩家平均分配在圆的边缘**（`TntRunArena#circleSpawns`：按角度均分后吸附到半径 5 上的整点，同一批人绝不重叠；>12 人才退化为按角度四舍五入）；重生也回自己那一格。**中心没设 → 退回出生点，全场站一起**（老场地行为不变）。
- **显示名**：`雪塔大作战`（HubCore 注册表显示名 = `TntRunManager.GAME` 常量）。插件名、jar 名、命令仍是 `TntRun` / `/tr`；**玩家记录命名空间仍是 `tntrun`（不迁移，老冠军次数照旧）**。
- **入口 / 主流程**：`TntRun/src/main/java/com/tntrun/TntRunPlugin.java#onEnable` → `TntRunManager#load`（读 `arenas` + 快照文件 `arena-*.snap` + `join-signs`、40 tick 后兜底注册 `雪塔大作战` 并把已有场地上报 HubCore 菜单、启动防蹲人任务）；等待倒计时 `WaitCountdown`（`TntRunManager#wait` / `#updateWaitCountdown`）；开局 `TntRunManager#start`（清背包 → **按人数算圆边缘站位并逐个传送** → `#giveSnowShovel` 发雪铲）；踩雪 `TntRunListener#onMove` → `TntRunManager#handleMove` → `#checkFade` → `#scheduleFade`（另有 `#startCampTask` 每 `camp-check-ticks` 兜一次）；挖雪 `TntRunListener#onBlockBreak` → `#breakSnow` → `#giveSnowball`；雪球砸雪块 `TntRunListener#onProjectileHit` → `#breakSnowByBall`；掉出判定写在 `#checkFade`（`Y < cutoff-y` → `#eliminate`）；结算 `#endGame`（冠/亚/季广播 → `#recordFirst` → `#restoreBlocks`＝**按快照重置地图** → 全员回主城）；关服 `TntRunPlugin#onDisable` → `TntRunManager#shutdown` + `#saveAll`。
- **命令**：`/tr`（别名 `tr` / `tntrun`）—— 玩家：`join <场名>`、`leave`、`list`、`stats [玩家]`、`help`；管理（`isOp()` 或权限节点 `tr.admin`）：`create <场名>`、`remove <场名>`、`edit <场名>`、`rename <旧场名> <新场名>`、`setup <场名>`、`setup-exit`、`pos1`、`pos2`、`spawn`、`lobby`、**`center`（最上层中心＝开局圆阵圆心）**、`cutoff`、`save`（＝**配置 + 地图快照**）、`start`、`joinsign <场名>`、`stats reset`。`plugin.yml` 另外声明了 `tr.use`（默认 true，代码里没有任何检查）。
- **配置**：`server/plugins/TntRun/config.yml`（源码模板 `TntRun/src/main/resources/config.yml`）—— 生效键与线上值：`min-players: 2`、`max-players: 12`、`fade-ticks: 10`（踩上后 10 tick = 0.5s 消失，往下每层再等 10 tick）、`camp-check-ticks: 20`、`join-signs: {}`、`arenas.<场名>.{pos1,pos2,spawn,lobby,center,cutoff-y}`；线上唯一场 `TNTRUNround`（世界 `world_tr`，`cutoff-y: -8`，没有 `lobby` 键，**`center` 键要等建场时用新 jar 设过中心并保存后才会出现**）。淘汰线键名是 `cutoff-y`，设置命令是 `/tr cutoff`。**没有新增配置键**（快照单独存文件；圆半径是代码常量 `TntRunArena.SPAWN_RADIUS = 5`）。
- **玩家记录**：命名空间 `tntrun`，YAML 结构 `games.tntrun.firsts.<uuid>`（插件视图内写作 `firsts.<uuid>`，值为冠军次数）；唯一文件 `server/plugins/MiniGameLib/players.yml`（权威说明 `docs/玩家记录.md`，代码 `MiniGameLib/src/main/java/com/hub/data/PlayerStats.java`）；清空命令 `/tr stats reset`（OP）。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 玩法：踩雪/层层塌/雪球/淘汰/结算/倒计时/告示牌 | `TntRun/src/main/java/com/tntrun/TntRunManager.java` |
  | **地图快照（保存/重置）、最上层中心、圆阵站位、快照文件读写** | `TntRun/src/main/java/com/tntrun/TntRunArena.java`（`captureSnapshot` / `saveSnapshotFile` / `loadSnapshotFile` / `restoreSnapshot` / `circleSpawns` / `circleOutOfRange`；半径常量 `SPAWN_RADIUS`） |
  | 事件入口：移动、挖雪给球、雪球砸块、退出、重生、摔落伤害、右键道具 | `TntRun/src/main/java/com/tntrun/TntRunListener.java` |
  | 命令、帮助文本、权限判断 | `TntRun/src/main/java/com/tntrun/TntRunCommand.java` |
  | 场地字段（范围/出生点/等待点/淘汰线） | `TntRun/src/main/java/com/tntrun/TntRunArena.java` |
  | 显示名 / 图标 / 颜色（`minigame:` 段） | `TntRun/src/main/resources/plugin.yml` |
  | 默认配置模板（新服首次生成） | `TntRun/src/main/resources/config.yml` |
  | 线上实际配置（场、加入告示牌） | `server/plugins/TntRun/config.yml` |
  | **线上地图快照（每局重置用，二进制无关的文本文件）** | `server/plugins/TntRun/arena-<场名>.snap`（格式 `x,y,z;blockdata` 每行一格，与建筑猜猜乐 `arena-*.snap` 一致；`pack-upload.ps1` 整包带 `plugins/`，会自动包含） |
- **注册名一处改、三处同步**（改名/改色时）：`TntRunManager.GAME`（Java 常量）＋ `TntRun/src/main/resources/plugin.yml` 的 `minigame.name` ＋ **`server/plugins/HubCore/config.yml` 的 `games` 段与 `menu` 段（`action: game:雪塔大作战`）**；`HubCore/src/main/resources/config.yml` 是模板同步改。少改一处 → 菜单点进去「游戏不存在」。
  **上传铁律**：`HubCore/config.yml` 与 `TntRun.jar` 必须**同一次**一起覆盖。只传 `TntRun.jar` 而线上 `games` 段还是 `TNT Run` ⇒ TntRun 报的场地状态（`setArena("雪塔大作战", …)`）在注册表里查不到、菜单羊毛永远绿/0 人（`HubAPI` 没有"已登记游戏名"的查询接口，插件端无法自动退回旧名）。
- **常见坑**：
  - **消失条件变宽了**：只看"脚下一格是 `SNOW_BLOCK` 且在 `pos1..pos2` 内"，不再要求正下方是特定方块；消失后如果下面还是雪块就继续排队，所以**必须用雪块铺塔（不要只铺一层）**，否则第一层没了玩家就一直往下掉。
  - `TntRunListener#onBlockBreak` 只在"对局中（RUNNING）+ 场地范围内 + 雪块"时接管：`setDropItems(false)` 取消原版掉落，改用 `#giveSnowball` 手动给 1 个雪球（背包满就掉在脚下）。**挖到的雪球不在 `broken` 之外**——`#breakSnow` 会把这一格记进 `broken`，结算/关服会还原。
  - **雪球砸块不连带塌**（需求明确）：只 `setType(AIR)` 那一格 + 记 `broken`；它砸出来的洞玩家自己掉进去才触发层层塌。
  - 配置只在 `load()` 里读一次，`/tr` **没有 reload 子命令** → 改 `config.yml` 必须重启服务器。
  - 关服 `saveAll()` 会用内存配置回写 `config.yml`，服务器运行中改或上传配置会被覆盖（`docs/开发须知.md` 第 4 节第 5 条）。
  - `broken` 只存在内存里；关服 `shutdown()` 会 `restoreAllBlocks()` 还原地板，但崩服/杀进程会留下永久空洞。
  - **快照与圆阵的坑**：
    - **没保存过快照（老场地）→ 每局结束只按 `broken` 逐格补回**，玩家新放的方块不会清、地形"污染的痕迹"也修不回来。**必须在新 jar 上用绿宝石/`/tr save` 认真保存一次**才会生成 `arena-<场名>.snap`。
    - **对局中禁止抓快照**（`saveWithSnapshot` 会跳过并提示）：那会把已经塌掉的地形存成"原样"，之后每局都重置成破的。
    - **圈地范围太小**（水平不足约 11×11）→ 半径 5 的圆会伸出 `pos1..pos2`，站到圆外的玩家**脚下没有雪块、开局直接掉下去**。设中心 / 保存时都会警告「圆有 N 个点在圈地范围外」。
    - 中心的 **Y 取的是"玩家脚下那一格"**：所以设中心时要**站在最上层雪面正中央**（脚底 Y 就是圆上玩家的脚底 Y）。站偏了/飞在半空设 → 开局全员悬空或卡在雪里。
    - 圆半径 5 上的**整点只有 12 个**（`(±5,0) (0,±5) (±3,±4) (±4,±3)`）：≤12 人时每人独占 1 格且正好 5 格远；**>12 人**（`max-players` 调大过）才退化成按角度四舍五入，可能出现同格/距离 4 的情况。
    - 快照是**文本文件**（`x,y,z;blockdata` 每行一格）：这份场（34×49×34）抓下来通常是几百 KB～1 MB 级别，抓快照那一下会有短暂卡顿（只在保存时发生一次）；每局结束只重写"和快照不一致"的格子。
    - 场地**改名**会把 `arena-旧名.snap` 重命名成 `arena-新名.snap`；**删场**会连快照一起删。
  - `cutoff-y` 缺省时 `getInt` 返回 0，玩家 Y<0 就淘汰；线上这个场是 -8。
  - 进服 / 回主城的 lobby 回调必须用静默版 `leave(p, true)`（`TntRunPlugin#onEnable` 已如此注册），否则进服会刷「你不在任何场中」（`docs/开发须知.md` 第 5 节）。
  - 结算只有冠军写战绩（`firsts`）；亚军/季军只在广播里出现，不落盘。
  - 旧世界 `world_tr` 里还是**沙子 + TNT** 的场地：代码只认雪块，不改地形就"踩上去什么都不塌"。铺雪方案见下发「验证方法」第一条。
- **验证方法**：
  - `/tr list` → 期望 `TNTRUNround [WAITING] 已配置 0人` 这类一行；`/hub games` 里应出现 **雪塔大作战**（不再是 TNT Run）。
  - **先把 `world_tr` 场地改成雪**：进 `/tr setup TNTRUNround` 用 `pos1/pos2` 圈的范围不变，把原「沙子 + 下方 TNT」那两层都换成雪块即可（命令 `/fill` 或用 `/tr setup` 配套工具外的方式铺；铺完 `/tr save`）。**一定要铺多层**，单层会直接掉到底。
  - 两名玩家 `/tr join TNTRUNround`（`min-players: 2`），OP 右键「开始游戏」纸或 `/tr start` → 期望被传到出生点、广播「游戏开始」、**背包第 1 格是「雪铲」(效率 V / 不毁)**。
  - **地图快照 + 重置（本次新增，按顺序测）**：
    1. `/tr setup TNTRUNround` → 快捷栏应为 6 件道具（木棍/金锄头/纸/**指南针（中心点）**/绿宝石/屏障）。
    2. 站在**最上层雪面正中央**用指南针右键 → 期望「已设置 … 最上层中心：world_tr x y z」；再右键绿宝石 → 期望「已保存场 TNTRUNround：地图快照 N 格」，若圆伸出圈地范围会多一行红色警告。
    3. 检查磁盘：`server\plugins\TntRun\arena-TNTRUNround.snap` 存在、非空（`Get-Item` 看字节数）；`server\plugins\TntRun\config.yml` 的 `arenas.TNTRUNround` 下多出 `center:` 段。控制台此时也应有 `场 TNTRUNround 地图快照已载入：N 格`（重启后）。
    4. 2~4 名玩家开局 → 期望**每人被放到以中心为圆心、半径 5 的圆上，彼此分开、面向圆心**；人数=3 时应是 `(5,0) (-3,4) (-3,-4)` 三点（见 `TntRunArena#circleSpawns`）。
    5. 故意挖/砸掉几格 + 手放一个方块（创造模式）→ 让对局结束（其余人跳下去）→ 期望广播后**地图完整回到保存那一刻**：挖掉的雪都回来了、手放的方块消失、下一局开局的圆阵脚下是完整的雪。
    6. 局刚结束时盯着上一局塌掉的位置 1~2 秒 → 期望**不会**再突然缺一格（`#scheduleFade` 的局次守卫：上一局残留的延迟消失任务不再改已重置的地图）。
    7. 关服再启动 → 期望控制台 `地图快照已载入：N 格`；把场地改名 `/tr rename TNTRUNround TR2` 后检查文件变成 `arena-TR2.snap`。
  - 站上雪层走两步 → 期望约 0.5s 后脚下那格变空气，**紧接着下面那些雪块也一格一格（每格 0.5s）继续消失**，人越走越低；原地不动也应塌（防蹲人任务）。
  - 对雪块左键挖 → 期望雪块立刻没、**背包里雪球 +1**，且不掉原版雪球物品。
  - 右键丢雪球砸远处雪块 → 期望**只有被砸中的那一格**消失（下方那格还在）；砸到别的玩家 → 无伤害、只是普通雪球击退。
  - 掉下平台到 Y < -8 → 期望变旁观 + 广播「XX 掉下去了」、人数减少。
  - 剩最后 1 人 → 期望广播冠军/亚军/季军、`/tr stats <玩家>` 冠军次数 +1、地板恢复（**按快照重置**，挖掉/砸掉的雪块、本局新放的方块一起回到保存那一刻）、在场玩家被送回主城。
  - 踩掉几格后重启服务器 → 期望地板恢复（关服兜底）；重启后 `/tr stats` 数字不丢。
  - `/tr joinsign TNTRUNround` 后右键一个告示牌 → 期望出现 `[加入] TNTRUNround` 牌（第 4 行是「雪球砸人/挖雪得球」），右键它可直接入场。
  - （只在动了玩家记录读写时）`check-stats.ps1` 期望最后一行 `[OK] player records: migration + merge-on-save verified`（见 `docs/玩家记录.md` 第 5 节）。
- **未确认 / TODO**：
  - **线上 `world_tr` 的地形还是沙子 + TNT，本次没有改世界**（只改代码）→ 上线后必须先按上面第一条把场地换成多层雪块，否则踩上去不会塌。
  - **线上 `TNTRUNround` 目前既没有 `center` 也没有 `.snap`**：新 jar 上线后必须由 OP 走一遍「站最上层中心 → 指南针 → 绿宝石保存」，否则开局仍是全队挤在出生点、每局结束仍走旧的 `broken` 逐格还原。
  - 一次抓快照/一次整份重置在这份场（34×49×34 ≈ 6 万格）的**实测耗时没量**（只在保存时抓一次、每局结束重置一次；按建筑猜猜乐同类实现推测是几百毫秒级）。
  - `plugin.yml` 的 `tr.use` 节点没有任何 Java 代码检查（只检查 `tr.admin`），是否有别处依赖它未确认。
  - `WaitCountdown` 的规则细节（`TntRunManager#wait` 注释写的 OP 暂停 / 人数不足取消 / 满人 3s / 达标 30s）未读 MiniGameLib 源码核实。
  - 场 `TNTRUNround` 没配 `lobby`（等待点），代码在 `join` 时兜底用 `spawn`；游戏内等待点的实际表现未实测。
  - 层层塌的**深度上限**没有单独配置（只受场地 `pos1..pos2` 的 Y 范围约束）；雪塔能塌多深完全取决于铺了多少层。
  - 圆阵半径写死在 `TntRunArena.SPAWN_RADIUS = 5`（需求就是 5 格）；要改成可配得加 `config.yml` 键（会牵动 `verify.ps1` 的关键配置检查）。
