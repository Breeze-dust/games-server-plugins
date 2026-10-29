# HotPotato（烫手山芋）

> 速查卡（AI 先读这里，别先读源码）

- **一句话职责**：开局给随机玩家发 TNT（存活 ≥ `two-bomb-threshold` 时同时 2 只）；**持弹者必须"主手拿着 TNT"去打同场其他存活玩家**才会把 TNT 传过去（手里没 TNT 打人无效），回合倒计时归零时仍持有 TNT 的人原地爆炸（只有粒子/音效，不破坏地形）变观战淘汰，淘汰到只剩 1 人时该玩家是冠军（第一名 +1）；走出 `pos1/pos2` 圈定区域也直接淘汰，对局中存活玩家免疫其他一切伤害/死亡；**持弹者高亮 = 头顶名字变红 + 全身红色粒子壳**（没有原版发光描边，被墙挡住时看不见，靠动作栏文字提示），公屏/动作栏显示"TNT 在谁身上"。
  - 加入播报：**全服公屏**播「`[烫手山芋] 玩家名 加入了游戏`」（`HotPotatoManager#join` 调 `HubAPI.announceJoin(plugin.getName(), p.getName())`）。游戏名/颜色来自 HubCore `config.yml` 的 `games` 段（`name: 烫手山芋`、`color: e`），由 `plugin.yml` 的 `minigame: {name: 烫手山芋}` 段把插件名 `HotPotato` 对到那条配置——**少了这个段就会显示成 `[HotPotato]`**。
- **入口 / 主流程**：`HotPotato/src/main/java/com/hotpotato/HotPotatoPlugin.java#onEnable` → `HotPotatoManager#load`（读 config 的 `arenas` + `join-signs`、`PlayerStats.load("hotpotato", …)` 读战绩、`startBoundsCheck` 每 0.5s 出圈兜底轮询）；排队 `HotPotatoManager#join` → `updateWaitCountdown`/`wait`（公共库 `WaitCountdown`）→ 开局 `start` → 分弹 `startRound`（这是"回合开始"）→ 每秒 `tickRound` → 归零 `explodeEndRound`（这是"回合结束"，持弹者爆炸）→ `eliminateOne` → `afterEliminate`（>1 人则 `startRound` 开下一回合）→ 只剩 1 人 `endGame`（这是"结算"：冠/亚/季军广播 + `recordFirst`）。相关：传弹 `handleAttack`、出圈 `handleMove`、退出清理 `handleQuit`、进服清理 `handleJoin`、离开 `leave(p, silent)`。以上都在 `HotPotato/src/main/java/com/hotpotato/HotPotatoManager.java`；事件在 `HotPotato/src/main/java/com/hotpotato/HotPotatoListener.java`。
- **命令**：
  - 玩家：`/hp join <场名>`、`/hp leave`、`/hp list`、`/hp stats [玩家]`、`/hp help`。主命令 `hotpotato`，别名 `/hp`、`/potato`（`HotPotato/src/main/resources/plugin.yml`）。
  - 管理员（`p.isOp() || p.hasPermission("hp.admin")`，权限节点 `hp.admin` 在 `plugin.yml` 声明，default: op）：`/hp create <名>`、`/hp remove <名>`、`/hp edit <名>`、`/hp rename <旧名> <新名>`、`/hp pos1`、`/hp pos2`、`/hp center`、`/hp lobby`、`/hp save`、`/hp start`、`/hp setup <名>`、`/hp setup-exit`、`/hp joinsign <名>`、`/hp stats reset`。
  - 细节：`/hp start` 只对 `/hp edit` 选中的场生效，且人数需 ≥ `min-players`；`/hp stats reset` 清空**全部**第一名记录（需 OP 且由玩家执行）；帮助文案在 `HotPotatoCommand#help`，子命令在 `HotPotatoCommand#onCommand`。
- **配置**：`server/plugins/HotPotato/config.yml`（源码模板 `HotPotato/src/main/resources/config.yml`，改线上要改前者）。
  - 代码真正读取的键与默认值：`min-players`（2，同时是等待倒计时门槛）、`max-players`（8）、`two-bomb-threshold`（5，存活 ≥ 此值同时 2 只 TNT）、`arenas.<场名>.pos1 / pos2 / center / lobby`、`join-signs.<场名>`（告示牌坐标列表）。
  - 场地生效条件 `HotPotatoArena#isReady` = pos1 + pos2 + center 三者都有；`lobby`（等待点）缺失时直接用 `center` 当等待点。
  - 线上现状：只有一个场 `群系`（世界 `world_hp`），`join-signs: {}`。
  - **回合时长不读配置**：`startRound` 内硬编码 存活 ≥8 → 40s、4~7 → 30s、<4 → 20s；线上遗留的 `round-seconds: 90` 没有任何代码读取。
- **玩家记录**：命名空间 `hotpotato`，结构 `firsts.<uuid>` = 该玩家第一名次数（只有这一个键，`HotPotatoManager#recordFirst` 每场冠军 +1）；文件 `server/plugins/MiniGameLib/players.yml`（位于 `games.hotpotato` 段下），加载写法 `PlayerStats.load("hotpotato", new File(plugin.getDataFolder(), "data.yml"))`；权威说明见 `docs/玩家记录.md`；游戏内查询 `/hp stats [玩家]`，清空 `/hp stats reset`。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 玩法/回合时长/传弹/淘汰/战绩逻辑 | `HotPotato/src/main/java/com/hotpotato/HotPotatoManager.java` |
  | 命令分支与 `/hp help` 文案 | `HotPotato/src/main/java/com/hotpotato/HotPotatoCommand.java` |
  | 免伤、出圈、退出床/开始纸、加入告示牌、禁放禁丢 TNT | `HotPotato/src/main/java/com/hotpotato/HotPotatoListener.java` |
  | 场地字段、`isReady`、往 config 存档的结构 | `HotPotato/src/main/java/com/hotpotato/HotPotatoArena.java` |
  | 新服默认配置模板 | `HotPotato/src/main/resources/config.yml` |
  | 线上真实生效配置（场地坐标、人数上限） | `server/plugins/HotPotato/config.yml` |
  | 主城菜单图标/入口（`games` 段、`menu.items` 的 `action: game:烫手山芋`） | `server/plugins/HubCore/config.yml` |
- **常见坑**：
  - ① lobby handler 必须用静默版：`onEnable` 注册的是 `manager.leave(p, true)`；`leave(p, true)` 在玩家本来不在场中时直接返回、**不打提示、也不回调 `HubAPI.teleportToLobby`**。改成有提示版会让玩家进服/右键"回主城"时刷"你不在任何场中"（`docs/开发须知.md` 第 5 节）。玩家主动 `/hp leave`、右键退出床走的是 `leave(p)`（有提示），要保留。
  - ② 对局中存活玩家在 `EventPriority.HIGHEST` 被 `EntityDamageEvent` / `EntityDamageByEntityEvent` 整体免伤（`HotPotatoListener`），只能被回合爆炸或出圈淘汰；`handleAttack` 只在 `State.RUNNING`、双方是场内玩家、对方当前不是持弹者、**且攻击者主手真的拿着 TNT（`isHoldingTnt`）**时才传递，等待阶段打人或空手打人都不会传弹。持弹者高亮 = **红名**（记分板主榜队伍 `hp_tnt`，`applyTntNameTag` 里只 `team.color(RED)` + `addEntry`，**不设 prefix、不碰 `playerListName`**，所以 HubCore 的 tab 前缀 `[已加入·烫手山芋]` 不受影响；`clearTntNameTag` 移除条目，队伍空了就 `unregister`）+ **红色粒子壳**（`glowTick` 每 5 tick：躯干 `DUST(Color.RED)` ×20 + 头肩 ×10 + 腿 ×8 + 脚下 `FLAME` ×2；任务存 `glowTasks`，由 `cancelTask` 随回合任务一起取消）。**故意不用原版 `GLOWING`**：它的轮廓线颜色是客户端硬编码白色，插件改不了（`clearBombVisual` 里仍留了一行 `removePotionEffect(GLOWING)` 兜底清历史版本残留）。`clearBombVisual` 会连同 `SPEED`、红名、粒子任务一起清（`leave` / 淘汰 / `handleQuit` 都会清，`start` 开局也会兜底清一次红名）。
  - ③ 开局会把场地世界 PvP 临时设为 `true`（`enableWorldPvp`），结束/关服由 `restoreWorldPvp` / `restoreAllWorldPvp` 还原；这个"原状态"只存在内存 `worldPvpSaved` 里，对局中强杀进程会把世界留在 PvP=true。
  - ④ 中央点别设太低、圈别画太小：`handleMove` 除 `pos1/pos2` 方框外还有三重垂直兜底 —— 低于 `center - 1` 格、低于 `pos1/pos2` 中较低者 `- 1` 格、低于世界最低高度 `+ 1` 格都判出圈，正常站位也可能被误淘汰。
  - ⑤ 无效/未使用的配置项别去改：`config.yml` 的 `round-seconds` 无效（见上）；`plugin.yml` 里的 `hp.use` 全仓库只有声明、代码不校验（权限只认 `hp.admin`）。
  - ⑥ 淘汰者是 `SPECTATOR` 观战；`handleQuit` / `handleJoin` 会强制把玩家恢复 `SURVIVAL`，改这两处别丢这个恢复（否则重进会"无敌"打不动、攻击没效果）。
  - ⑦ 关服 `onDisable` 会 `restoreAllWorldPvp()` + `saveAll()`（场地 + 加入告示牌回写 `config.yml`）并 `saveData()` 写战绩，所以运行中上传配置会被关服时的旧内存覆盖（`docs/开发须知.md` 第 4 节）。
  - ⑧ **场地配置改动后必须重新上报中央菜单**：`saveAll()`（`/hp create`、`/hp pos1|pos2|center|lobby|save`、配置模式工具、删除、改名都会走它）末尾调 `reportAllArenas()` 把 `arenas` 全量 `reportArena`。`reportArena` 只在开服延迟 40 tick、玩家进出场、开局/结束时调用，运行中新建/刚圈好地的场不上报，就会「主菜单点烫手山芋 → 打开一块羊毛都没有的场地子菜单」（HubCore `arenasOf` 空表）。以后新增场地改动路径别绕开 `saveAll()`。
  - ⑨ `HotPotatoListener#onMove` 里"只转头不算移动"的早退**必须先比世界**：`if (!p.getWorld().equals(event.getFrom().getWorld())) return;`。别的小游戏（真实案例：TntRun 雪塔大作战 `endGame`）会在自己的 `PlayerMoveEvent` 里把玩家跨世界传回主城，此时 `event.getFrom()` 还是旧世界坐标，直接 `p.getLocation().distanceSquared(event.getFrom())` 会抛 `Cannot measure distance between …`（`docs/已知坑.md` §2）。出圈另有 `startBoundsCheck` 轮询兜底，跳过这一次移动事件不会漏判。
- **验证方法**：
  - 进服 / 右键"回主城"道具：**不应**出现"你不在任何场中"（验证静默版 lobby handler）。
  - `/hp list` 看场与状态；`/hp join 群系` 应被传到等待点、背包被清空、快捷栏第 9 格（槽位 8）是"退出游戏 (右键)"红床；再 `/hp join` 别的场应提示"你已在其他场"。
  - 中央菜单：**运行中**建场（`/hp create` + `/hp pos1|pos2|center` + `/hp save`）后**不重启**，回主城点「烫手山芋」应直接进唯一的场（内部就是 `hp join <场名>`），不再弹空的场地子菜单；控制台不应出现 `[HubCore] 已为「烫手山芋」生成 0 个场地子菜单槽位`。老 jar 的临时绕过办法：先 `/hp join <场名>` 再 `/hp leave`（进出场会触发一次上报）。
  - 等待倒计时：队列里没有 OP 且人数 ≥ `min-players` 时广播"30s 后自动开始…"、动作栏"开始倒计时：Ns"，然后自动开局；队列里有 OP 时提示"OP 在队列中，等待手动开始"（改为 OP 右键开始纸或 `/hp start`）；人满 8 人时缩短为 3s。
  - 对局中：持弹者主手和头盔是 TNT + 速度效果 + **红名 + 全身红色粒子壳**（已去掉原版白描边，被墙挡住时看不见），动作栏显示"TNT！剩余 Ns — 拿 TNT 打人才能传出去！"，其他人动作栏显示"TNT 在 <名字> 身上（红色高亮）！"，回合开始时公屏也有"TNT 在 <名字> 身上！"；拿着 TNT 打同场其他存活玩家 → 公屏"X 把 TNT 传给了 Y"、被打者红屏击退但不掉血；**手里没有 TNT 打人（比如把 TNT 换到副手/背包后）不传弹**；持弹者放置/丢弃 TNT 被拒绝。
  - 回合归零：持弹者原地爆炸粒子 + 爆炸音效并变观战；走出 `pos1/pos2` 方框 → 变观战、全服播报"掉出了这个世界！"并被拉回中央点。
  - 结算：只剩 1 人时广播"游戏结束"与冠军/亚军/季军，全员自动回主城；随后 `/hp stats` 应显示该玩家"获得第一名 N 次"，且 `server/plugins/MiniGameLib/players.yml` 的 `games.hotpotato.firsts.<uuid>` 加 1（可跑 `check-stats.ps1` 做离线自检）。
- **未确认 / TODO**：
  - 本卡全部结论来自静态读码（`HotPotato/src/main/java/com/hotpotato/*.java`、`HotPotato/src/main/resources/plugin.yml` 与 `config.yml`）、线上配置 `server/plugins/HotPotato/config.yml` 与 `docs/玩家记录.md`；**未在游戏里实测**上述命令与现象。
  - `server/plugins/MiniGameLib/` 当前是**空目录**（没有 `players.yml`），而 `server/plugins/HotPotato/data.yml` 里还有 3 条 `firsts.<uuid>`：说明这份本地副本尚未发生迁移，下次启动 HotPotato 时才会写入 `players.yml`（迁移规则见 `docs/玩家记录.md` 第 3 节）。
  - `/hp stats <玩家>` 只认在线玩家（`getPlayerExact`，离线提示"玩家不在线"），离线查询未实现。
  - 两条开局路径未实测：命令 `/hp start`（走 `HotPotatoCommand` 的 `editing` 记录）与 OP 右键开始纸（`handleStartPaper`，走 `playerArena`）；`/hp create` 后必须 `pos1/pos2/center` 齐全才能 `/hp join`。
