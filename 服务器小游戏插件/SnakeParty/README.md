# SnakeParty（蛇行派对）—— 速查卡

> AI 先读这一段，别先读源码

- **一句话职责**：方形场地里所有人**沿视线方向自动前进**，走过的地方会留下**自己颜色的玻璃板**；撞到任何玻璃板（自己的也算）或掉出场地就淘汰，**最后存活者获胜**；玻璃板会定时消失，而且**越到后面消失得越慢**。单场地，命令 `/snake`（别名 `/snakeparty`），显示名 `蛇行派对`。
- **规则**：
  - 开局至少 `min-players`（默认 2），最多 `max-players`（16）；等待倒计时用公共库 `WaitCountdown`，OP 在队列时暂停自动开局（右键开始纸或 `/snake start`）。
  - **开局分两段**：`/snake start` 后玩家被传送到出生点，进入 **倒计时**（`countdown-seconds` 默认 3）：期间**完全不能移动**（只能转头），也不会生成玻璃板；屏幕中央每秒播 `游戏即将开始：3 / 2 / 1`。倒计时结束才正式开始计时。**街机派对开局时跳过这段倒计时**（休息阶段已经计过时，直接开始）。
  - **自动前进**：每 tick 按**水平朝向（yaw）**给速度（`movement.speed`，默认 0.28），所以低头看脚也照样向前走；玩家用鼠标转向；**不能跳跃**（纵向位移会被按回原地）。
  - **疾跑与体力**：原版疾跑被禁用；**经验条就是体力条**（`stamina.max`，默认 100）。按住 W 且有体力时按 `movement.boost-multiplier`（默认 1.7 倍）加速并每秒消耗 `stamina.drain-per-second`（默认 35）；**体力为 0 但仍按住 W** 时按 `movement.empty-w-multiplier`（默认 1.15 倍）走，比完全松开 W 的人稍快；松开 W 才恢复 `stamina.regen-per-second`（默认 18）。
  - **玻璃板**：玩家离开一格时，在**身后那一格的脚部高度**放一块**自己颜色的染色玻璃板**（`*_STAINED_GLASS_PANE`，`plate.colors` 顺序分配），相邻板子会自动连成一道墙。**板子不是立刻放**：等玩家离那一格 ≥ `plate.place-distance`（默认 1.2 格）才真正生成，所以快速左右转头也不会正好撞到自己刚铺的板；**斜着走时会补一个拐角格**，整条轨迹首尾相接、不会断节。按 `plate.lifetime-seconds`（默认 3 秒）消失，并**每过 1 秒存活时间增加 `plate.lifetime-growth-ms-per-second`（默认 150ms）**，封顶 `plate.max-lifetime-seconds`（默认 45 秒）。越到后面板子存在越久、越难躲。
  - **淘汰**：走进已经有玻璃板的格子（无论谁的）→ 淘汰；**撞到方块（墙/障碍）→ 淘汰**；走出场地边界或掉到平台下方 6 格 → 淘汰。被淘汰的人留在场上的玻璃板不会消失，继续挡人。
  - **胜负**：场上剩 1 人 → 他获胜；时间到（`game-seconds`，默认 120 秒）仍有多人存活 → **共同获胜**；全员淘汰 → 无人获胜。
  - **前三名**：获胜者并列第一；被淘汰的按淘汰顺序倒序补第二、第三。前三名分别 **+3/+2/+1 积分**，同时累计 `firsts/seconds/thirds`；写入 `players.yml` 的 `games.snakeparty.points / firsts / seconds / thirds`。
  - 场内玩家无伤（PvP/摔落/火焰等全部取消，不会死），不会掉物品，不能破坏/放置方块。
- **命令**：`/snake`（别名 `/snakeparty`）
  - 玩家：`join`、`leave`、`list`、`stats [玩家]`、`help`
  - OP（`isOp()` 或权限 `snake.admin`）：**`setarena <半径>`**（站中心执行：写入范围 + 自动铺方形平台 + 清旧平台残留）、**`setarea <半径>`**（只划范围不动方块，自己搭平台时用）、`setlobby`、`addspawn`、`clearspawns`、**`genspawns [数量]`**（沿场地内圈生成出生点，默认最多人数，朝向切线）、`save`、`start`、`stop`、`reload`、`stats reset`
- **配置**：`server/plugins/SnakeParty/config.yml`（源码模板 `SnakeParty/src/main/resources/config.yml`）
  - `min-players` / `max-players` / `game-seconds` / `countdown-seconds`
  - `movement.speed`：每 tick 前进速度；`movement.boost-multiplier`：按住 W 有体力时的加速倍率；`movement.empty-w-multiplier`：体力为 0 仍按 W 时的倍率
  - `stamina.max` / `stamina.drain-per-second` / `stamina.regen-per-second`：体力上限、消耗、恢复
  - `plate.lifetime-seconds` / `plate.lifetime-growth-ms-per-second` / `plate.max-lifetime-seconds` / `plate.colors` / `plate.place-distance`（离开多少格后才铺板，默认 1.2；调大更不容易撞到自己的板，但轨迹出现更晚）
  - `arena.center` / `arena.radius` / `arena.floor-material`（默认 `STONE`）/ `arena.floor-layers`（默认 2，两层同材质）/ `arena.lobby` / `arena.spawns`（用命令写，别手写坐标）
- **建场步骤（OP，第一次上线）**：
  1. 站到场地中心执行 `/snake setarena 16`（半径 4~64，场地是边长 `2*半径+1` 的方形）：自动铺**两层石头**平台（`arena.floor-material` / `arena.floor-layers` 可调）并清掉上方 9 格。
     - 想自己搭平台：先 `/snake setarea 16` 只划范围，再手动铺方块/围墙。
  2. `/snake genspawns 16` 沿内圈生成出生点（朝向切线方向，开局大家并行绕圈）。
  3. 场地外 `/snake setlobby`。
  4. `/snake save`；`/snake list` 应显示「已配置」。
  5. `/snake start`（或右键开始纸）测试；`/hub games` 应出现 **蛇行派对**。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 前进/玻璃板/淘汰/胜负/战绩 | `SnakeParty/src/main/java/com/snakeparty/SnakePartyManager.java` |
  | 方形范围/铺平台/出生点 | `SnakeParty/src/main/java/com/snakeparty/SnakePartyArena.java` |
  | 倒计时锁定/禁止跳跃/保护/道具 | `SnakeParty/src/main/java/com/snakeparty/SnakePartyListener.java` |
  | 命令/权限/tab | `SnakeParty/src/main/java/com/snakeparty/SnakePartyCommand.java` |
  | 默认配置 | `SnakeParty/src/main/resources/config.yml` |
  | 显示名/图标/颜色 | `SnakeParty/src/main/resources/plugin.yml` 的 `minigame:` 段 |
- **常见坑**：
  - **玻璃板是脚部高度的矮墙**：等玩家离那一格 ≥ `plate.place-distance` 才放，不会卡住自己；走进别人/自己的板子会被判负（物理碰撞也会被拦下）。斜走会自动补拐角格，轨迹连续。
  - **`setarena` 会改世界**：铺方块 + 清上方 9 格，重建时还会清旧平台残留；别在已有建筑里执行。
  - **出生点朝向**：`genspawns` 生成的点朝切线方向；用 `addspawn` 手加的点会保留你当时的朝向，注意别让所有人朝中间冲。
  - **速度手感**：`movement.speed` 0.28 偏快；场地小可降到 0.22，场地大可以加。
  - **菜单入口**：HubCore 主菜单不会自动加，需要在 HubCore 的 `games` 段加一条 `name: 蛇行派对` / `icon: GLASS_PANE` / `color: 'a'` / `command: snake join` / `join-prefix: snake join`，再 `/hub menu set <槽位> GLASS_PANE game:蛇行派对 &a&l蛇行派对`。
- **验证方法**：
  1. `/snake list` → 场地已配置、半径/出生点正确；`/hub games` 有 **蛇行派对**。
  2. 2 人 `/snake join` → OP 右键开始纸：玩家被传送、**完全不能走动**（可以转头），屏幕依次出现「游戏即将开始：3/2/1」，脚下没有玻璃板，经验条是满的体力。
  3. 倒计时结束：玩家开始沿视线自动前进，脚下留下自己颜色的玻璃板；按住 W 会加速且经验条下降，松开 W 慢慢回满；玻璃板约 3 秒后恢复成原地板。
  4. 故意拐回去踩自己的板 → 立刻淘汰并广播；撞到墙/障碍方块或掉出场地也会立刻淘汰。**快速左右转头**不应撞到自己刚铺的板（板子等你离开 1.2 格才生成）；斜着走时轨迹应是一整条相连接的玻璃板，没有对角断节。
  5. 时间过半再观察：新铺的玻璃板停留明显更久（增长配置生效）。
  6. 剩 1 人 → 立刻结束并广播前三名；`/snake stats <玩家>` 能查到积分与名次。
- **未确认 / TODO**：
  - 自动前进的手感（速度/是否会被客户端自己的 WASD 干扰）需要进服实测；必要时改成纯速度驱动并屏蔽玩家自行移动。
  - 本地没有真实玩家，只做了启动冒烟，玩法需进服验证。
