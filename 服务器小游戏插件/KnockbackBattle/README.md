# KnockbackBattle（击退大战）

> 速查卡（AI 先读这里，别先读源码）

- **一句话职责**：自由混战击退游戏。开局每人一把**击退镐**（默认铁镐，左键敲人）和**风弹**（右键发射，风弹会同时推动自己）；把别人打下平台，**低于 `arena.void-y` 淘汰为旁观者**；最后一人获胜；**时间到时仍存活的玩家全部获胜**。对局中所有伤害取消，只保留击退。开局还会按配置给全体**速度**与**跳跃提升**。
- **入口 / 主流程**：`KnockbackBattle/src/main/java/com/knockback/KnockbackPlugin.java#onEnable` → `KnockbackManager#load`（读 config 与场地）→ `/kb join` 进等待队列（`KnockbackManager#join`）→ 公共库 `WaitCountdown` 自动倒计时 → `KnockbackManager#startGame` 清背包、发棒/风弹、随机出生点、`HubAPI.setPlaying` → 每秒 `tickGame`（计时 + 补风弹 + 动作栏）→ 风弹命中 `detonateWindCharge` / 棒击 `stickHit` → 掉出 `void-y` 时 `eliminate` 变旁观 → 只剩 1 人 `endGame`；时间到则所有存活者共同获胜。
- **关键文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 开局/淘汰/计时/胜负/风弹/棒击/弹药 | `KnockbackBattle/src/main/java/com/knockback/KnockbackManager.java` |
  | 右键道具、伤害取消、风弹爆炸、掉落/方块保护 | `KnockbackBattle/src/main/java/com/knockback/KnockbackListener.java` |
  | 场地坐标 / 出生点 / 淘汰高度 | `KnockbackBattle/src/main/java/com/knockback/KnockbackArena.java` |
  | `/kb` 命令与提示 | `KnockbackBattle/src/main/java/com/knockback/KnockbackCommand.java` |
  | 默认配置 | `KnockbackBattle/src/main/resources/config.yml` |
  | 线上配置 | `server/plugins/KnockbackBattle/config.yml` |
- **命令**（主命令 `/knockback`，别名 `/kb`）：
  - 玩家：`/kb join`、`/kb leave`、`/kb list`、`/kb help`。
  - 管理员（OP 或 `kb.admin`）：`/kb start`（至少 1 人立即开）、`/kb stop`、`/kb setpos1`、`/kb setpos2`、`/kb setlobby`、`/kb addspawn`、`/kb clearspawns`、`/kb setvoid [Y]`、`/kb save`、`/kb reload`。
- **建场步骤**：
  1. 站到场地一个角：`/kb setpos1`；站到对角：`/kb setpos2`（只取 X/Z；旁观的观看点取两角中点，Y 取较高角 +3）。
  2. `/kb setlobby` 设置等待点。
  3. 站到出生点：`/kb addspawn`（可多次；开局随机挑）。
  4. 站在淘汰高度（平台下方）执行 `/kb setvoid`，或 `/kb setvoid <Y>` 直接写 Y。
  5. `/kb save` → `/kb list` 检查。
  6. 可选主城菜单入口（选空槽，例如 36）：
     ```
     /hub menu set 36 WIND_CHARGE cmd:kb join &b&l击退大战
     /hub menu lore 36 &7用击退镐和风弹把别人打下去|&7最后一人或时间到时存活者获胜
     ```
- **配置**（`server/plugins/KnockbackBattle/config.yml`）：
  - `min-players`（2）、`max-players`（16）、`game-seconds`（180）。
  - `wait-items.return`：返回大厅床，默认 `RED_BED`、槽位 8（最后一格）。
  - `wait-items.start`：管理员开始纸，默认 `PAPER`、槽位 4（第 5 格）。
  - `player-effects.speed` / `player-effects.jump-boost`：`enabled`、`amplifier`（0 = 等级 I，1 = 等级 II）、`duration-seconds`（0 = 整局）。
  - `stick.material`：击退镐材质，默认 `IRON_PICKAXE`；`stick.name` / `stick.lore`：显示名与介绍。
  - `stick.knockback.horizontal` / `vertical`：击退镐水平/垂直力度；`stick.cooldown-seconds`：敲击冷却。
  - `wind-charge.speed`：风弹飞行速度；`use-cooldown-seconds`：使用冷却。
  - `wind-charge.ammo.amount`：开局/上限弹药；`refill-amount` / `refill-seconds`：每多少秒补多少个。
  - `wind-charge.knockback.horizontal` / `vertical` / `radius`：爆炸击退力度和半径（距离越远力度略低）。
  - `wind-charge.knockback.self-horizontal` / `self-vertical`：发射瞬间推动自己的力度。
  - `arena.void-y`：低于此 Y 淘汰；`arena.pos1/pos2/lobby/spawns` 用命令写。
- **常见坑**：
  - **风弹是自定义投射物**：`spawnWindCharge` 生成一个 `Snowball`，用 `setItem(WIND_CHARGE)` 显示风弹外形、`setGravity(false)` 直线飞行；`ProjectileHitEvent` 命中后取消原版雪球效果，再由 `detonateWindCharge` 按配置给半径内玩家速度。不要改成原版 `WindCharge` 实体 + `ExplosionPrimeEvent`（本项目曾试过，事件链在无玩家/未加载区块下不稳定）。
  - **所有伤害都被取消**：`onDamage` 对 `isInGame` 玩家一律 `setCancelled(true)`；击退镐/风弹都只改速度，不扣血。想加伤害类玩法要重新设计。
  - **场地世界必须 PvP=true**：Minecraft 在 PvP 关闭时会直接拦截玩家近战，`EntityDamageByEntityEvent` 根本不触发，击退镐就不会有反应（风弹是自定义投射物，不受影响）。`startGame` 会 `enableWorldPvp` 临时设 true，`endGame` / `shutdown` 用 `restoreWorldPvp` 还原；GameWorlds 里最好也把该世界配成 `pvp: true` 作为兜底。
  - **速度/跳跃提升**：`preparePlayer` 在 `resetPlayer` 之后调 `applyConfigEffects`，用 `PotionEffect.INFINITE_DURATION`（`duration-seconds: 0`）或配置秒数；淘汰、离开、结算都会清掉效果，不会带回主城。
  - **风弹弹药是真实物品堆叠**：用 PDC 标记 `isWindCharge`，右键消耗 1 个；`refillAmmo` 按 `ammo.amount` 上限补。不要用普通 `WIND_CHARGE` 物品判定，否则会误吞地图里的原版风弹。
  - **淘汰后是旁观者**：`eliminate` 会清背包、切 `SPECTATOR`、传送到场地中心上方；`/kb leave`/退出会清回生存模式，避免下次进服还是旁观。
  - `HIGHEST` 事件只处理自己场内的玩家；别改成无条件取消所有世界。
  - 关服/结束会 `clearWindCharges()` 清掉还在飞的风弹；新一局开始也会清。
- **验证方法**：
  1. `/kb list` 看场地状态；`/kb join` 应被传到等待点，第 9 格是返回大厅床，管理员第 5 格是开始纸。
  2. 至少 2 人开局（或管理员右键开始纸/`/kb start`）；每人第 1 格击退镐、第 2 格风弹；动作栏显示剩余时间和风弹数量；身上应有速度与跳跃提升效果（默认等级 I、整局）。
  3. 左键用击退镐敲人：对方被水平+垂直击退，不扣血；连续快敲会受 `stick.cooldown-seconds` 限制。
  4. 右键风弹：自己向后上方弹一下，风弹飞出后命中方块/玩家，半径内玩家被击退；风弹数量减 1，每 `refill-seconds` 秒补 `refill-amount` 个。
  5. 把玩家打到 `void-y` 以下：变旁观者并传送到场地中心上方；只剩一人时立刻结算获胜。
  6. 把 `game-seconds` 改成 20 测试：时间到仍有多人存活时，广播「共同获胜」并全员回大厅；所有人都掉下去则「无人获胜」。
  7. 对局中不能破坏/放置方块、不能丢弃道具、不能受到伤害；离开/退出后恢复生存并清空对局道具。
