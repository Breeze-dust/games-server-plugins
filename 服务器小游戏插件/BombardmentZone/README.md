# BombardmentZone（轰炸区）—— 速查卡

- **一句话职责**：海盗船上的炮口向不规则小岛随机发射炮弹（黑色羊毛方块指示），玩家只有**一颗心**、饱食度无限，被炮弹爆炸命中或掉进水里就淘汰转旁观；开局一段时间后进入「饱和轰炸」阶段（多炮口同时开火、发射更快），最后存活者第一名，结算记录前三名并给星星。已接入街机派对（key `bombard`）。
- **核心规则**：
  - 场地：管理员手建小岛（不需要生成场地的指令），`pos1` / `pos2` 划出小岛的矩形范围并记录地面 Y；服务器**只扫描这个范围内配置的落点方块**，所以形状不规则的小岛只会被落在真实岛面上。
  - 炮口：站到海盗船炮口位置执行 `/bombard addcannon`（记录**眼睛位置**，可多个）。炮弹从随机炮口沿**直线**飞向随机落点，撞到实体方块或到达落点即爆炸。
  - 爆炸：纯视觉（`EXPLOSION_EMITTER` 粒子 + 爆炸音效），**不破坏地形**；半径 `shell.explosion-radius`（默认 3.5 格）内的玩家直接淘汰。
  - 玩家：最大生命值临时设为 2（一颗心），饱食度/饱和度锁定满且取消饥饿；局内所有原版伤害取消，只有炮弹爆炸和落水会淘汰。淘汰后转旁观并传送到小岛中心上方。
  - 阶段：`phases.late-start-seconds`（默认 90 秒）前是「零星炮火」（每 `early-interval-ticks` 随机一个炮口发一颗）；之后聊天栏 + title 播报「饱和轰炸」，每 `late-interval-ticks` 一轮、每轮 `late-cannons-per-volley` 个炮口同时发射。
  - 炮弹外观：`BlockDisplay` 显示 `shell.material`（默认 `BLACK_WOOL`）方块，带烟雾粒子。
  - 胜负：只剩 1 人立刻结束；`game-seconds` 时间到则剩余玩家并列第一，按淘汰倒序补第二、第三名。`Top3` 记 `firsts/seconds/thirds/points`，并 `ArcadeBridge.report("bombard", ...)`。
  - 管理员可以 1 人 `/bombard start` 测试；单人局不记成绩、不向街机派对上报名次。
- **命令**（别名 `/bz`、`/bombardzone`）：
  - 玩家：`/bombard join`、`leave`、`list`、`help`
  - 管理（OP 或 `bombard.admin`）：
    - `/bombard pos1` / `pos2`：站在小岛两个对角执行（只取 XZ；pos1 的脚部 Y 记为地面）
    - `/bombard setspawn` / `addspawn` / `clearspawns`：固定出生点
    - `/bombard setlobby`：等待点
    - `/bombard setground [Y]`：扫描中心高度（默认当前位置）
    - `/bombard addcannon`：把**眼睛位置**记录为一个炮口；`cannons` 查看、`clearcannons` 清空
    - `/bombard scan`：立即扫描并统计可落点数量（建岛后自检用）
    - `/bombard setlate <秒>`：开局多少秒后进入饱和轰炸（默认 90）
    - `/bombard save`、`start`、`stop`、`reload`、`debug`
- **配置**：`server/plugins/BombardmentZone/config.yml`
  - 人数：`min-players`、`max-players`
  - 对局：`game-seconds`（300）、`start-delay-seconds`（3）、`end-delay-seconds`（4）
  - 阶段：`phases.late-start-seconds`（90）、`early-interval-ticks`（40）、`late-interval-ticks`（10）、`late-cannons-per-volley`（2）、`max-in-flight`（30）
  - 炮弹：`shell.material`（BLACK_WOOL）、`shell.size`（0.7）、`shell.speed`（1.6 格/tick）、`shell.explosion-radius`（3.5）
  - 落点：`landing.materials`（可写一种或多种方块）、`landing.scan-above`（10）、`landing.scan-below`（12）
  - 场地：`arena.pos1 / pos2 / lobby / spawns / cannons / ground-y`
  - 等待室道具：`wait-items.return` / `wait-items.start`
  - 计分板：`sidebar.enabled` / `sidebar.title`
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 发射频率/阶段/炮弹/爆炸/胜负 | `BombardmentZone/src/main/java/com/bombardmentzone/BombardmentZoneManager.java` |
  | 场地/出生点/炮口/地面 | `BombardmentZone/src/main/java/com/bombardmentzone/BombardmentZoneArena.java` |
  | 命令/权限/tab | `BombardmentZone/src/main/java/com/bombardmentzone/BombardmentZoneCommand.java` |
  | 事件（伤害/饥饿/死亡/退出） | `BombardmentZone/src/main/java/com/bombardmentzone/BombardmentZoneListener.java` |
  | 配置模板 | `BombardmentZone/src/main/resources/config.yml` |
- **建场步骤**：
  1. 手动建好小岛（可高低不平、形状不规则），保证岛面方块在 `landing.materials` 里。
  2. 站到小岛两个对角执行 `/bombard pos1`、`/bombard pos2`。
  3. 站到每个出生位置执行 `/bombard addspawn`；`/bombard setlobby` 设置等待点。
  4. 飞到海盗船炮口位置，执行 `/bombard addcannon`（可多次）。
  5. `/bombard scan` 确认可落点数量 > 0；`/bombard list` 确认「可开局」。
  6. `/bombard save`，至少 2 人 `/bombard join` → `/bombard start`。
- **注意**：
  - 落点只会落在 `landing.materials` 列出的方块上；岛面用了别的方块要加进配置，否则炮弹可能找不到落点（开局会提示）。
  - 炮口记录的是玩家眼睛位置；如果炮口埋在方块里，炮弹会在飞出几格后自爆，建议把炮口放在船沿外侧。
  - 爆炸半径是球形距离，站在落点正上方/相邻格也可能被淘汰。
  - 局内取消所有原版伤害（包括摔落），所以只有爆炸和落水会淘汰；落水判定用 `isInWater()`，浅水也算。
  - 已接入 ArcadeParty：`games.bombard`（`bombard join` / `bombard start`），街机派对过渡时会正常拉人进场。
  - 玩家记录命名空间 `bombard`，写入 `server/plugins/MiniGameLib/players.yml`。
- **验证**：
  1. `/bombard list` 显示场地已就绪、炮口 N 个；`/bombard scan` 可落点 > 0。
  2. 2 人 `/bombard join` + `/bombard start`：头顶有黑色羊毛方块从炮口飞向小岛，落地爆炸但小岛方块不变。
  3. 站到落点附近被炸 → 淘汰转旁观；跳进水里 → 淘汰转旁观。
  4. 到 `late-start-seconds` 时聊天栏播报「第二阶段：饱和轰炸」，炮弹明显变密、多炮口同时发射。
  5. 只剩 1 人 → 结算播报前三名带 ★；街机派对里能看到 `轰炸区` 并正常发星。
