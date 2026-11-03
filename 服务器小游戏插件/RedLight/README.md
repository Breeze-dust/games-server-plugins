# RedLight（一二三木头人）—— 速查卡

- **一句话职责**：玩家从起跑点向终点线移动；木头人每隔一段随机时间回头，回头前 title 倒数（默认 2~5 秒随机），回头期间**位置发生变化的玩家淘汰转旁观**；前 3 名到达终点线的人获胜。已接入街机派对。
- **核心规则**：
  - 开局随机分配到**起跑出生点**，清空背包、冒险模式；对局中玩家之间关闭碰撞（计分板队伍 `redlight_nocollide` 的 `COLLISION_RULE=NEVER`），不会互相推挤导致“被移动”。
  - **移动手感**：默认禁止疾跑（`movement.disable-sprint`）——对局把玩家饥饿值压在 **6**（原版客户端允许疾跑的下限），客户端自己按不出疾跑，同时阻止疾跑跳；`PlayerToggleSprintEvent` 作为第二道保险。默认开启移动惯性（`movement.inertia`）——松开按键时施加**一次**推力（上一 tick 速度 × `movement.strength`，默认 1.8），之后交给客户端原生摩擦自然滑行；低于 `movement.min-speed`（默认 0.02）停止。滑行阶段也会被检查判定为“移动”，所以必须在木头人回头前提前松手。
  - 三个循环阶段：
    1. **可以移动**（`check.interval-min-seconds` ~ `check.interval-max-seconds`，默认 5~12 秒随机）：玩家自由向终点线跑。
    2. **木头人回头倒数**（`check.countdown-min-seconds` ~ `check.countdown-max-seconds`，默认 2~5 秒随机）：每次回头随机一个整数秒数，每秒 title 报数，**倒数期间仍可移动**。
    3. **不能动**（`check.duration-min-seconds` ~ `check.duration-max-seconds`，默认 3~8 秒随机）：每 tick 对比玩家当前位置与回头瞬间的快照；坐标变化超过 `check.move-threshold`（默认 0.02 格）即淘汰。**只算坐标，转头不算移动**。
  - 到达终点线：按「出生点中心 → 终点线」自动判断沿 X 还是 Z、正方向还是负方向；总坐标越过终点线即算到达，按先后顺序记录第 1、2、3 名。
  - 第 `finish-slots`（默认 3）名到达后立即结束；时间到（`game-seconds`，默认 180 秒）只给已经到达的玩家名次；掉到起点地面以下 `fall-limit`（默认 10）格算掉出场外淘汰。
  - 结算只把**到达终点线的玩家**按名次上报给街机派对，被淘汰的玩家不会获得星星。
  - 管理员可以 1 人 `/redlight start` 测试；单人局不记成绩、不向街机派对上报名次。
- **命令**（别名 `/rl`、`/woodenman`）：
  - 玩家：`/redlight join`、`leave`、`list`、`help`
  - 管理（OP 或 `redlight.admin`）：
    - `/redlight pos1` / `pos2`：站到场地两个对角执行（只取 XZ；pos1 的脚部 Y 记为地面）
    - `/redlight setspawn` / `addspawn` / `clearspawns`：起跑出生点
    - `/redlight setfinish`：站在终点线上执行（方向自动判断）
    - `/redlight setlobby`：等待点 / 淘汰旁观点
    - `/redlight setground [Y]`：地面高度（不写 Y = 当前位置）
    - `/redlight setcountdown <最小秒> [最大秒]`：木头人回头前 title 倒数范围（默认 2~5，每次随机）
    - `/redlight save`、`start`、`stop`、`reload`、`debug`
- **配置**：`server/plugins/RedLight/config.yml`
  - 人数：`min-players`、`max-players`
  - 对局：`game-seconds`（180）、`end-delay-seconds`（3）、`finish-slots`（3）、`fall-limit`（10）
  - 检查：`check.countdown-min/max-seconds`（2/5）、`interval-min/max-seconds`（5/12）、`duration-min/max-seconds`（3/8）、`move-threshold`（0.02）
  - 移动手感：`movement.inertia`（true）、`strength`（1.8）、`min-speed`（0.02）、`disable-sprint`（true）
  - 场地：`arena.pos1 / pos2 / lobby / spawns / finish / ground-y`
  - 提示文字：`messages.*`（`{time}` 会替换成倒数数字）
  - 等待室道具：`wait-items.return` / `wait-items.start`
  - 计分板：`sidebar.enabled` / `sidebar.title`
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 阶段/检查/终点/胜负 | `RedLight/src/main/java/com/redlight/RedLightManager.java` |
  | 场地/出生点/终点线/地面 | `RedLight/src/main/java/com/redlight/RedLightArena.java` |
  | 命令/权限/tab | `RedLight/src/main/java/com/redlight/RedLightCommand.java` |
  | 事件（伤害/破坏/死亡/退出） | `RedLight/src/main/java/com/redlight/RedLightListener.java` |
  | 配置模板 | `RedLight/src/main/resources/config.yml` |
- **建场步骤**：
  1. 搭一条从起跑线通向终点线的通道（两侧可以加围栏，避免绕路）。
  2. 站到场地两个对角：`/redlight pos1`、`/redlight pos2`。
  3. 站到起跑位置：`/redlight addspawn`（每个玩家一个，可多个）。
  4. 站到终点线上：`/redlight setfinish`。
  5. `/redlight setlobby`；可选 `/redlight setground <Y>`、`/redlight setcountdown 2 5`。
  6. `/redlight save`，`/redlight list` 确认「可开局」。
  7. 至少 2 人 `/redlight join` → `/redlight start`（或右键等待室里的开始纸）。
- **注意**：
  - 终点线是**一整条垂直于移动方向的平面**：只要玩家沿移动方向的坐标越过 `/redlight setfinish` 的位置就算到达；请让终点线横跨整条通道。
  - 「移动」只比较坐标：原地转头、打开聊天、切换物品栏都不会被淘汰；跳跃、潜行时位置变化、被推挤都会。
  - 对局中玩家之间的碰撞已通过计分板队伍关闭；仍然建议场地内不要有会推人的实体（矿车、船等）。
  - 局内所有伤害被取消；掉出场外由 `fall-limit` 检测淘汰，不会真的摔死。
  - 惯性只作用于水平方向（Y 轴保持原版跳跃/下落），蹲下时不滑行。觉得滑得太远把 `movement.strength` 调小（如 1.0），想更明显就调大（2.0~2.5）；不想用惯性把 `movement.inertia` 设为 false。
  - 禁疾跑会把对局内饥饿值固定在 6（饥饿条显示 3 格），不会饿死，也不会影响移动速度；这是客户端层面的硬限制，比只取消 `PlayerToggleSprintEvent` 可靠。
  - 因为滑行也会被判定为移动，玩家需要在倒数结束前提前松手；如果觉得太难，把 `movement.strength` 调小或把 `check.move-threshold` 调大。
  - 已接入 ArcadeParty：`games.redlight`（`redlight join` / `redlight start`）。
- **玩家记录**：命名空间 `redlight`，`games.redlight.firsts/seconds/thirds/points.<uuid>`，文件 `server/plugins/MiniGameLib/players.yml`。
