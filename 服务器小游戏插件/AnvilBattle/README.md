# AnvilBattle（铁砧掘战）—— 速查卡

> AI 先读这一段，别先读源码

- **一句话职责**：在一个悬空场地上，天上每隔几秒随机落一批**铁砧**；铁砧落地会砸掉周围方块并继续往下钻一小段时间，被铁砧砸中或掉到 `void-y` 以下的人淘汰，**最后存活的人获胜**，记录**前三名**；每波砧量会随游戏时间递增。单场地，命令 `/anvil`（别名 `/ab`、`/anvilbattle`），显示名 `铁砧掘战`。
- **规则**：
  - 开局至少 `min-players`（默认 2），最多 `max-players`（12）；等待倒计时用公共库 `WaitCountdown`，OP 在队列时会暂停自动开局（右键开始纸或 `/anvil start`）。
  - 每 `anvil.interval-seconds`（默认 1 秒）落一波，每波初始 `anvil.amount`（默认 3）个铁砧；随游戏时间每 `anvil.ramp.interval-seconds`（默认 30 秒）增加 `anvil.ramp.amount-per-step`（默认 1）个，封顶 `anvil.ramp.max-amount`（默认 12）。`anvil.ramp.enabled: false` 关闭递增。
  - **玩家头顶定向落砧**：每波先按人逐个判定（顺序随机），概率从 `anvil.player-target.base-chance`（默认 0.15）起；判定通过就把这颗铁砧直接落到该玩家所在列的头顶，**落成后该玩家概率重置为 base-chance**；没落成则叠加 `anvil.player-target.chance-step`（默认 +0.10），封顶 `anvil.player-target.max-chance`（默认 0.75）。所以躲得越久，被针对的概率越高。定向砧与随机砧**共用本波名额**（`currentAnvilAmount()`），不会让每波总量变多；同一列本波已有铁砧时跳过、概率不变。`anvil.player-target.enabled: false` 关闭。概率状态在开局/淘汰/离场/结算时清空。
  - 落砧位置：在场地 X/Z 范围内随机选一列，**从圈地的最高 Y 往下找第一块不是空气、也不是液体的方块**（下界到 `void-y` 以上，所以站在平台表面圈地也能找到平台）；整列被挖空（只剩空气/液体）就跳过这一列。铁砧默认在最上方方块头顶 `anvil.spawn-height`（默认 12）格生成，自由落下；也可以用 `arena.anvil-y` / `/anvil sety [Y]` 指定**固定的起始 Y**（0 = 自动）。
  - 铁砧落地（`EntityChangeBlockEvent` 被取消，不会留下铁砧方块）时**默认只砸落点下方第一块实体方块**（`anvil.break-radius: 0`、`anvil.break-depth: 0`）；想砸 3×3 就把 `break-radius` 设 1、`break-depth` 设 2（半径 R 时是 (2R+1)×(2R+1) 格，深度 d 时砸 d+1 层）。只破坏场地范围内的方块，**基岩/屏障/液体不破坏**。第一次砸中方块后开始计时，铁砧会在坑底重新生成并**继续往下钻 `anvil.fall-after-break-seconds`（默认 2 秒）**，每次落地继续砸，到点消失；设 0 = 砸完立刻消失。落点没砸掉任何方块（已经是空气/液体）时不会原地反复生成。
  - 被铁砧砸中（实体碰撞或铁砧伤害事件）→ 淘汰；掉到 `arena.void-y` 以下 → 淘汰；其它伤害/摔落/饥饿全部取消。
  - 存活人数剩 1 → 该玩家获胜；时间到（`game-seconds`，默认 180）→ 仍存活的玩家共同获胜；全员淘汰 → 无人获胜。
  - **前三名**：获胜者并列第一名；被淘汰的按淘汰顺序倒序补：最后被淘汰的当第二名、倒数第二个被淘汰的当第三名。多于 1 个获胜者时名次顺延（例如 2 人并列第一，最后被淘汰的是第三名）。前三名分别加 **+3 / +2 / +1 积分**，同时累计 `firsts / seconds / thirds` 次数；写入 `players.yml` 的 `games.anvilbattle.points / firsts / seconds / thirds`，结算公屏会广播前三名。
  - 每局被铁砧破坏的方块会记录原始 BlockData，**局末/关服还原**；玩家不能破坏/放置方块、不能丢东西。崩服可能留下坑洞，需要手动修复。
- **命令**：`/anvil`（别名 `ab` / `anvilbattle`）
  - 玩家：`join`、`leave`、`list`、`stats [玩家]`、`help`
  - OP（`isOp()` 或权限 `anvil.admin`）：`setpos1`、`setpos2`（站在两个对角，X/Y/Z 都取）、`setlobby`、`addspawn`、`clearspawns`、**`genspawns [数量]`（自动生成出生点）**、`setvoid [Y]`（不写 Y 用当前高度）、**`sety [Y]`（铁砧起始 Y，0 = 自动）**、`save`、`start`、`stop`、`reload`、`stats reset`（清空积分与前三名记录）
- **配置**：`server/plugins/AnvilBattle/config.yml`（源码模板 `AnvilBattle/src/main/resources/config.yml`）
  - `min-players` / `max-players` / `game-seconds`
  - `anvil.interval-seconds` / `anvil.amount` / `anvil.spawn-height` / `anvil.break-radius` / `anvil.break-depth`
  - `anvil.ramp.enabled` / `anvil.ramp.interval-seconds` / `anvil.ramp.amount-per-step` / `anvil.ramp.max-amount`（随时间递增）
  - `anvil.player-target.enabled` / `base-chance` / `chance-step` / `max-chance`（玩家头顶定向落砧，概率可叠加）
  - `anvil.fall-after-break-seconds`（砸穿后继续下钻多久再消失；0 = 关闭）
  - `arena.pos1` / `arena.pos2` / `arena.lobby` / `arena.spawns` / `arena.void-y` / `arena.anvil-y`（用命令写，别手写坐标；`anvil-y: 0` = 自动）
- **建场步骤（OP，第一次上线）**：
  1. 在游戏世界搭一块**悬空平台**（下面就是虚空）；平台上方不要有屋顶/装饰，否则落砧会砸到它们。
  2. 站在平台两个对角：`/anvil setpos1`、`/anvil setpos2`。**X/Z 必须圈住平台**；Y 取你站的位置即可（落砧/破坏会一路找到 `void-y` 以上，不再要求把两个角的 Y 圈到平台最底层），但 `void-y` 必须设在平台下方。
  3. 站到平台外的安全位置：`/anvil setlobby`。
  4. **自动生成出生点**：`/anvil genspawns <数量>`（不写数量 = `max-players`）。它会清空旧出生点，在区域 X/Z 上按网格找每列**最高的非空气/非液体方块**，把出生点放在方块顶；洞/水的位置用随机列补齐。也可以继续用 `/anvil addspawn` 手动加。
  5. 站到平台下方虚空处：`/anvil setvoid <Y>`（或直接 `/anvil setvoid` 用当前高度）。需要固定落砧高度就再执行 `/anvil sety <Y>`（0 = 自动柱顶+spawn-height）。
  6. `/anvil save`（`genspawns` 会顺手保存）；`/anvil list` 应显示「已配置」和出生点数量。
  7. `/anvil start`（或右键开始纸）测试；`/hub games` 应出现 **铁砧掘战**。
  - 开局会把出生点洗牌后**尽量一人一个**（出生点不够才会复用）。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 落砧/破坏/淘汰/胜负/还原/战绩 | `AnvilBattle/src/main/java/com/anvilbattle/AnvilBattleManager.java` |
  | 场地范围/等待点/出生点/淘汰高度/topBlockY | `AnvilBattle/src/main/java/com/anvilbattle/AnvilBattleArena.java` |
  | 事件（伤害/铁砧落地/虚空/地图保护/等待室道具） | `AnvilBattle/src/main/java/com/anvilbattle/AnvilBattleListener.java` |
  | 命令/权限/tab | `AnvilBattle/src/main/java/com/anvilbattle/AnvilBattleCommand.java` |
  | 默认配置 | `AnvilBattle/src/main/resources/config.yml` |
  | 显示名/图标/颜色 | `AnvilBattle/src/main/resources/plugin.yml` 的 `minigame:` 段 |
- **常见坑**：
  - **铁砧砸不穿平台**：`void-y` 一定要在平台下方；1.0.3 起落砧/破坏不再受两个角的 minY 限制（会往下找到 void-y 以上），站平台表面圈地也能砸穿。默认只砸 1 格（`break-radius: 0`、`break-depth: 0`）；想 3×3 就把它们设成 `1` / `2`，并确认圈地 X/Z 至少 3 格。
  - **整列被挖空后不再落砧**：落砧前会从范围顶部往下找第一块非空气/非液体方块；整列只剩空气或水/岩浆时该列永远跳过。这是需求里的「检测该位置是否有方块，不检测液体」。
  - **平台上方别有屋顶**：topBlockY 会把屋顶当成最高方块，铁砧会落在屋顶上。场地要露天空旷。
  - **出生点不够会重叠**：用 `/anvil genspawns <数量>` 一次生成足够多，或 `addspawn` 多加几个；开局会洗牌后尽量一人一个。
  - **局末还原只在内存里记录**：正常结算/`/anvil stop`/关服会还原；崩服或杀进程会留下坑洞，需要手动修复。
  - **菜单入口**：HubCore 主菜单不会自动加。先在 HubCore 的 `games` 段加一条：`name: 铁砧掘战` / `icon: ANVIL` / `color: '7'` / `command: anvil join` / `join-prefix: anvil join`，然后用 `/hub menu set <槽位> ANVIL game:铁砧掘战 &7&l铁砧掘战` 摆一格（动作是一个 token，不能写成带空格的 `cmd:anvil join`；要执行命令可以先摆物品再用 `/hub menu action <槽位> cmd anvil join`）。
  - 本游戏不需要 PvP；场内所有伤害都被取消，别在世界规则里依赖 PvP。
- **验证方法**：
  1. `/anvil list` → 场地已配置、无 missing；`/hub games` 有 **铁砧掘战**。
  2. `/anvil genspawns 12` → 提示「自动生成 N 个并保存」，`/anvil list` 的出生点数量应增加；`server/plugins/AnvilBattle/config.yml` 的 `arena.spawns` 应出现 12 条左右（洞/水多或平台太小时会少于请求数并给出提示）。
  2. 2 人 `/anvil join` → OP 右键开始纸/`/anvil start`：两人被随机传送到平台出生点，背包清空。
  3. 等 1~2 秒：天上开始落铁砧；落点附近出现坑洞，**不会留下铁砧方块**；坑里的铁砧会继续往下钻约 `fall-after-break-seconds` 秒后消失；站在坑里的人被砸中 → 变旁观并广播「XX 被铁砧砸中」。
  4. 从坑里掉到 `void-y` 以下 → 变旁观并广播「XX 掉进虚空」。
  5. 最后一人存活 → 广播第一/第二/第三名，`/anvil stats <玩家>` 对应名次 +1；**场地坑洞全部恢复**，所有人回大厅。
  6. 等过 `anvil.ramp.interval-seconds` 秒 → 行动栏/`/anvil list` 的「每波 X 个」应变大，封顶不超过 `max-amount`。
  7. 故意在整列被挖空的柱子上等下一波 → 该列不应再落铁砧。
  8. 站在平台上不动观察几波：会看到铁砧有时**直接落到自己头顶**；躲过一个没落的波次后，之后被针对得越来越频繁（`chance-step` 叠加）。想确认就把 `base-chance` 设 0、`chance-step` 设 1.0，第二波必定砸头顶。
  9. 时间到仍有 2 人以上 → 共同获胜；`/anvil stop` 也能立即结束并还原。
  10. 重启服务器 → 配置/战绩保留；`/anvil list` 场地仍在。
- **未确认 / TODO**：
  - 铁砧砸方块的范围/层数手感（默认只砸 1 格）和每波数量（默认 3/秒）需要按实际场地大小调；大场地可调大 `anvil.amount` 或把 `break-radius`/`break-depth` 设成 1/2 做 3×3。
  - 铁砧从生成到落地的伤害事件/碰撞检测依赖 Paper 的 FallingBlock 行为，需进服实测；本地没有玩家只做了启动冒烟。
  - 崩服还原没有做快照，只有本局内存记录；需要的话可以后续加 `arena-*.snap`。
