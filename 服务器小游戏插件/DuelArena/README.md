# DuelArena（单挑 PvP）

> 速查卡（AI 先读这里，别先读源码）

- **一句话职责**：单挑 PvP 小游戏：每个场固定 2 人排队配对，凑满自动倒计时开局，两名玩家共用一套 OP 配的 Kit，胜场写进玩家记录并渲染到场战绩牌／加入牌／总胜场榜告示牌。
  - 加入提示：对局内播「`玩家名` 加入了决斗场（N/2）」（`DuelManager#join`），**全服公屏**播「`[单挑 PvP][场名] 玩家名 加入了游戏`」——场名靠 `HubAPI.announceJoin(plugin.getName(), name, arenaName)` 三参数版带出来（没场名的游戏用两参数版）。
    - 公屏那行的游戏名/颜色取自 **HubCore 的 `config.yml` `games` 段**（`name: 单挑 PvP`、`alias: 决斗`、`color: c`），不是插件名；改中文名/颜色改那份配置即可。`plugin.yml` 里的 `minigame: {name: 单挑 PvP}` 段负责建"插件名 `DuelArena` → 显示名"的映射（`HubCore/GameRegistry`）；**少了这个段，传 `plugin.getName()` 的播报会兜底成 `[DuelArena]`**（jar 文件名 = 插件名，别改）。
- **入口 / 主流程**：`DuelArena/src/main/java/com/duel/arena/DuelPlugin.java#onEnable` → `DuelManager#load`（读 config 的 arenas/signs/joinsigns/topsigns + 战绩）；玩家 `/duel join` → `DuelManager#join` → 公共库 `WaitCountdown`（该场 2 人起自动开局；**OP 在队列里时自动倒计时暂停**，OP 用开局纸手动开）→ `DuelManager#startFight`（3 秒无敌读秒）→ `handleDeath` → `endFight` 结算（`addWin` 立即写盘、双方自动离场、刷告示牌）；关服 `DuelPlugin#onDisable` → `saveAll()` 回写 config.yml。
- **命令**：`/duel …`（别名 `/pvp`、`/1v1`；命令级权限 `duel.use`（默认所有人），管理子命令要 OP 或权限节点 `duel.admin`）
  - 玩家：`join <场名>`、`leave`、`list`、`stats [玩家]`、`help`；`stats reset` 要 OP 或 `duel.admin`（清空全部战绩）
  - 管理：`create <场名>`、`edit <场名>`、`setup <场名>`（发配置道具：木棍圈范围、金锄头定出生点、绿宝石配物品、纸保存、屏障退出）、`setup-exit`、`pos1`/`pos2`/`spawn1`/`spawn2`/`lobby`、`kit`、`save`、`remove <场名>`、`rename <旧场名> <新场名>`、`info <场名>`、`sign <场名>`、`joinsign <场名>`、`topsign`
- **配置**：`server/plugins/DuelArena/config.yml`（源码里的 `DuelArena/src/main/resources/config.yml` 只是模板）
  - `arenas.<场名>.pos1/pos2/spawn1/spawn2/manual-lobby/kit`：场范围、两个出生点、等待点（可省，默认范围中心上方）、两人共用的 Kit（ItemStack 序列化）
  - `signs.<世界,方块X,Y,Z> = <场名>`（场战绩牌）、`joinsigns.<…> = <场名>`（加入牌）、`topsigns.<…> = true`（总胜场榜）
  - `max-players`：模板里是 2；线上 config.yml 没有这个键 → 走代码默认 2
  - 场地算"已配置"的条件是 `DuelArena#isReady()`：pos1+pos2+spawn1+spawn2 都不为空
- **玩家记录**：命名空间 `duel`，结构 `wins.per-arena.<场名>.<uuid>` / `wins.total.<uuid>`；代码 `PlayerStats.load("duel", new File(plugin.getDataFolder(), "data.yml"))`，实际落在唯一文件 `server/plugins/MiniGameLib/players.yml` 的 `games.duel`（见 `docs/玩家记录.md`；旧 `plugins/DuelArena/data.yml` 首次加载时自动迁移，旧文件保留）；清空命令 `/duel stats reset`（OP）
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 对局流程／配对／Kit／告示牌刷新／战绩读写 | `DuelArena/src/main/java/com/duel/arena/DuelManager.java` |
  | 事件拦截（伤害、风弹爆炸、死亡、出界、告示牌点击） | `DuelArena/src/main/java/com/duel/arena/DuelListener.java` |
  | 命令与 `/duel help` 文本 | `DuelArena/src/main/java/com/duel/arena/DuelCommand.java` |
  | 场地数据结构（state/queue/isReady/contains）与 Kit | `DuelArena/src/main/java/com/duel/arena/DuelArena.java`、`Kit.java` |
  | 命令与权限声明 | `DuelArena/src/main/resources/plugin.yml` |
  | 线上场地与 Kit（真实生效的那份） | `server/plugins/DuelArena/config.yml` |
  | 战绩数据（唯一文件） | `server/plugins/MiniGameLib/players.yml` |
  | 单挑世界规则（pvp 等） | `server/plugins/GameWorlds/config.yml` |
- **常见坑**：
  - **退出床（红床）必须吃掉交互**：床是方块物品，`onInteract` 里**只 `return` 不 cancel 会把床真的放到地上**
    （真实 bug：非 OP 在相扑场等待中右键床退出，人退了、地上一张床；等待中 `onBlockPlace` 因 `!inFight`
    直接 return，没人拦）。现在的做法：`giveLeaveBed` 给床打 PDC 标记 `duel-leave-bed`，
    `isLeaveBedItem` 只认带标记的床（`onInteract` 先 `setCancelled(true)` + `useItemInHand/useInteractedBlock = DENY`
    再退出；`onBlockPlace` 开头对带标记的床一律 cancel 兜底）——玩家自己拿的普通红床不受影响。
  - **同优先级事件必须限定"是不是我的对局"**：`DuelListener#onDamage`／`#onExplode` 都在 `HIGHEST`，只在"双方都在本场对局中"（`inFight` + `sameArena`）或"风弹射手在对局中"时才 `setCancelled(false)`；改成无条件放行会波及别的世界（"GTB 在 HIGHEST 取消爆炸吃掉单挑风弹"的真实案例，见 `docs/开发须知.md` 第 5 节）。爆炸还要联查 Paper `config/paper-world-defaults.yml` 的 `environment.disable-explosion-knockback`（设成 true 时爆炸不减击退，风弹就推不动人；诊断日志已在定位完成后删除）。
  - 风弹放行只判"射手在对局中"，**没有**再判世界或爆炸点是否在场地范围内。
  - 告示牌位置存成 `世界名,方块X,Y,Z`（`locKey`/`parseLocKey`）**方块**坐标；历史坑是这里存过 +0.5 的坐标，导致 `Location` 精确比较永不相等 → 右键告示牌没反应。现在查找统一走 `sameBlock`（只比世界 + 方块 XYZ），但改这里仍别写小数坐标。
  - `/duel rename` 会同步配置、菜单、场榜／加入牌，并迁移 `wins.per-arena.<旧名>` → 新名；**手工改 config.yml 里的场名不会迁战绩**，看起来像"战绩丢了"。
  - 关服时 `saveAll()` 会回写 `config.yml`（arenas/signs/joinsigns/topsigns）→ 运行中上传会被旧内存覆盖，先停服再覆盖。
  - 场名规则：1~20 字符，仅中英文／数字／下划线／短横线（`DuelManager#validName`）。
  - 现有两个场不在同一个世界：`重锤` 在 `minecraft:world_duel`（GameWorlds 里 `pvp: true`），`相扑` 在 `minecraft:overworld`（不在 GameWorlds 的 `worlds` 列表里，规则不由它套用）。单挑伤害不看世界 pvp 规则，靠 `onDamage` 同场放行。
  - **`PlayerRespawnEvent` 也必须限定世界**（2026-10-18 真实事故）：原来 `onRespawn` 里"不在场就
    `HubAPI.teleportToLobby`"是**无条件**的，于是别的游戏（幸运之柱死后旁观）每重生一次就被从自己局里
    拉回主城，而且 `teleportToLobby` 会 `notifyLobbyHandlers` → 顺手触发对方的 `leave()` 清掉游戏状态。
    现在只有 `isDuelWorld(...)`（重生点或当前世界 = 某个决斗场所在世界）才兜底，其他世界一律 `return`，
    由 HubCore / 那个游戏自己处理。
- **验证方法**：
  1. `D:\BuildBattle\build.bat -Deploy` 后重启：控制台应出现 `DuelArena 已启用` 与 `已加载 N 个决斗场`（当前应为 2）。
  2. 游戏内 `/duel list`：应列出 `重锤 [WAITING] 已配置 (等待 0/2)`、`相扑 …`；缺 pos/出生点的场会显示 `未配置`。
  3. `/duel stats`：打印自己的总胜场与分场胜场（迁移前 `data.yml` 里有 test1/重锤/相扑 三场旧数据）。
  4. 两人 `/duel join 重锤`（或右键加入牌）：满 2 人后自动倒计时开局；开局 3 秒无敌（屏幕 3/2/1，此时移动会被拉回出生点），之后能互相造成伤害；用 Kit 自带的副手风弹炸地面应能把人推飞。
  5. 一方死亡／出界／退出后：`/duel stats <胜者>` 总胜场 +1，场战绩牌与总榜牌刷新；`/duel stats reset` 后告示牌显示"暂无战绩"。
  6. 关服后确认 `server/plugins/DuelArena/config.yml` 已写回 arenas/signs/joinsigns/topsigns，且 `server/plugins/MiniGameLib/players.yml` 出现 `games.duel.wins`（首次迁移会打日志 `已迁移 data.yml -> players.yml [duel]`）。
- **未确认 / TODO**：
  - （已核实并更正）`docs/已知坑.md`、`docs/改动记录.md`、`docs/玩家记录.md` 均已存在，按速查卡里的引用查即可。
  - 本机 `server/plugins/MiniGameLib/players.yml` **尚不存在**，`server/plugins/DuelArena/data.yml` 仍是唯一数据源（含 test1/重锤/相扑）；迁移是否已实跑未验证。
  - `max-players` 只见过模板值 2（线上没这个键，走默认），改成别的值／>2 的实际表现未验证。
  - `onDamage` 用 `setCancelled(false)` 覆盖服务器 `pvp=false` 在计分板队友（`allowFriendlyFire=false`）等场景下是否真生效，未实测。
