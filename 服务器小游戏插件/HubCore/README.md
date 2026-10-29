# HubCore（主城/中枢）—— 速查卡

> AI 先读这一段，能省掉读 2500 行源码

- **一句话职责**：主城大厅 + 中央菜单（OP 游戏内编辑，落盘 `server/plugins/HubCore/config.yml` 的 `menu` 段）+ 统一 tab（每秒刷新）+ 扫描 `plugins/*.jar` 的 plugin.yml 自动发现小游戏；并实现 MiniGameLib 的 `HubService`（= 各游戏用的 `HubAPI`）。**改了 HubCore，所有游戏 jar 都要一起重传。**
- **入口**：`HubCore/src/main/java/com/hub/HubCorePlugin.java#onEnable` —— `saveDefaultConfig()` → 建 `HubManager` / `GameRegistry`（`registry.rescan()`）/ `MenuLayout` / `MenuEditor` → `HubAPI.register(manager)`（`HubManager implements HubService`，onDisable 里 `unregister`）→ 注册 `HubListener` + `MenuEditor` 两个监听器 → 主城规则 `applyHubWorldRules(world)`（`DO_DAYLIGHT_CYCLE=false`、`DO_WEATHER_CYCLE=false`、`DO_MOB_SPAWNING=false`、时间固定 1000、清雨清雷）+ 每 5 秒 `enforceHubWorldRules()` 兜底（规则/时间/天气被改回去就自动恢复）→ 把 `hub` 命令的 executor/tabCompleter 指向自己 → `runTaskTimer(manager::refreshTab, 20L, 20L)`。
- **命令**：入口是 `/hub`（`plugin.yml` 里的别名 `lobby` / `spawn`；管理权限只有 `hub.admin`，`default: op`，判定 = `isOp() || hasPermission("hub.admin")`，控制台算管理员）
  - 玩家：`/hub`（回大厅：先广播 lobby handler 清游戏状态，再传送 + 清背包发菜单道具）、`/hub menu`（开中央菜单）。
  - OP：`/hub set`（把当前位置设为大厅）、`/hub reload`（`reloadConfig()` + `registry.rescan()` + `refreshTab()`）、`/hub item [玩家]`（发 CLOCK 回主城道具，带 PDC 标记）、`/hub lock`（切换 `lock-inventory` 并保存；**菜单道具的禁丢/禁挪位不受它影响**）、`/hub games`（权威游戏名单）、`/hub arena desc <游戏> <场地> <介绍…>`（`|` 分行、`clear` 清除；只给到场地=查看，`info` 同义）。
  - OP 菜单：`/hub menu edit [游戏]` / `cancel` / `set [游戏] <槽位> [材质] [动作] [显示名]` / `move` / `name` / `lore` / `action [游戏] <槽位> <动作…>` / `del`（`delete` 同义）/ `rows [游戏] <1-6>` / `title [游戏] <标题>` / `reset [游戏]` / `help`；不带游戏名=主菜单，带游戏名=该游戏的场地子菜单，`<槽位>` 省略时用当前打开的格子。
  - 动作 6 种（**空格分隔**；`cmd:gtb join` 这种类型尾部带一个冒号的也认）：`game <游戏>`（该游戏只有 1 个场地就直接进，≥2 个场地或还没上报就开子菜单）、`open <游戏>`（总是开子菜单）、`arena <游戏> <场地>`（直接加入指定场地）、`cmd <命令>`（以玩家身份执行）、`spectate`（打开观战列表）、`none`（纯装饰）。
- **OP 专属物品**：`menu.items.<槽位>` 支持 `op-only: true`，非 OP 打开菜单时该格不渲染、也点不到。装了 ArcadeParty 时，启动会自动在主菜单找一个空槽放「派对设置」（COMPASS，`action: cmd:arcade menu`，`op-only: true`），已有 `cmd:arcade menu` 条目则只补 `op-only`，不重复添加。
- **配置 / 数据**：`server/plugins/HubCore/config.yml`（源码 `HubCore/src/main/resources/config.yml` 只是模板，改的时候要连线上那份一起改）—— `lobby.*`（`/hub set` 写）、`menu-item`（默认 COMPASS）/ `menu-item-slot`（默认 4）、`menu-title`（旧键，只在 `menu.title` 不存在时兜底）、`games` 段（`name` 显示名 / `alias` / `color` / `icon` / `command` 无场地信息时的兜底命令 / `join-prefix` 拼成 `"<前缀> <场地名>"`）、`menu` 段（`rows` / `title` / `items.<槽位>` / `sub.<游戏>.items.<场地名或槽位>` / `arena-desc.<游戏>.<场地>`）、`tab.show-empty-games`、`lock-inventory`。**改菜单布局/提示不用改代码**：编辑每一步都立即 `saveConfig()` 落盘。
- **tab 名字颜色**：tab（头部分区名单 + 玩家列表名）每秒重刷成"大厅灰 / 等待黄 / 已加入绿"，会盖掉记分板队伍色；小游戏要对局内按队伍染色（如绿茵足球红/蓝队）时调 `HubAPI.setTabNameColor(uuid, "c"/"9")`，刷新只换名字段颜色、状态前缀不变；`clearGame`（退出游戏/下线）自动清除。实现：`HubManager#setTabNameColor` + `refreshTab`。
- **场地状态渲染规则**：子菜单里每个场地一块羊毛，物品名就是场地名。`MenuLayout.ArenaState.of`：上报 `state != WAITING`（含还没上报/刚删）→ **红 RED_WOOL = 游戏中 `&c`**；`WAITING` 且 `players <= 0` → **绿 LIME_WOOL = 没人 `&a`**；`WAITING` 且有人 → **黄 YELLOW_WOOL = 等待中 `&e`**。**颜色不对先查游戏插件上报的 `players`**（0 人却报 PLAYING 就会显示成红）。游戏名必须与注册表显示名一致：`/hub games` 与启动日志「已登记 N 个游戏：…」是权威名单（`canonicalName` 会把 alias / 少空格归一化，但别依赖 OP 手写的名字）。OP 给该场地写过 `material` / `lore` 就以 OP 的为准（默认状态行不显示）。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 大厅 / 场地状态 / tab / 菜单点击分发 | `HubCore/src/main/java/com/hub/HubManager.java` |
  | 菜单渲染、`menu` 段读写、动作解析、默认布局生成 | `HubCore/src/main/java/com/hub/menu/MenuLayout.java` |
  | GUI 编辑、聊天栏输入、菜单库存事件 | `HubCore/src/main/java/com/hub/menu/MenuEditor.java` |
  | 插件发现、显示名、别名归一化 | `HubCore/src/main/java/com/hub/game/GameRegistry.java` |
  | 子命令、tab 补全、权限判定 | `HubCore/src/main/java/com/hub/HubCorePlugin.java` |
  | 进服 / 重生 / 右键道具 / 物品栏锁定 | `HubCore/src/main/java/com/hub/HubListener.java` |
  | 小游戏怎么接进来 | `HubCore/docs/游戏插件接入指南.md` |
- **常见坑**：`menu.items` 被删空会被当成"没编辑过"→ 按 `games` 段重新生成默认布局（想留空白布局至少留一个 `action: none` 的装饰物品）；YAML 的 `键: 值` 冒号后必须有空格，否则整份配置加载成空（表现为"场地全没了"）；游戏名对不上（少空格 / 写成 alias）会让 `arenasOf` 查空 → 子菜单一块羊毛都没有，或点羊毛误报"正在游戏中"；`/hub`、右键"回主城"道具会广播 lobby handler，游戏插件的处理在"玩家不在自己场中"时必须静默，否则刷"你不在任何场中"；`gamehub` 命名空间不要改名（PDC key `gamehub:hub-item` / `gamehub:menu-item` 写在各插件 kit 存档的物品数据里）；菜单一律靠 `MenuHolder` 识别，别改回比标题文本；改了本插件必须连同所有游戏 jar 一起重传。
- **验证方法**：`/hub games` 看权威名单 → `/hub menu` 右键指南针看布局 → 进一个等待中的场地后重开子菜单：对应羊毛应变黄/红并显示人数，绿/黄能点进去，红只提示"正在游戏中" → `/hub menu edit` 摆一格物品、关菜单，查 `server/plugins/HubCore/config.yml` 的 `menu` 段是否立刻多出该槽位 → `/hub reload` 后布局不回滚即落盘成功 → `/hub arena desc <游戏> <场地> …` 后重开子菜单看那块羊毛的 lore。
  - 主城白天/晴天：`/time query daytime` 隔一会儿再查仍是 `1000`（昼夜循环关了）；`/weather rain 60` 后 **5 秒内自动转晴**，日志出现 `主城时间/天气被改动，已恢复为白天晴天`。世界状态落在 `world\dimensions\minecraft\overworld\data\minecraft\` 的 `game_rules.dat`（`advance_time / advance_weather = 0`）、`world_clocks.dat`（`total_ticks ≡ 1000 mod 24000`）、`weather.dat`（`raining / thundering = 0`）——自动存档后才刷新，看之前先 `/save-all`。
- **未确认 / TODO**：
  - 线上 `config.yml` 的 `games` 段只有 5 条（建筑猜猜乐 / 单挑 PvP / 烫手山芋 / 雪塔大作战 / 密室杀手），**天坑**不在其中，靠 `SkyPit/src/main/resources/plugin.yml` 的 `minigame.name: 天坑` 自动发现，所以名单一律以 `/hub games`（或启动日志）为准；实际登记条数需在服上核对。
  - 线上 `menu.sub.天坑` 只写了 `rows: 3`；`menu.arena-desc` 段当前不存在（还没写过场地介绍）。
  - `MenuLayout.setSubSlot` 会写 `action: slot:<槽位>`，这版源码里只看到读取（`configuredSubSlot`），没找到命令入口去调用它，是否还在用未确认。
  - `menu-item-slot` 没出现在线上 config.yml 里（走默认第 4 格）。

# HubCore（主城插件）

大厅、中央菜单、统一 tab、小游戏插件发现。对外接口是 MiniGameLib 里的 `com.hub.api.HubAPI`
（本插件在 `onEnable` 把 `HubManager` 注册进去当 `HubService` 实现）。

## 菜单现在是"OP 可编辑"的

- 玩家侧：进大厅拿到菜单道具（`menu-item`，默认 COMPASS，**名字固定是 `小游戏菜单`，由 `HubManager.giveMenuItem` 写死**，
  改配置/改名都不会生效；放 `menu-item-slot` 格 = 默认第 4 格正中间；
  该格被 OP 摆的菜单物品占住时才就近顺延），右键开菜单。
  **菜单道具不能被丢弃、也不能在物品栏里被挪格/替换/换手**（`HubListener.onDrop` / `onMenuClick` / `onSwapHands` 硬禁，
  不受 `lock-inventory` 影响；线上那份 `lock-inventory` 就是 `false`）。
  点游戏图标 = **该游戏只有 1 个场地才直接进；有 2 个及以上场地都打开该游戏的场地子菜单**（还没上报场地信息时也开子菜单）。
  场地子菜单里**每个场地一块羊毛，物品名就是场地名**，颜色是实时状态：
  **绿（LIME_WOOL）= 没人**、**黄（YELLOW_WOOL）= 等待中（有人但没开局）**、**红（RED_WOOL）= 游戏中**；
  悬停可见状态/人数/实时进度（如 `第 3/8 轮`）和 OP 写的**场地介绍**，绿/黄点击即可加入该场地。
- 默认布局：第一次打开菜单时按 `games` 段生成，第一行按数量**居中**排并让开 `menu-item-slot` 那格，
  所以开服后看到的和以前一样（指南针仍在中间，五个游戏图标分列两侧）。
- OP 侧：菜单内容存在 `config.yml` 的 `menu` 段，可以用 GUI 或命令编辑，**改完立即写回配置**。

### 可视化编辑（推荐）

```
/hub menu edit            # 编辑主菜单（打开 GUI）
/hub menu edit 单挑 PvP    # 编辑「单挑 PvP」的场地子菜单
```

编辑模式下的操作：

| 操作 | 效果 |
| --- | --- |
| 左键 | 把光标上的物品放下到该格；若是刚"右键复制"来的，**连动作一起还原**（否则保留该格原动作） |
| 右键 | 把该格物品（连同动作）复制到光标，左键点到别处即复制摆放 |
| SHIFT+左键 | 聊天栏输入材质名（如 `DIAMOND_SWORD`，`cancel` 取消） |
| SHIFT+右键 | 聊天栏输入显示名（`&` 颜色代码；`clear` 恢复默认，`cancel` 取消） |
| 双击 | 聊天栏输入 lore 提示，多行用 `|` 分隔（`clear` 恢复默认，`cancel` 取消） |
| Q | 删除该格 |
| 关闭菜单 | 退出编辑模式 |

### 命令式编辑

```
/hub menu help
/hub menu set    [游戏] <槽位> [材质] [动作] [显示名]   # 材质省略时用主手物品
/hub menu move   [游戏] <从槽位> <到槽位>
/hub menu name   [游戏] <槽位> <显示名>
/hub menu lore   [游戏] <槽位> <第1行|第2行>          # |clear 恢复默认
/hub menu action [游戏] <槽位> <动作>
/hub menu del    [游戏] <槽位>
/hub menu rows   [游戏] <行数1-6>
/hub menu title  [游戏] <标题>
/hub menu reset  [游戏]                                # 恢复自动生成的布局
```

不带游戏名 = 主菜单；带游戏名 = 那个游戏的场地子菜单。命令里的 `<槽位>` 可以省略（用当前打开的格子）。

### 动作 action 的写法

命令里**用空格分隔**（帮助文本里带冒号的写法也照样认：`cmd:gtb join`）：

| 写法 | 点击效果 |
| --- | --- |
| `/hub menu action <槽位> game <游戏名>` | 只有 1 个场地时直接加入；2 个及以上场地打开场地子菜单 |
| `/hub menu action <槽位> open <游戏名>` | 总是打开该游戏的场地子菜单 |
| `/hub menu action <槽位> arena <游戏名> <场地>` | 直接加入指定场地（子菜单槽位默认就是这种，首次打开时自动生成） |
| `/hub menu action <槽位> cmd <命令>` | 以玩家身份执行任意命令（多词命令用空格分隔，如 `cmd gtb join`） |
| `/hub menu action <槽位> none` | 纯装饰 |

> 游戏名必须和 `games` 段的 `name` 完全一致（注册表日志里会列出实际名字，例如 `SkyPit` 而不是 `天坑`）。
> 写 `game:`/`open:`/`arena:` 时如果不一致（少个空格、写了 `alias`，如 `单挑PvP`），
> 查询场地状态和写入动作时都会**自动归一化成注册表的显示名**，所以点了能进子菜单、也点得动。（`cmd:` 不受影响。）

### 场地介绍（选场地菜单里那块羊毛的提示）

```
/hub arena desc <游戏> <场地> <介绍…>     # 多行用 | 分隔；写 clear 清除
/hub arena desc <游戏> <场地>             # 查看当前介绍
```

例：`/hub arena desc 单挑 PvP 风场 &7风很大，小心被吹下去|&7场地很小，别掉下去`

介绍存在 `config.yml` 的 `menu.arena-desc.<游戏>.<场地>`（**和 `menu.sub` 分开存**，
所以 `/hub menu reset`（重置场地槽位）不会把介绍一起清掉），显示在该场地羊毛的 lore 里：
第一行状态（没人/等待中/游戏中 + 人数），接着是介绍每一行，最后一行点击提示。
场地是用 `{arena}` 等占位符自定义过 lore 的，就以 OP 写的 lore 为准（介绍不显示）。
游戏名可用 `alias`（如 `单挑 PvP` / `决斗` 都认），场地名必须和游戏插件上报的完全一致。

### 占位符（场地子菜单的 name / lore 里可用）

`{arena}` 场地名、`{players}` 人数、`{extra}` 实时状态（如 `第 3/8 轮`）、`{state}` 没人/等待中/游戏中。

## 观战（HubCore 1.3.3）

- **入口 1**：主菜单**左下角**（最后一行第一格，默认第 45 格）放一个末影之眼「观战」条目，动作写 `spectate`；点它打开「观战：选择对局」GUI，**每个正在进行的一局占一条**（多场地游戏按场地上报逐个列出，名字是「游戏 · 场地」）。这个条目由 `MenuLayout#ensureSpectateMenuEntry()` 在启用时自动补（该格被 OP 摆过东西就跳过），想在别处再加一个就把动作写成 `spectate`。**物品栏里不再发末影之眼**（1.3.3 起）。
- **入口 2**：主菜单点游戏图标 —— 该游戏没有等待中场地但正在打时直接进观战；子菜单点**红色（PLAYING）场地羊毛**进的是**那个场地**。
- **落点（1.3.1 起精确到场）**：各游戏通过 `HubAPI.setArenaSpectator(游戏, 场地, Location)` 上报静态观战坐标（一般报场地中心或等待区，见各游戏 `reportArena`）。HubCore 存 `spectatorSpots`，进观战时先找该场地，再找 `arena=""` 的游戏级兜底；**都没上报**才退回老办法（`findRunningPlayer()` 挑一位 `PLAYING` 玩家，传到他头顶 3 格）。
- **退出**：旁观模式用不了道具（快捷栏都不显示），所以进观战时清空背包，聊天栏发一行可点的「[ 点击这里退出观战 ]」（`ClickEvent.runCommand("/hub")`），也可以直接输 `/hub`、`/lobby`；`teleportToLobby` / `clearGame` 里都 remove 了 `spectating`。
- **自动收尾**：`tickSpectate()` 每秒跑（`HubCorePlugin#onEnable` 里的第二个 20L 任务）：经验条上方刷「观战中：X ｜ 输入 /hub 退出（聊天栏有按钮）」；`isGameRunning()` 变 false 或玩家被改成非旁观 → 自动回大厅。
- `isGameRunning()` = 有场地报 `PLAYING`，**或**有该游戏的玩家处于 `PLAYING`（兼容天坑 / 粒子枪战这种不上报场地的单场游戏）。
- **观战者不算在局内**：不写 `gameMap`，所以 tab 仍在大厅分区、不参与 `announceJoin`/胜负；`lock-inventory` 相关的大厅保护（禁止捡东西等）对观战者照常生效。
- 注意：末影之眼同样受「禁丢弃 / 禁换手 / 禁挪格」保护（`HubListener#onDrop/onSwapHands/onMenuClick`）。
- 各游戏上报的坐标来源：BuildRun `getLobby`、ColorParty/Duel `getLobbyPoint`、Football/HotPotato/LuckyPillar/MurderMystery/TntRun `getCenter`、GuessTheBuild `Arena#getSpawn`、JumpFill `arena.lobby`、LandClaim `arena.center`、TNTBattle `arena().spectatorLocation`、SkyPit `getEntry`、Quark `arena().getLobby`、KnockbackBattle `arena().center`、GroupRace `arena.getStart`。

## 配置文件要点（`server/plugins/HubCore/config.yml`）
- `menu.rows / menu.title / menu.items / menu.sub.<游戏>`：菜单布局，见上。
- `menu.arena-desc.<游戏>.<场地>`：场地介绍（`/hub arena desc` 写入，多行用 `|` 分隔）。
- `games`：游戏注册表（显示名、tab 颜色、兜底命令 `command`、加入前缀 `join-prefix`）。
  首次启动（或 `/hub menu reset`）时按这里的 `icon` 生成主菜单图标。
- `tab.show-empty-games`：tab 是否显示 0 人的游戏分区（默认 `false`：游戏多时全列会把在线玩家挤出屏幕；要始终显示全部游戏就改 `true`，完整游戏列表可看主城菜单）。
- `lock-inventory`：大厅是否禁止捡/丢/移动物品（`/hub lock` 切换）。菜单 GUI 本身是放行的。
  **例外（永远生效，与该项无关）**：菜单道具不能被丢弃、不能在物品栏里挪位/被顶掉、不能换手（F 键）。

## 其他命令

`/hub`（回大厅）、`/hub menu`（玩家开菜单）、`/hub set`（把当前位置设为大厅）、
`/hub item [玩家]`（发回主城道具）、`/hub lock`、`/hub games`、`/hub arena desc`、`/hub reload`。

## 实现备忘（改代码前先看）

| 文件 | 作用 |
| --- | --- |
| `com/hub/HubManager.java` | 大厅/场地状态/tab；`openMenu`/`handleMenuClick` 只按槽位动作分发 |
| `com/hub/menu/MenuLayout.java` | `menu` 段的读写、主菜单与场地子菜单渲染、动作字符串解析、默认布局生成 |
| `com/hub/menu/MenuEditor.java` | GUI 编辑、聊天栏输入、菜单的所有库存事件（点击/拖拽/关闭/锁定） |
| `com/hub/menu/MenuHolder.java` | 用 `InventoryHolder` 标记"这是主城菜单"，不再靠标题比对 |

- 菜单识别一律走 `MenuHolder`：OP 改标题后旧写法（比标题文本）会失效。
- `/hub reload` 会 `reloadConfig()`，`menu` 段随之刷新；编辑时每步都 `saveConfig()`。
