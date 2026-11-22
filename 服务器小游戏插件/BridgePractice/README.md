# BridgePractice（搭路练习）—— 速查卡

> AI 先读这一段，能省掉通读源码。

- **一句话职责**：起床战争搭路练习。用 `/bridge` 打开房间菜单，列出所有练习房间；
  进入房间后物品栏里的练习方块**自动补满**，从起点搭路到终点计时，掉进虚空自动清掉本次方块、送回起点。
  终点可以是一个点，也可以用两个端点设成**一条终点线**（站到线附近任意位置都算到达）。
  每个房间同一时间可有多名玩家一起练；**第一个进入的人是房主**，可以切换房间是否允许其他人加入。
- **定位**：主城练习设施（类似 CloudWalk / LobbyToys），**不注册进 HubCore 的小游戏列表**，
  没有等待室。只依赖 `MiniGameLib`（`HubAPI` + `PlayerStats` + `Chat`），可选主城；
  进入时 `HubAPI.setPlaying("搭路练习")`，离开/下线 `clearGame`；注册了回主城回调。
- **入口 / 主流程**：`BridgePracticePlugin#onEnable` → `BridgePracticeManager#load`
  （读 `config.yml` 设置 + `courses.yml` 房间 + `players.yml` 纪录，开服清理残留练习方块）→
  `/bridge` 打开房间菜单（或 `/bridge join <房间>`）→ `#join`（保存背包、清空、发练习方块和道具、
  传送到出生点（没设置则用起点）并朝向终点；记录房主）→ 每 2 tick `#tick`：掉到虚空线以下 → 0.5 秒后回出生点；
  进入终点半径 → 记录用时、刷新个人最佳和房间最快榜，3 秒后回出生点继续下一轮。
- **计时**：走到起点后，第一次放方块或离开起点 1.5 格时开始；动作栏实时显示「用时 / 距终点 / 个人最佳」。
  出生点离起点较远时，进房/掉落回出生点不会立刻计时，要先走到起点。
- **每次掉落/到达终点后的成绩**（聊天栏 + 标题）：
  - 搭路时间
  - 水平位移（到达终点时 = 起点到终点的水平距离；掉落时 = 本次最远水平位移）
  - 平均速度 = 水平位移 ÷ 时间（格/秒）
  - 放置方块数
  - 以及房间当前最快纪录
- **房间 / 房主**：房间 = `courses.yml` 里配置的一条练习路线。
  - **出生点**：`/bridge setspawn <房间>` 单独设置，进入房间、掉落、到达终点后都回到这里；
    不设置时默认用起点线中点。出生点离起点线超过 `start-line` 时，玩家要先走到起点再出发才会计时。
  - 第一个进入的玩家成为房主；房主离开时，房主自动转给房间里下一个玩家。
  - 房主右键第 6 格金镐（或 `/bridge breakblocks`）切换房内玩家**能不能拆别人搭的方块**，默认禁止。
  - 房主右键第 7 格比较器（或 `/bridge lock`）切换开放/关闭；关闭后其他人无法进入该房间。
  - 房间空了以后自动恢复成开放、禁止挖别人方块。
- **自动补方块**：放方块后下一 tick 补一次，另有每 `refill-interval-ticks`（默认 20）兜底；
  把物品栏里的练习方块补回 `give-amount`（默认 64）。练习方块耗尽不会没得搭。
- **物品栏**：第 1 格练习方块；第 6 格房主专属「挖别人方块」金镐；
  第 7 格房主专属「房间加入」比较器；第 8 格「回到出生点」星标；第 9 格「离开练习」屏障
  （**离开后回主城出生点**）。道具用 PDC 标记，不能丢；掉出房间范围会被送回出生点。
- **命令**：
  - 玩家：`/bridge`（菜单）、`/bridge join <房间>`、`/bridge leave`、`/bridge reset`、
    `/bridge lock`、`/bridge breakblocks`、`/bridge list`、`/bridge top [房间]`、
    `/bridge stats [玩家]`、`/bridge help`
  - 管理（OP 或 `bridgepractice.admin`）：`/bridge create <名字>`、`/bridge setstart <名字> [1|2]`、
    `/bridge setspawn <名字>`、`/bridge setend <名字> [1|2]`、`/bridge icon <名字> <方块>`、
    `/bridge clear <名字>`、`/bridge delete <名字>`、`/bridge reload`
  - `setstart` / `setend` 不填序号：设成一个点；填 `1` / `2`：分别设置起点线 / 终点线的两个端点
    （两个端点连起来就是线）。
- **房间最快排行榜**：按完成用时从短到长，数据在每个房间（场地名）下：
  - 菜单里每个房间显示前 3 名
  - `/bridge top <房间>` 显示前 `top-size`（默认 10）名
  - 刷新房间第一时全服播报
- **配置**：`server/plugins/BridgePractice/config.yml`
  - `give-amount`、`refill-interval-ticks`、`fall-depth`（虚空线 = 起点/终点较低者往下多少格）、
    `respawn-delay-ticks`、`success-delay-ticks`、`end-radius`、`start-line`、`top-size`、
    `practice-block`（练习方块材质）、`clear-margin`、`cleanup-on-startup`
- **房间数据**：`server/plugins/BridgePractice/courses.yml`，由命令写入，不要手改。
  每个房间：`world`、`start`、`spawn`、`end-1`、`end-2`、`end-radius`、`fall-y`、`icon`。
- **玩家记录**：命名空间 `bridgepractice`，写在唯一的 `server/plugins/MiniGameLib/players.yml`：
  - `best.<房间>.<uuid>` = 最佳用时（毫秒）、`bestAt.<房间>.<uuid>`
  - `finishes.<房间>.<uuid>` 完成次数、`falls.<房间>.<uuid>` 掉落次数、`names.<uuid>` 玩家名
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 加入/离开、房主/开放状态、计时、虚空/终点判定、补方块、纪录 | `BridgePractice/src/main/java/com/bridgepractice/BridgePracticeManager.java` |
  | 房间数据与位置字符串 | `BridgePractice/src/main/java/com/bridgepractice/PracticeCourse.java` |
  | 命令 / 权限 / tab 补全 | `BridgePractice/src/main/java/com/bridgepractice/BridgePracticeCommand.java` |
  | 事件入口（放/拆方块、道具、免伤、下线、重生） | `BridgePractice/src/main/java/com/bridgepractice/BridgePracticeListener.java` |
  | 房间选择菜单 | `BridgePractice/src/main/java/com/bridgepractice/CourseMenu.java` |
  | 背包备份与恢复 | `BridgePractice/src/main/java/com/bridgepractice/PlayerState.java` |
  | 房间运行时状态（房主/开放/玩家） | `BridgePracticeManager` 末尾的 `RoomState` |
- **怎么搭一个房间**：
  1. 建好起点平台、终点平台/终点线（中间留空，下面是虚空或深坑），两段之间距离就是练习长度。
  2. 站到起点平台：`/bridge create 直线搭路` → `/bridge setstart 直线搭路`（点）。
     想用一条起点线：在起点线两端分别 `/bridge setstart 直线搭路 1` / `2`。
     出生点可以单独设（比如起点后面的准备平台）：站在上面 `/bridge setspawn 直线搭路`；不设就用起点线中点。
  3. 设置终点（两种任选）：
     - 点：站到终点平台中心，`/bridge setend 直线搭路`。
     - 线：站到终点线一端 `/bridge setend 直线搭路 1`，再站到另一端
       `/bridge setend 直线搭路 2`；玩家从线的任意位置附近经过都算到达。
  4. `/bridge join 直线搭路` 试一次：从起点搭到终点应提示完成并显示用时/速度/位移/方块数；
     掉下去应回起点并显示本次数据。
  5. 更多房间重复以上步骤，玩家用 `/bridge` 菜单选。
- **注意**：
  - 起点/终点平台**不要用练习方块（默认白色羊毛）搭**：开服清理和 `/bridge clear` 会沿
    起点→终点走廊删掉该材质的方块。用石头、木板等其它材质做平台最安全。
  - `/bridge clear <房间>` 和开服清理只删 `practice-block` 材质，且只扫起点→终点走廊 ±`clear-margin`；
    房间里有人练习时改起点/终点、删除房间会被拒绝。
  - 练习中免伤、免饥饿、不能丢物品；默认只能拆自己本次搭的方块，房主可用第 6 格金镐
    放开「挖别人方块」；非练习玩家不能拆别人练习中的方块。
  - 同一房间多人同时练习时，大家共用同一条路线，方块可能重叠；不想被打扰就由房主关闭加入。
  - 终点判定：点模式按水平半径（`end-radius`，默认 2.5）+ 垂直 ±(radius+1)；
    线模式按「离线段的水平最近距离 ≤ `end-radius`」+ 垂直高度判断，走到线上任意位置都算到达。
    起点线同理：离开起点线 `start-line` 格后开始计时，水平位移也按离起点线的距离计算。
  - 房间被删除/修改后，旧纪录仍留在 `players.yml`（按房间名索引）。
- **验证方法**：
  1. 部署 `BridgePractice.jar` + `MiniGameLib.jar` 后启动，控制台应出现
     `已加载 N 个搭路练习房间，练习方块 WHITE_WOOL`。
  2. 按上面步骤建一个 30 格左右的房间，`/bridge` 菜单应出现该房间，显示「空闲，可进入」和最快纪录。
  3. `/bridge join <房间>`：背包被清空，第 1 格白色羊毛；每搭一块，数量自动回到 64；
     第 6 格金镐、第 7 格比较器（房主专属）；动作栏显示「用时 / 距终点 / 最佳」。
  4. 右键第 7 格比较器：房间变为「关闭加入」，另一个号再点菜单应被拒绝；再切回开放。
     右键第 6 格金镐：另一个号尝试拆你的方块，应能拆掉；关掉后应提示只能拆自己的。
  5. 跳到虚空：显示本次成绩（时间/水平位移/平均速度/方块数），延迟后回到**出生点**，方块被清掉。
  6. 搭到终点：显示成绩和「房间最快」；`/bridge top <房间>` 出现你的成绩，3 秒后回出生点。
  7. 点第 9 格离开：背包恢复，并回到**主城出生点**（装了 HubCore 时）。
