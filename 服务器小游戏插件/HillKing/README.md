# HillKing（高台之王）—— 速查卡

> AI 先读这一段，别先读源码

- **一句话职责**：所有人站在一块**圆形红色羊毛高台**上，**每站 1 秒 +1 分**；离开高台不得分。每人手里一把**咸鱼**（默认击退 II，可配），左键把别人拍下台；掉下台会被自动送回台上，但掉台期间没有分。限时（默认 60 秒）结束后**分数最高者获胜**，同分并列。命令 `/hill`（别名 `/king`、`/koth`、`/hillking`），显示名 `高台之王`。
- **规则**：
  - 开局至少 `min-players`（默认 2），最多 `max-players`（16）；等待倒计时用公共库 `WaitCountdown`，OP 在队列时暂停自动开局（右键开始纸或 `/hill start`）。
  - 开局前有公共库 `StartCountdown` 的 3 秒 title 倒计时（`countdown.*`，街机派对里自动跳过），倒计时结束后才开始计时。
  - **得分**：每秒判定一次，玩家 XZ 在高台圆内且高度贴着台面（跳起来也算）→ `points-per-second`（默认 1）分；站在地面/台外不得分。
  - **咸鱼**：`fish.material`（默认 `COD`），名字 `fish.name`，附魔显示 `fish.knockback-level`（默认 2）。命中**只击退不掉血**：水平 = `horizontal-per-level × 等级`（默认 0.6×2 = 1.2），垂直 = `vertical-per-level × 等级`（默认 0.25×2 = 0.5）；`fish.cooldown-seconds` 控制两次有效拍击的间隔。开局会给场地世界临时打开 PvP（结算还原），否则近战事件不触发。
  - **掉台**：掉下高台后**留在下面、不会自动传送回台上或等待点**，掉台期间不得分（想回到台上要自己走/搭路上去）；只有真的掉到世界底部（虚空）才送回等待点，避免一直下坠。**没有按“离台多远”触发的传送**。
  - **结算**：按分数从高到低取前三档（多人同分一起拿同一名次），分别 +3/+2/+1 积分并记录 `firsts/seconds/thirds`（`games.hillking`）；没人得分则本局无人获奖。
  - 场内玩家无伤、不能破坏/放置方块、不能丢东西、不能饿。
- **命令**：`/hill`（别名 `/king`、`/koth`、`/hillking`）
  - 玩家：`join`、`leave`、`list`、`stats [玩家]`、`help`
  - OP：**`build [半径] [台高]`**（站在地面上，台面会自动抬到你头顶上方 `台高` 格，一键建圆形红羊毛高台 + 自动生成出生点 + 保存）、`setlobby`、`addspawn`、`clearspawns`、`genspawns [数量]`、`save`、`start`、`stop`、`reload`、`stats reset`
- **配置**：`server/plugins/HillKing/config.yml`
  - `min-players` / `max-players` / `game-seconds`（默认 60）/ `points-per-second`（默认 1）
  - `fish.*`：材质/名字/lore/击退等级/每级力度/冷却
  - `wait-items.return|start.*`：等待室床和开始纸的材质/名字/lore/槽位
  - `sidebar.enabled/title`：右侧实时积分榜
  - `arena.*`：`radius`（默认 12）/ `height`（台面比脚下高几格，默认 3）/ `depth`（台面下方石头层数，默认 3）/ `platform-material`（默认 `RED_WOOL`）/ `base-material`（默认 `STONE`）/ `lobby` / `spawns`
- **建场步骤（OP，一键）**：
  1. 找一块空地，站在地上执行 `/hill build 12 3`（半径 12、台面抬高 3 格；不写参数用配置里的 `arena.radius` / `arena.height`）。会自动：清掉上一版台子残留 → 在头顶上方铺圆形红色羊毛台 + 下方 `arena.depth` 层石头底座 → 清空上方 6 格 → 生成 `max-players` 个出生点 → 保存 → 把你送到台面上。
  2. 站到场外执行 `/hill setlobby`。
  3. `/hill list` 显示「已配置」；`/hill start` 测试。
  - 台子是**圆形**（按真实圆逐行算半宽，四个方向对称）；半径 4~64，台高 0~32（0 = 与脚下齐平，底座厚度仍按 `arena.depth`）。
- **街机派对**：已接入本局池（key `hillking`，命令 `hill join` / `hill start`，`max-players: 16`，非多场地）。派对准备阶段结束后直接开局（不重复播开局倒计时）。
- **HubCore 菜单**：插件带 `minigame-game: true`，启动后自动登记到 tab/播报；要摆进指南针菜单，在 HubCore 的 `games` 段加 `name: 高台之王` / `icon: COD` / `color: '6'` / `command: hill join` / `join-prefix: hill join`，再用 `/hub menu set <槽位> COD game:高台之王 &6&l高台之王`。
- **验证**：
  1. `/hill build 8 3` → 脚下出现圆形红羊毛高台，`/hill list` 显示半径/台高/出生点数正确；`/hub games` 有 **高台之王**。
  2. 2 人 `/hill join` → OP 右键开始纸 → 3 秒 title 倒计时 → 站在台上每秒 +1，走下台后不再加分。
  3. 手持咸鱼左键拍对方 → 对方被水平击飞但不掉血；对方掉下台后**留在下面、不再得分**（不会自动回台上，除非掉进虚空才会被送回等待点）。
  4. 60 秒结束 → 按分数播报前三名（同分并列）并 +3/+2/+1；`/hill stats` 能查到积分与名次。
  5. 派对里抽到高台之王 → 能正常加入、直接开局、回报名次。
