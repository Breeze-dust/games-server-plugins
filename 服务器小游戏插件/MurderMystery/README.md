# MurderMystery（密室杀手）—— 速查卡

> AI 先读这一段，能省掉读 3300 行源码

- **一句话职责**：每局随机出 1 名杀手（持铁剑、不隐身）、1 名侦探和 1 名猎人，其余是无辜者（**最少 3 人才能开局**）；杀手用铁剑近战一击必杀、杀光所有无辜者才算赢，无辜者捡死亡掉落的金锭去绿宝石商店买「一击必杀石剑/弩」反杀，**猎人开局自带 1 把无限箭的弓**（原版弓手感：按住右键蓄力，松开发射；箭矢命中杀手一击必杀、打到好人两人同归于尽）；杀手被消灭或时间到（线上 300 秒）都是无辜者获胜。**猎人死亡时弓会掉在原地（发光 + 全员 title「猎人的弓掉落了」）；杀手不能捡，其他玩家可以捡起来使用，捡到的人再死亡会再次掉落并再次播报。** 杀手的追踪指南针会自动指向最近的存活无辜者，目标死亡后自动换下一个；**最后 `endgame-reveal.seconds` 秒杀手每秒自动看到连向所有存活好人的粒子线（只发给杀手本人，右键指南针可立即重画）**。
- **入口 / 主流程**：`MurderMystery/src/main/java/com/murdermystery/MurderMysteryPlugin.java#onEnable` → `MurderMysteryManager#load`（读 `arenas` 段 + 战绩，并把场地上报给 HubCore）。
  - 开局：`MurderMysteryManager#start`（同文件）→ 置 `State.RUNNING`、`enableWorldPvp`、`assignRoles`、把玩家分散传送到金块上、
    **只发商店道具**（`holdShopItem`：绿宝石进中间 4 号槽**并把手持槽停在那一格**，人人一个不暴露身份）→ `startRoleCountdown` **两段式倒数**（都在 `roleCountdown` 里，`isRoleCountdown` 拦掉一切伤害）：
    第一段 3/2/1（副标题「准备…」）→ `revealRoles` 公布身份 **且此刻计 `gameEndMs`（300 秒从这里算）** →
    第二段 3/2/1（副标题「&lt;身份&gt; 职业道具 N 秒后发放…」）→ `giveRoleItems` 发**职业道具**（杀手铁剑+指南针 / 猎人弓+无限箭弹）→
    `startGameTask` 起每秒 `tickGame`。**发道具之前谁手里都没武器，所以"看谁拿铁剑"猜不出杀手。**
    **发道具时武器只写死 `WEAPON_SLOT`（快捷栏第 1 格）、不碰手持槽**，发完由 `holdShopItem` 把所有人的手持槽停在中间的绿宝石上——
    杀手/猎人拿到武器后手上仍然是绿宝石，别人没法靠"谁手里突然多出一把剑/弓"当场认出职业（想用武器自己按 1 切，发道具时会私聊告诉本人武器在第 1 格）。
  - 角色分配：`assignRoles`（**三个职业各自独立累计概率 + 杀手禁连庄**：每局没当上某职业 → 该职业权重 = `base + misses × step`（默认 1% / +2% / 封顶 50%，config `role-chance`）；当上该职业 → 该职业计数清零；三个职业互不影响。抽取顺序 杀手 → 侦探 → 猎人，每人最多拿一个职业；上一局杀手只要候选够仍不参与本局杀手抽取。等待室经验条上方显示的是按**当前等待人数、各职业数量、禁连庄、职业互斥**模拟 4000 次得到的**实际中选率**，不是原始权重）。
    累计计数存在 `server/plugins/MurderMystery/role-history.yml`（`round` + `misses.<职业>.<uuid>` + `previous-murderers`），每局开局写一次，重启后继续累计；删掉该文件 = 全部清零。
  - 身份音效：`revealRoles` 里杀手/侦探/猎人的音效用 `Player#playSound` **只发给本人**，附近玩家听不到（不要改回 `p.getWorld().playSound`，否则会靠音效暴露职业）。
  - 击杀：`MurderMysteryListener#onDamage` 判定 → `handleMurdererKill`（受害者淘汰 + 掉金锭 + 杀手之剑锋利 +1，上限 5；**近战刀有 2 秒冷却 `KNIFE_COOLDOWN_MS`，冷却中再砍不生效、只在本人 actionbar 提示**；飞剑仍走自己的 5 秒冷却）/ `handleInnocentKill`（无辜者阵营消灭杀手 → 直接结算，**结算横幅写明「杀手（X）被 Y 消灭了」**）/ `handleMutualDeath`（误伤好人 → 两人同归于尽，武器名写进私聊提示：石剑/弓/弩）。
  - 其他循环：`tickGame`（补金锭 + 最后 N 秒 `drawEndgameReveal` 粒子指引 + 状态栏；**已不再有"最后 N 秒全员发光"**）、`handleMove`（由 `startBoundsCheck` 每 0.5 秒查一次出界淘汰）。
  - 结算：`endGame(a, innocentsWin, outcome)`（揭晓本局杀手、`recordWin`、把玩家送回主城）；另有 `checkMurdererWin`、`murdererEliminated`（非商店武器致死，用 `Player#getKiller()` 写凶手名）、`handleLeaveDuringGame`。
  - 买武器：商店道具（绿宝石，栏位 4）右键 → `openShop` → `onShopBuy` → `buyItem`。
  - 真理之石（商店 `truth-stone-cost` / 侦探每 `detective-stone-seconds` 秒补给）：**右键打开「真理之石 · 选择探测目标」菜单**，
    一格格头颅、点谁探测谁；也可以左键直接点人探测。结果都是把**那颗石头变成**红石块（杀手）或钻石块（无辜者）。
    菜单：`openTruthMenu` / `onTruthMenuClick`（`TruthMenuHolder` 按格子记 UUID）；探测核心 `truthResult`（杀手用会被拒绝且不消耗）；
    消耗逻辑 `findTruthStoneSlot` / `consumeTruthStone`（一叠多颗只扣一颗，多余结果方块进背包、满了掉脚下）。
- **命令**：`/mm …`（插件命令 `murdermystery`，别名 `mm` / `murder`；管理权限 `mm.admin`，`plugin.yml` 里还声明了 `mm.use`）
  - 玩家：`join <场名>`、`leave`、`list`、`stats [玩家]`、`watch`（**被淘汰后**开「存活玩家」菜单，点头像传送）、`help`
  - OP：`create <场名>`、`remove <场名>`、`rename <旧场名> <新场名>`、`edit <场名>`、`pos1|pos2|center|lobby`、`save`、`setup <场名>`、`setup-exit`、`start`（需先 `/mm edit`；队列里有 OP 时可右键「开始游戏」纸）、`joinsign <场名>`、`goldblocks <场名>`（别名 `gold`）、`reload`、`stats reset`（清空全部战绩）
- **配置**：`server/plugins/MurderMystery/config.yml` —— 这份是真实生效的；源码 `MurderMystery/src/main/resources/config.yml` 只是模板。线上当前值：
  - `min-players: 3`、`max-players: 16`、`murderer-count: 1`、`detective-count: 1`、`hunter-count: 1`
  - `game-seconds: 300`（时间到 → 无辜者胜）、`detective-stone-seconds: 90`
  - `gold-per-death: 1`、`gold-spawn-interval: 10`、`gold-spawn-amount: 3`
  - 商店：`sword-cost: 8`、`crossbow-cost: 12`（旧 `bow-cost` 仍作兜底）、`arrow-cost: 2`、`truth-stone-cost: 8`、`death-note-cost: 3`、`steak-cost: 1`（断头饭 ×`steak-amount: 5`）
  - `role-chance.{murderer,detective,hunter}.{base,step,max}`：三个职业的累计权重，默认 1.0 / 2.0 / 50.0；等待室行动栏显示的是按当前队列模拟的实际中选率。
  - 场地在 `arenas.<场名>`（pos1/pos2/center/lobby，都是 `org.bukkit.Location`），加入牌在 `join-signs`。**代码兜底默认值和这份文件不一样**（`game-seconds` 180、`crossbow-cost` 8、`gold-spawn-amount` 2、`truth-stone-cost` 10、`death-note-cost` 1、`detective-stone-seconds` 30），以线上这份为准。
- **玩家记录**：命名空间 `murdermystery`，结构 `wins.<uuid>` / `kills.<uuid>`；唯一文件 `server/plugins/MiniGameLib/players.yml`（见 `docs/玩家记录.md`），读写在 `MurderMysteryManager#load`、`recordWin`、`recordKill`、`getWins`、`getKillsStat`；清空命令 `/mm stats reset`（OP，一次清掉 wins+kills，不能只清一个人）。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 玩法/击杀/商店/结算/战绩 | `MurderMystery/src/main/java/com/murdermystery/MurderMysteryManager.java` |
  | 事件（伤害、地图保护、商店点击、存活玩家菜单点击、告示牌） | `MurderMystery/src/main/java/com/murdermystery/MurderMysteryListener.java` |
  | 存活玩家菜单的格子→玩家映射（`InventoryHolder`） | `MurderMystery/src/main/java/com/murdermystery/WatchMenuHolder.java` |
  | 子命令 | `MurderMystery/src/main/java/com/murdermystery/MurderMysteryCommand.java` |
  | 场地结构（范围/中心/等待点/运行时角色） | `MurderMystery/src/main/java/com/murdermystery/MurderMysteryArena.java` |
  | 权限节点/别名/自动发现显示名 | `MurderMystery/src/main/resources/plugin.yml` |
  | 线上参数 | `server/plugins/MurderMystery/config.yml` |
  | 世界规则（`world_mm` 必须 `pvp: true`） | `server/plugins/GameWorlds/config.yml` |
- **常见坑**：
  - 关服时 `onDisable` 会 `saveAll()` **回写 config.yml 和战绩**：运行中改配置或上传会被内存里的旧配置覆盖（先停服 → 再覆盖 → 再启动）。YAML 语法错误（冒号后漏空格）时 `load()` 读不到场地，此时 `saveAll()` 会**跳过写配置、只存战绩**，防止清掉手写的场地。
  - 开局 `enableWorldPvp` 会把场地世界临时设成 `pvp=true`，局末 `restoreWorldPvp` 还原，对局中关服由 `restoreAllWorldPvp` 兜底；世界本身仍要在 GameWorlds 里配 `pvp: true`。
  - lobby handler（`onEnable` 里注册）必须用静默版 `leave(p, true)`，并且里面不要再调 `HubAPI.teleportToLobby`，否则进服/回主城会刷「你不在任何场中」（详见 `docs/开发须知.md` §5）。
  - `HIGHEST` 优先级的事件只处理「在自己场地里的玩家」（`arenaOf(...)` / `inArena(...)` 判空就 `return`），别改成无条件取消其它世界，否则会吃掉别的游戏的爆炸/交互。
  - 场地坐标（pos1/pos2/center/lobby）改动要重启才生效；`/mm reload` 只重载 config.yml 并重扫金块。
  - `MurderMysteryListener#onMove` 里"只转头不算移动"的早退**必须先比世界**：`if (!p.getWorld().equals(event.getFrom().getWorld())) return;`。别的小游戏（真实案例：TntRun 雪塔大作战 `endGame`）会在自己的 `PlayerMoveEvent` 里把玩家跨世界传回主城，此时本监听器拿到的 `event.getFrom()` 还是旧世界坐标，直接 `p.getLocation().distanceSquared(event.getFrom())` 会抛 `Cannot measure distance between …`（`docs/已知坑.md` §2）。出界淘汰另有 `startBoundsCheck` 0.5s 兜底，跳过这一次移动事件不会漏判。
  - **猎人弓掉落**：`eliminate()` 里先调 `dropHunterBow()` 再清背包；掉落物 `setGlowing(true)` + `setPickupDelay(10)`，全员 `broadcastTitle("猎人的弓掉落了")`。`MurderMysteryListener#onPickup`（`EntityPickupItemEvent`）挡杀手和非玩家实体；捡到弓的人再被淘汰会再次走同一段逻辑。局末/离开/退出会调 `removeHunterBow()`，别让弓被带出对局世界。
  - **指南针自动跟踪**：`startGameTask()` 另起 `compassTasks`（每 5 tick 刷新），`cancelGameTask()` 里一起取消。目标只从 `a.getPlayers()` 里选非杀手；目标被淘汰/离开后 `forgetCompassTarget()` + `updateCompassTargets()` 立即换人。右键仍可手动重新锁最近的。改 `endGame()` 的清理时别漏 `a.getCompassTargets().clear()`。
  - **猎人弓 + 无限箭**：弓是真正的原版弓——**按住右键蓄力，松开发射**。背包里被塞进 1 支带 `mm-hunter-ammo` 标记的无限箭弹（背包最后一格，不占快捷栏、不可丢弃），`MurderMysteryListener#onShootBow`（`EntityShootBowEvent`）对它 `setConsumeArrow(false)` / `setConsumeItem(false)`，所以永远不消耗 = 无限箭；无限箭弹被清掉/弓被捡走时由 `ensureHunterAmmo()` 补发。**商店卖的是「一击必杀弩」**（消耗普通箭，原版装填/发射），用商店的箭搭配。命中判定仍走 `onDamage` 的 projectile 分支。不要拦截 `PlayerInteractEvent` 右键自己生成箭。
  - **最后 N 秒粒子指引**：`tickGame()` 每秒调 `drawEndgameReveal()`；`isEndgameRevealActive()` 用 `gameEndMs - now <= endgame-reveal.seconds*1000` 判定。`drawRevealLines()` 只通过 `Player#spawnParticle(..., force=true)` 发给杀手本人，线到每个存活好人、终点加竖列粒子；右键指南针在窗口内立即重画一次（`Material.COMPASS` 0.5 秒冷却）。粒子不穿墙、没有碰撞/实体清理问题；高 `step` 密度会明显增加包量。
- **验证方法**：
  1. `/mm list` → 期望看到 `圣诞工厂 [WAITING] 已配置 0人`（线上当前只有这一个场）。
     - 等待室行动栏应显示「杀手 X% | 侦探 Y% | 猎人 Z%」；加人/退人后数字应变化（按当前人数模拟的实际中选率）。打完一局后，没当上某职业的玩家该职业概率上升，当上的归零；`/mm reload` 后计数不丢（存在 `role-history.yml`）。
  2. **3 人** `/mm join 圣诞工厂`（2 人时不应能开局：等待倒计时不启动，OP `/mm start` 提示「人数不足（至少 3 人）」），OP `/mm edit 圣诞工厂` 后 `/mm start`：期望中央 `3 / 2 / 1` 大字数数期间打不掉血、**谁手里都没有武器**（只有第 5 格绿宝石商店道具）；倒数后**只有本人**看到身份；**接着再数 `3 / 2 / 1`（副标题带自己的身份 +「职业道具 N 秒后发放…」），这 3 秒一样打不掉血**；
     第二段数完才发武器：杀手第 1 格铁剑 + 第 9 格指南针、猎人第 1 格无限箭的弓（聊天栏会出现「职业道具已发放！」），
     **但手持槽不跳**：全场人（含杀手/猎人）手上都还是第 5 格那颗绿宝石，要自己按 `1` 才切出武器。出生点分散在金块上，场内所有人的名字标签都被隐藏。
  3. 猎人：第二段倒数结束后按 `1` 应看到 1 把「猎人之弓」（lore 写「无限箭：按住右键蓄力，松开发射」），状态栏左侧显示 `猎人 | 金币 … ｜ 弓：无限箭`。**按住右键蓄力，松开发射**，箭不消耗（背包最后一格那支「无限箭弹」一直在）。射杀手 → 立即「无辜者获胜」结算（横幅应写 `杀手（X）被 Y 的弓消灭了。`）并回主城；射到好人（侦探/无辜者）→ **双方都静默变旁观**（不弹任何提示）、各掉 1 金锭、各留一具尸体。
  4. 杀手持铁剑杀一人：受害者**变旁观 + 聊天栏只有 TA 本人收到两行提示**（「你被打死了！你现在是旁观者…」+「输入 `/mm watch` 打开『存活玩家』菜单…」，公屏仍是静的）、原地掉 1 个金锭、留下尸体，杀手收到私聊「杀手之剑升级」；再用金锭右键绿宝石买一击必杀石剑打死杀手 → 立即「无辜者获胜」结算（横幅应写 `杀手（X）被 Y 消灭了。`）并回主城。
     另外可试：用石剑/徒手把杀手打进死亡（徒手路径走 `murdererEliminated`）→ 横幅同样是 `杀手（X）被 Y 消灭了。`；若杀手是掉出世界淘汰，横幅则写「杀手掉出了世界」。
     **死亡只看后台日志**：每次淘汰后台会多一行 `淘汰：<名字> 场地=<场名>`；用 `/mm` 商店买「死亡名单」右键→ 聊天框应列出已死玩家（按死亡顺序，没人死时不消耗）。
     另：砍人 / 射箭 / 探测 / 飞剑之后，把鼠标移到铁剑、弓、石剑、弩、真理之石上**不应出现耐久条**（全部不可毁）。
  5. `/mm stats` 看本人胜场/击杀有增长，并核对 `server/plugins/MiniGameLib/players.yml` 里的 `games.murdermystery.wins.<uuid>`。
  6. `/mm goldblocks 圣诞工厂` → 期望列出扫描到的金块坐标（带序号）和补货指针位置。
  7. **最后 30 秒不再发光**：开局后等到只剩 30 秒（或把线上 `game-seconds` 临时改成 `40` 快速验），**公屏不应再播报「最后 N 秒…」**，
     场上任何人（含杀手）**都不应有白色描边**，隔着墙看不到人；被淘汰的旁观同样没有描边。配置里的 `reveal-seconds` 已删除，改它不会再有任何效果。
  8. **杀手飞剑外形是铁剑**：杀手持铁剑按 `1` 切出后**右键** → 应看到**一把铁剑**从手上飞出去（不是雪球、也不是翻滚的平面图标），
     剑尖始终朝着飞行方向、飞多远都跟着走；命中玩家/方块/墙的瞬间铁剑一起消失（不应在原地留一把剑），落地后也不留残留实体。
     5 秒冷却期间右键只提示「飞剑冷却中」；对局结算/关服后场上不应残留任何铁剑展示实体（`/kill @e[type=item_display]` 可清残留，正常情况应为 0 个）。
  9. **tab 里看不出谁死了**：对局中让杀手刀掉一个人（或让人掉出世界），立刻按 `Tab` —— 死者应**仍显示在 `■ 密室杀手` 分区的 `[已加入]` 名单里、名字格式与活着的人完全一样**，
     不应掉到「大厅」那一栏；局末回主城后该玩家的游戏分区才消失（tab 变回大厅灰名字）。
 10. **杀手刀 2 秒冷却**：杀手在 2 秒内连砍两个好人，第二刀**不应秒人**（受害者不掉血、不掉金锭），杀手本人 actionbar 出现「杀手之剑冷却中（Ns）」；
     等满 2 秒再砍恢复一击必杀。飞剑不受这条影响（仍是自己的 5 秒冷却）。
 11. **杀手不再连庄（公平轮换）**：3~4 人连开 4~5 局，每局结算横幅的「本局杀手：X」**不应出现相邻两局同一个人**；
     同时 `server/plugins/MurderMystery/role-history.yml` 里 `round:` 每局 +1、`previous-murderers:` 变成上一局那个人。
     多打几局：没当上某职业的玩家，该职业的等待室概率应逐局上升；当上后该职业归零。想清零重来就停服删掉 `role-history.yml`。
     注意：人数刚好 2 人（`murderer-count: 1`）时禁连庄会让杀手**只能轮流**，这是预期行为。
 12. **职业装备丢不掉**：开局发完职业道具后，杀手按 `Q` 丢铁剑 / 切到第 9 格丢指南针、猎人丢弓 →
      **物品不落地**（地上没有掉落物、`/kill @e[type=item]` 之前也查不到），本人聊天栏收到
      「职业装备不能丢弃（铁剑 / 追踪指南针 / 弓），局内只能自己用。」；金锭、商店石剑/弩/箭、真理之石、死亡名单、断头饭**照常能丢**。
     把铁剑换到别的格、先丢绿宝石（仍提示「商店道具不能丢弃」）都不应影响这条判定。
 13. **观战者 `/mm watch`**：被杀手打死后在聊天栏应看到两行提示 → 输入 `/mm watch` 打开「存活玩家 (N 人存活)」菜单，
     每格一颗对应玩家的头颅（皮肤是本人、名字下面写「点击传送到 TA 那里」和距离）；点某一格 → 关菜单 + 立刻被传送到那个人身上。
     还活着的时候输入 `/mm watch` → 只回一句「你还在场上…」不开菜单；对局没开始/已结束 → 「对局还没开始或已经结束了」；
     菜单里的头颅点/拖都拿不走；点的人刚好刚死 → 提示「TA 已经不在了」并自动重开一份最新名单。
 14. **真理之石右键菜单**：对局中买/领到真理之石 → 右键（对空或对方块都行）→ 弹出「真理之石 · 选择探测目标 (N 人)」，
     一格里一颗头颅，只列**自己以外的存活玩家**（死掉的旁观者不在名单里，只剩自己时提示「没有其他存活玩家」不开菜单）；
     点某个头像 → 关菜单、那颗石头变成红石块（杀手）或钻石块（无辜者）、本人收到对应私聊、只消耗一颗；
     点自己背包格子 → 只取消、不探测；不选就关掉菜单 → 石头还在。**左键直接点人探测照旧可用**。
     杀手拿到真理之石右键 → 只提示「你是杀手，真理之石对你没有意义」，不开菜单、不消耗。
 15. **猎人弓掉落**：让杀手刀掉猎人 → 猎人原地掉落一把**发光**的「猎人之弓」，全场屏幕中央出现 title「猎人的弓掉落了」；
     杀手走到弓上**捡不起来**（actionbar 提示「杀手不能捡起猎人的弓」）；让其他玩家（侦探/无辜者）捡起后按 `1` 切出弓、**按住右键蓄力，松开发射（不需要箭）** 打杀手 → 正常一击必杀结算；
     让捡到弓的好人再被杀 → 弓再次掉在原地、再次发光、再次播报 title。局末/`/mm leave`/退出后，弓不应被带出对局世界。
 16. **杀手指南针自动跟踪**：开局拿到指南针后**不按右键**，移动时指针应持续指向最近的存活无辜者；让被指向的人被杀手刀掉（或退出/掉出世界）→
     指南针应在约 0.25 秒内自动转向下一个最近的存活无辜者；右键仍会立即重新锁定最近的玩家并报出距离。多杀手局里指针不会指向其他杀手。
 17. **最后 N 秒粒子指引**：把 `endgame-reveal.seconds` 设为 `60`（或 `50`）→ 对局进入最后 N 秒后，杀手每秒自动看到从自己连向**每个存活好人**的粒子线，终点有竖向粒子标记；粒子只发给杀手，其他玩家看不到。右键指南针会立即重画一次（0.5 秒节流）。把 `seconds` 设为 `0` 可关闭。**粒子会被墙挡住**，只适合指示方向/大致位置；要穿墙的真实轮廓仍需协议库或假实体方案。

- **未确认 / TODO**：
  - **2026-10-18 真理之石右键打开「选择探测目标」菜单**：新增 `TruthMenuHolder`（`InventoryHolder`，格子→UUID）+
    `MurderMysteryManager#openTruthMenu / onTruthMenuClick / truthTargetButton / truthResult / findTruthStoneSlot / consumeTruthStone / giveOrDrop`
    + `MurderMysteryListener#onTruthClick|onTruthDrag`；原来右键只提示「对局中不能放置方块」，现在直接开菜单（`notifyTruthStoneUsage` 已删）；
    左键点人探测保留（`handleTruthStone` 改为调 `truthResult` 拿结果再塞主手）。
    菜单选人**不看距离/视野**（用户要的就是"选玩家使用"），相对"必须贴脸左键"是机制放宽，手感需要实测确认。
    已 `build.ps1 -Only MurderMystery -Deploy` + `verify.ps1` 0 problems（`MurderMystery.jar 70,773 B`，旧 68,645 B），
    **未进游戏实测**（验证方法第 14 条是按代码推断的期望）。
  - **2026-10-18 观战者 `/mm watch` 存活玩家菜单 + 被打死后的聊天提示**：新增
    `WatchMenuHolder`（`InventoryHolder`，格子→UUID）+ `MurderMysteryManager#openWatchMenu / onWatchClick / headButton`
    + `MurderMysteryListener#onWatchClick|onWatchDrag` + `/mm watch` 子命令；`eliminate()` 里给死者本人两行私聊提示
    （公屏仍不播报死亡）。已 `build.bat -Only MurderMystery -Deploy` + `verify.ps1` 0 problems
    （`MurderMystery.jar 68,645 B`，旧 65,883 B），**未进游戏实测**（验证方法第 13 条是按代码推断的期望）。
    注意：Gui 标题「存活玩家 (N 人存活)」带人数、按 holder 判定，不靠标题字符串认界面。
  - **2026-10-18 职业装备（杀手铁剑/追踪指南针、猎人弓）局内不可丢弃**：新增 PDC 标记 `mm-role-item`
    （`murdererSword` / `murdererCompass` / `crossbowItem` 都打上），
    `onDrop` 在 RUNNING 阶段拦下并只私聊提示本人；金锭与商店道具的丢弃规则没动。
    已 `build.bat -Only MurderMystery -Deploy` + `verify.ps1` 0 problems（`MurderMystery.jar 65,883 B`，旧 65,595 B），**未进游戏实测**（第 12 条期望现象按代码推断）。
    注：铁剑被别的插件清掉时仍由 `selfHealItems` 兜底补发（猎人弓**没有**补发，现在至少丢不掉了）。
  - **2026-10-16 商店新增「断头饭」（1 金锭 5 牛排）**：商店第 6 格（slot 5）新商品——花 `steak-cost`（线上 1）金锭买
    `steak-amount`（线上 5）块牛排；物品名「&6断头饭」，lore 两行「吃完这顿，可能就没有下一顿了 / 饱腹 8 点，跑路更有力气」。
    纯食物、无特殊行为（无 PDC 标记、不限购、杀手也能买），可丢弃分享；原 slot 5-8 的灰玻璃填充缩为 6-8。
    `/mm help` 的商店价格行改为读配置动态生成（原来写死的"8 金锭=石剑或弩"早与线上 crossbow-cost: 12 不符）。
    已编译部署 + `verify.ps1` 0 problems（`MurderMystery.jar 63,455 B`，旧 62,736 B），但**未进游戏实测**。
  - **2026-10-16 真理之石验人结果方块加名字与介绍**：`handleTruthStone` 变出的红石块/钻石块不再是无名原版方块，
    改走新增的 `probeResultBlock(...)` 生成——红石块「&c杀手罪证」、钻石块「&a无辜证明」，
    lore 三行：探测对象 / 身份 /「可丢给队友分享，对局中不能放置」。**不带 truthKey**（`isTruthStone` 只认 PDC，
    所以结果方块不能拿去再探测一次）；丢弃、放置行为与原来完全一致。
    已编译部署 + `verify.ps1` 0 problems（`MurderMystery.jar 62,736 B`，旧 62,483 B），但**未进游戏实测**。
  - `plugin.yml` 声明的 `mm.use` 权限**在代码里没有被检查**（`/mm join` 只校验是不是玩家），是否有意如此未确认。
  - `join-signs` 线上是空 `{}`，告示牌加入牌流程只读了代码（`placeJoinSign`、`onSignClick`、`PlayerSignOpenEvent`），没在游戏里实测过。
  - **2026-10-16 两段式开局倒数 + 结算写凶手**：已 `build.bat -Only MurderMystery -Deploy` 编译部署、`verify.ps1` 0 problems
    （`MurderMystery.jar 62,074 B`），但**未进游戏实测**，上面第 2/3/4 条「期望现象」是按代码推断的。
    已知副作用：第二段那 3 秒里 `tickGame` 还没起跑（`startGameTask` 在第二段结束时才调），所以这 3 秒**没有状态栏、不刷金锭**，
    但 `gameEndMs` 在公布身份时就定好了（300 秒 / `game-seconds` 一局时长不受影响）。
  - 金块扫描的具体实现（先用区块调色板快筛等细节）是按本文原文转述的，本次没有读那一段代码。
  - **2026-10-16 最后 30 秒改为"全员发光"（取消杀手透视）**：已编译部署，但**未进游戏实测**（第 7 条期望现象按代码推断）。
  - **2026-10-17 删除"最后 N 秒全员发光" + 飞剑外形改铁剑（剑尖指飞行方向）**：
    `tickGame` 里的 `glowPhase` / `GLOWING` 药水效果 / 播报整段删除，`revealSeconds()`、`revealApplied`、`clearRevealGlow(a)`、
    `reveal-seconds` 配置项（源码模板 + 线上 config.yml）一并删除——**最后一局不再有任何发光，也没有"最后 N 秒"提示**。
    飞剑改成：判定实体仍是隐形雪球（`setItem(AIR)` + `setInvisible(true)`，命中判定/伤害链路完全没动），
    外形另起一个无碰撞的 `ItemDisplay` 拿铁剑，由 1 tick 任务贴到雪球位置、用四元数（先把剑尖转到 -Y 再对齐速度方向）让**剑尖始终指着飞行方向**；
    尺寸 `0.8`（想更显眼/更小就改 `spawnFlyingSwordDisplay` 和 `tickFlyingSwords` 里那两个 `new Vector3f(0.8f, …)`）。
    清理：命中时 `removeFlyingSwordDisplay` 立即收走、每 tick 任务兜底（雪球没了就收）、`endGame` 与 `onDisable` 都 `clearFlyingSwords()`。
    已编译部署 + `verify.ps1` 0 problems（`MurderMystery.jar 62,483 B`，旧 61,272 B），但**未进游戏实测**——第 7/8 条期望现象按代码推断；
    `ItemDisplay` 的旋转/尺寸观感若和预期不符，只需微调上面那两个 `Vector3f` 和 `swordRotation()`，不必动判定逻辑。
  - **2026-10-16 发完职业道具不再切手持槽**（`giveRoleItems` 武器改写入 `WEAPON_SLOT=0`、末尾统一 `holdShopItem`；`selfHealItems` 补发铁剑同样不碰手持槽）：
    已编译部署 + `verify.ps1` 0 problems（`MurderMystery.jar 61,362 B`），但**未进游戏实测**（第 2/3 条期望现象按代码推断）。
    发道具时会给当事人私聊一行「铁剑/弓在快捷栏第 1 格，按 1 切出」，免得杀手以为没发到武器。
    影响面：杀手原来靠"开局主手就有铁剑"提示，现在要自己按 `1`；`upgradeSword`、投掷飞剑、一击必杀判定都读**主手**，所以只在杀手实际手持铁剑时生效——**没改这部分逻辑**，行为与从前一致。
  - **2026-10-16 删掉死亡显示（死亡零提示）+ 局内道具全部不可毁**：`eliminate()` 不再给当事人任何提示（连"你被杀手杀害了！"都删了，
    含掉出世界的那句），只在后台打一行 `淘汰：<名字> 场地=…`；同时铁剑/弓/石剑/弩/真理之石/指南针全部 `setUnbreakable(true)`（`upgradeSword` 也补一次）。
    已编译部署 + 反编译核对（`MurderMystery.jar 61,272 B`，旧 61,362 B；死亡文案在 jar 里 0 处命中），但**未进游戏实测**。
    副作用：被淘汰者没有任何文字反馈（突然变旁观）；想知道谁死了只能买死亡名单 / 看尸体 / 看"剩余无辜者"。

# 密室杀手（MurderMystery）小游戏插件

适配本服务器（Paper 26.2 + HubCore 大厅）的密室杀手插件，玩法参照
[Plugily-Projects/MurderMystery](https://github.com/Plugily-Projects/MurderMystery/) 并做了本服定制。

## 玩法

- **角色**：每局随机分配 `murderer-count` 名杀手（默认 1）、`detective-count` 名**侦探**、`hunter-count` 名**猎人**（都可在 config.yml 调整），其余玩家为普通无辜者。**侦探与猎人都属于无辜者阵营**，胜负判定与普通无辜者一致。开局至少 `min-players`（线上 3）人。
- **猎人**：**身份公布后倒数 3 秒**发 **1 把「猎人之弓」**。弓是**原版弓 + 无限箭**：按住右键蓄力，松开发射；背包里被塞进 1 支不可丢弃、永不消耗的「无限箭弹」（`EntityShootBowEvent#setConsumeArrow(false)`），所以箭永远是∞。箭矢**命中杀手一击必杀**（→ 无辜者获胜）、**打到好人（无辜者/侦探/猎人）两人同归于尽**——和商店弩一个道理，只是提示写「弓」。弓本身买不到，也带「职业装备」标记，局内一律丢不掉（按 Q 只会收到私聊提示，物品留在背包里）。商店卖的是**一击必杀弩**（消耗普通箭，原版装填/发射）。
- **侦探**：每 `detective-stone-seconds`（默认 30 秒）自动获得一颗**真理之石**——左键点击玩家探测身份（杀手→红石块，无辜者→钻石块），道具会变成对应的方块（红石块显示名「**杀手罪证**」（红）、钻石块「**无辜证明**」（绿），介绍写明探测对象与身份，可丢给队友分享；不能再用来探测）。**第一颗从"身份公布"那一刻起算**（`revealRoles` 里写 `detectiveStoneAt`）。
- **杀手**：**不隐身**（可见），**身份公布后倒数 3 秒**拿到 1 把铁剑，近战一击必杀（**刀死一个人后有 2 秒冷却**，冷却中再砍人不生效），右键可投掷飞剑（5 秒冷却），无加速。
  **投出的飞剑外形是一把铁剑**（剑尖朝着飞行方向；判定实体仍是隐形的雪球，伤害规则不变）。
  **只有手持铁剑命中才能秒人**——弓、徒手、捡到的其他武器一律不生效（也不能买弓和箭）；此外快捷栏第 9 格有**追踪指南针**，会自动指向最近的存活无辜者（右键显示距离并重新锁定最近目标）。
  每击杀一人剑升级（锋利 +1，上限 5 级）。**杀光所有无辜者才算赢，撑到时间结束不算**（时间到是无辜者获胜）。
- **全体玩家**：开局（身份倒数第一段就）获得**商店道具**（绿宝石，栏位 4 = 快捷栏正中间，**发放时就拿在手上**、**不可丢弃**，不暴露身份所以不跟着职业道具延后），右键打开金锭商店：

  | 商品 | 价格 | 效果 |
  | --- | --- | --- |
  | 一击必杀石剑 | 8 金锭 | 近战：命中杀手一击必杀；**误伤无辜者两人同归于尽** |
  | 一击必杀弓 | 8 金锭 | 远程：箭矢命中杀手一击必杀；**误伤无辜者两人同归于尽**（杀手买不了，也买不了箭） |
  | 箭 ×1 | 2 金锭 | 搭配弓使用 |
  | 真理之石 | 10 金锭 | 左键点击玩家探测身份：杀手→红石块，无辜者→钻石块（杀手使用无效，不消耗） |
  | 死亡名单 | 1 金锭 | 右键使用：**按死亡顺序**列出本局已经死了哪些人（**死亡一律不播报、也不提示**，想知道谁死了只能买这个）；没人死亡时不消耗 |
  | 断头饭 ×5 | 1 金锭 | 纯食物（牛排，饱腹 8 点），吃完这顿可能就没有下一顿了；凶手与无辜者均可购买，可丢弃分享 |

- **开局**：玩家**随机分散传送到场地里的金块上**（每人一个互不重复的位置，面朝场地中心；金块不够时用中心周围的分散点补齐），
  此时**只发商店道具（绿宝石）**，谁都没有武器（绿宝石进中间第 5 格**并直接拿在手上**）。随后屏幕中央大字倒数 **3 / 2 / 1**（副标题「准备…」，倒数期间禁止一切伤害，防抢跑），
  倒数结束**公布各自身份**（中央大字幕 + 聊天栏细节，只有本人看得到），**一局 300 秒的计时也从这一刻开始算**；
  接着**再倒数 3 秒**（副标题「你是杀手/侦探/猎人/无辜者 + 职业道具 N 秒后发放…」），这 3 秒**同样禁止一切伤害**，
  倒数结束才发放**职业道具**——杀手拿到铁剑 + 追踪指南针、猎人拿到弓 + 无限箭弹（无辜者/侦探这次不发武器，仍是空手）。
  武器一律放进**快捷栏第 1 格**，**发完不切手持槽**：所有人（含杀手/猎人）手上都还停在中间那颗绿宝石上，谁想用武器自己按 `1` 切。
  **身份公布前、倒数期间、以及刚发完道具时没人手持武器，所以"看谁手里有剑"猜不出杀手。**
- **地图保护**：对局中**不能改动地图**——不能破坏/放置方块，不能把**盔甲架上的装备拿下来（"脱衣服"）**、不能拿走或旋转物品展示框里的东西、不能打掉画/展示框、也不能打坏地图上的装饰实体（盔甲架、展示框等）。
- **金锭来源**：每名玩家死亡掉落 1 个金锭；另外**按顺序给金块补货**——每隔 `gold-spawn-interval`（默认 10 秒），从上次停下的位置往后找，给接下来 `gold-spawn-amount`（默认 1）个**还没有金锭**的金块各补 1 个（每个金块上最多 1 个，指针循环前进；玩家捡走后等指针绕回来会补上）。金块坐标由插件**分帧扫描后缓存**（先用区块调色板快筛，所以有屋顶的房间里也能正确找到）；地图里一个金块都没有时才退回"随机地面"刷新。**开局与局末会清空场地里的掉落物**，上一局剩的金锭不会混进下一局。
- **丢弃**：对局中金锭与**商店买来的**道具（石剑/弩/箭/真理之石/死亡名单/断头饭）均可丢弃分享；
  **不可丢弃**：①商店道具（绿宝石）；②**职业装备**——杀手铁剑、追踪指南针、猎人弓。
  职业装备靠物品上的 `mm-role-item` 标记判定（`MurderMysteryManager#isRoleItem`，物品构造时就打上），
  拦截在 `MurderMysteryListener#onDrop`，只私聊提示本人（不会暴露身份）。理由：丢到地上别人能捡，等于把职业武器送给对手。
  商店里另外买来的箭不带标记，仍可丢。
- **不掉耐久**：局内由插件发放的道具**全部不可毁**（杀手铁剑、追踪指南针、猎人弓、商店石剑/弩、真理之石、死亡名单、商店绿宝石）——铁剑砍人、弓/弩射箭、真理之石探测、飞剑投掷都不掉耐久、不会打坏。玩家自己带进场的东西不受保护。
- **匿名**：对局中所有玩家的名字标签隐藏（含已淘汰的观战者），只能靠外观辨认**主动切出铁剑**的杀手（刚发完道具时所有人手上都是绿宝石），局末恢复；**死亡不上公屏**——被杀/误伤/掉出世界都**不播报**，后台只打一行 `淘汰：<名字> 场地=…`；想知道谁出局了只能靠**状态栏的"剩余无辜者"**、地上的尸体，或者花金锭买**死亡名单**。但**死者本人会在聊天栏收到两行私聊**（2026-10-18 加）：一行「你被打死了！你现在是旁观者…」，一行告诉 TA 用 `/mm watch` 打开存活玩家菜单点头像传送。
  **tab 也不区分死活**：被淘汰的人依然挂在 `■ 密室杀手` 分区的 `[已加入]` 名单里，名字格式与存活者完全一致（`eliminate()` 里改成 `HubAPI.setPlaying(...)`，不再 `clearGame`），否则一死就掉到「大厅」分区 = 当众公布谁死了；局末回主城时 `teleportToLobby` 会自动清掉游戏分区。
- **尸体**：被击杀的玩家会在原地留下一具躺倒的尸体——用 Paper 的 `Mannequin`（**完整玩家模型，整身都是死者的皮肤**，不是盔甲架拼的），不带名字标签；右键尸体可在聊天框显示「这具尸体是 XXX 的！」，局末自动清理。
- **胜负**：杀手被消灭 → 无辜者获胜（结算横幅写明**杀手是谁、被谁消灭的**：`杀手（X）被 Y 的弓消灭了。`；取不到凶手时写「凶手未知」）；
  **时间到（默认 180 秒）→ 无辜者获胜**（杀手没能杀光所有人）；无辜者全灭 → 杀手获胜。结算横幅会按实际原因给出，并**揭晓本局杀手是谁**。
- 出界 / 掉出世界 → 淘汰为观战者。
- **观战传送（`/mm watch`）**：只有**已被淘汰的旁观者**能用（还活着的会被拒绝并提示）。开一个「存活玩家 (N 人存活)」GUI：
  一格里一颗该玩家的 `PLAYER_HEAD`（皮肤就是本人），名字 + 「点击传送到 X 那里」+ 距离，**不写身份**（免得旁观者拿着身份去指认）。
  点某格 → 关界面 + 传送到该玩家当前位置（旁观者能自由飞，落地不会掉出世界淘汰，`handleMove` 只认存活玩家）。
  点的时候如果 TA 刚死/下线 → 提示一句并在下一 tick 重开一份最新名单；菜单只列**本场**存活玩家，空/拖拽全部 cancel（头颅拿不走）。
  实现在 `MurderMysteryManager#openWatchMenu` / `onWatchClick` + `MurderMysteryListener#onWatchClick|onWatchDrag` + `WatchMenuHolder`。

## 命令

玩家：`/mm join <场名>`、`/mm leave`、`/mm list`、`/mm stats [玩家]`、`/mm watch`（被淘汰后观战：打开存活玩家菜单，点头像传送）

OP：
| 命令 | 说明 |
| --- | --- |
| `/mm create <场名>` | 创建场地 |
| `/mm setup <场名>` | 配置模式：木棍圈范围（左=角1 右=角2）、金锄头中心点、纸等待点、绿宝石保存、屏障退出 |
| `/mm edit <场名>` / `/mm pos1|pos2|center|lobby` / `/mm save` | 命令方式编辑 |
| `/mm joinsign <场名>` | 把告示牌变成加入牌（随后右键点告示牌） |
| `/mm start` | 开始对局（排队中有 OP 时用手动开始） |
| `/mm remove <场名>`、`/mm rename <旧> <新>` | 删除 / 改名（同步菜单与告示牌） |
| `/mm goldblocks <场名>` | 列出该场扫描到的**所有金块坐标**（带序号 + 补货指针位置；扫描中会显示进度） |
| `/mm reload` | 重载 config.yml 并**重新扫描所有场地的金块**（场地坐标等改动仍需重启生效） |
| `/mm stats reset` | 清空全部战绩 |

## 配置（src/main/resources/config.yml）

| 键 | 默认 | 说明 |
| --- | --- | --- |
| min-players / max-players | 3 / 16 | 开局人数与上限（**3 人起**：杀手 + 侦探 + 猎人） |
| murderer-count | 1 | 每局杀手数量（其余全是无辜者） |
| hunter-count | 1 | 每局猎人数量（无辜者阵营，与侦探并存；开局发无限箭的弓） |
| role-chance.murderer / detective / hunter | 1.0 / 2.0 / 50.0 | 三个职业各自独立的累计权重：没当上 +step，当上清零，封顶 max；等待室显示的是按当前队列模拟的实际中选率 |
| game-seconds | 180 | 一局时长，时间到杀手获胜 |
| endgame-reveal.seconds | 60 | 最后多少秒给杀手画粒子线指向所有存活好人；0 = 关闭 |
| endgame-reveal.particle | END_ROD | 粒子类型（只支持不需要额外数据的，如 END_ROD / GLOW） |
| endgame-reveal.step | 1.5 | 粒子线取样间距（格）；越小越密、越费性能 |
| gold-per-death | 1 | 每人死亡掉落的金锭数 |
| gold-spawn-interval | 10 | 场地内定时刷新掉落金锭的间隔（秒） |
| gold-spawn-amount | 2 | 每次刷新掉落的金锭数量 |
| sword-cost | 8 | 商店：一击必杀石剑价格 |
| crossbow-cost | 8 | 商店：一击必杀弩价格（旧 `bow-cost` 仍作兜底） |
| arrow-cost | 2 | 商店：每支箭价格 |
| steak-cost / steak-amount | 1 / 5 | 商店「断头饭」：花几金锭买几块牛排（纯食物，无特殊效果） |

## 接入说明（已完成）

- `server/plugins/MurderMystery.jar` — 已部署
- `server/plugins/HubCore/config.yml` — 菜单新增「密室杀手」入口（icon=IRON_SWORD，
  command/join-prefix=`mm join`），源码 `HubCore/src/main/resources/config.yml` 同步
- `server/plugins/GameWorlds/config.yml` — 新增 `world_mm` 世界（pvp=true，禁刷怪/禁天气/禁爆炸破坏），
  源码 `GameWorlds/src/main/resources/config.yml` 同步
- 冒烟测试已通过：插件加载无报错、HubCore 登记「密室杀手」、world_mm 世界自动创建

## 首次部署步骤

1. 启动服务器（`start.bat`），进游戏后 `/gw tp world_mm` 搭建密室杀手地图（房间/走廊/掩体）。
2. 回大厅（或在地图外任意位置）：`/mm create mm1`（场名可自定）。
3. `/mm setup mm1` 进配置模式：
   - 木棍：左键/右键点两个对角方块圈定对局范围；
   - 金锄头：站在对局出生点右键设中心点；
   - 纸：站在大厅等待点右键设等待点；
   - 绿宝石：保存；屏障：退出配置。
4. 在大厅放一个告示牌，`/mm joinsign mm1`，右键告示牌绑定为加入牌。
5. 测试：`/mm join mm1`，凑 **3 人**（或 OP 站队列里用 `/mm start`）开局。

## 构建

```powershell
$env:JAVA_HOME = "$env:TEMP\gtb-jdk25\jdk-25.0.4.1+1"
& "C:\Users\Miracle\AppData\Local\Temp\gtb-maven\apache-maven-3.9.16\bin\mvn.cmd" clean package -o
```

产物：`target/MurderMystery.jar`（Paper API 26.2、Java 21 字节码，与现有插件一致）。

## 已知问题 / 修复记录

- **进服看到"你不在任何场中"（2026-10-15 修复）**：`HubCore` 进服时会调
  `teleportToLobby` → `HubAPI.notifyLobbyHandlers`，把"复原大厅状态"广播给所有注册了
  lobby handler 的游戏插件。本插件（连同 TntRun / HotPotato）的 `leave()` 在玩家本来就不在
  场中时会各喊一句"你不在任何场中"，所以进服一次刷三行。
  现在 `leave(Player)` 是 `leave(Player, boolean silent)` 的便捷重载，`registerLobbyHandler`
  一律用 `leave(p, true)`：**不在场中就直接返回，不打提示、也不再回调 `HubAPI.teleportToLobby`**
  （回主城本身由 HubCore 负责）。玩家主动离开（`/mm leave`、右键退出床）仍保留提示。
  → 结论：游戏插件的 lobby handler 只负责"清自己的状态"，**不要给玩家报"你不在场中"这类提示**，
  也不要在里面重复调 `teleportToLobby`（HubCore 已经会做，重复调用还会再触发一轮广播）。
