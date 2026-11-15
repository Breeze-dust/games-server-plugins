# LuckyPillar（幸运之柱）

> 速查卡（AI 先读这里，别先读源码）。设计稿：`docs/幸运之柱设计稿.md`。

- **一句话职责**：开局把全员丢到**圆形排布的哭泣黑曜石柱**顶上（背包全空、没有装备），**每 2 秒每人各得 1 件随机物品**（按类别权重抽：武器/工具/装备/方块/**食物**/**生物蛋**/其他；食物和生物蛋也各有 `loot-weight-*` 比例），其中**武器/工具/装备**按各自权重出，这些装备还有 **35%（`loot-enchant-chance`）挂 1 个低等级附魔**（默认只有 I 级，无诅咒；凋零/末影龙/监守者蛋已排除）。**世界边境墙**开局在柱环外 `shrink-start-gap` 格（默认 20，柱子在圈内约 20 格处），**先等 `shrink-delay-seconds`（默认 60s）才开始缩圈**，之后从该半径往中心缩，圈外方块被吃掉、掉下去淘汰；全程有 **箭雨 / 苦力怕雨**（每波随机二选一，箭数/苦力怕数也随机；**箭雨落点完全随机撒在圈内**，不再追着玩家头顶；提前 `disaster-countdown-seconds`（默认 5s）公屏 + 动作栏 + Boss 条倒计时，**苦力怕会炸掉方块**，而且**落地后靠近玩家 4 格内才点引信**（约 2 秒「嘶——」后爆炸）；顶部 **Boss 血条**用进度条显示**下次物品**倒计时（`loot-bossbar`）；缩到半径 2 时边境冻结、**在柱子中间（柱子半高、z 轴居中）刷新一只凋零**（会拆方块、追人；不再下 TNT 雨），**中心在柱底高度铺一块 3×3 草方块平台**，最后活着的人获胜，冠军写 `players.yml`。柱数 = 开局人数，柱子不设保护（能挖就挖），不锁饥饿。**三叉戟 / 重锤 / 钓鱼竿的耐久上限是 1（用一次就碎）**；OP 可用 `/lp ban` 禁用手上物品（写入 `config.yml` 的 `loot-banned-items`，以后不会再抽到）。
- **接入**：`plugin.yml` 的 `minigame-game: true` + `minigame.name: 幸运之柱`（icon `CRYING_OBSIDIAN`，tab 颜色 `'5'` 紫）；HubCore `config.yml` 的 `games` 段 + `menu.items` 槽位 `'19'`（`action: game:幸运之柱`）+ `menu.sub.幸运之柱`。加入播报走 `HubAPI.announceJoin(plugin.getName(), p.getName(), a.getName())`（带场地名）。
- **入口 / 主流程**：`LuckyPillar/src/main/java/com/luckypillar/LuckyPillarPlugin.java#onEnable` → `LuckyPillarManager#load`（读 `arenas`、`PlayerStats.load("luckypillar", …)`、延迟 40 tick 上报中央菜单、起 `startVoidTask`）→ 排队 `join`（发退出床；OP/`lp.admin` 再发一张开始纸）→ `wait`（公共库 `WaitCountdown`，30s/满员 3s/有 OP 暂停；右键开始纸 = 走 `start`）→ `start`/`startMatch`（`nextMatch()` 局号 +1 → `LuckyPillarArena#generate` 生成柱阵 → `beginShrink` → 传送每人一根柱顶 → `applyBorder` → `startTasks`）→ 局内定时任务（见下）→ `eliminate`/`afterEliminate` → 剩 1 人 `endMatch`（冠亚季军 + `recordFirst`）→ **结算展示 8s，场地保持原样（柱子/圈都还在）** → `finish`（**先把所有人传回主城，再分批清场**）。
  - `LuckyPillarManager.java`：状态机、柱阵/缩圈/天灾/结算/文案（**最常改**）。局内任务：`loot`（`loot-interval` 秒；发物品并重置 Boss 条）、`bossBar`（1 tick：刷新「下次物品」进度条 + 天灾倒计时文案）、`second`（1s：同步边境墙 + 圈外侵蚀）、`disaster`（每 `disaster-interval` 秒先起 `disaster-countdown-seconds` 秒倒计时，到点再落箭/落苦力怕）、`creeperWatchers`（每波苦力怕一个：落地 + 靠近玩家才点引信，清完自取消）；终局再加 `finalTimer`（1s 检查一次 `final-max-seconds` 封顶，并已刷出一只凋零）。
  - `LuckyPillarArena.java`：场地字段（只有 `center` / `lobby`）、`generate`（圆周均分；中心柱底 `platformY` 铺 3×3 草方块平台）、`blocks`（**本局方块集合**，缩圈只扫它）、`entities`（本局实体）、`trimOutside`、分批 `beginClear`/`clearStep`/`clearAllNow`、`radiusAt` 线性插值、`save`/`load`。
  - `LuckyPillarListener.java`：死亡/掉线淘汰、**死亡掉落 + 立刻转旁观**、**苦力怕爆炸清 blockList（先判世界+对局）**、苦力怕摔落免伤、PvP 开关、点火拦截、玩家放方块/倒桶/流体蔓延记进本局集合、配置模式道具、等待室开始纸右键开局 / 红床退出（只认主手）。
  - `LuckyPillarLoot.java`：「任意物品」池（`Material.values()` 过滤）+ 三张黑名单表 + **武器/工具/装备子池（72 件）** + 药水随机效果池 + 附魔书/装备低阶附魔，**纯静态**。
  - `LuckyPillarCommand.java`：`/lp` 全部子命令与 tab 补全。
- **命令**（主命令 `luckypillar`，别名 `/lp`、`/lpz`、`/lucky`）：
  - 玩家：`/lp join [场名]`、`/lp leave`、`/lp list`、`/lp stats [玩家]`、`/lp help`。
  - 管理（`p.isOp() || p.hasPermission("lp.admin")`，节点 `lp.admin` default: op）：`create` `remove` `rename` `setup` `setup-exit` `center` `lobby` `save` `preview` `start` `stop` `stats reset` `reload` `ban [材料]` `unban [材料]` `banlist`。
  - `/lp center|lobby|save|preview|start|stop` 可带场名；不带时用 `/lp create`、`/lp setup` 记下的"当前编辑场"。
- **配置**（`server/plugins/LuckyPillar/config.yml`，模板 `LuckyPillar/src/main/resources/config.yml`，改线上要改前者；YAML 冒号后必须有空格）：
  - 人数：`min-players`(2)、`max-players`(12)。
  - 柱阵：`pillar-depth`(30，柱子总高)、`pillar-spacing`(16)、`ring-radius`(0=按 `人数×间距/2π` clamp 5~32)、`center-platform-radius`(1=中心柱底 3×3 草方块平台；0=不铺)。⚠️ 改 `pillar-depth` 要同时抬/降 `arenas.<场名>.center.y`（柱顶 Y），否则柱底会扎进地面、淘汰线跑到地面以下。
  - 缩圈：`shrink-start-gap`(20=开局圈比柱环大 20 格)、`shrink-delay-seconds`(60=开局多少秒后才开始缩)、`shrink-total-seconds`(0=45+10×人数)、`shrink-target-radius`(2=圈内只剩约 5×5 空地)、`out-of-zone-damage`(1)、`out-of-zone-grace-seconds`(3)。
  - 事件：`loot-interval`(2)、类别权重 `loot-weight-weapon`(25) / `loot-weight-tool`(20) / `loot-weight-armor`(10) / `loot-weight-block`(25) / **`loot-weight-food`(10)** / **`loot-weight-spawn-egg`(5)** / `loot-weight-other`(20)（相对值；`loot-gear-chance` 是旧键，当前不再读取）、`loot-enchant-chance`(0.35=装备再带 1 个低阶附魔的概率)、`loot-enchant-max-level`(1=附魔等级上限，1 即只有 I 级)、`loot-banned-items`(`[]`=OP 用 `/lp ban` 写入的禁用材料名单，被禁物品不会再进随机池)、`loot-bossbar`(true=顶部 Boss 条显示下次物品倒计时进度)、`disaster-interval`(22)、`disaster-countdown-seconds`(5=天灾前预警/倒计时秒数，0=关闭，必须小于间隔)、`pvp`(true)。
  - 终局：`final-max-seconds`(120=封顶时长；到点按血量排名结算；终局刷一只凋零，不再有 TNT 雨)。
  - 场地：`arenas.<场名>.world / center{x,y,z,yaw,pitch} / lobby{…}`（只有两个点）。
- **玩家记录**：命名空间 `luckypillar`，键 `firsts.<uuid>`（冠军次数）；唯一文件 `server/plugins/MiniGameLib/players.yml`（`games.luckypillar` 段），**不写** `plugins/LuckyPillar/data.yml`（只作为首次迁移的旧文件）。见 `docs/玩家记录.md`。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 玩法/缩圈/天灾/结算/文案 | `LuckyPillar/src/main/java/com/luckypillar/LuckyPillarManager.java` |
  | 柱阵生成、本局方块集合、清场、场地存取 | `LuckyPillar/src/main/java/com/luckypillar/LuckyPillarArena.java` |
  | 物品池/黑名单/药水/附魔 | `LuckyPillar/src/main/java/com/luckypillar/LuckyPillarLoot.java` |
  | 伤害/淘汰/建场道具 | `LuckyPillar/src/main/java/com/luckypillar/LuckyPillarListener.java` |
  | 命令与帮助文案 | `LuckyPillar/src/main/java/com/luckypillar/LuckyPillarCommand.java` |
  | 世界规则（pvp/刷怪/火） | `server/plugins/GameWorlds/config.yml` 的 `world_lp` |
  | 主城图标/菜单格位 | `server/plugins/HubCore/config.yml`（`games` 段、`menu.items` 的 `'19'`） |
- **实现时定下的几个细节**（事后可查）：
  - **世界用 FLAT，不是 VOID**：`GameWorlds` 只支持 `FLAT` / 省略（NORMAL），**没有 VOID**（`GameWorldsPlugin#loadOrCreate` 只判 `FLAT`）。所以 `world_lp` = FLAT，建场时把中心点 Y 设在离地（默认平坦地面 y=64）**足够高**：判淘汰线 = `center.y - pillar-depth - 2`，默认 `pillar-depth: 10` 时要 `center.y ≥ 77`（= 柱底离地 ≥ 4 格 + 再下 3 格），否则玩家掉到地面还活着。掉到地面时 Y 已低于这条线，`startVoidTask`（10 tick）直接判淘汰。
  - **一个世界只能跑一场**：`WorldBorder` 是**每个世界一份**的，`borders` 表按世界名存原设置。多场请各自独占世界（设计稿 §13 已列）。
  - **淘汰线**：`arena.getVoidY()` = `center.y - pillar-depth + 1 - 3`（即柱底再下 3 格）。摔落伤害不关、饥饿不锁。
  - **等待室两个道具**：槽位 `8` = 红床「退出游戏 &7(右键)」，槽位 `4` = 纸「开始游戏 &7(右键)」（只发给 `p.isOp() || p.hasPermission("lp.admin")` 的人；开局 `resetPlayer` 清背包，纸自然消失）。纸用 PDC 标记 `luckypillar:start-paper` 识别（`LuckyPillarManager#isStartPaper`），**局中抽到的纸 / 玩家自己的纸不会误触发**；右键依次过 `activeArenaOf` → `WAITING` → `canAdmin` 三道门，人数不足由 `start()` 提示。`PlayerInteractEvent` 只认主手（副手会再触发一次，开局后会多打一条「该场正在游戏中」）。
  - **箭雨伤害** `ARROW_RAIN_DAMAGE = 4.0`、**箭矢存活** `ARROW_LIFE_TICKS = 200`、**清场预算** `CLEAR_BUDGET_PER_TICK = 9000`、**结算展示** `SETTLE_SECONDS = 8` 都在 `LuckyPillarManager` 顶部常量。
  - **苦力怕雨落点 / 下落（2026-11-14）**：落点**玩家正上方 +20 格**（水平偏移 0.0；1×1 柱顶只有正上方才落得上去），`setVelocity(0,-0.4,0)` + `setGravity(true)` 保证垂直下落，**摔落伤害被取消**（`LuckyPillarListener#onFallDamage` + `isTrackedEntity`）。
  - **苦力怕「落地靠近玩家才激活」（2026-11-14）**：生成时**不点火**（旧版生成当 tick 就 `setIgnited(true)`，引信在半空/落地瞬间就走完 → 玩家看到「掉到脚边立刻爆」，没有「嘶——」预警）。`watchCreeperActivation`（每 2 tick）负责：**还在半空不点火** → **落地且 4 格内有存活玩家**（`CREEPER_ACTIVATE_RANGE`）→ `igniteCreeper`（先 `setMaxFuseTicks(40)` 再 `setFuseTicks(0)`、最后 `setIgnited(true)`；Paper 的 `setFuseTicks` 写的是**当前膨胀值 `swell`**、不是剩余引信，所以必须归 0 让它自己数到 40＝2 秒，刚好对上「你只剩两秒」）；落地时附近没人 → 保持原版 AI 自己追，`CREEPER_MAX_LIFE_TICKS=300`（15 秒）内还没贴到玩家就 `remove()`。本波清完观察任务自己取消，并已并入 `MatchTasks#cancelGameplay`。⚠️ **不能用 `setAI(false)` 让它「安静下落」**：26.2 的 `LivingEntity#aiStep` 在 `isEffectiveAi()==false` 时连 `travel()` 都不调，noAI 的苦力怕会挂在半空不掉（javap 核对）。
  - **苦力怕会炸方块（2026-11-14）**：`LuckyPillarListener` 不再拦截 `EntityExplodeEvent`，同时 GameWorlds 的 `world_lp` 改成 `mob-griefing: true`（`mobGriefing=false` 时 MOB 爆炸走 `BlockInteraction.KEEP`，一颗方块都炸不掉）。辛苦攒的柱顶/平台会被炸掉，注意站位。
  - **天灾倒计时**：`disaster` 任务在事件前 `disaster-countdown-seconds`（默认 5）秒起一个每秒任务：公屏警告 + 动作栏「⚠ 箭雨/苦力怕雨 Xs」+ 3 秒内点击音；Boss 条标题同时显示天灾剩余秒数。倒计时到点才真正调用 `arrowRain`/`creeperRain`；结束/终局/`/lp stop` 会一并取消 `disasterWarn`/`disasterFire`，不会在结算里补落一波。
  - **下次物品 Boss 条**：`startTasks` 记录 `nextLootMs`，`bossBar` 任务每 tick 按 `remaining / loot-interval` 更新 Adventure `BossBar` 进度（1→0，绿→黄→红）；标题 `🎁 下次物品 X.Xs`，天灾倒计时期间前面加 `⚠ 苦力怕雨 Xs |`。`loot-bossbar: false` 可关闭；淘汰/离开/结算/停服都会 `hideBossBar`（不残留）。
  - **爆炸不再拦截**：苦力怕和终局凋零都要拆方块，所以本插件没有 `EntityExplodeEvent` 处理器；别的游戏靠它们自己的世界判定保护。GameWorlds 的 `world_lp` 必须 `mob-griefing: true`。
  - **清场**：结算展示（`SETTLE_SECONDS`=8s）**期间完全不清**（场地原样，冠军能在原地看到结果）；8s 到点 `finish` 先 `sendHome` 把所有人（含已淘汰的旁观者）传回主城，**然后**才 `startClear`。清场按**整盒**（水平 `R0+4`、竖直 `柱底-3 ~ 柱顶+30`）分批清成 AIR，**跳过等待点 5×5×5 区域**；`/lp stop`、关服用一次性同步清完（`stop` 会先撤掉 `finish` 起的清场任务）。开局前若 `isClean()==false`（崩溃残留）会先同步清一遍再生成。⚠️ 盒子是照设计稿「盒内全部清空」的，**中心点别设太低**：`center.y - pillar-depth - 2` 低于地面（默认 64）时，局末会把地面也挖掉一块。
  - **缩圈只吃"本局方块集合"**：生成的柱阵 + 中心平台（柱底高度 (2r+1)² 草方块，`center-platform-radius` 默认 1 = 3×3） + 玩家放的方块（`BlockPlaceEvent`）+ 倒桶（`PlayerBucketEmptyEvent`）+ 流体蔓延（`BlockSpreadEvent`/`BlockFromToEvent`/`BlockFormEvent`）。中心平台不参与缩圈（它就在圆心，永远在圈内）。
  - **物品权重**：`LuckyPillarLoot` 正常物品权重 4、低价值权重 1；低价值 = `JUNK` 表里的纯材料（矿锭/染料原料/唱片碎片…）+ 名字后缀（`RECORD_`/`_DYE`/`_SEEDS`/`_BANNER_PATTERN`/`_POTTERY_SHERD`/`_SMITHING_TEMPLATE`）+ **能放置但踩不住的方块**（`isBlock() && !isSolid()`：旗帜、火把、告示牌、花…）。
  - **一次性物品**：三叉戟 / 重锤 / 钓鱼竿在 `LuckyPillarLoot#byMaterial` 里把 `Damageable#setMaxDamage(1)`（耐久上限 1），并且 `enchantLow` 跳过这三种物品（避免 Unbreaking 让「只能一次」失效）；用一次就碎。
  - **OP 禁物**：`/lp ban` 取主手物品的材料（也可 `/lp ban <材料名>`，控制台可用），`LuckyPillarLoot` 把该材料从 `POOL` / 五类 `BUCKETS` 里永久剔除并重建；`LuckyPillarManager#saveBannedItems` 把名单写进 `config.yml` 的 `loot-banned-items`（重启/`/lp reload` 后仍生效）；同时把该材料从在场玩家的背包、护甲、副手、光标上清掉。`/lp unban` 解除，`/lp banlist` 查看。
  - **装备抬概率 + 低阶附魔**：`random()` 先掷 `loot-gear-chance`（默认 0.3）——命中就从「武器/工具/装备」子池（**72 件**：名字后缀 `_SWORD/_PICKAXE/_AXE/_SHOVEL/_HOE/_HELMET/_CHESTPLATE/_LEGGINGS/_BOOTS` + `BOW/CROSSBOW/TRIDENT/SHIELD/FISHING_ROD/SHEARS/FLINT_AND_STEEL/MACE`，仍受三张黑名单过滤）里**均匀**抽 1 件；没命中才走上面的权重池。命中装备后再掷 `loot-enchant-chance`（默认 0.35）：只挑 `canEnchantItem(这件)` 且名字不以 `_curse` 结尾的附魔，挂 **1 个**，等级 `1 ~ min(loot-enchant-max-level, 该附魔上限)`。启动日志会打「武器/工具/装备 72 件，命中率 30%，附魔率 35%，附魔上限 1 级」（`LuckyPillarManager#load` 里的 `LuckyPillarLoot.configure`）。
  - **PvP 必须显式放行**：`SkyPitListener#onDamageGuard`（LOWEST）是全服 PvP 守卫——「双方不都在天坑内」就无条件 `setCancelled(true)`（主城防误伤），幸运之柱的玩家当然不在天坑里，所以**只靠 `world_lp` 的 `pvp: true` 永远打不掉血**。`LuckyPillarListener#onPvp`（HIGHEST）在双方同属本场 RUNNING 对局时 `setCancelled(false)` 覆盖回来（与 `DuelListener#onDamage` 同机制），配置 `pvp: false` 时改成取消；handler **不能**加 `ignoreCancelled = true`，否则收不到已被取消的事件。
  - **开局圈与准备期**：`startRadius = 柱环 + shrink-start-gap`（默认 20），前 `shrink-delay-seconds`（默认 60）秒 `radiusAt` 固定返回 `startRadius`，之后才线性收缩。
  - **死亡掉落 + 立刻旁观**（2026-10-19）：`LuckyPillarListener#onDeath` 只 `setKeepInventory(false)` + `deathMessage(null)`，**不再 `getDrops().clear()`**（原来是"谁死了身上东西直接蒸发"）；`LuckyPillarManager#handleDeath` 在淘汰后**下一 tick 自动 `p.spigot().respawn()`** 跳过死亡界面，`PlayerRespawnEvent` 里落到 `spectateLocation()`（中心上方 `SPECTATE_HEIGHT=14` 格）并 `SPECTATOR`，一直看到本局结束。**坑**：`eliminate()` 里**不能**再调 `HubAPI.clearGame(id)`——清掉游戏状态后 HubCore 认为他"已在大厅"，同一次重生的 `PlayerRespawnEvent`（HubCore 延迟 1 tick）会 `teleportToLobby` 把人拉回主城，旁观就没了。淘汰的人由 `finish()` / `leave()` / 掉线 统一 `clearGame`。
    - ENDING（结算展示期）里死亡（含"最后一个死的人"）**也是旁观**：`handleRespawn` 不区分状态，一律落旁观点 + `SPECTATOR`，8 秒后由 `finish()` 统一回主城（`playerArena` 映射留到 `finish` 才删，别提前 `remove`/`clearGame`，否则又会被 HubCore 提前送回去）。
    - **`world_lp` 必须 `keep-inventory: false`**（2026-10-18 实测事故：最后一个死的人"没掉落 + 直接回主城"）：
      GameWorlds 默认 `keep-inventory: true`（`GameWorldsPlugin#applyRules`），而 26.2 的 `ServerPlayer#die` 是在
      `PlayerDeathEvent` **之前**就按 gamerule 算好掉落表的（`KEEP_INVENTORY` 命中时表是空的），所以 `onDeath` 里
      `setKeepInventory(false)` 只能把背包清空——**地上什么都不掉**。已在 `GameWorlds/config.yml` 的 `world_lp` 配成
      `false`，`verify.ps1` 会检查这一条。
    - **别的小游戏的重生监听器会抢走旁观**（已修）：`DuelListener#onRespawn` 原来不判世界，任何重生都
      `HubAPI.teleportToLobby`（还会触发本插件的 `leave()` 清状态）。DuelArena 加了「只在单挑世界兜底」的世界判定。
      以后再出现"死后被秒送回主城"，先 grep 所有 `PlayerRespawnEvent` 监听器有没有世界/场地判定。
  - **`/lp` 与 LuckPerms 撞名**：面板服若装了 LuckPerms，它的 `/lp` 别名可能抢占；`/lpz`、`/lucky` 一定可用（设计稿 §11-2）。
  - **别在死亡界面还没关时调 `teleportToLobby`（重生键变黑的坑）**：`HubCore` 的 `teleportToLobby` 末尾会 `p.setHealth(20)`，
    对血量还是 0 的人这么干＝在服务端把"尸体"复活，而客户端仍停在死亡界面；之后点「重生」发的包会被
    `ServerGamePacketListenerImpl#handleClientCommand` 的 `if (getHealth() > 0) return;` 丢弃 ⇒ **重生键永远按不动，只能重连**。
    26.2 的 `PlayerDeathEvent` 没有 `setShowDeathScreen`，所以只能用「不复活他」来规避：`LuckyPillarManager#sendHome`
    在血量 `<= 0` 时只 `HubAPI.clearGame` 不传送，等他点重生后由主城 `PlayerRespawnEvent` 接回大厅（`finish` / `leave` 都走它）。
    诱因（已修，2026-10-18 后续）：`endMatch` 的 8s 展示期原本会立刻 `startClear`，柱子一没，还站在柱顶的人会掉进虚空；
    这期间场地已是 `ENDING`，所有死亡处理都提前 return（死亡信息也不清），所以那段时间摔死的人在日志里能看到裸的
    `X fell out of the world`。现在清场挪到了 `finish`（全体回主城之后），展示期不会再掉人。
- **验证方法**：
  - `/lp create 天灾柱` → `/lp setup 天灾柱` → **金锄头**点中心（圆心 + 柱顶高度）→ **纸**点等待点（脚下无方块会自动铺 5×5）→ **绿宝石**保存 → `/lp list` 显示"已配置"。
  - 2 人开局：只生成 2 根柱（**同高 30 格**、间距 ~16，**中心柱底有一块 3×3 草方块平台**）、背包**全空**；动作栏每 2 秒 `🎁 <物品名>`；抽到过方块/镐子/食物/药水/水桶/岩浆桶。
  - **装备概率**：站 30 秒（约 15 件）里应能看到 4~5 件武器/工具/装备（剑/镐/斧/铲/锄/头盔/胸甲/护腿/靴/弓/盾），其中约 1/3 带 I 级附魔（如 保护 I、锋利 I、效率 I）；启动日志有一行「武器/工具/装备 72 件，命中率 30%，附魔率 35%，附魔上限 1 级」。
  - **PvP**：两人在柱顶互砍，对方掉血、血条变红、有击退（不再是"打了没反应"）；`pvp: false` 时才不掉血。
  - 等待期间（`/lp join` 后）：OP 背包第 5 格有绿色「开始游戏」纸、第 9 格红床，非 OP 只有红床；人数不够时右键纸提示「人数不足（至少 2 人）」，够了右键直接开局（开局后纸随背包清空消失）；`/lp start` 仍然可用。
  - 世界边境墙可见、上下无边界；**开局墙在柱环外 20 格**（柱子在圈内约 20 格处），**60 秒后**才看到墙开始收；缩圈时外侧柱子整根逐批消失（`BLOCK_CRUMBLE` 粒子 + 低频音效）；站圈外 3 秒后每秒掉 1 点血。
  - **天灾倒计时 / 随机（2026-11-14）**：每波前 5 秒公屏「⚠ 箭雨/苦力怕雨 将在 5 秒后降临」→ 动作栏每秒「⚠ 箭雨/苦力怕雨 5s/4s/3s/2s/1s」→ 到点才公屏「🏹 箭雨来袭！」/「💥 苦力怕雨！」；事件类型每波 50/50 随机，箭数在基础值 75%~100% 随机、苦力怕数在基础值 ±1 随机；**箭雨落点完全随机撒在圈内**（不再对着玩家），高度也随机 12~22 格。
  - **苦力怕落地再炸、会拆方块、靠近才点火（2026-11-14）**：看到「苦力怕雨」倒计时结束后，苦力怕从玩家头顶约 20 格**垂直落下**；**落地后只有靠近某个存活玩家 4 格内才点亮引信**，听到「嘶——」约 **2 秒**后才炸（没贴到人的 15 秒后直接消失），不会再出现「刚落到脚边就爆」；爆炸会**炸掉柱顶/平台方块**（`mob-griefing: true`）。
  - **食物 / 生物蛋（2026-11-14）**：物品池新增 FOOD 和 SPAWN_EGG 两个类别，默认权重 10 / 5（总权重 115）；能抽到面包/熟肉/苹果/金苹果等食物和随机生物蛋，凋零/末影龙/监守者蛋已排除；生物蛋刷出来的其他生物在局末清场时一并清掉。
  - **下次物品 Boss 条**：屏幕顶部有一条「🎁 下次物品 2.0s → 0.0s」的进度条，每发一件物品回满；剩余 >50% 绿色、20%~50% 黄色、<20% 红色；`loot-bossbar: false` 时整条不显示。
  - 缩到半径 2 时公屏「终局！」，在**柱子中间（柱子半高、z 轴取圆心）**刷新**一只凋零**（会拆方块、追人）；**不再下 TNT 雨**。凋零在 8 秒结算展示期开始时会立刻被清掉（生物蛋刷出来的敌对生物也会一起清），不会继续炸冠军。
  - 只剩 1 人：冠亚季军广播、`/lp stats <冠军>` +1；**接下来 8 秒是展示期**——柱子、圈、脚下方块都还在（冠军站在原地看着）；
    8 秒到点才**全体回主城**，回主城之后柱子才开始一批批消失，最后场地干净、`/lp list` 回到等待。
  - **死亡 → 掉落 + 立刻旁观（2026-10-19）**：3 人以上对局里，任意方式被打死 → **不弹死亡界面**（服务端下一 tick 自动重生）→
    落地就是**旁观模式**、镜头在中心上方 14 格、可以自由飞着看别人打完；死的地方会**掉一地物品**（尸体上的剑/方块都能被活人捡走）。
    **不会**再被拉回主城（旧行为）。本局结束（8s 展示期后）自动回主城、恢复生存模式。
    2 人对局也一样：一个人死就立刻结算，但**死的人也照样变旁观**，看这 8 秒结算画面，再和大家一起回主城。
  - 比赛已经结束时（8s 展示期）摔死 → 同样直接转旁观看结算，8s 后一起回主城（不再有人被单独提前踢回去）。
  - 关服/`/lp stop`：`logs/latest.log` 出现"场地 … 已清空（清掉 N 个方块）"，世界里不留柱子与实体。
  - **OP 禁物**：`/lp ban`（主手拿三叉戟）→ 聊天栏提示已禁用并写入 `config.yml`；`/lp banlist` 能看到 `TRIDENT`；之后连续抽 3000 件不应再出现三叉戟；重启或 `/lp reload` 后仍然禁用；`/lp unban TRIDENT` 后恢复。
  - **一次性物品**：抽到三叉戟/重锤/钓鱼竿时物品栏耐久上限是 1，攻击/投掷/收竿一次后立即碎掉；三者不会带 Unbreaking 等附魔。
- **未确认 / TODO**：
  - 2026-11-14「苦力怕靠近玩家才点火」：只做了编译 + `javap` 核对（`Creeper#tick` 里 `ignited → swellDir=1` 才走引信、`CraftCreeper#setFuseTicks` 写的是 `swell`、`LivingEntity#aiStep` 的 `isEffectiveAi()` 门控 `travel()`），**还没进真人局**；验证点：苦力怕落地后先安静站着，玩家在 4 格内才「嘶——」、约 2 秒后炸；玩家跑开引信也照走；落点没人 15 秒后消失。
  - 2026-11-14「天灾倒计时 + 苦力怕落地 + 下次物品 Boss 条」：只做了编译 + `javap` 核对 `Creeper.tick` 的 `swell/maxSwell` 语义，**还没进真人局**；验证点见上（倒计时 5→1、苦力怕落地约 2 秒才炸、Boss 条每 2 秒回满）。
  - 2026-11-14「苦力怕 +20 格 / 会拆方块 / 终局改凋零」：只做了编译 + `javap` 核对 `ServerLevel.explode0`（`mobGriefing=false` → `BlockInteraction.KEEP`），**还没进真人局**；验证点：苦力怕落地后把柱顶/平台炸出坑，终局中心刷一只凋零、无 TNT 雨。
  - 2026-11-14「箭雨全随机 / 凋零刷柱子半高 / 食物 + 生物蛋」：已编译 + `javap` 核对（`arrowRain` 只调 `randomInCircle`；`spawnWither` 用 `circleZ` 和 `center.y - pillar-depth/2`），并已部署 `LuckyPillar.jar` 76,546 B，**还没进真人局**；验证点：箭雨不再集中砸头顶、整圈均匀落，凋零在柱子半高、z 轴圆心，动作栏能看到随机抽到食物或生物蛋；生物蛋刷出的怪由 `removeEntities` 在局末按盒清掉，结算展示期开始时 `removeBoss` 也会清掉所有敌对生物。
  - 2026-10-18 15:45 有过一次 2 人真人实测（旧配置 gap 8 / delay 15）：开局、圈、缩圈都正常；**苦力怕雨崩溃已修**（见上）。
  - 2026-10-18 后：柱高改 30（`pillar-depth: 30`，`虚空` 场的 `center.y` 81.64 → 101.64，柱底不动）+ PvP 放行修复（见上），**还没进游戏实测**。
  - 2026-10-19「死亡掉落 + 立刻旁观」也只做了编译 + `verify.ps1` 静态核对，**没进真人局**（要 3 人以上才看得到旁观；2 人一死就结算）。
  - 2026-11-13「OP 禁物 + 三叉戟/重锤/钓鱼竿耐久 1」：离线冒烟服 + 临时插件实测通过（`max=1/1/1`、禁用后 3000 次抽取 0 命中、`config.yml` 落盘/解除均正常）；**还没进真人局**。
  - 药水与附魔书的随机加工依赖 `Registry.ENCHANTMENT` / `PotionMeta#setBasePotionType`，只在纸面核对过 API。
  - 苦力怕现在 75% 落在随机存活玩家正上方 +20 格、25% 落在圈内随机点；前者基本都会砸在柱顶附近，后者可能落到虚空/地面（摔落伤害已免）。**落地后由 `watchCreeperActivation` 按「4 格内有存活玩家」决定是否点火，不是一落地就爆**。
