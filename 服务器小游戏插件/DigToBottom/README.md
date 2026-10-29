# DigToBottom（一掘到底）

> 速查卡（AI 先读这里，别先读源码）

- **一句话职责**：每人分到一根**完全相同的 1 格宽随机方块柱子**，头顶被玻璃围住；开局发 **无限耐久的钻石镐/斧/铲**，3 秒冻结倒计时后同时往下挖，**最先挖到最底层基岩**的人获胜；时间到还没挖到底的按**已挖深度**排名。命令 `/dig`（别名 `/dgb`、`/digdown`）。显示名 `一掘到底`（HubCore 注册名 = `DigToBottomManager.GAME`）。
- **入口 / 主流程**：`DigToBottom/src/main/java/com/digdown/DigToBottomPlugin.java#onEnable` → `DigToBottomManager#load`（读 `arenas` + `PlayerStats` 命名空间 `digdown`，40 tick 后 `registerGame` 并把已有场地上报 HubCore）→ 等待倒计时 `WaitCountdown`（`#wait`）→ `#start`（`resetRoundState` → 随机分坑位 → **所有坑位填同一条随机柱体** → 逐个传送到柱子顶部、面向圆心、发三件工具 → `#startFreeze` 冻结 3 秒）→ `#startTimer`（`game-seconds`，每秒刷动作栏 + 上报 `setArena`，过半完成时 `#checkHalf` 把剩余时间缩到 `shorten-seconds`）→ 挖方块 `DigToBottomListener#onBlockBreak` → `#canBreak` / `#onPillarBreak` → 挖到最底层 `#finish`（转旁观、广播名次）→ 全员完成或时间到 `#endGame`（前三名/平局 → 写入 `firsts/seconds/thirds` → 回大厅 → `#clearLanes`）。
- **场地怎么建（OP）**：
  1. `/dig create <场名>`；
  2. 站在**柱子最底层方块所在的位置**（这里的 Y 就是柱底）→ `/dig center`；
  3. `/dig lanes <1-32>`（默认 8）、`/dig radius <3-64>`（默认 8，必须让任意两坑中心横/纵距离 ≥ 3，否则玻璃重叠会拒绝生成）；
  4. `/dig build` —— **只生成玻璃外壳 + 每坑底部一格基岩**，坑内是空气；柱体方块开局时才随机填；
  5. 站到圆外安全位置 → `/dig lobby`（等待点，必须有）；可选 `/dig spectator`（不设默认圆心上方柱高+6）；
  6. `/dig save`。
- **命令**：`/dig`（别名 `dgb` / `digdown`）—— 玩家：`join <场名>`、`leave`、`list`、`stats [玩家]`、`help`；管理（`isOp()` 或权限节点 `dig.admin`）：`create`、`edit`、`remove`、`rename`、`center`、`lobby`、`spectator`、`lanes`、`radius`、`build`、`save`、`start [场名]`（不带场名 = 当前 `/dig edit` 的场；控制台必须带场名）、`debug`、`stats reset`。
- **配置**：`server/plugins/DigToBottom/config.yml`（源码模板 `DigToBottom/src/main/resources/config.yml`）——
  `min-players`（默认 1）、`max-players`（默认 8，实际人数上限 = min(坑位数, 这个值)）、`countdown-seconds`（默认 3）、`game-seconds`（默认 180）、`shorten-seconds`（默认 20）、`debug-break-logs`（默认 true，诊断用，平时可设 false）、`pillar.height`（默认 60）、`pillar.materials.wood/pickaxe/shovel`（三个方块池，随机时合并使用）。
- **每局规则**：
  - 柱体高度 = `pillar.height`；材料池先洗牌循环再整体洗一次，**同一局所有玩家柱子完全一样**，尽量每种方块都用上，且相邻两层不重复。
  - 开局 3 秒（可配）内**不能移动、不能挖方块**，但工具已发放；倒计时结束才正式计时。
  - 只能挖**自己坑位**里的柱体方块；玻璃（任何名字带 `GLASS` 的方块）与基岩**永远禁止破坏**；挖方块**不掉落物品/经验**；场地内取消方块物理更新，**沙子/沙砾不会塌下来**。
  - 挖掉最底层那一格 = 到达底部，立刻转旁观并广播「第 N 个完成」；旁观者可以飞，输入 `/dig leave` 退出。
  - **过半玩家完成**（向上取整）时，剩余时间缩短到 `shorten-seconds`（只在原剩余时间更长时生效）。
  - 结束条件：所有还活着的玩家都完成，或计时归零。时间到时：已完成者按用时排前，未完成者按**已挖格数从多到少**排；同用时/同深度并列同一名次（例如两人并列第一，下一位是第三名）。
  - 前三名（含并列）分别累计 `firsts` / `seconds` / `thirds` 到 `server/plugins/MiniGameLib/players.yml` 的 `games.digdown`。
- **常改文件**：  | 需求 | 文件 |
  | --- | --- |
  | 开局/计时/缩时/排名/战绩/建场与柱体生成 | `DigToBottom/src/main/java/com/digdown/DigToBottomManager.java` |
  | 场地字段（圆心/等待点/观战点/坑位数/半径/运行时数据） | `DigToBottom/src/main/java/com/digdown/DigToBottomArena.java` |
  | 方块破坏保护、冻结、伤害/饥饿/丢弃、右键道具 | `DigToBottom/src/main/java/com/digdown/DigToBottomListener.java` |
  | `/dig` 命令、帮助与补全 | `DigToBottom/src/main/java/com/digdown/DigToBottomCommand.java` |
  | 显示名 / 图标 / 颜色 | `DigToBottom/src/main/resources/plugin.yml` 的 `minigame:` 段 |
  | 默认配置（柱高、材料池、时长） | `DigToBottom/src/main/resources/config.yml` |
- **常见坑**：
  - **非 OP 玩家挖不动任何方块（方解石 / 诡异菌柄等）、OP 能挖**：按顺序查两处：
    1. **GTB 全局兜底（任何世界，最常见）**：建筑猜猜乐在 NORMAL 优先级对所有**非 OP 且不在 GTB 局内**的玩家无条件取消方块破坏，**不分世界**。1.0.2 起 DigToBottom 在 HIGHEST 上对「自己柱子内 + 对局中」显式 `setCancelled(false)` 放行；**场地外的方块仍会被 GTB 拦**（这是 GTB 的全局保护，不是本插件）。
    2. **原版出生点保护**：`server.properties` 的 `spawn-protection`（默认 16），只作用于 **respawn 世界（通常是主世界）** 的出生点半径内，OP 免疫，且在 `BlockBreakEvent` **之前**拦截；**GameWorlds 新建的自定义世界不受它影响**。
    `/dig debug` 会打印 spawn-protection、respawn-world、目标方块距出生点距离，并提示非 OP 还会撞上 GTB 那层。
  - **改了 `pillar.height` 必须重新 `/dig build`**：玻璃外壳高度是按建场时的柱高生成的；只改配置不重建，顶部会比柱子矮一截，玩家可能跳出/看穿。
  - **改 `lanes` / `radius` 也要重新 `/dig build`**：旧坑位的玻璃不会自动消失，需要手动清理或换一块空地。
  - **`/dig center` 的 Y 是柱底**：站高了/站低了整根柱子和玩家落点都会跟着偏。
  - **等待点必须安全**：`/dig lobby` 尽量放在圆外平台上；放在圆内空地上玩家会直接掉下去（本插件只兜底对局中掉出柱子，不兜底等待室）。
  - **坑位太挤**：任意两坑中心横/纵距离 < 3 时 `/dig build` 会拒绝（3×3 玻璃壳会重叠），把 radius 调大或 lanes 调小。
  - **玻璃/基岩保护只在本场地范围内**（`arenaAt` 按圆心 + 坑位 ±1 + 柱高判定），不会影响别的世界。场地内的 `BlockPhysicsEvent` 也会被取消（防止沙子/沙砾塌落），同样只限本场地。
  - **想拆掉整个场地**：先 `/dig remove <场名>`（把场地从插件里删掉），玻璃失去保护后就能手动挖/`/fill` 清理；直接挖玻璃会被取消。
  - **没有 `/dig reload`**：改 `config.yml`（柱高、材料池、时长）后需要重启服务器；改柱高/坑位后还要重新 `/dig build`。
  - 木/石/土三类方块只是分类方便编辑，**实际随机时合并成一个池子**；想调整某类占比就在对应列表里增减条目。
  - 关服时 `shutdown()` 会把 `arenas` 段写回 `config.yml`，**运行中改线上配置会被覆盖**；按工程约定先停服再覆盖。
  - 自动开局受 `WaitCountdown` 规则约束：**队列里有 OP 会暂停自动倒计时**，需要 OP 右键开始纸或 `/dig start`。
- **验证方法**（新服第一次上线）：
  1. `/dig create test` → `/dig center`（站在目标柱底）→ `/dig lanes 8`、`/dig radius 8` → `/dig build`：控制台/聊天提示「已生成 8 个坑位的玻璃与基岩」；现场应看到 8 个环形排列的 3×3 玻璃筒，筒底一格基岩。
  2. 圆外平台 `/dig lobby` → `/dig save`；`server/plugins/DigToBottom/config.yml` 的 `arenas.test` 下应出现 `center/lobby/lanes/radius/built: true`。
  3. 两名玩家 `/dig join test` → OP 右键开始纸：两人被分别传进两个玻璃筒、站在柱顶、正对圆心，快捷栏 1/2/3 是钻石镐/斧/铲（物品名「无限耐久」）。
  4. 倒计时 3 秒内试着走动/挖方块：应被定在原地、方块不掉；3 秒后动作栏开始显示「剩余 180s」。
  5. 挖一格方块：方块直接消失、**背包里没有掉落物**；试着挖玻璃/基岩：被取消；试着挖隔壁坑位：被取消。
  6. 挖到最底层基岩：玩家转旁观、公屏「XX 挖到底了！第 1 个完成」；两个人里第一个完成 → 公屏应出现「过半玩家已到达底部！剩余时间缩短为 20 秒」。
  7. 让另一人挖到底 → 结算广播第一名/第二名；`/dig stats <玩家>` 对应次数 +1（`players.yml` 的 `games.digdown.firsts`）。
  8. 再开一局，只让一人挖到底、另一人挖一半后等时间归零 → 应广播「时间到」、按已挖深度给出第二/第三名；场地柱子应被清空、所有人回大厅。
  9. 重启服务器 → `/dig list` 场地仍在、`built` 仍为 true；进大厅 `/hub games` 应能看到 **一掘到底**。
- **未确认 / TODO**：
  - 菜单入口不会自动加进线上 `menu.items`（线上主菜单已有固定布局）：要么 OP `/hub menu edit` 手动摆一格，要么执行
    `/hub menu set <槽位> DIAMOND_PICKAXE game:一掘到底 &6&l一掘到底`。
  - 大规模柱体（柱高 × 坑位）填充/清理的**实际耗时没量**；默认 60×8 ≈ 480 格，推测在单 tick 内完成，未实测。
  - 环形坑位用四舍五入吸附到整格，`lanes` 很大 / `radius` 很小时可能算出重复点；建场前的距离检查会拒绝，但不会自动帮玩家找可行组合。
