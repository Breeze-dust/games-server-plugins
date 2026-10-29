# MiniGameLib（公共库）

> 速查卡（AI 先读这里，别先读源码）

- **一句话职责**：小游戏公共库——只提供主城对接契约（HubAPI）、消息（Chat）、建场模式（SetupMode）、等待倒计时（WaitCountdown）与全服唯一玩家记录（PlayerStats）；自身不注册命令、不含玩法代码。
- **对外 API**：
  - `com.hub.api.HubAPI`（静态门面）：游戏插件向主城上报玩家状态 / 场地状态、公屏"加入"播报、回主城联动时用它；关键方法 `setGame` / `setPlaying` / `setGameState` / `clearGame` / `setArena` / `removeArena` / `announceJoin`（两参数版 = 只报游戏名；三参数版多带场地名，播报成 `[游戏][场地] 玩家 加入了游戏`，多场地游戏用） / `registerLobbyHandler`（另有 `register` / `isReady` / `getLobby` 等）；主城缺席时全部空操作。
  - `com.hub.api.HubService`：HubCore 侧实现、在它 `onEnable` 里 `HubAPI.register(...)` 接进来的接口；小游戏只调 HubAPI，不要实现它。
  - `com.hub.api.GameState`：玩家/场地状态枚举，只有 `WAITING`（等待中）与 `PLAYING`（已开始）。
  - `com.hub.util.Chat`：所有文字与颜色统一走它；`send` / `broadcast` / `title` / `actionBar` / `color` / `toLegacy`（`&` 与 `§` 都认）。
  - `com.hub.util.SetupMode`：OP 建场模式（备份并清空背包 → 发带 PDC 标记的工具 → 退出还原）；每个插件 new 一个实例，用 `enter` / `exit` / `handleInteract` / `isEditing` / `isTool` / `exitAll`，专属工具回调走 `Tool` + `Handler`。
  - `com.hub.util.WaitCountdown`：等待室自动开局倒计时（队列有 OP 暂停、<min 取消、≥max 3 秒、达标未满 30 秒、加退人不重置）；每场地一个实例，插件实现 `Host` 后调 `update` / `cancel`。
  - `com.hub.data.PlayerStats`：玩家记录的唯一入口；`load(命名空间, 旧data.yml)` / `save(命名空间, 视图)` / `file()` / `summary()` / `playerCounts()`。
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
  | 等待室自动开局规则 | `MiniGameLib/src/main/java/com/hub/util/WaitCountdown.java` |
  | 建场模式工具、背包备份与还原 | `MiniGameLib/src/main/java/com/hub/util/SetupMode.java` |
  | 向主城上报玩家/场地状态、回主城联动 | `MiniGameLib/src/main/java/com/hub/api/HubAPI.java`（实现方 `MiniGameLib/src/main/java/com/hub/api/HubService.java`） |
  | 玩家记录读写与命名空间 | `MiniGameLib/src/main/java/com/hub/data/PlayerStats.java` |
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
- **验证方法**：`powershell -ExecutionPolicy Bypass -File D:\BuildBattle\check-stats.ps1`（玩家记录自检：迁移 / 合并保存 / 未知命名空间；需先跑过一次 `build.ps1` 生成 `.build\out\MiniGameLib.classes`）+ 启动日志里 `[MiniGameLib]` 那几行（"MiniGameLib 已启用…" 与 "玩家记录 <路径> -> duel=…, gtb=…"）
- **未确认 / TODO**：
  - `MIGRATION.md` 第 5 节让自检产物放 `.build\check-<插件名>`，但结尾说删 `build\check-<插件名>`（少一个点），未核实是否笔误。
  - 本卡只覆盖 `MiniGameLib/` 自身；各游戏侧怎么调这些 API 请看对应插件 README 的速查卡。
