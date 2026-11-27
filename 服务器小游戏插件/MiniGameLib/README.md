# MiniGameLib（公共库）

> 速查卡（AI 先读这里，别先读源码）

- **一句话职责**：小游戏公共库——只提供主城对接契约（HubAPI）、消息（Chat）、建场模式（SetupMode）、等待倒计时（WaitCountdown）、队伍语音（VoiceChat）与全服唯一玩家记录（PlayerStats）；自身不注册命令、不含玩法代码。
- **对外 API**：
  - `com.hub.api.HubAPI`（静态门面）：游戏插件向主城上报玩家状态 / 场地状态、公屏"加入"播报、回主城联动时用它；关键方法 `setGame` / `setPlaying` / `setGameState` / `clearGame` / `gameOf`（查玩家当前上报的游戏名，不在游戏中为 null） / `setArena` / `removeArena` / `announceJoin`（两参数版 = 只报游戏名；三参数版多带场地名，播报成 `[游戏][场地] 玩家 加入了游戏`，多场地游戏用） / `registerLobbyHandler`（另有 `register` / `isReady` / `getLobby` 等）；主城缺席时全部空操作。
  - `com.hub.api.HubService`：HubCore 侧实现、在它 `onEnable` 里 `HubAPI.register(...)` 接进来的接口；小游戏只调 HubAPI，不要实现它。
  - `com.hub.api.GameState`：玩家/场地状态枚举，只有 `WAITING`（等待中）与 `PLAYING`（已开始）。
  - `com.hub.api.GameTitles`：小游戏发头衔的桥；`grant(playerId, titleId)` / `grantOrCreate(playerId, titleId, 名称, 颜色, 描述)` / `grantOrCreateHidden(...)`（隐藏头衔，未拥有者看不到）/ `ensure(titleId, 名称, 颜色, 描述)` / `ensureHidden(...)`（启用时先建好定义，管理菜单立刻能看到，不发给任何人）/ `owns(playerId, titleId)`。反射调头衔插件的 `com.example.playertitles.api.TitleProvider`，小游戏只依赖 MiniGameLib；没装头衔插件或版本旧时全部返回 false、不抛异常。返回 true = 玩家本次新获得（可用来决定是否弹提示）。
  - `com.hub.util.Chat`：所有文字与颜色统一走它；`send` / `broadcast` / `title` / `actionBar` / `color` / `toLegacy`（`&` 与 `§` 都认）。
  - `com.hub.util.SetupMode`：OP 建场模式（备份并清空背包 → 发带 PDC 标记的工具 → 退出还原）；每个插件 new 一个实例，用 `enter` / `exit` / `handleInteract` / `isEditing` / `isTool` / `exitAll`，专属工具回调走 `Tool` + `Handler`。
  - `com.hub.util.WaitCountdown`：等待室自动开局倒计时（队列有 OP 暂停、<min 取消、所有等待玩家都点了准备 → 3 秒、≥max 3 秒、达标未满 30 秒、加退人不重置）；每场地一个实例，插件实现 `Host` 后调 `update` / `cancel`。
  - `com.hub.util.ReadyItems`：「准备」道具本身（槽位 2，灰色染料=未准备、青柠染料=已准备）与全局右键监听。游戏在发等待物品时调 `countdown.giveReadyItem(player)` 即可；点击后由 `WaitCountdown#toggleReady` 切换并触发 `update()`（全员准备→3 秒倒计时）。街机派对接管期间（`WaitCountdown.isGloballySuppressed()`）不发放；**不在派对小游戏池里的常驻游戏**（如 LiarBar 牌桌）构造实例后接 `.exemptFromGlobalSuppression()` 声明豁免。
  - `com.hub.util.LiveSidebar`：游戏内的实时侧边栏（存活名单 / 回合 / 分数）；每秒 `update(key, 标题, 参赛者, 行列表)`。**会自动把「离参赛者 96 格内、正在主城观战」的玩家也切到这块板子上**，观战者因此看到和局内一样的信息；退出观战/离开场地自动还原主计分板。
  - `com.hub.util.SpectatorHud`：自建 HUD（自己的计分板 / BossBar）的游戏用它接观战者——周期性 `viewers(key, 参赛者, 半径)` 拿「参赛者 + 附近观战者」，结束后 `restore(key)` 还原计分板；底层是 `HubAPI.spectatorsNear(参赛者, 半径)` + `HubAPI.isSpectating(id)`。
  - `com.hub.data.PlayerStats`：玩家记录的唯一入口；`load(命名空间, 旧data.yml)` / `save(命名空间, 视图)` / `file()` / `summary()` / `playerCounts()`。
  - `com.hub.voice.VoiceChat`：Simple Voice Chat 队伍语音门面。`available()` / `createIsolatedGroup(名字)`（互相听不到的隔离组，返回组 id）/ `join(玩家UUID, 组id)` / `leave(玩家UUID)` / `deleteGroup(组id)`。**服务器没装 voicechat 插件时全部是空操作**，小游戏不用判断；没装客户端模组的玩家也不会被踢，只是听不到。SVC 的组可以被玩家自己退出，门面每秒对账一次把玩家拉回原组。
    - **初始化时序**：SVC 在 onEnable 只登记插件，`initialize()` 要等服务端启动后的下一个 tick 才回调，所以 `available()` 在刚开服时可能是 false；门面会每秒重试，就绪后打日志「已接入 Simple Voice Chat，队伍语音功能可用」。小游戏在「开局」时判断 `available()` 即可（服务端启动到开局早已就绪）。
    - **距离**：SVC 的语音距离（`plugins/voicechat/voicechat-server.properties` 的 `max_voice_distance`）是全局设置，不能按世界改。想「同队不受距离影响」就建隔离组（组内语音不看距离）；想「受距离影响」就不建组、用原版接近语音。是否这样做的开关放在**各游戏自己的 config.yml** 里（如 TNTBattle 的 `voice-chat.distance-based`），所以每个游戏可以不一样。
- **入口**：`MiniGameLib/src/main/java/com/hub/MiniGameLibPlugin.java#onEnable`（只打两行启动日志；`onDisable` 调 `SetupMode.exitAllRegistered()` 还原还停在建场模式里的 OP 背包）
- **命令**：无
- **配置**：无 config.yml（`src/main/resources/` 下只有 `plugin.yml`，且它不注册任何命令）
- **玩家记录**：`com.hub.data.PlayerStats` 是唯一读写入口；文件 `server/plugins/MiniGameLib/players.yml`，命名空间 `duel/gtb/hotpotato/tntrun/murdermystery/skypit/football`；详见 `docs/玩家记录.md`（命名空间与键结构表、迁移、备份/恢复命令）。
  - 写盘语义：`save` 先读盘再只替换自己那块（原子替换），旧 `plugins/<插件>/data.yml` 首次加载自动迁移（旧文件保留）；6 个小游戏的 Manager 现在都走 `PlayerStats.load/save`。
  - 备份/上传：`pack-upload.ps1` 默认把该文件排除在压缩包外（快照到 `backups\players-<时间>.yml`），`-WithPlayers` 才覆盖面板记录；恢复用 `players-backup.ps1 -Action Restore`（`java.exe` 在跑时会拒绝执行）。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 提示文字 / 颜色 / 标题 / 动作栏 | `MiniGameLib/src/main/java/com/hub/util/Chat.java` |
  | 等待室自动开局规则（含准备道具的自动开局） | `MiniGameLib/src/main/java/com/hub/util/WaitCountdown.java` |
  | 准备道具的外观 / 槽位 / 右键处理 | `MiniGameLib/src/main/java/com/hub/util/ReadyItems.java` |
  | 建场模式工具、背包备份与还原 | `MiniGameLib/src/main/java/com/hub/util/SetupMode.java` |
  | 向主城上报玩家/场地状态、回主城联动 | `MiniGameLib/src/main/java/com/hub/api/HubAPI.java`（实现方 `MiniGameLib/src/main/java/com/hub/api/HubService.java`） |
  | 小游戏满足条件后给玩家发头衔 | `MiniGameLib/src/main/java/com/hub/api/GameTitles.java`（头衔内容在调用方；已接入示例见 `MurderMysteryManager#grantNoKnifeTitle`） |
  | 玩家记录读写与命名空间 | `MiniGameLib/src/main/java/com/hub/data/PlayerStats.java` |
  | 队伍语音（建组/拉人/对账） | `MiniGameLib/src/main/java/com/hub/voice/VoiceChat.java`（SVC 实现在同目录 `SvcBackend.java`） |
  | 启动日志、关服兜底 | `MiniGameLib/src/main/java/com/hub/MiniGameLibPlugin.java` |
  | 插件元信息（是否注册命令） | `MiniGameLib/src/main/resources/plugin.yml` |
  | 迁移做法与各 API 用法示例 | `MiniGameLib/MIGRATION.md` |
- **改动影响面**：改这里 → 必须 `build.ps1 -Deploy` 重传全部 9 个 jar（`MiniGameLib` / `HubCore` / `GameWorlds` + 6 个小游戏）。原因：HubCore 与 6 个小游戏的 `plugin.yml` 都是 `depend: [MiniGameLib, …]`，并且都以 `provided` 编译（不打进各自 jar），运行期由服务器加载 MiniGameLib 里的类——新旧混跑会签名/行为不一致。（`GameWorlds` 并未 depend MiniGameLib，`-Deploy` 只是整体复制。）
- **常见坑**：
  - 只重传 `MiniGameLib.jar` 不够，见上一条。
  - `SetupMode` 工具最多 9 件（只放快捷栏 0–8）；认识但点错键（如右键型工具被左键点）时交互会被吞掉却不做任何事。
  - `SetupMode` 没有"在配置模式里给场地改名"的 API；需要就自己让 handler 持有场地对象。
  - HubAPI 在主城未启用/缺席时全是空操作、不抛异常——"调了没反应"先查 HubCore 有没有在跑。
  - `Chat` 颜色统一用 `&`（`§` 会被换算），不要再自己写 `LegacyComponentSerializer`。
  - `players.yml` 的层级 `games.<命名空间>` 是对外契约：不要手改层级，也不要在 `PlayerStats.save()` 之外直接写这个文件（会覆盖别人那块）。
- **验证方法**：`powershell -ExecutionPolicy Bypass -File D:\BuildBattle\check-stats.ps1`（玩家记录自检：迁移 / 合并保存 / 未知命名空间；需先跑过一次 `build.ps1` 生成 `.build\out\MiniGameLib.classes`）+ 启动日志里 `[MiniGameLib]` 那几行（"MiniGameLib 已启用…" 与 "玩家记录 <路径> -> duel=…, gtb=…" 与 "已接入 Simple Voice Chat…" / "未安装 Simple Voice Chat…"）
- **未确认 / TODO**：
  - `MIGRATION.md` 第 5 节让自检产物放 `.build\check-<插件名>`，但结尾说删 `build\check-<插件名>`（少一个点），未核实是否笔误。
  - 本卡只覆盖 `MiniGameLib/` 自身；各游戏侧怎么调这些 API 请看对应插件 README 的速查卡。
