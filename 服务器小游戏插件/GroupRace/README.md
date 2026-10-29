# GroupRace（群友竞速赛）

> 速查卡（AI 先读这里，别先读源码）

- **一句话职责**：骑**飞猪**沿赛道穿过竖着的方块圆环。飞猪**自动前进**，用视角（水平朝向）控制方向，**空格上升、下蹲下降**；穿过普通圆环 **+1 分**（每个圆环每人只算一次，漏掉不补但可以继续飞后面的），穿过**终点圆环**结束自己的比赛；时间到按分数排名，同分时**先到终点的赢**，都没到终点则比最后穿过的圆环序号，仍相同再并列。**超过一半玩家到达终点**时，把剩余时间压缩到 `finish-short-seconds`（默认 20 秒）。飞出 `pos1/pos2` 的水平范围会被传回**最后穿过的圆环中心**，并面朝下一个更靠近终点的圆环。结束按名次播报**冠军 / 亚军 / 季军**。等待室第 9 格是返回大厅床，管理员第 5 格是开始游戏纸；准备阶段可以骑普通猪（手动、不飞）。
- **入口 / 主流程**：`GroupRace/src/main/java/com/grouprace/GroupRacePlugin.java#onEnable` → `GroupRaceManager#load`（读 `arena` + `rings`，重建方块圆环）→ `/race join` 排队（`GroupRaceManager#join`）→ 公共库 `WaitCountdown` 自动倒计时 → `GroupRaceManager#startGame` 清背包、在起跑点生成飞猪并骑上、`HubAPI.setPlaying` → 每 tick `tickFlight`（飞猪速度/惯性/升降 + 穿环判定 + 出界救援）→ 每秒 `tickGame`（倒计时/动作栏）→ 穿终点 `finishPlayer`（变旁观、检查半程缩短）→ 时间到/全员到达 `endGame`（`computeWinners` 排名）。
- **关键文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 开局/飞猪/穿环/终点/出界/胜负 | `GroupRace/src/main/java/com/grouprace/GroupRaceManager.java` |
  | 右键/输入/下蹲防下猪/伤害与方块保护 | `GroupRace/src/main/java/com/grouprace/GroupRaceListener.java` |
  | 圆环方块生成、清除、配置序列化 | `GroupRace/src/main/java/com/grouprace/GroupRaceRing.java` |
  | 等待点/起跑点/出界方框/圆环列表 | `GroupRace/src/main/java/com/grouprace/GroupRaceArena.java` |
  | `/race` 命令 | `GroupRace/src/main/java/com/grouprace/GroupRaceCommand.java` |
  | 默认配置模板 | `GroupRace/src/main/resources/config.yml` |
  | 线上配置 | `server/plugins/GroupRace/config.yml` |
- **命令**（主命令 `/race`，别名 `/gr`）：
  - 玩家：`/race join`、`/race leave`、`/race list`、`/race help`。
  - 管理员（OP 或 `race.admin`）：`/race start`、`/race stop`、`/race setlobby`、`/race setstart`、`/race pos1`、`/race pos2`、`/race addring`、`/race addfinish`、`/race clearrings`、`/race listrings`、`/race save`、`/race reload`。
- **建场步骤**：
  1. 站到等待位置：`/race setlobby`。
  2. 站到起跑位置并**面朝赛道第一环**：`/race setstart`（面朝方向会用来决定起跑朝向，玩家开局会自动面朝第一环）。
  3. 站到出界范围的一角：`/race pos1`；站到对角：`/race pos2`（只取 X/Z，Y 自动忽略）。
  4. 站到第一个圆环位置（地面即可，圆心会自动抬高）、面朝赛道前进方向：`/race addring`；沿赛道重复 `addring`（后续环自动沿用第一环的单轴朝向）。
  5. 站到终点圆环位置、面朝终点方向：`/race addfinish`（必须是最后一环；加完后不能再 `addring`）。
  6. `/race listrings` 检查；`/race save`（`setlobby/setstart/pos1/pos2/addring/addfinish/clearrings` 都会自动保存）。
  7. 可选主城菜单入口（选空槽，例如 37）：
     ```
     /hub menu set 37 PIG_SPAWN_EGG cmd:race join &e&l群友竞速赛
     /hub menu lore 37 &7骑飞猪穿过圆环|&7分数最高者获胜
     ```
- **配置**（`server/plugins/GroupRace/config.yml`）：
  - `min-players`（2）、`max-players`（16）、`game-seconds`（300）、`finish-short-seconds`（20）。
  - `pig.speed`：自动前进速度（格/tick）；`pig.inertia`：惯性（0~0.99，越大越滑）；`pig.ascend-speed` / `descend-speed`：按空格/下蹲的升降速度；`pig.start-spread`：多人起跑横向散开间距。
  - `ring.radius`：圆环半径；`ring.material` / `ring.finish-material`：普通/终点圆环方块材质。
  - `lobby-pig.enabled` / `lobby-pig.speed`：准备阶段可骑猪、手动移动速度（有重力，不会飞）。
  - `invisible-glowing`：参赛玩家隐身但**不发光**；飞猪保持可见并带发光轮廓（默认 true；false = 玩家和猪都恢复正常可见）。
  - `wait-items.return`（床，槽位 8）、`wait-items.start`（开始纸，槽位 4）。
  - `arena.lobby` / `arena.start` / `arena.pos1` / `arena.pos2`：用命令写（`pos1/pos2` 只取 X/Z）；`rings:` 由 `addring/addfinish` 自动写入。
- **常见坑**：
  - **圆环是真实方块**：`GroupRaceRing#blockLocations` 在圆环平面内按整数格扫描，把距离落在 `[radius-0.6, radius+0.6]` 的格子全部填充，生成完整的一格厚圆。`addRing` 的 `ringCenter` 会在最低点埋进方块或卡在玩家身体里时自动把圆心抬高，所以站在地面生成也是完整圆；空中生成且最低点本来就是空气时不会抬高。所有环的 `normal` 会吸附到 X 或 Z 单轴，且后续环直接沿用第一环的轴，保证全部是竖直、同方向的平面。`clearrings` 会把圆周设回 AIR；改 `ring.radius` 只影响新生成的圆环。
  - **准备阶段骑猪**：`join` 在等待点生成一只普通猪并骑上（`lobby-pig.enabled`），`tickLobbyPigs` 只按 `PlayerInputEvent` 的 WASD 和跳跃更新 X/Z/Y 速度，`setGravity(true)`，**不自动前进、不会飞**；玩家可以主动下猪/重新右键骑上。开局 `removeAllLobbyPigs` 清掉等待猪，再生成赛道飞猪。等待猪用 `AI=true + aware=false`，不能 `setAI(false)`（NoAI 会冻结物理）。
  - **参赛玩家/飞猪外观**：`invisible-glowing: true` 时，`preparePlayer` 只给玩家 `INVISIBILITY`（无限时长），**不再 `setGlowing`**；`spawnPig` 只给飞猪 `setGlowing(true)`，猪可见并带发光轮廓。完成比赛/离开/结算时 `finishPlayer` / `restorePlayer` 会清掉玩家隐身，猪直接移除。把它设为 false 即可让玩家和猪都恢复正常外观。
  - **名次**：`computeRanking` 先按分数降序；同分时有到达终点的优先、再按终点顺序；都没到终点按最后圆环序号；仍相同按名字排序。`endGame` 广播前三名「冠军 / 亚军 / 季军」，前三名的 title 分别显示冠军/亚军/季军，其余玩家 title 副标题显示「再接再厉」。
  - **终点必须最后**：`addfinish` 之后不能再 `addring`；要重排就 `/race clearrings` 全部重来。
  - **飞猪是服务端速度驱动**：`tickFlight` 每 tick `pig.setVelocity(当前*惯性 + 期望*(1-惯性))`。猪必须保持 `AI=true` + `setAware(false)`：这个 Paper 构建里 `setAI(false)` 的 NoAI 实体完全不跑物理，`setVelocity` 不会前进（真实踩坑）。玩家视角的水平朝向决定前进方向，空格/下蹲只改垂直速度。下蹲会触发原版下猪，`EntityDismountEvent` 会被取消；每 tick 还会兜底重新 `addPassenger`。
  - **跳跃/下蹲输入**来自 Paper `PlayerInputEvent`（`Input.isJump/isSneak`），同时用 `PlayerToggleSneakEvent` 兜底下蹲；如果客户端不发输入事件，升降会失效，先看这里。
  - **出界范围只算 X/Z**：`arena.inside` 只比较水平坐标，Y 不限制；圆环和起跑点都要在水平范围内，否则会立刻被传回。传回目标是 `lastRingIndex` 对应的圆环中心，面朝 `lastRingIndex+1`；还没穿过任何圆环时传回起跑点、面朝第一环。
  - **计分是独立的**：每个普通圆环每人只计一次，漏掉不补，但后面的圆环仍可得分；`lastRingIndex` 取已穿过的最大序号，用于出界救援和同分比较。
  - **同分规则**：最高分有人到终点 → 这些人里最早到终点的赢；最高分都没到终点 → 比较 `lastRingIndex`，再相同就并列。
  - **超过一半到达终点**：`finished.size() * 2 > playing.size()` 时把 `secondsLeft` 压到 `finish-short-seconds`（只触发一次）；全员到达会直接结算。
  - 比赛中不能改赛道/拆方块/受伤；下蹲只下降，不会把玩家从猪上弄下来。
- **验证方法**：
  1. `/race list` / `/race listrings` 看赛道是否完整；`/race join` 会被传到等待点，第 9 格是返回大厅床，管理员第 5 格是开始纸。
  2. 等待阶段：玩家应骑上一只普通猪，W 前进、S 后退、**A 向左、D 向右**，空格跳跃，但不会自动前进也不会飞；下猪后右键猪可以重新骑上。
  3. 至少 2 人 `/race start`：每人骑一只飞猪，**自动向前飞**；转动视角改变方向，空格上升，下蹲下降；下蹲不会下猪；参赛玩家隐身但不发光，飞猪可见并带发光轮廓。
  4. 结束/时间到：公屏应播报**冠军 / 亚军 / 季军**（按分数、终点顺序、最后圆环序号排名）；前三名 title 显示对应名次，其余玩家显示「再接再厉」。
  3. 穿过普通圆环：动作栏 +1、圆环位置冒绿色粒子；同一圆环第二次穿过不再加分；漏掉一个后继续穿后面的圆环仍能得分。
  4. 穿过终点圆环：变旁观、全服播报到达终点与分数；超过一半人到达后剩余时间变成 20 秒；全员到达直接结算。
  5. 飞出行程方框：被传回最后穿过的圆环中心，并面朝下一个圆环；没穿过任何圆环则回起跑点。
  6. 时间到：按分数排名；同分先到终点者赢；都没到终点比最后圆环序号；仍并列则多人共同获胜。
