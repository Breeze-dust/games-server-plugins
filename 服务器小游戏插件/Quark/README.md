# Quark（粒子枪战）

> 速查卡（AI 先读这里，别先读源码）

- **一句话职责**：自由混战枪战。开局每人发一把锄头当枪（PDC 标记，只认这把），**右键**沿准星发射一条直线粒子弹道；弹道被方块挡住就停下，命中玩家**直接击杀**，击杀者 +1 分；玩家可无限复活（随机出生点 + 1.5 秒无敌）；`game-seconds`（默认 90）到点后**击杀数最多者获胜**（并列第一一起播报）。左键不发射。
- **入口 / 主流程**：`Quark/src/main/java/com/quark/QuarkPlugin.java#onEnable` → `QuarkManager#load`（读 config 的 `arena` 与枪械参数）→ `/quark join` 进等待队列（`QuarkManager#join`）→ 公共库 `WaitCountdown` 自动倒计时 → `QuarkManager#startGame` 清背包、发枪、随机出生点、`HubAPI.setPlaying` → 每秒 `tickGame` 刷新动作栏 → `QuarkManager#shoot`（粒子弹道 + 命中判定）→ `QuarkManager#kill`（加分、播报、`setHealth(0)`）→ `QuarkManager#handleDeath` / `handleRespawn`（保留物品、自动复活）→ 时间到 `QuarkManager#endGame`（排名、冠军、全员回主城）。
- **关键文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 开局/复活/计时/射击/计分/排名 | `Quark/src/main/java/com/quark/QuarkManager.java` |
  | 右键开枪、免伤、禁破坏、退出清理 | `Quark/src/main/java/com/quark/QuarkListener.java` |
  | 场地（pos1/pos2/lobby/spawns）读写 | `Quark/src/main/java/com/quark/QuarkArena.java` |
  | `/quark` 子命令与提示文案 | `Quark/src/main/java/com/quark/QuarkCommand.java` |
  | 默认配置模板 | `Quark/src/main/resources/config.yml` |
  | 线上配置（真实生效） | `server/plugins/Quark/config.yml` |
- **命令**（主命令 `/quark`，别名 `/q`）：
  - 玩家：`/quark join`、`/quark leave`、`/quark list`、`/quark help`。
  - 管理员（OP 或 `quark.admin`）：`/quark start`（至少 1 人立即开）、`/quark stop`、`/quark setpos1`、`/quark setpos2`、`/quark setlobby`、`/quark addspawn`、`/quark clearspawns`、`/quark save`、`/quark reload`。
- **建场步骤**：
  1. 站到场地一个角：`/quark setpos1`；站到对角：`/quark setpos2`（只取 X/Z，Y 不限制；跑出这个范围会被传回出生点）。
  2. 站到等待位置：`/quark setlobby`。
  3. 站到出生位置：`/quark addspawn`（可多次，复活时随机选一个；**出生点必须在 pos1/pos2 范围内**）。
  4. `/quark save` → `/quark list` 检查。
  5. 可选：主城加菜单入口（选空槽，示例 33）：
     ```
     /hub menu set 33 IRON_HOE cmd:quark join &b&l粒子枪战
     /hub menu lore 33 &7右键发射粒子弹道，命中即击杀|&7限时击杀最多者获胜
     ```
- **配置**（`server/plugins/Quark/config.yml`，改完 `/quark reload` 或重启；对局中不能 reload）：
  - `min-players`（2）、`max-players`（16）、`game-seconds`（90）、`respawn-invulnerable-ticks`（30 tick = 1.5 秒，开局也套用）。
  - `gun.material`（IRON_HOE）、`gun.cooldown-seconds`（3.0，支持小数）、`gun.range`（64 格）、`gun.hit-radius`（0.45，玩家判定箱外扩）、`gun.particle-step`（0.25 格一个粒子）、`gun.particle`（Bukkit `Particle` 名，默认 `END_ROD`）、`gun.name` / `gun.lore`。
  - `arena.pos1` / `arena.pos2` / `arena.lobby` / `arena.spawns`（用命令写，不要手写）、`arena.void-y`（低于此高度传回出生点）。
- **常见坑**：
  - ① **队列里有 OP 时自动倒计时会暂停**（公共库 `WaitCountdown` 的约定），这是正常的；测试时用 `/quark start` 立即开。
  - ② 改 `gun.material` 后，旧枪的 PDC 还在但材质对不上，`isGun` 会失效；重开一局即可。
  - ③ 子弹是**纯粒子 + 射线判定**，没有实体；弹道每 `particle-step` 格生成一个粒子，服务端与客户端都会看到，服务器卡顿时粒子会稀疏。
  - ④ 对局中除了枪的一切伤害都被取消（摔落、火焰、近战、爆炸），玩家只能被枪击杀；血量/饥饿/经验在开局与复活时重置。
  - ⑤ 死亡采用真实死亡事件 + `spigot().respawn()` 自动复活，开了 `setKeepInventory(true)`，枪不会掉；复活后仍会清背包重新发枪。
  - ⑥ 场地边界与虚空兜底在 `PlayerMoveEvent` 里；跨世界移动会直接跳过（避免 `event.getFrom()` 还停在旧世界的距离计算异常）。
  - ⑦ 枪右键方块/实体时同一 tick 可能派发两个交互事件，用 `player.getTicksLived()` 去重，避免一枪打两发。
- **验证方法**：
  - `/quark list`：场地「已配置」、出生点数量正确。
  - 两名玩家 `/quark join` → 30 秒倒计时（OP 在队列时等 `/quark start`）→ 开局每人第 1 格是「粒子手枪」，右键出现直线粒子。
  - 拿枪打墙：粒子停在墙面，不穿墙、不击杀；打中玩家：全服播报「X 击杀了 Y（N 分）」，被杀者原地复活在随机出生点并有约 1.5 秒无敌。
  - 左键点击不发射；右键间隔小于冷却时动作栏提示「枪械冷却中」。
  - 时间到：全服播报获胜者/并列第一与排名，全员回主城；`/quark list` 回到等待中。
