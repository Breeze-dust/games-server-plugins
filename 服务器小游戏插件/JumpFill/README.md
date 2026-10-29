# JumpFill（跳水填格）—— 速查卡

- **一句话职责**：多名玩家从高台跳进一格高的水池，落点水块替换成该玩家颜色的彩色混凝土并 +1 分；整片水域全部变混凝土时结束，按积分排名。单人场地，最多 16 人（16 种混凝土色）。对局中玩家隐身 + 发光轮廓（不挡视线），结算/离开恢复原状态。
- **入口 / 主流程**：`JumpFillPlugin#onEnable` → `JumpFillManager#load`（读 `arena` 配置 + 冠军记录）→ `/jump join` 加入等待（管理员拿"开始游戏"纸）→ `WaitCountdown` 自动开局（OP 在队列则等手动右键纸或 `/jump start`）→ `#startGame`（`arena.fillWater()` 刷水 + 全员平均分配高台）→ `#onMove` / 每秒 20 次兜底扫描落水判定（水→混凝土、+1 分、剩余格 -1；跳偏→无分传回）→ 每次回高台轮到下一个出发点 → `#endGame`（排名/冠军记录）→ 3 秒后恢复背包、回主城、`arena.restore()` 恢复开局前水域。
- **命令**：
  - 玩家：`/jump join`、`/jump leave`、`/jump list`、`/jump stats [玩家]`、`/jump help`
  - 管理（OP 或 `jumpfill.admin`）：`/jump setwater 1|2`（站在水域两个对角，Y 必须相同）、`/jump addstart`（站在高台出发点）、`/jump clearstarts`、`/jump setlobby`、`/jump save`、`/jump start`、`/jump stop`、`/jump reload`
- **配置**：`server/plugins/JumpFill/config.yml`（模板同路径 `src/main/resources/config.yml`）
  - `min-players`（默认 2）、`max-players`（默认/上限 16）、`respawn-delay-ticks`（落水/跳偏回高台延迟）、`end-delay-ticks`（结算停留）
  - `arena.world`、`arena.water-pos1/2`、`arena.lobby`、`arena.start-points`（用命令设置后 `/jump save` 写入，不要手写坐标）
- **玩家记录**：命名空间 `jumpfill`，`games.jumpfill.firsts.<uuid>`（冠军次数），唯一文件 `server/plugins/MiniGameLib/players.yml`。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 等待/开局/落水判定/结算 | `JumpFill/src/main/java/com/jumpfill/JumpFillManager.java` |
  | 水域范围、水快照、位置字符串 | `JumpFill/src/main/java/com/jumpfill/JumpFillArena.java` |
  | 命令 / 权限 / tab 补全 | `JumpFill/src/main/java/com/jumpfill/JumpFillCommand.java` |
  | 事件入口（移动/免伤/水流/退出/重生） | `JumpFill/src/main/java/com/jumpfill/JumpFillListener.java` |
  | 玩家状态备份与恢复 | `JumpFill/src/main/java/com/jumpfill/PlayerState.java` |
  | 显示名 / 图标 / 颜色（`minigame:` 段） | `JumpFill/src/main/resources/plugin.yml` |
- **注册名同步**：`JumpFillManager.GAME`（`跳水填格`）＋ `plugin.yml` 的 `minigame.name`；HubCore 会自动扫描 `minigame-game: true` 登记到 tab。
- **落水判定**：看玩家中心所在**水位方块**（`waterY` 那一层）是不是水域水格，并要求玩家 Y 已经落到水面以下（水源顶面约 +0.875，阈值 +0.9；站在池边 +1.0 不算）。这样水面上方坠落不会提前生成混凝土，正常跳入/走入水中都会计分。开局后每 tick 兜底扫描一次。
- **走下高台提醒**：通过“上一 tick 在地面、这一 tick 离地且 Y 没有上升”识别为走下高台（不是跳跃），动作栏提示“请按跳跃键跳下去（走下去不算跳水）”，每次落下只提醒一次。
- **单局倒计时**：`config.yml` 的 `game-seconds`（默认 180，0 = 不限时）；每秒动作栏显示剩余时间/剩余水格，30/10 秒公屏提示，时间到按当前得分结算。
- **出发点**：开局按人数平均分配；每次落水/跳偏回高台自动轮到下一个出发点（1→2→3→4→1…），每人独立循环。
- **管理员开始道具**：加入等待队列时，有 `jumpfill.admin` 权限的玩家会在第 5 格拿到"开始游戏"纸，右键立刻开局（不可丢弃）。
- **对局内隐藏**：开局后参赛者身体隐身 + 发光轮廓（能看到位置/剪影，不挡视线），并用计分板队伍 `jumpfill_hide`（`NAME_TAG_VISIBILITY=NEVER`、`COLLISION_RULE=NEVER`）隐藏头顶 ID/头衔；结算/离开时移除队伍、恢复原隐身/发光状态。`refreshTitle` 会通过反射调用 PlayerTitles 的 `refresh` 把头顶头衔加回来（旧版没有该方法时退回世界切换刷新）。
- **注意**：
  - 水域必须是**一格高**：两个角的 Y 不同会拒绝保存/开局。
  - 填掉的格子用彩色混凝土替换，插件会取消水域内的水流动，避免邻格水重新灌满。
  - 玩家在游戏内免伤/免饥饿；跳偏/掉出池子自动回高台，不会淘汰。
  - 没有设置快照时，第一次开局会自动记录水域原始方块到 `plugins/JumpFill/arena.snapshot.yml`，结算后恢复并删除。
- **验证方法**：
  1. 建好水池（1 格高）与至少 2 个高台出发点（越高越好），`/jump setwater 1`（水池一角）→ `/jump setwater 2`（对角）→ 站在每个高台上 `/jump addstart` → `/jump setlobby` → `/jump save`。
  2. `/jump list` 应显示 `水域 WxD (N 格) | 出发点 K 个`；`/hub games` 应出现“跳水填格”。
  3. 两名玩家 `/jump join` → 倒计时或 `/jump start` → 全员传高台；跳进水池后该格变成自己的颜色，动作栏 +1，剩余格减少；跳偏无分回高台。
  4. 填满后广播排名，冠军写 `players.yml` 的 `jumpfill.firsts`，全员回主城，水域恢复原样。
