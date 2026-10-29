# LandClaim（battlefield 1 (占地1)）—— 速查卡

> 游戏内显示名（tab/播报）为 **battlefield 1 (占地1)**；命令仍是 `/land`。

- **一句话职责**：最多 16 人，每人一种羊毛颜色；开局发一把"划地锄"，右键把准星指向的地面染成自己的颜色，之后只能在自己的羊毛附近扩展；圆形场地铺满或倒计时结束按占地格数排名。
- **等待室道具**：所有等待玩家第 9 格给"退出房间"（默认屏障）；有 `landclaim.admin` 的玩家第 5 格额外给"开始游戏"（默认纸）。材质/名字/描述在 `config.yml` 的 `wait-items` 段可改，改完 `/land reload`。
- **界面**：物品栏上方动作栏会显示等待倒计时，以及游戏内“剩余时间 | 你占地 | 全场剩余”；每次成功占一格也会即时 +1 提示。占地格数的公屏播报已去掉，只保留开局、30/10 秒、时间到和结算的公屏提示。
- **核心规则**：
  - 第一次右键：场地内任意一格地面都能占。
  - 之后：只能占自己已有羊毛**前后左右相邻**的一格（曼哈顿距离 1，不含斜角；`claim-radius` 可调，默认 1）。
  - 右键/锄头作用的方块是**准星指向的方块**，不是脚下。
  - 已经有颜色（任何玩家）的格子不能覆盖。
  - 场地为圆形（按方块中心落在半径 +0.5 的圆内生成，边缘更圆滑），开局/结束时自动铺回草方块并清空上方。
- **命令**：
  - 玩家：`/land join`、`/land leave`、`/land list`、`/land stats [玩家]`、`/land help`
  - 管理（OP 或 `landclaim.admin`）：`/land setcenter`（站在地面中心）、`/land setradius <1-128>`、`/land generate`（铺草方块）、`/land genspawns <1-16>`（圆周自动出生点）、`/land addspawn`、`/land clearspawns`、`/land setlobby`、`/land save`、`/land start`、`/land stop`、`/land reload`
- **配置**：`server/plugins/LandClaim/config.yml`
  - `min-players`（默认 2）、`max-players`（默认/上限 16）、`game-seconds`（默认 300，0 = 不限时）、`claim-radius`（默认 1，曼哈顿距离）、`floor-block`（默认 GRASS_BLOCK）、`clear-height`（默认 3）
  - `arena.center`、`arena.radius`、`arena.lobby`、`arena.spawns`（用命令设置后 `/land save` 写入）
- **玩家记录**：命名空间 `landclaim`，`games.landclaim.firsts.<uuid>`（冠军次数），唯一文件 `server/plugins/MiniGameLib/players.yml`。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 占地判定/计分/结算/倒计时 | `LandClaim/src/main/java/com/landclaim/LandClaimManager.java` |
  | 圆形范围、场地生成、出生点 | `LandClaim/src/main/java/com/landclaim/LandClaimArena.java` |
  | 锄头交互/防破坏/防丢 | `LandClaim/src/main/java/com/landclaim/LandClaimListener.java` |
  | 命令/权限/tab | `LandClaim/src/main/java/com/landclaim/LandClaimCommand.java` |
  | 显示名/图标/颜色 | `LandClaim/src/main/resources/plugin.yml` |
- **注意**：
  - 场地每局开始和结束都会 `resetFloor()`：半径圆内铺 `floor-block`，上方 `clear-height` 格清空。
  - 玩家在游戏内为冒险模式、免伤免饥饿；锄头不可丢弃/移动。
  - 场地格数 = 半径圆内方块数（半径 20 ≈ 1257 格）。
- **验证方法**：
  1. `/land setcenter`（站地面中心）→ `/land setradius 12` → `/land generate` → `/land genspawns 8` → `/land setlobby` → `/land save`。
  2. `/land list` 显示半径/格数/出生点；`/hub games` 出现"划地为王"。
  3. 两名玩家 `/land join` → `/land start`；第一次右键任意地面生成自己的羊毛；之后站在自己的羊毛 3 格内右键继续占；踩到别人的羊毛/远处会提示不能占。
  4. 场地铺满或 `game-seconds` 到点 → 公布冠亚季军，冠军写入 `players.yml`，场地恢复草方块。
