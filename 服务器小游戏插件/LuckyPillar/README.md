# LuckyPillar（幸运之柱）

> 速查卡（AI 先读这里，别先读源码）。设计稿：`docs/幸运之柱设计稿.md`。

- **一句话职责**：开局把全员丢到**圆形排布的哭泣黑曜石柱**顶上（背包全空、没有装备），**每 2 秒每人各得 1 件随机物品**（任何东西：方块/镐子/食物/药水/水桶/岩浆桶…），其中 **30%（`loot-gear-chance`）直接从「武器/工具/装备」子池出**，这些装备还有 **35%（`loot-enchant-chance`）挂 1 个低等级附魔**（默认只有 I 级，无诅咒）。**世界边境墙**开局在柱环外 `shrink-start-gap` 格（默认 20，柱子在圈内约 20 格处），**先等 `shrink-delay-seconds`（默认 60s）才开始缩圈**，之后从该半径往中心缩，圈外方块被吃掉、掉下去淘汰；全程有 **箭雨 / 苦力怕雨**（公屏播报，覆盖圈内全范围，**苦力怕只伤人不炸方块**）；缩到半径 2 时边境冻结、改下 **TNT 雨**（既炸方块也伤人），**中心在柱底高度铺一块 3×3 草方块平台**，最后活着的人获胜，冠军写 `players.yml`。柱数 = 开局人数，柱子不设保护（能挖就挖），不锁饥饿。
- **接入**：`plugin.yml` 的 `minigame-game: true` + `minigame.name: 幸运之柱`（icon `CRYING_OBSIDIAN`，tab 颜色 `'5'` 紫）；HubCore `config.yml` 的 `games` 段 + `menu.items` 槽位 `'19'`（`action: game:幸运之柱`）+ `menu.sub.幸运之柱`。加入播报走 `HubAPI.announceJoin(plugin.getName(), p.getName(), a.getName())`（带场地名）。
- **入口 / 主流程**：`LuckyPillar/src/main/java/com/luckypillar/LuckyPillarPlugin.java#onEnable` → `LuckyPillarManager#load`（读 `arenas`、`PlayerStats.load("luckypillar", …)`、延迟 40 tick 上报中央菜单、起 `startVoidTask`）→ 排队 `join`（发退出床；OP/`lp.admin` 再发一张开始纸）→ `wait`（公共库 `WaitCountdown`，30s/满员 3s/有 OP 暂停；右键开始纸 = 走 `start`）→ `start`/`startMatch`（`nextMatch()` 局号 +1 → `LuckyPillarArena#generate` 生成柱阵 → `beginShrink` → 传送每人一根柱顶 → `applyBorder` → `startTasks`）→ 局内四个定时任务（见下）→ `eliminate`/`afterEliminate` → 剩 1 人 `endMatch`（冠亚季军 + `recordFirst`）→ **结算展示 8s，场地保持原样（柱子/圈都还在）** → `finish`（**先把所有人传回主城，再分批清场**）。
  - `LuckyPillarManager.java`：状态机、柱阵/缩圈/天灾/结算/文案（**最常改**）。四个局内任务：`loot`（`loot-interval` 秒）、`disaster`（`disaster-interval` 秒，箭雨/苦力怕雨随机）、`second`（1s：同步边境墙 + 圈外侵蚀）、`trim`（5 tick：吃掉圈外方块）；终局再加 `finalWave`（10 tick 检查一次，到点下 TNT 雨，`final-max-seconds` 封顶）。
  - `LuckyPillarArena.java`：场地字段（只有 `center` / `lobby`）、`generate`（圆周均分；中心柱底 `platformY` 铺 3×3 草方块平台）、`blocks`（**本局方块集合**，缩圈只扫它）、`entities`（本局实体）、`trimOutside`、分批 `beginClear`/`clearStep`/`clearAllNow`、`radiusAt` 线性插值、`save`/`load`。
  - `LuckyPillarListener.java`：死亡/掉线淘汰、**死亡掉落 + 立刻转旁观**、**苦力怕爆炸清 blockList（先判世界+对局）**、苦力怕摔落免伤、PvP 开关、点火拦截、玩家放方块/倒桶/流体蔓延记进本局集合、配置模式道具、等待室开始纸右键开局 / 红床退出（只认主手）。
  - `LuckyPillarLoot.java`：「任意物品」池（`Material.values()` 过滤）+ 三张黑名单表 + **武器/工具/装备子池（72 件）** + 药水随机效果池 + 附魔书/装备低阶附魔，**纯静态**。
  - `LuckyPillarCommand.java`：`/lp` 全部子命令与 tab 补全。
- **命令**（主命令 `luckypillar`，别名 `/lp`、`/lpz`、`/lucky`）：
  - 玩家：`/lp join [场名]`、`/lp leave`、`/lp list`、`/lp stats [玩家]`、`/lp help`。
  - 管理（`p.isOp() || p.hasPermission("lp.admin")`，节点 `lp.admin` default: op）：`create` `remove` `rename` `setup` `setup-exit` `center` `lobby` `save` `preview` `start` `stop` `stats reset`。
  - `/lp center|lobby|save|preview|start|stop` 可带场名；不带时用 `/lp create`、`/lp setup` 记下的"当前编辑场"。
- **配置**（`server/plugins/LuckyPillar/config.yml`，模板 `LuckyPillar/src/main/resources/config.yml`，改线上要改前者；YAML 冒号后必须有空格）：
  - 人数：`min-players`(2)、`max-players`(12)。
  - 柱阵：`pillar-depth`(30，柱子总高)、`pillar-spacing`(16)、`ring-radius`(0=按 `人数×间距/2π` clamp 5~32)、`center-platform-radius`(1=中心柱底 3×3 草方块平台；0=不铺)。⚠️ 改 `pillar-depth` 要同时抬/降 `arenas.<场名>.center.y`（柱顶 Y），否则柱底会扎进地面、淘汰线跑到地面以下。
  - 缩圈：`shrink-start-gap`(20=开局圈比柱环大 20 格)、`shrink-delay-seconds`(60=开局多少秒后才开始缩)、`shrink-total-seconds`(0=45+10×人数)、`shrink-target-radius`(2=圈内只剩约 5×5 空地)、`out-of-zone-damage`(1)、`out-of-zone-grace-seconds`(3)。
  - 事件：`loot-interval`(2)、`loot-gear-chance`(0.3=每次抽物品直接从武器/工具/装备子池出的概率)、`loot-enchant-chance`(0.35=装备再带 1 个低阶附魔的概率)、`loot-enchant-max-level`(1=附魔等级上限，1 即只有 I 级)、`disaster-interval`(22)、`pvp`(true)。
  - 终局：`final-tnt-interval`(2.5)、`final-max-seconds`(120)、`final-tnt-fuse`(20)。
  - 场地：`arenas.<场名>.world / center{x,y,z,yaw,pitch} / lobby{…}`（只有两个点）。
- **玩家记录**：命名空间 `luckypillar`，键 `firsts.<uuid>`（冠军次数）；唯一文件 `server/plugins/MiniGameLib/players.yml`（`games.luckypillar` 段），**不写** `plugins/LuckyPillar/data.yml`（只作为首次迁移的旧文件）。见 `docs/玩家记录.md`。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 玩法/缩圈/天灾/结算/文案 | `LuckyPillar/src/main/java/com/luckypillar/LuckyPillarManager.java` |
  | 柱阵生成、本局方块集合、清场、场地存取 | `LuckyPillar/src/main/java/com/luckypillar/LuckyPillarArena.java` |
  | 物品池/黑名单/药水/附魔 | `LuckyPillar/src/main/java/com/luckypillar/LuckyPillarLoot.java` |
  | 伤害/淘汰/爆炸拦截/建场道具 | `LuckyPillar/src/main/java/com/luckypillar/LuckyPillarListener.java` |
  | 命令与帮助文案 | `LuckyPillar/src/main/java/com/luckypillar/LuckyPillarCommand.java` |
  | 世界规则（pvp/刷怪/火） | `server/plugins/GameWorlds/config.yml` 的 `world_lp` |
  | 主城图标/菜单格位 | `server/plugins/HubCore/config.yml`（`games` 段、`menu.items` 的 `'19'`） |
- **实现时定下的几个细节**（事后可查）：
  - **世界用 FLAT，不是 VOID**：`GameWorlds` 只支持 `FLAT` / 省略（NORMAL），**没有 VOID**（`GameWorldsPlugin#loadOrCreate` 只判 `FLAT`）。所以 `world_lp` = FLAT，建场时把中心点 Y 设在离地（默认平坦地面 y=64）**足够高**：判淘汰线 = `center.y - pillar-depth - 2`，默认 `pillar-depth: 10` 时要 `center.y ≥ 77`（= 柱底离地 ≥ 4 格 + 再下 3 格），否则玩家掉到地面还活着。掉到地面时 Y 已低于这条线，`startVoidTask`（10 tick）直接判淘汰。
  - **一个世界只能跑一场**：`WorldBorder` 是**每个世界一份**的，`borders` 表按世界名存原设置。多场请各自独占世界（设计稿 §13 已列）。
  - **淘汰线**：`arena.getVoidY()` = `center.y - pillar-depth + 1 - 3`（即柱底再下 3 格）。摔落伤害不关、饥饿不锁。
  - **等待室两个道具**：槽位 `8` = 红床「退出游戏 &7(右键)」，槽位 `4` = 纸「开始游戏 &7(右键)」（只发给 `p.isOp() || p.hasPermission("lp.admin")` 的人；开局 `resetPlayer` 清背包，纸自然消失）。纸用 PDC 标记 `luckypillar:start-paper` 识别（`LuckyPillarManager#isStartPaper`），**局中抽到的纸 / 玩家自己的纸不会误触发**；右键依次过 `activeArenaOf` → `WAITING` → `canAdmin` 三道门，人数不足由 `start()` 提示。`PlayerInteractEvent` 只认主手（副手会再触发一次，开局后会多打一条「该场正在游戏中」）。
  - **箭雨伤害** `ARROW_RAIN_DAMAGE = 4.0`、**箭矢存活** `ARROW_LIFE_TICKS = 200`、**清场预算** `CLEAR_BUDGET_PER_TICK = 9000`、**结算展示** `SETTLE_SECONDS = 8` 都在 `LuckyPillarManager` 顶部常量。
  - **苦力怕雨**：从 `center.y+30` 落下、`setIgnited(true)` + `setFuseTicks(CREEPER_FUSE_TICKS=40)`；⚠️ 必须先 `setMaxFuseTicks(40)`——1.21+ 默认上限 30，直接 `setFuseTicks(40)` 会抛 `IllegalArgumentException: ticks > maxFuseTicks`（15:46 实测踩过，整轮苦力怕雨没生成）。**它自己的摔落伤害被取消**（`LuckyPillarListener#onFallDamage` + `isTrackedEntity`），否则 30 格落地就摔死、炸不出来。
  - **爆炸拦截铁律**：`EntityExplodeEvent` 只在 `entity instanceof Creeper` 且 `runningArenaIn(world) != null` 时清 `blockList()`；TNT 雨不拦（要炸方块），也只在本世界生效（AGENTS.md §5-5）。
  - **清场**：结算展示（`SETTLE_SECONDS`=8s）**期间完全不清**（场地原样，冠军能在原地看到结果）；8s 到点 `finish` 先 `sendHome` 把所有人（含已淘汰的旁观者）传回主城，**然后**才 `startClear`。清场按**整盒**（水平 `R0+4`、竖直 `柱底-3 ~ 柱顶+30`）分批清成 AIR，**跳过等待点 5×5×5 区域**；`/lp stop`、关服用一次性同步清完（`stop` 会先撤掉 `finish` 起的清场任务）。开局前若 `isClean()==false`（崩溃残留）会先同步清一遍再生成。⚠️ 盒子是照设计稿「盒内全部清空」的，**中心点别设太低**：`center.y - pillar-depth - 2` 低于地面（默认 64）时，局末会把地面也挖掉一块。
  - **缩圈只吃"本局方块集合"**：生成的柱阵 + 中心平台（柱底高度 (2r+1)² 草方块，`center-platform-radius` 默认 1 = 3×3） + 玩家放的方块（`BlockPlaceEvent`）+ 倒桶（`PlayerBucketEmptyEvent`）+ 流体蔓延（`BlockSpreadEvent`/`BlockFromToEvent`/`BlockFormEvent`）。中心平台不参与缩圈（它就在圆心，永远在圈内）。
  - **物品权重**：`LuckyPillarLoot` 正常物品权重 4、低价值权重 1；低价值 = `JUNK` 表里的纯材料（矿锭/染料原料/唱片碎片…）+ 名字后缀（`RECORD_`/`_DYE`/`_SEEDS`/`_BANNER_PATTERN`/`_POTTERY_SHERD`/`_SMITHING_TEMPLATE`）+ **能放置但踩不住的方块**（`isBlock() && !isSolid()`：旗帜、火把、告示牌、花…）。
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
  - 公屏「🏹 箭雨来袭！」/「💥 苦力怕雨！」（每次从几句随机文案里挑一句）；苦力怕炸完**地上方块不变**、人掉血；缩到半径 2 时公屏「终局！」，TNT 雨**能炸方块**也能伤人。
  - 只剩 1 人：冠亚季军广播、`/lp stats <冠军>` +1；**接下来 8 秒是展示期**——柱子、圈、脚下方块都还在（冠军站在原地看着）；
    8 秒到点才**全体回主城**，回主城之后柱子才开始一批批消失，最后场地干净、`/lp list` 回到等待。
  - **死亡 → 掉落 + 立刻旁观（2026-10-19）**：3 人以上对局里，任意方式被打死 → **不弹死亡界面**（服务端下一 tick 自动重生）→
    落地就是**旁观模式**、镜头在中心上方 14 格、可以自由飞着看别人打完；死的地方会**掉一地物品**（尸体上的剑/方块都能被活人捡走）。
    **不会**再被拉回主城（旧行为）。本局结束（8s 展示期后）自动回主城、恢复生存模式。
    2 人对局也一样：一个人死就立刻结算，但**死的人也照样变旁观**，看这 8 秒结算画面，再和大家一起回主城。
  - 比赛已经结束时（8s 展示期）摔死 → 同样直接转旁观看结算，8s 后一起回主城（不再有人被单独提前踢回去）。
  - 关服/`/lp stop`：`logs/latest.log` 出现"场地 … 已清空（清掉 N 个方块）"，世界里不留柱子与实体。
- **未确认 / TODO**：
  - 2026-10-18 15:45 有过一次 2 人真人实测（旧配置 gap 8 / delay 15）：开局、圈、缩圈都正常；**苦力怕雨崩溃已修**（见上）。
  - 2026-10-18 后：柱高改 30（`pillar-depth: 30`，`虚空` 场的 `center.y` 81.64 → 101.64，柱底不动）+ PvP 放行修复（见上），**还没进游戏实测**。
  - 2026-10-19「死亡掉落 + 立刻旁观」也只做了编译 + `verify.ps1` 静态核对，**没进真人局**（要 3 人以上才看得到旁观；2 人一死就结算）。
  - 药水与附魔书的随机加工依赖 `Registry.ENCHANTMENT` / `PotionMeta#setBasePotionType`，只在纸面核对过 API。
  - 苦力怕雨早期（圈还很大时）大多落在虚空/地面，只有在圈变小、柱子稀疏时才有杀伤——这是"落点覆盖整个圈"的必然结果，觉得太弱就调 `disaster-interval` 或改 `creeperRain` 的落点策略。
