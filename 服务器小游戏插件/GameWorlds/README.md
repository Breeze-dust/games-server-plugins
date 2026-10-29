# GameWorlds（多世界托管）

> 速查卡（AI 先读这里，别先读源码）

- **一句话职责**：仓库里**唯一的多世界管理插件**——启动时按配置创建/加载 `world_xxx` 并给每个世界套用规则（pvp/难度/固定时间/刷怪/天气/火蔓延/爆炸破坏/死亡不掉落）；它自己不传送玩家，玩家进服仍落在主城 `world`（`server.properties` `level-name=world`、HubCore `lobby.world: world`）。
- **入口**：`GameWorlds/src/main/java/com/gameworlds/GameWorldsPlugin.java#onEnable`（整个插件只有这一个类）——顺序：`saveDefaultConfig()` → `loadWorlds()`（遍历 `worlds` 段，跳过 `enabled: false`，逐个 `loadOrCreate()`：已存在就直接加载、不存在才 `WorldCreator` 建；随后 `applyRules()` 套规则，失败则 3 秒后重试一次）→ 注册 `/gw` 的 executor/tabCompleter → 打一行"已启用。托管世界：…"。
- **命令**：`/gw`（别名 `gworlds`、`world`）。`plugin.yml` 上命令级 `permission: gameworlds.admin`（`default: op`），所以**非 OP 连 `/gw list` 也用不了**（代码里 `list` 本身没做 OP 检查，是命令声明拦的）。
  - `/gw`（无参 = 帮助；非 OP 只见 `list` 一行）、`/gw list`（列所有世界 + `[托管]/[普通]`、pvp、难度、在线人数）
  - OP：`/gw tp <世界名>`、`/gw create <世界名> [void]`（默认超平坦；加 `void` = 完全没有方块的虚空世界；都会套默认规则并写盘）、`/gw delete <世界名> confirm`（卸载 + 删文件夹 + 删配置段，二次确认）、`/gw rules <世界名>`、`/gw global <规则> <值>`、`/gw reload`（重新加载并重新套规则）
  - `/gw global` 规则名（连字符/下划线可省）：`keepinventory`、`pvp`、`difficulty <PEACEFUL|EASY|NORMAL|HARD>`、`mob-spawning`、`weather-cycle`、`fire-spread`、`mob-griefing`、`fixed-time <数字|off>`；只有 `gameworlds.admin` 一个权限节点。
- **配置**：`server/plugins/GameWorlds/config.yml` 是**真实生效那份**（`GameWorlds/src/main/resources/config.yml` 只是模板）。顶层 `worlds.<世界名>`，键：`enabled`、`environment`、`type`、`fixed-time`、`difficulty`、`pvp`、`mob-spawning`、`weather-cycle`、`fire-spread`、`mob-griefing`。线上 5 个世界全部 `enabled: true`、`environment: NORMAL`、`type: FLAT`、`fixed-time: 6000`、`difficulty: NORMAL`、`mob-spawning/weather-cycle/fire-spread/mob-griefing: false`；`pvp` 仅 `world_duel`、`world_mm` 为 **true**，`world_gtb/world_hp/world_tr` 为 false。线上没写的键走代码默认：`keep-inventory: true`、`mob-loot: false`、`ANNOUNCE_ADVANCEMENTS=false`。`type` 还支持 `VOID`（空层超平坦 + `the_void` 生物群系，生成后没有任何方块；只对**尚未生成**的世界生效）。
- **世界清单**（存档在 `server/world/dimensions/minecraft/`）：`world_gtb` = GuessTheBuild（建筑猜猜乐）、`world_duel` = DuelArena（单挑）、`world_hp` = HotPotato（烫手山芋）、`world_tr` = TntRun（雪塔大作战）、`world_mm` = MurderMystery（密室杀手）。SkyPit（天坑）不托管（其 `server/plugins/SkyPit/config.yml` 里 `region.world: world`，用主城世界）。GTB/Duel/HotPotato/MurderMystery/TntRun 的 `plugin.yml` 都 `softdepend: [GameWorlds]`，SkyPit 没有。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 改某世界的 pvp/刷怪/时间等规则 | `server/plugins/GameWorlds/config.yml`（改完 `/gw reload` 或重启；也可用 `/gw global` 自动写回） |
  | 改规则**默认值**或新增规则 | `GameWorlds/src/main/java/com/gameworlds/GameWorldsPlugin.java`（`applyRules` / `applyRule`） |
  | 改模板、命令/权限声明 | `GameWorlds/src/main/resources/config.yml`、`GameWorlds/src/main/resources/plugin.yml` |
  | 世界存档本体（gamerule 落盘处） | `server/world/dimensions/minecraft/<世界名>/` |
  | Paper 世界级默认（爆炸击退等） | `server/config/paper-world-defaults.yml` |
- **常见坑**：① 世界 gamerule 存在**世界存档里**，而 `applyRules` **每次启动都按 `GameWorlds/config.yml` 重新套用**——手工 `/gamerule` 或游戏内临时 `setPVP` 会被下次启动覆盖，要持久就必须落到配置（单挑世界必须 `pvp: true`）。② 源码 `resources/config.yml` 与线上那份是两套，改规则**两边一起改**（`/gw global` 会 `saveConfig()` 直接改线上那份）。③ `type`/`environment` 只在**首次生成**生效，已有存档直接加载（改类型不会重建地形）。④ `plugin.yml` 故意不写 `load: STARTUP`（Paper 26.2 该阶段禁止建世界），靠 `softdepend` 保证它先于游戏插件加载——别为了"更早"加回去。⑤ 世界文件夹是 `server/world/dimensions/minecraft/<名>`，不是 `server/<名>`。⑥ `/gw delete` 真删存档不可恢复，世界里有人时拒绝；非托管世界（主城等）拒绝删。
- **验证方法**：游戏里 `/gw list`（每行形如 `[托管] world_duel (pvp=true 难度=NORMAL 玩家=0)`）与 `/gw rules world_duel`；控制台启动日志实测每世界一行 `[GameWorlds] 世界 world_duel 就绪：pvp=true 难度=NORMAL 怪物=false`，随后 `[GameWorlds] GameWorlds 已启用。托管世界：world_gtb, world_duel, world_hp, world_tr, world_mm`。
- **未确认 / TODO**：① `server/world/dimensions/minecraft/` 下还有 `0BB-Candyland`、`0MM-SantaFactory`、`0Void World` 三个世界，未在任何插件 yml 里被引用，来源与是否还需要**未确认**。② `/gw create` 的世界名校验 `name.matches("[^a-z0-9_\\-]")` 实际只拦住"单字符非法名"，`Bad Name!` 之类能通过——**疑似 bug，未改**。③ `plugin.yml` 的 `usage` 只写了 `<list|tp|create|rules|reload>`，漏了 `delete`/`global`。④ 未确认是否有 `environment` 非 `NORMAL` 的托管世界（线上全 NORMAL）。
