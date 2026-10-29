# BuildBattle —— Minecraft 小游戏服务器

Paper 26.2（Java 25）单服多小游戏：一个公共库 + 一个主城插件 + 一个世界管理插件 + 6 个小游戏插件，全部自研。

## 文档地图（先看这张表，别通读）

| 想干什么 | 看这里 |
| --- | --- |
| 让 AI 帮你改东西（**从这里开始**） | [AGENTS.md](AGENTS.md) —— 六条铁律 + 命令 + 路由表（约 1k token） |
| 全局规约 / 会话纪律 / 新会话开场话术 | [docs/开发须知.md](docs/开发须知.md) |
| 出了怪现象（"本地行、上传就坏"…） | [docs/已知坑.md](docs/已知坑.md) —— 症状→根因→怎么确认 |
| 玩家战绩 / 备份上传玩家数据 | [docs/玩家记录.md](docs/玩家记录.md) |
| 改某个插件前先摸清它 | 各插件目录下的 `README.md` 顶部「速查卡」（一段话 + 关键文件索引） |
| 接入一个新小游戏 | [HubCore/docs/游戏插件接入指南.md](HubCore/docs/游戏插件接入指南.md) |
| 最近改了什么、上一步的结论 | [docs/改动记录.md](docs/改动记录.md) |

```
        ┌───────────────────────────────────────────────┐
        │  MiniGameLib（公共库，谁都能依赖它）              │
        │  HubAPI/HubService · Chat · SetupMode ·        │
        │  WaitCountdown                                │
        └───────▲───────────────────────▲───────────────┘
                │ depend                │ depend
        ┌───────┴──────────────┐        │
        │  HubCore（主城/中枢）  │        │  实现 HubService → HubAPI.register()
        │  大厅·中央菜单·统一 tab│        │
        │  /hub                 │        │
        └───────▲───────┬───────┘        │
   HubAPI.setGame/      │ Bukkit.dispatchCommand ("<game> join <arena>")
   setPlaying/setArena  │                │
   clearGame/...        ▼                ▼
   ┌──────────┬──────────────┬───────────┬────────────┬──────────┬────────┐
   │ 建筑猜猜乐 │   单挑 PvP    │  烫手山芋  │ 雪塔大作战  │  密室杀手  │  天坑
   │   GTB     │  DuelArena   │ HotPotato │  TntRun    │    MM      │ SkyPit
   └──────────┴──────────────┴───────────┴────────────┴──────────┴────────┘
        ▲ 全部 depend: [MiniGameLib, HubCore]；softdepend: [GameWorlds]
        │
 ┌──────┴───────────┐
 │   GameWorlds     │  启动时创建/加载各游戏世界并按配置套用规则
 │  /gw 多世界托管   │  world_gtb / world_duel / world_hp / world_tr / world_mm
 └──────────────────┘
```

## 目录结构

| 目录 | 内容 |
| --- | --- |
| `MiniGameLib/` | **公共库**：`com.hub.api.HubAPI`（主城对接契约）、`Chat`（消息）、`SetupMode`（OP 建场模式）、`WaitCountdown`（等待室自动开局倒计时）。不注册命令。迁移说明见 [MIGRATION.md](MiniGameLib/MIGRATION.md) |
| `HubCore/` | **主城插件**：大厅、中央菜单（OP 可在游戏内用 `/hub menu edit` 摆位置/改物品/改悬停提示）、统一 tab、小游戏插件发现（`/hub`）。实现 HubAPI 需要的 `HubService`，详见 [README.md](HubCore/README.md) |
| `GameWorlds/` | 多世界托管（`/gw`）：创建/删除世界，按配置套用 PvP/难度/时间/刷怪等规则 |
| `GuessTheBuild/` | 建筑猜猜乐（建造+猜词+评分），唯一带完整 README 的插件 |
| `DuelArena/` | 单挑 PvP（排队配对、Kit 配置 GUI、战绩告示牌） |
| `HotPotato/` | 烫手山芋（空手互打传 TNT，回合结束爆炸淘汰） |
| `TntRun/` | 雪塔大作战（原 TNT Run：踩雪层层塌、挖雪得雪球砸雪块，命令 `/tr`） |
| `MurderMystery/` | 密室杀手（杀手铁剑 vs 无辜者攒金锭买武器） |
| `SkyPit/` | 天坑（主城内一块区域，走进去自动发装备并开 PvP，击杀排行） |
| `server/` | 服务端本体（Paper 26.2 + 自带 JDK 25 + 世界 + 插件数据）。**玩家记录只有一个文件**：`server/plugins/MiniGameLib/players.yml` |
| `.build/` | 构建输出 `out/`、历史备份 `backup/`、自检临时文件（整个目录可以删，会自动重建） |
| `docs/` | `开发须知.md`（规约+地图）、`已知坑.md`（症状→根因）、`玩家记录.md`、`改动记录.md` |
| `backups/` | 玩家记录快照 `players-<时间>.yml`（`pack-upload.ps1` / `players-backup.ps1` 自动生成） |
| `build.ps1` / `build.bat` | 一键编译全部插件（见下） |
| `paper-classpath.ps1` | 编译依赖清单（`build.ps1` 与 `check-stats.ps1` 共用，加依赖改这里） |
| `verify.ps1` | 上传前预检：YAML 语法 / 关键配置 / jar 指纹 / 嵌套目录 |
| `check-stats.ps1` | 玩家记录自检：老 `data.yml` → `players.yml` 迁移 + 合并写盘 |
| `pack-upload.ps1` | 打包上传（根=服务端根；自动快照并排除玩家记录） |
| `players-backup.ps1` | 玩家记录单独存档 / 恢复 |

> **为什么主城插件叫 HubCore 而不是 GameHub**：Bukkit 会给每个插件命令注册一个
> `插件名:命令名` 的命名空间命令（原来的 `gamehub:hub`），它在 tab 补全列表里紧挨着
> `gamemode`，用 `/gamemode` 补全时容易误选。改成不以 `game` 开头的名字就避开了。
> 插件间的接入标记也随之换成中性的 `minigame-game` / `minigame:`。

## 编译与部署

```powershell
# 只编译，产物在 .build\out\
.\build.ps1
# 编译并复制到 server\plugins\
.\build.ps1 -Deploy
# 只编一个插件（自动带上它依赖的 MiniGameLib / HubCore）
.\build.ps1 -Only TntRun
# 输出到别的目录（多个窗口同时排查时不打架）
.\build.ps1 -Only TntRun -OutDir .build\check-tntrun
```

也可以双击 `build.bat`。

- 用仓库自带 `server\jdk`（Java 25）编译，目标字节码 Java 21，编码固定 UTF-8。
- 依赖从本机 `%USERPROFILE%\.m2` 读取 Paper 26.2 API；**要加/改编译依赖只改 `paper-classpath.ps1`**
  （`build.ps1` 与自检脚本共用这一份清单）。
- 编译顺序即依赖顺序：`MiniGameLib` → `HubCore` → 各游戏插件，后面的插件直接引用前面的 class 输出。
- 各插件也保留了 Maven 工程（`mvn -DskipTests package`），但 Maven 构建需要先把 `MiniGameLib` 装进本地仓库。

> Windows PowerShell 5.1 只有在 `.ps1` **带 UTF-8 BOM** 时才按 UTF-8 读取，所以 `build.ps1`
> 刻意只用 ASCII 文本，中文说明放在本文件里。

## 启动服务端

```bat
server\start.bat
```

服务端配置要点（`server/server.properties`）：端口 `25560`、`online-mode=false`、`max-players=20`、
全局 `pvp=false`（各小游戏自己临时开启）、难度 `peaceful`。

Paper 26.2 把额外世界存成主世界的维度：`server/world/dimensions/minecraft/<世界名>`。

## 小游戏插件怎么接入

完整说明见 [HubCore/docs/游戏插件接入指南.md](HubCore/docs/游戏插件接入指南.md)，三步：

1. `plugin.yml` 写 `depend: [MiniGameLib, HubCore]`，并加 `minigame-game: true` + `minigame: {name, icon, color}` 让主城自动登记；
2. 编译期依赖 `MiniGameLib.jar`（provided，不打包进去）；`HubAPI` 包名仍是 `com.hub.api`；
3. 在三个时机调 API：进等待室 `HubAPI.setGame(...)`、正式开局 `HubAPI.setPlaying(...)`、
   结束/退出 `HubAPI.clearGame(...)`；场地状态变化再调 `HubAPI.setArena(...)`；
   另外用 `HubAPI.registerLobbyHandler(...)` 接住玩家右键「回主城」道具的情况。

## 玩家记录（只有一个文件）

6 个小游戏（GTB / 单挑 / 烫手山芋 / TntRun / 密室杀手 / 天坑）的胜场、击杀、第一名次数、玩家名，
全部写在 **`server/plugins/MiniGameLib/players.yml`** 一个文件里（读写入口 `com.hub.data.PlayerStats`）：

```yaml
games:
  duel:   { wins: { per-arena: {...}, total: {...} } }
  gtb:    { firsts: {...} }
  ...
```

- 老版本散在各插件 `plugins/<插件>/data.yml` 的记录，**首次启动自动迁移**进这个文件（旧文件保留不删、之后不再读取）；
- `pack-upload.ps1` 打包时会自动把 `players.yml` 快照到 `backups\`，并**把它排除在压缩包之外**，
  所以上传服务端不会覆盖面板上的玩家记录（真要覆盖加 `-WithPlayers`）；
- 单独存档 / 恢复：`players-backup.ps1 -Action Save | List | Restore`；
- 改了玩家记录相关代码后跑 `check-stats.ps1` 自检。

详见 [docs/玩家记录.md](docs/玩家记录.md)。

## 开发注意（多游戏共用一个服务端时的两个坑）

> 完整版（症状 → 根因 → 怎么确认，共 13 条）见 [docs/已知坑.md](docs/已知坑.md)。下面是两条最容易踩的。

**1. 事件监听必须限定自己的世界 / 场地，尤其是同优先级（HIGHEST）的拦截。**
Bukkit 在**同一优先级**下按"插件注册顺序"调用监听器，而注册顺序取决于插件 jar 的读取顺序 ——
**Windows 本地和 Linux 面板不一样**，于是会出现"本地好好的、传上去就坏"的诡异现象。

真实案例：建筑猜猜乐的爆炸保护原本无条件取消**所有世界**的 `EntityExplodeEvent`，
而单挑（Duel）也在 HIGHEST 上放行风弹爆炸 → 谁后注册谁赢 → 本地能击飞、面板不能。
**写法要求**：动手前先判断"这个爆炸/破坏/伤害是不是发生在我自己的世界里"，
不是就直接 `return`，别去碰别人的事件：

```java
@EventHandler(priority = EventPriority.HIGHEST)
public void onExplode(EntityExplodeEvent event) {
    if (!inArenaWorld(event.getLocation())) return;   // 不是我的世界 → 不插手
    ...
}
```

**2. 换服 / 上传服务端：先停服，再覆盖，且不能只传 `plugins/*.jar`。**
`MurderMystery / SkyPit / TntRun / DuelArena / HotPotato` 都会在 `onDisable` 里
`saveAll()` 把自己的内存状态写回配置文件，运行中上传会被旧实例在关服时覆盖掉。

需要一起传的内容（`pack-upload.ps1` 就是按这个清单打包的）：

```
plugins/                 jar + 各插件数据（config.yml / data.yml / words.yml / arena-*.snap）
                         + plugins/MiniGameLib/players.yml（唯一玩家记录；默认排除出压缩包，见上）
config/                  paper-global.yml + paper-world-defaults.yml ★容易漏
server.jar  server.properties  bukkit.yml  spigot.yml  eula.txt
world/                   只在你改过地图/存档时才传
```

另外还有几处"状态不在 plugins 里"的地方，排查"本地行、面板不行"时要想到：
世界存档里的 gamerule（`pvp` / `mob-griefing`）、各世界的 `paper-world.yml`、
以及计分板队伍（`allowFriendlyFire=false` 的队伍会让爆炸不推人）。

## 权限

每个插件都声明了 `<插件>.use` 与 `<插件>.admin`，代码里的判断是
**OP 或持有对应 `.admin` 权限节点**，因此可以直接用 LuckPerms 之类授权：

| 权限节点 | 作用 |
| --- | --- |
| `gtb.admin` / `duel.admin` / `hp.admin` / `tr.admin` / `mm.admin` / `skypit.admin` | 对应插件的管理命令（建场、开始、改名、重载、清战绩等） |
| `gameworlds.admin` | `/gw` 多世界管理 |
| `hub.admin` | `/hub` 管理子命令 |

除 OP 外，`hp.admin` 这类节点还额外允许「开始游戏」。
