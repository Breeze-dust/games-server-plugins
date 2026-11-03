# RedLight（一二三木头人）—— 速查卡

- **一句话职责**：玩家从起跑点向终点线移动；木头人每隔一段随机时间回头，回头前 title 倒数（默认 3 秒），回头期间**位置发生变化的玩家淘汰转旁观**；前 3 名到达终点线的人获胜。已接入街机派对。
- **核心规则**：
  - 开局随机分配到**起跑出生点**，清空背包、冒险模式；对局中玩家之间关闭碰撞（计分板队伍 `redlight_nocollide` 的 `COLLISION_RULE=NEVER`），不会互相推挤导致“被移动”。
  - 三个循环阶段：
    1. **可以移动**（`check.interval-min-seconds` ~ `check.interval-max-seconds`，默认 5~12 秒随机）：玩家自由向终点线跑。
    2. **木头人回头倒数**（`check.countdown-seconds`，默认 3）：每秒 title 报数，**倒数期间仍可移动**。
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
    - `/redlight setcountdown <秒>`：木头人回头前 title 倒数（默认 3）
    - `/redlight save`、`start`、`stop`、`reload`、`debug`
- **配置**：`server/plugins/RedLight/config.yml`
  - 人数：`min-players`、`max-players`
  - 对局：`game-seconds`（180）、`end-delay-seconds`（3）、`finish-slots`（3）、`fall-limit`（10）
  - 检查：`check.countdown-seconds`（3）、`interval-min/max-seconds`（5/12）、`duration-min/max-seconds`（3/8）、`move-threshold`（0.02）
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
  5. `/redlight setlobby`；可选 `/redlight setground <Y>`、`/redlight setcountdown 3`。
  6. `/redlight save`，`/redlight list` 确认「可开局」。
  7. 至少 2 人 `/redlight join` → `/redlight start`（或右键等待室里的开始纸）。
- **注意**：
  - 终点线是**一整条垂直于移动方向的平面**：只要玩家沿移动方向的坐标越过 `/redlight setfinish` 的位置就算到达；请让终点线横跨整条通道。
  - 「移动」只比较坐标：原地转头、打开聊天、切换物品栏都不会被淘汰；跳跃、潜行时位置变化、被推挤都会。
  - 对局中玩家之间的碰撞已通过计分板队伍关闭；仍然建议场地内不要有会推人的实体（矿车、船等）。
  - 局内所有伤害被取消；掉出场外由 `fall-limit` 检测淘汰，不会真的摔死。
  - 已接入 ArcadeParty：`games.redlight`（`redlight join` / `redlight start`）。
- **玩家记录**：命名空间 `redlight`，`games.redlight.firsts/seconds/thirds/points.<uuid>`，文件 `server/plugins/MiniGameLib/players.yml`。
