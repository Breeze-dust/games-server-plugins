# MurderMystery（密室杀手）—— 速查卡

> AI 先读这一段，能省掉读 3300 行源码
> **道具 / 机关改动规则见 [AGENTS.md](AGENTS.md)：新增或修改道具时必须同步 items.yml、mechanisms.yml 和 README。**

- **一句话职责**：每局随机出 1 名杀手（持铁剑、不隐身）、1 名侦探和 1 名猎人，其余是无辜者（**最少 3 人才能开局**）；杀手用铁剑近战一击必杀、杀光所有无辜者才算赢，无辜者捡死亡掉落的金锭去绿宝石商店买「一击必杀弩」反杀（也可以买把一次性的「留人剑」把别人定住），**猎人开局自带 1 把无限箭的弓**（原版弓手感：按住右键蓄力，松开发射；箭矢命中杀手一击必杀、打到好人两人同归于尽）；杀手被消灭或时间到（线上 300 秒）都是无辜者获胜。**猎人死亡时弓会掉在原地（发光 + 全员 title「猎人的弓掉落了」）；杀手不能捡，其他玩家可以捡起来使用，捡到的人再死亡会再次掉落并再次播报。** 杀手的追踪指南针会自动指向最近的存活无辜者，目标死亡后自动换下一个；**最后 `endgame-reveal.seconds` 秒杀手每秒自动看到连向所有存活好人的粒子线（只发给杀手本人，右键指南针可立即重画）**。
- **地图机关 / 新道具（2026-11-05）**：**售货机**由「钻石块 + 按钮」组成，OP 对准按钮执行 `/mm vending add` 登记（`/mm vending remove|list` 取消/查看）；对局中右键已登记按钮，会按 `plugins/MurderMystery/mechanisms.yml` 里 `vending.goods` 的权重随机给一件货物（直接进背包，满了掉脚下），**每次抽货扣 `vending.cost-gold`（默认 2）个金锭，不够就不卖**，同一玩家有 `vending.cooldown-seconds` 冷却。**GT混沌汽水**（药水瓶，`items.yml` 的 `items.gt-soda`）：右键随机触发 10 种事件之一（迅捷、限时迅捷、迅捷5后死亡、迈达斯之手、阵营音效、全图嚎叫、可乐播报、变身鸡、胀气变大、隐身 7 秒），每个事件都能在 `items.yml` 的 `effects.gt-soda` 里单独开关、调权重、调时长/等级/音效/金锭数；**抽到事件才消耗一瓶**。迈达斯之手让下一次近战（空手也行）直接杀死对方并把尸体换成金锭，好人误杀好人默认仍两人一起淘汰（可配置关闭）。**场地选区内的告示牌只有管理员能修改文字**（非管理员右键不会打开编辑器，`SignChangeEvent` 也会被取消）。
- **老虎机机关（2026-11-06）**：地图上放拉杆，OP 对准执行 `/mm slot add` 登记；对局中右键已登记拉杆，**先扣 `slot-machine.cost-gold`（默认 2）金锭**，再按 `mechanisms.yml` 的 `slot-machine.effects` 权重抽一个效果：金锭翻倍（保底 6，全房间播报）、+5、+3、-2、获得「烫手山芋」。老虎机也按场地隔离，**每根拉杆记录 type 与所属场地**。
- **烫手山芋**：从老虎机抽出时 30 秒倒计时（action bar 显示），并向全房间播报「有人获得了烫手山芋！」；拿着它攻击其他玩家可把山芋和**剩余倒计时**一起转手（不重置）；倒计时结束在持有者位置爆炸（不破坏地形）并**只淘汰持有者本人，不算任何人的击杀**。不能丢弃。
- **RPG（2026-11-06）**：铁锄头外观，**只有杀手能获得/使用**（售货机抽货会按 `items.yml` 的 `murderer-only: true` 过滤）。右键朝视线方向发射一枚直线导弹，命中玩家/方块或达到最大距离时爆炸，**爆炸半径内所有好人淘汰**（不误伤其他杀手；默认不伤开火者，可配）。半径、飞行距离、速度、命中判定、是否消耗、冷却都在 `items.yml` 的 `items.rpg` 里配。
- **头衔「无声之刃」（2026-11-13）**：杀手阵营获胜时，**本局没有用近战刀/飞剑击杀过任何人**的杀手自动获得头衔「无声之刃」，并收到一条私聊提示（已拥有过就不再提示）。用刀记录在 `MurderMysteryArena#getKnifeUsers`，打点在 `MurderMysteryListener#onDamage`（近战刀两处 + 飞剑一处）；RPG、迈达斯之手、鸡形态击杀都**不算**"用刀"。头衔通过 `com.hub.api.GameTitles#grantOrCreate`（MiniGameLib）发给头衔插件，没装头衔插件时静默跳过。头衔 ID `mm_no_knife`，内容常量在 `MurderMysteryManager#TITLE_NO_KNIFE*`；**插件启用时 `MurderMysteryPlugin#onEnable` 会先调 `GameTitles.ensure` 把定义建好**，所以管理/头衔菜单里立刻能看到（靠 `plugin.yml` 的 `softdepend: [PlayerTitles]` 保证头衔插件先启用）。想改名字/颜色/描述，直接在 PlayerTitles 里改这个头衔即可（引擎不会覆盖已存在的定义）。
- **房间配置（2026-11-06）**：`plugins/MurderMystery/rooms.yml` 按场地独立配置金锭刷新（`gold-spawn-enabled` / `gold-spawn-interval` / `gold-spawn-amount` / `gold-per-death`），没写的项沿用 config.yml 的全局值；`/mm rooms` 查看每个房间当前生效值。
- **传送器机关（2026-11-05）**：地图上放一个拉杆，OP 对准执行 `/mm teleporter add` 登记为传送器；站到落点执行 `/mm teleporter point` 登记（可多个），`/mm teleporter list` 查看，`/mm teleporter point remove <编号>` 删除。对局中右键已登记拉杆 → 随机传送到本场地内其他已登记落点（有冷却与最小距离），**每次扣 `teleporter.cost-gold`（默认 5）个金锭，不够就不传**。机关参数在 `plugins/MurderMystery/mechanisms.yml`，登记数据在 `mechanisms-data.yml`。**每根拉杆都记录自己的机关 type 和所属场地**（目前是 `teleporter`），登记时绑定场地、使用时只读同场地数据，**不同地图的机关不会串联**；拉杆和落点都必须放在该场地的 pos1/pos2 选区内。
- **加入播报（2026-11-23）**：`join()` 现在只发**一条全服公屏**：`&8[&8密室杀手&8][&e场地名&8] &e玩家 &7加入了游戏&7（&eN&7 人）`，其中 N = 该场地当前等待人数。原来的「场地内 N 人」+ HubCore「[密室杀手] 玩家 加入了游戏」两条已合并；因为 HubCore 的 `announceJoin` 没有人数参数，这里改用 `Chat.broadcastTitled` 本地拼整行、不再调 `HubAPI.announceJoin`。
- **语音（Simple Voice Chat 可选集成，2026-11-22；2026-11-23 加「死者频道」）**：服务端装 SVC 的 Bukkit/Paper 版（`voicechat-bukkit-*.jar`，Modrinth 有，支持 MC 26.2）就自动生效，**不用改 SVC 源码**。SVC 默认就是「真实世界麦」：玩家不在任何语音组时声音按距离衰减（服务端 `max_voice_distance`，默认 48 格）；但**语音组是无方向、恒定音量的**，玩家自己建组就能跨全场通话，等于开挂。所以 MM 做三件事：①进局/离场/结算时把人踢出语音组；②局内禁止建组和入组；③**被淘汰时把死者 `setGroup(死人组)`**——一个隐藏的固定组「死者频道」（固定 UUID、`persistent` + `hidden`），组语音恒定音量、不受距离限制，死者隔着半张图也能商量；默认 `NORMAL` 类型（`dead-group.isolated: false`）：死者之间能听见彼此，**还能听见附近活人说话**（按 `max_voice_distance` 衰减），但说不到活人、活人也听不见死者。两个坑：SVC 的 `setGroup(已存在的组)` 内部走 `ServerGroupManager#joinGroup`，会派发 `JoinGroupEvent`——正是 `block-groups` 用来禁止局内入组的事件，所以必须给「死人组 + 死者」留**豁免**，否则死者被自己的规则挡在门外；反过来死者也**退不出**这个组（取消 `LeaveGroupEvent`），否则会掉回旁观者距离麦、声音仍会被附近活人听见（SVC 的 `spectator_interaction=false` 挡不住这一点），只有 `/mm leave` 或结算才放出来。参数在 `config.yml` 的 `voice-chat.enabled` / `voice-chat.block-groups` / `voice-chat.dead-group.enabled` / `voice-chat.dead-group.isolated`（前三个默认 `true`，`isolated` 默认 `false` = 不隔离、死者能听见附近活人）。**2026-11-28 起**：等待室放行 MiniGameLib 的主城公共组「大厅公共频道」（`VoiceChatHook#waiting` + `isHubGroup` 豁免 `JoinGroupEvent`），真正开局时 `MurderMysteryManager#start` 调 `voiceChat.play(id)` 关掉放行并把人踢出公共组；其余局内规则不变。
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
  - 击杀：`MurderMysteryListener#onDamage` 判定 → `handleMurdererKill`（受害者淘汰 + 掉金锭 + 杀手之剑锋利 +1，上限 5；**近战刀无冷却，飞剑（剑气）有 5 秒冷却**）/ `handleInnocentKill`（无辜者阵营消灭杀手 → 直接结算，**结算横幅写明「杀手（X）被 Y 消灭了」**）/ `handleMutualDeath`（误伤好人 → 两人同归于尽，武器名写进私聊提示：弓/弩）。
  - 其他循环：`tickGame`（补金锭 + 最后 N 秒 `drawEndgameReveal` 粒子指引 + 状态栏；**已不再有"最后 N 秒全员发光"**）、`handleMove`（由 `startBoundsCheck` 每 0.5 秒查一次出界淘汰）。
  - 结算：`endGame(a, innocentsWin, detail)`（**公屏固定两行：第一行只写谁赢 `无辜者获胜！` / `杀手获胜！`，第二行 `detail` 写关键信息**，如谁杀了谁/时间到还剩几人；之后揭晓本局杀手、`recordWin`，然后**公屏再提示「对局结束，3 秒后返回大厅…」，每个玩家屏幕下方 actionbar 每秒倒数 3/2/1，3 秒后才统一送回主城**（新增 `scheduleLobbyReturn`；2026-11-22 改，不再结算瞬间传送，这 3 秒内玩家不受任何伤害；**这 3 秒里已淘汰玩家保持旁观者模式，只有真正回大厅时才恢复生存——被杀的杀手也不会在结算瞬间变回生存**）；**每个在场玩家屏幕中央还会收到 5 秒大字幕，第一行「无辜者胜利！」/「杀手胜利！」、第二行同一句关键信息；结算不再公屏展示前三名（`Top3` 仅静默记分）**）；另有 `checkMurdererWin`（杀手获胜时用 `murdererWinDetail` 生成第二行）、`murdererEliminated`（非商店武器致死，用 `Player#getKiller()` 写凶手名）、`handleLeaveDuringGame`。
  - 买武器：商店道具（绿宝石，栏位 4）右键 → `openShop` → `onShopBuy` → `buyItem`。
  - 真理之石（商店 `truth-stone-cost` / 侦探每 `detective-stone-seconds` 秒补给）：**右键打开「真理之石 · 选择探测目标」菜单**，
    一格格头颅、点谁探测谁；也可以左键直接点人探测。结果都是把**那颗石头变成**红石块（杀手）或钻石块（无辜者）。
    菜单：`openTruthMenu` / `onTruthMenuClick`（`TruthMenuHolder` 按格子记 UUID）；探测核心 `truthResult`（杀手用会被拒绝且不消耗）；
    消耗逻辑 `findTruthStoneSlot` / `consumeTruthStone`（一叠多颗只扣一颗，多余结果方块进背包、满了掉脚下）。
- **命令**：`/mm …`（插件命令 `murdermystery`，别名 `mm` / `murder`；管理权限 `mm.admin`，`plugin.yml` 里还声明了 `mm.use`）
  - 玩家：`join <场名>`、`leave`、`list`、`stats [玩家]`、`watch`（**被淘汰后**开「存活玩家」菜单，点头像传送）、`help`
  - OP：`create <场名>`、`remove <场名>`、`rename <旧场名> <新场名>`、`edit <场名>`、`pos1|pos2|center|lobby`、`save`、`setup <场名>`、`setup-exit`、`start`（需先 `/mm edit`；队列里有 OP 时可右键「开始游戏」纸）、`joinsign <场名>`、`goldblocks <场名>`（别名 `gold`）、`vending add|remove|list`（对准钻石块上的按钮登记/取消售货机）、`teleporter add|remove|list|point`（对准拉杆登记传送器/登记落点）、`slot add|remove|list`（对准拉杆登记老虎机）、`rooms`（查看每房间金锭设置）、`reload`、`stats reset`（清空全部战绩）
- **配置**：`server/plugins/MurderMystery/config.yml` —— 这份是真实生效的；源码 `MurderMystery/src/main/resources/config.yml` 只是模板。线上当前值：
  - `min-players: 3`、`max-players: 16`、`murderer-count: 1`、`detective-count: 1`、`hunter-count: 1`
  - `game-seconds: 300`（时间到 → 无辜者胜）、`detective-stone-seconds: 90`
  - `gold-per-death: 1`、`gold-spawn-interval: 10`、`gold-spawn-amount: 3`
  - 商店：`sword-cost: 2`、`crossbow-cost: 12`（旧 `bow-cost` 仍作兜底）、`arrow-cost: 2`、`truth-stone-cost: 8`、`death-note-cost: 3`、`steak-cost: 1`（断头饭 ×`steak-amount: 5`）
  - `role-chance.{murderer,detective,hunter}.{base,step,max}`：三个职业的累计权重，默认 1.0 / 2.0 / 50.0；等待室行动栏显示的是按当前队列模拟的实际中选率。
  - 场地在 `arenas.<场名>`（pos1/pos2/center/lobby，都是 `org.bukkit.Location`），加入牌在 `join-signs`。**代码兜底默认值和这份文件不一样**（`game-seconds` 180、`crossbow-cost` 8、`gold-spawn-amount` 2、`truth-stone-cost` 10、`death-note-cost` 1、`detective-stone-seconds` 30），以线上这份为准。
- **玩家记录**：命名空间 `murdermystery`，结构 `wins.<uuid>` / `kills.<uuid>`；唯一文件 `server/plugins/MiniGameLib/players.yml`（见 `docs/玩家记录.md`），读写在 `MurderMysteryManager#load`、`recordWin`、`recordKill`、`getWins`、`getKillsStat`；清空命令 `/mm stats reset`（OP，一次清掉 wins+kills，不能只清一个人）。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 玩法/击杀/商店/结算/战绩/机关/汽水事件 | `MurderMystery/src/main/java/com/murdermystery/MurderMysteryManager.java` |
  | 道具总表 / GT汽水事件配置 | `MurderMystery/items.yml`（线上 `server/plugins/MurderMystery/items.yml`） |
  | 机关参数（售货机/传送器/老虎机） | `MurderMystery/mechanisms.yml`（线上 `server/plugins/MurderMystery/mechanisms.yml`） |
  | 每房间金锭刷新设置 | `MurderMystery/rooms.yml`（线上 `server/plugins/MurderMystery/rooms.yml`） |
  | 机关参数（传送器冷却/距离/音效/提示） | `MurderMystery/mechanisms.yml`（线上 `server/plugins/MurderMystery/mechanisms.yml`） |
  | 机关登记数据（拉杆 type / 传送点） | `server/plugins/MurderMystery/mechanisms-data.yml`（由命令自动写入） |
  | `items.yml` 解析 | `MurderMystery/src/main/java/com/murdermystery/MurderMysteryItems.java` |
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
  3. 猎人：第二段倒数结束后按 `1` 应看到 1 把「猎人之弓」（lore 写「无限箭：按住右键蓄力，松开发射」），状态栏左侧显示 `猎人 | 金币 … ｜ 弓：无限箭`。**按住右键蓄力，松开发射**，箭不消耗（背包最后一格那支「无限箭弹」一直在）。射杀手 → 立即结算，**公屏两行**：第一行 `无辜者获胜！`，第二行 `杀手 X 被 Y 的弓消灭了。`，随后回主城；射到好人（侦探/无辜者）→ **双方都静默变旁观**（不弹任何提示）、各掉 1 金锭、各留一具尸体。
  4. 杀手持铁剑杀一人：受害者**变旁观 + 聊天栏只有 TA 本人收到两行提示**（「你被打死了！你现在是旁观者…」+「输入 `/mm watch` 打开『存活玩家』菜单…」，公屏仍是静的）、原地掉 1 个金锭、留下尸体，杀手收到私聊「杀手之剑升级」；再用金锭右键绿宝石买一击必杀弩打死杀手 → 立即结算，**公屏两行**：`无辜者获胜！` + `杀手 X 被 Y 消灭了。`，并回主城。
     另外可试：徒手把杀手打进死亡（徒手路径走 `murdererEliminated`）→ 同样是 `无辜者获胜！` + `杀手 X 被 Y 消灭了。`；若杀手是掉出世界淘汰，第二行则写 `杀手 X 掉出了世界。`
     **死亡只看后台日志**：每次淘汰后台会多一行 `淘汰：<名字> 场地=<场名>`；用 `/mm` 商店买「死亡名单」右键→ 聊天框应列出已死玩家（按死亡顺序，没人死时不消耗）。
     另：砍人 / 射箭 / 探测 / 飞剑之后，把鼠标移到铁剑、弓、留人剑、弩、真理之石上**不应出现耐久条**（全部不可毁）。
  5. `/mm stats` 看本人胜场/击杀有增长，并核对 `server/plugins/MiniGameLib/players.yml` 里的 `games.murdermystery.wins.<uuid>`。
  6. `/mm goldblocks 圣诞工厂` → 期望列出扫描到的金块坐标（带序号）和补货指针位置。
  7. **最后 30 秒不再发光**：开局后等到只剩 30 秒（或把线上 `game-seconds` 临时改成 `40` 快速验），**公屏不应再播报「最后 N 秒…」**，
     场上任何人（含杀手）**都不应有白色描边**，隔着墙看不到人；被淘汰的旁观同样没有描边。配置里的 `reveal-seconds` 已删除，改它不会再有任何效果。
  8. **杀手飞剑外形是铁剑**：杀手持铁剑按 `1` 切出后**右键** → 应看到**一把铁剑**从手上飞出去（不是雪球、也不是翻滚的平面图标），
     剑尖始终朝着飞行方向、飞多远都跟着走；命中玩家/方块/墙的瞬间铁剑一起消失（不应在原地留一把剑），落地后也不留残留实体。
     右键投掷飞剑（剑气），**5 秒冷却**；冷却中右键只提示「飞剑冷却中」，对局结算/关服后场上不应残留任何铁剑展示实体（`/kill @e[type=item_display]` 可清残留，正常情况应为 0 个）。
  9. **tab 里看不出谁死了**：对局中让杀手刀掉一个人（或让人掉出世界），立刻按 `Tab` —— 死者应**仍显示在 `■ 密室杀手` 分区的 `[已加入]` 名单里、名字格式与活着的人完全一样**，
     不应掉到「大厅」那一栏；局末回主城后该玩家的游戏分区才消失（tab 变回大厅灰名字）。
 10. **杀手刀无冷却 / 剑气 5 秒冷却**：杀手连续砍两个好人，两刀都**应立即秒人**（受害者淘汰、掉金锭），不再出现「杀手之剑冷却中」；
     飞剑（剑气）连续右键时，第二次起只出现「飞剑冷却中（Ns）」，等满 5 秒才能再投。
 11. **杀手不再连庄（公平轮换）**：3~4 人连开 4~5 局，每局结算横幅的「本局杀手：X」**不应出现相邻两局同一个人**；
     同时 `server/plugins/MurderMystery/role-history.yml` 里 `round:` 每局 +1、`previous-murderers:` 变成上一局那个人。
     多打几局：没当上某职业的玩家，该职业的等待室概率应逐局上升；当上后该职业归零。想清零重来就停服删掉 `role-history.yml`。
     注意：人数刚好 2 人（`murderer-count: 1`）时禁连庄会让杀手**只能轮流**，这是预期行为。
 12. **职业装备丢不掉**：开局发完职业道具后，杀手按 `Q` 丢铁剑 / 切到第 9 格丢指南针、猎人丢弓 →
      **物品不落地**（地上没有掉落物、`/kill @e[type=item]` 之前也查不到），本人聊天栏收到
      「职业装备不能丢弃（铁剑 / 追踪指南针 / 弓），局内只能自己用。」；金锭、商店留人剑/弩/箭、真理之石、死亡名单、断头饭**照常能丢**。
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
 18. **售货机**：在地图里放一个钻石块、在它侧面/上面安一个按钮，OP 对准按钮执行 `/mm vending add` → 聊天栏应报出按钮坐标；`/mm vending list` 能看到它。对局中右键该按钮 → 应先扣 `vending.cost-gold`（默认 2）个金锭，再按 `mechanisms.yml` 的 `vending.goods` 权重给一件货物（聊天栏「售货机吐出了 …（花费 2 金锭）」）；**金锭不足 2 个时只提示「金锭不足」，不给货也不扣冷却**；3 秒内再点只显示冷却；对局外右键仍是普通按钮。`/mm vending remove` 后再点不再发货。
 19. **场地告示牌保护**：用非管理员账号在场地选区里右键告示牌 → 不应打开编辑器，actionbar 提示「非管理员不能修改场地内的告示牌」；若通过其他方式触发 `SignChangeEvent` 同样会被取消。OP / `mm.admin` 账号可以正常改字（仍然不能破坏/放置方块）。
 20. **GT混沌汽水**：把 `mechanisms.yml` 的 `vending.goods` 里 `gt-soda` 权重临时调到很大，从售货机抽到后右键 → 应消耗 1 瓶并随机触发 1 种事件；事件文案/音效/药水效果与 `effects.gt-soda` 配置一致；把某个事件 `enabled: false` 或 `weight: 0` 后 `/mm reload`，它不应再出现。汽水在**对局外右键不会消耗**。
 21. **特殊事件**：`speed-doom`（默认杀手不能触发）触发时全场聊天栏出现「<玩家>：你跑不过我你信吗」、本人紫色「你觉得你的血条有点发紫」，30 秒后本人被淘汰；`midas` 下一次近战（空手也行）直接杀人、原地掉 5 个金锭且**不留尸体**，好人误杀好人默认两人都淘汰；`chicken` 期间其他玩家只看到一只鸡跟着移动、本人不能右键任何道具，杀手打鸡可把本人淘汰；`inflate` 期间体型变大、移动变慢，5 秒后恢复。局末/`/mm leave`/退出后汽水与售货机货物从背包清掉。
 22. **传送器**：地图里放拉杆，OP 对准 `/mm teleporter add` → 聊天栏报出坐标（并标明场地）；站到至少两个不同位置各执行一次 `/mm teleporter point`，`/mm teleporter list` 应列出**当前场地**的拉杆与落点（编号从 1 开始）。对局中右键拉杆 → 应先扣 `teleporter.cost-gold`（默认 5）个金锭，再播放末影人传送音效 + 粒子，并随机传到**另一个**落点（距离玩家或拉杆小于 `min-distance` 的落点都会被跳过，保证不会传回脚下/原地）；**金锭不足 5 个时只提示「金锭不足」，不传送也不扣冷却**。冷却内再点只显示 actionbar 冷却。拉杆应手动拉下并在 10 tick 后回弹。`/mm teleporter point remove 1` 按编号删除落点；`/mm teleporter remove` 取消拉杆后，再右键恢复为普通拉杆行为。机关数据应写入 `server/plugins/MurderMystery/mechanisms-data.yml` 的 `levers.teleporter.<场名>` 与 `points.teleporter.<场名>`。
 23. **机关按场地隔离**：准备两个都配置了 pos1/pos2 的场地 A、B。在 A 里登记拉杆和落点，在 B 里放一个未登记的拉杆 → 在 B 对局中右键 B 的拉杆应只是普通拉杆；`/mm teleporter list` 站在 A 里只列 A 的机关，站在 B 里只列 B 的（站在场地外才显示全部）。把 A 的拉杆用 `/mm teleporter add` 时若不在任何场地内，应提示必须先放进场地选区。旧数据（没有场地分组）加载后应自动迁移到 `levers.teleporter.<场名>`。
 24. **老虎机**：场地里放拉杆，OP 对准 `/mm slot add`；`/mm slot list` 应列出该场地的老虎机与启动花费。对局中右键拉杆 → 先扣 2 金锭，再按权重触发效果：大奖（金锭翻倍、至少 6 个、全房间聊天栏播报）、+5、+3、-2（不足则扣光）；金锭不足 2 个时只提示、不启动也不扣冷却。把某个效果 `enabled: false` 或 `weight: 0` 后 `/mm reload` 应不再出现。
 25. **烫手山芋**：抽到后背包出现「烫手山芋」，action bar 每秒显示剩余秒数，全房间聊天栏出现「有人获得了烫手山芋！」；30 秒后持有者位置爆炸（不破坏地形）并只淘汰本人（不算击杀）。拿着山芋攻击其他玩家 → 被塞的一方会播放**受击动画 + 伤害粒子 + 受击音效**（攻击方也有攻击音效），山芋和**剩余倒计时**一起转给对方（对方聊天栏提示剩余秒数，倒计时不重置）；对方已经拿着时只提示、不重复给。山芋按 Q 丢不掉。
 26. **房间配置**：在 `rooms.yml` 给某个场地写 `gold-spawn-interval: 20` / `gold-spawn-amount: 2` / `gold-per-death: 3` → `/mm reload` 后 `/mm rooms` 应显示该场地的值，其他场地仍显示全局值；该场地补金锭间隔变为 20 秒、每次补 2 个金块、死亡掉 3 金锭。设 `gold-spawn-enabled: false` 后该场地不再自动补货。
 27. **隐身效果**：把 `effects.gt-soda.invisibility` 权重调高，喝汽水抽到后 → 玩家进入隐身且**不冒药水粒子**，手中物品消失（`hide-held-item: true` 时），聊天栏收到「你变得透明」；7 秒后手中物品自动回到原槽。把 `hide-held-item` 改成 `false` 后，隐身时手中物品应保持可见。
 28. **RPG**：把 `items.rpg` 和 `mechanisms.yml` 的 `vending.goods.rpg` 配好后，让杀手从售货机抽到（好人不应抽到 RPG）。杀手右键 → 发射直线飞行的火焰/烟雾导弹，命中玩家/方块/最大距离后爆炸，半径内好人全部淘汰；试着调大 `explosion-radius` 后再开一炮，范围应明显变大；默认自己不会被炸死，`damage-shooter: true` 时应把自己也炸死并判无辜者胜。
  29. **结算公屏两行**：公屏先出现 `========== 游戏结束 ==========`，紧接着**第一行只写谁赢**（`无辜者获胜！` / `杀手获胜！`），**第二行写关键信息**，之后才是「本局杀手：X」；**不再显示前三名**（`Top3` 只静默记分，避免外部排行丢数据）。同时**每个玩家屏幕中央出现 5 秒大字幕**：第一行 `无辜者胜利！` / `杀手胜利！`，第二行就是上面的关键信息。**此外结算后不再瞬间回大厅**：公屏多一行 `对局结束，3 秒后返回大厅…`，每个玩家屏幕下方 actionbar 依次显示 `3`/`2`/`1`（每秒一次、带轻响），3 秒后才真正传回主城；这 3 秒里互砍/摔落/掉出世界都不掉血、不死亡，这期间 `/mm join` 会取消这次排队（直接进新局），`/mm leave` 则立刻回大厅。
      - 杀手杀光无辜者 → `杀手获胜！` + `杀手 X 击杀了最后一名无辜者 Y。`
      - 最后一名无辜者掉出世界/被汽水或山芋炸死（无凶手）→ `杀手获胜！` + `最后一名无辜者 Y 倒下了。`
      - 最后两名无辜者互砍同归于尽 → `杀手获胜！` + `最后两名无辜者 X 与 Y 同归于尽。`
      - 时间到 → `无辜者获胜！` + `时间到，杀手没能杀光所有人（还剩 N 名无辜者）。`
      - 杀手被打死 → `无辜者获胜！` + `杀手 X 被 Y 消灭了。`（弓/弩会写武器名；杀手退出/掉出世界各有对应文案）
  30. **Simple Voice Chat 语音（可选）**：服务端 `plugins/` 放 `voicechat-bukkit-*.jar` 后重启（首次会在 `plugins/voicechat/` 生成 `voicechat-server.properties`），日志应出现 SVC 启动和本插件的「已接入 Simple Voice Chat：局内玩家自动退出语音组…」；客户端装同版本 SVC 并连上（默认 UDP 24454）。进局前先在大厅建一个语音组，再 `/mm join` → 进局瞬间应被踢出该组（按 V 看组已消失），从此只能听/说附近玩家，走远声音变小到听不见；局内按 V 建组应失败/被取消（`block-groups: true`）。被淘汰后：**一死就会被拉进「死者频道」**——按 V 应看到组名 `死者频道`，和另一个淘汰者隔着半张图说话音量不变；默认（`dead-group.isolated: false`，NORMAL 类型）死者**还能听见附近活人说话**（走远声音变小到听不见），但活人听不见你、你也说不到活人；死者自己按 V 退组应失败（组还在，退不出去）；`/mm leave` 或结算回主城后应能重新自由建组。把 `voice-chat.enabled` 改成 `false` 再 `/mm reload` → 进局不再退组、局内可正常建组。**反过来，没装 SVC 的服务器**：启动日志只出现「未安装 Simple Voice Chat，跳过语音集成（装上后重启服务器即自动生效）」，MurderMystery 照常 Enabling（2026-11-22 修掉了这里的 `NoClassDefFoundError`），所有非语音玩法不受影响。
  31. **淘汰后停在死亡位置**：开一局，让杀手砍死一名无辜者 → 死者应**就在被打死的地方**变成旁观者（不跳回场地中心），尸体也在同一位置，本人聊天栏两行提示照旧；换个死法（迈达斯之手、烫手山芋、速度末日）同样原地变旁观者。再让一名玩家掉出世界/走出场地边界 → 这种淘汰**仍会**把人拉回场地中心（否则旁观者会卡在虚空）。`/mm watch` 点存活玩家头像传送不受影响。
  32. **加入播报合并 + 结算旁观（2026-11-23）**：开一局并记下场上人数，让一名玩家 `/mm join` 或点菜单加入 → 全服公屏应**只出现一条** `[密室杀手][场名] 玩家 加入了游戏（N 人）`（N 为加入后的人数），不再出现「玩家 加入了（N 人）」和「[密室杀手] 玩家 加入了游戏」两行。再打一局让杀手最后被猎人弓/弩打死：结算 3 秒倒数期间，被杀杀手的视角应保持**旁观者**（能飞行、不能攻击），actionbar 正常倒数；3 秒后回主城才恢复生存模式。先被淘汰的其他玩家同样在这 3 秒保持旁观。

  33. **死者频道（2026-11-23；2026-11-27 默认改成 NORMAL）**：装 SVC 的服务器开一局，让两名玩家先后被淘汰 → 两人按 V 都应看到组名 `死者频道`（组列表里**看不到**这个组可选，因为 `hidden`）；让两人站在地图两端同时说话 → 音量和贴脸一样、完全不受距离限制；再让活人走到死者旁边说话 → 死者**能听见附近活人**（默认 `dead-group.isolated: false`，NORMAL 类型，音量按距离衰减、走远听不见），但活人听不见死者、死者也说不到活人（死者的麦克风只进组）。死者自己按 V 退组 → 应退不掉（组还在，声音仍只传组内）；再 `/mm leave` 或等结算回大厅 → 组消失，可以重新自由建组。把 `voice-chat.dead-group.enabled` 改 `false` 再 `/mm reload` → 现场死者立刻被退出组，之后新死的玩家只被退组（回到旧的旁观者距离麦）；再把 `isolated` 改回 `true`（退回只听死者）要重启服务器才生效（组类型在建组那一刻确定）。

- **未确认 / TODO**：
  - **2026-11-27 死者频道默认从 ISOLATED 改成 NORMAL（死者能听见附近活人）**：用户要求「死的人自动进入观战组、无视距离说话」，复查后确认这功能 2026-11-23 已实现（就是「死者频道」），这次只调语义：`dead-group.isolated` 默认 `true` → `false`，`VoiceChatHook#deadGroupOf` 就建 `Group.Type.NORMAL`。依据（`.tmp/svc` 源码）：`Server.java:427-437` 的 `processMicPacket` 只在「玩家不在组里或组是 `OPEN`」时才走 `processProximityPacket`，所以 NORMAL 组员的麦克风**只进组** → 活人听不见死者（不报点）；`Server.java:536-555` 的 `broadcast` 只跳过「接收者所在组 `isIsolated()`」的情况 → NORMAL 组员（死者）仍能收到附近活人的距离麦，按 `max_voice_distance` 衰减。**纯配置 + 文档改动，没有改 Java**（`MurderMysteryManager` 只是把 4 个配置键原样传给 `VoiceChatHook`）。三份 config 同步：模板 `src/main/resources/config.yml`、随包 `新服务器小游戏插件/MurderMystery/config.yml`、线上 `server/plugins/MurderMystery/config.yml`；注释改成「true = 死者只能听见同为淘汰者的玩家；false（默认）= 还能听见附近活人说话（按距离衰减），活人仍然听不见死者」。README 速查卡 / 验证方法 30、33 / 配置表 / 语音实现段同步。重新打包：28 个 class、140,830 B、SHA256 `D876E7113A7C09E31F38E68B68B0EE2703E4713BEB22B6055C807C4F76EC4212`（上一版 140,810 B），已覆盖 `新服务器小游戏插件/MurderMystery.jar` 与线上 `server/plugins/MurderMystery.jar`，旧 jar 备份 `.build-check/MurderMystery-live-before-normal.jar`。冒烟 `.build-check/smoke-normal.log`：`Done (26.375s)`、MurderMystery v1.1.0 正常 Enabling、无 SVC 时打「未安装 Simple Voice Chat，跳过语音集成」、4 个场地 / 8 把匕首 / 10 条汽水事件、stop 后 Disabling 正常、exit 0（日志里的 watchdog 线程转储来自 spark 的 tile-entity 采样，与本插件无关）。**线上没装 SVC，只能编译期验证**；装上后改 `isolated` 要重启（组类型在建组那一刻确定）。
  - **2026-11-23 死者频道（SVC 语音组，替代原生旁观者范围）**：`VoiceChatHook#eliminated()` 不再只是 `setGroup(null)`，改成把死者 `setGroup(死人组)`——固定 UUID `8a6f0f4c-4b2a-4f8d-9d3e-2c5b1a7e6f10`、名字 `死者频道`、`persistent` + `hidden`、默认 `Group.Type.ISOLATED`；组语音恒定音量、不受距离限制，所以死者跨全图能通话，且（ISOLATED 时）说不到活人、也听不见活人。为不被自己的 `block-groups` 规则拦死：①`Hook#onJoinGroup` 加豁免（`事件里的组 ID == 死人组 ID && 连接玩家在 dead 集合里`）——`setGroup(已存在的组)` 在 SVC 里走 `ServerGroupManager#joinGroup`（`.tmp/svc/common/.../voice/server/ServerGroupManager.java:105-137`）→ 派发 `JoinGroupEvent`，正是 `block-groups` 取消的那条；`onCreateGroup` 同理（服务端懒建组时 connection 为 null，也要放行）。②新增 `onLeaveGroup`：死者不许退出死人组（否则掉回旁观者距离麦，而 `spectator_interaction=false` 挡不住旁观者声音被附近活人听见，见 `Server.java:461-502` 的 `processProximityPacket`），因此 `exit()` 里改成**先** `dead.remove(id)` **再** `release(id)`，否则正常离场也会被这道规则拦住。③接口 `VoiceChatBridge#load` 的两个 boolean 换成 `record Settings(enabled, blockGroups, deadGroup, deadGroupIsolated)`，`MurderMysteryManager#load` 一次读 4 个配置键；`/mm reload` 关掉死者组时会把现场死者放回自由状态（先清标记再 `releaseAllDead`）。配置新增 `voice-chat.dead-group.enabled` / `voice-chat.dead-group.isolated`（默认都 `true`），`config.yml` 注释同步改写。

    编译：`D:\minigame\server\jdk\bin\javac.exe '@D:\mm\.build-check\javac-args.txt'`（`--release 21`；`sources.txt` / `javac-args.txt` 这次把之前漏掉的 `VoiceChatBridge.java` / `VoiceChatHook.java` 补进去了）→ `D:\mm\.build-check\classes-voice`（**28 个 class**，exit 0，仅 3 条既有的 deprecation 警告）；`jar --create --manifest .build-check\manifest-voice.txt` 打 classes + `src/main/resources` → **140,810 B**（上一版 137,945 B，多出 `VoiceChatBridge$Settings.class`），SHA256 `03AFF1C1EEDF9D77CBC896EE5E88005D8C4600750544E19ED30F9446935F65A2`，已复制到 `D:\mm\新服务器小游戏插件\MurderMystery.jar` 与 `D:\minigame\server\plugins\MurderMystery.jar`（三处 SHA256 一致；线上旧 jar 备份在 `.build-check\MurderMystery-live-before-deadgroup.jar`）。配置**没有整份覆盖线上**（线上 `crossbow-cost: 12`、`death-note-cost: 3` 等本地化差异要保留），只把 `voice-chat.dead-group` 段与对应注释手术式追加到 `D:\minigame\server\plugins\MurderMystery\config.yml` 和 `D:\mm\新服务器小游戏插件\MurderMystery\config.yml`，三份 config 都用 PyYAML 校验通过（`voice-chat` 解析出 `{'enabled': True, 'block-groups': True, 'dead-group': {'enabled': True, 'isolated': True}}`）；未改 items/mechanisms，未上传 `mechanisms-data.yml`。
    已做服务端加载冒烟（Paper 26.2，`.build-check\smoke-deadgroup.ps1` → `smoke-deadgroup.log`）：`Done (21.682s)`、MurderMystery v1.1.0 正常 Enabling、日志出现「未安装 Simple Voice Chat，跳过语音集成」、4 个场地 / 8 把匕首 / 10 条汽水事件正常加载、`stop` 后正常 Disabling、退出码 0，全程无本插件异常（imageio / Ambiguous plugin name / jline 是环境旧问题）。**注意：线上这台服务器没装 SVC，所以 SVC 分支只能靠编译期（voicechat-api 2.5.27）验证，`setGroup(死人组)` 的真实行为未在游戏内实测**——验证方法第 33 条。
  - **2026-11-23 加入播报合并 + 被杀的杀手结算时保持旁观**：①`join()` 删掉场地广播「玩家 加入了（N 人）」和 `HubAPI.announceJoin(plugin.getName(), p.getName())`，改成一条 `Chat.broadcastTitled` 全服消息 `[密室杀手][场地] 玩家 加入了游戏（N 人）`（HubCore 的三参 `announceJoin` 只有场地没有人数，且线上 HubCore 版本未必支持三参，故在插件本地拼整行；N = 该场当前人数）。②`endGame` 的已淘汰循环删掉 `pp.setGameMode(GameMode.SURVIVAL)`，让最后被杀的杀手和其他淘汰者在结算 3 秒里继续旁观；`scheduleLobbyReturn` 在 `HubAPI.teleportToLobby(pp)` 前显式 `pp.setGameMode(GameMode.SURVIVAL)` 兜底（旧版 HubCore 的 teleportToLobby 不一定改模式），存活者循环保持不变。
    编译：`D:\minigame\server\jdk\bin\javac.exe`（JDK 25，`--release 21`）+ 103 个 server/libraries jar + MiniGameLib + HubCore + voicechat-api → `D:\mm\.build-check\classes-20261123`（27 个 class，exit 0，仅 3 条 deprecation 警告）；`jar --create --manifest` 打 classes + `src/main/resources` → **137,945 B**（上一版 137,907 B），SHA256 `C3F77CA29466C591DBB6EDB7AD5BCFA3DFA7D26BAE732956692A0D603257BECA`，已复制到 `D:\mm\新服务器小游戏插件\MurderMystery.jar` 与 `D:\minigame\server\plugins\MurderMystery.jar`（两处 SHA256 一致）。未改 items/mechanisms/config，未上传 `mechanisms-data.yml`。
    已做服务端加载冒烟（Paper 26.2，`D:\mm\.build-check\smoke-joinspectator.ps1` → `smoke-joinspectator.log`）：`Done (28.271s)`、MurderMystery v1.1.0 正常 Enabling、4 个场地 / 8 把匕首 / 10 条汽水事件正常加载、`stop` 后正常 Disabling、退出码 0，全程无本插件异常（日志里的 imageio/Ambiguous plugin name/jline 是其它插件或环境的旧问题）。**未进游戏实测**（没有真人玩家跑完一局）——验证方法第 32 条。
  - **2026-11-22 结算后延迟 3 秒回大厅（不再瞬间传送）**：`endGame` 两个还原循环仍立刻
    `playerArena.remove` + `HubAPI.clearGame(id)`，但不再直接 `HubAPI.teleportToLobby`，改成调新增的
    `scheduleLobbyReturn(pp)`（`MurderMysteryManager`，常量 `LOBBY_RETURN_SECONDS = 3`、字段
    `lobbyReturnTasks`）：结算时公屏多一行 `对局结束，3 秒后返回大厅…`，每个玩家屏幕下方 actionbar
    每秒倒数 `3`/`2`/`1`（`Chat.actionBar` + `BLOCK_NOTE_BLOCK_HAT` 轻响），第 3 秒才
    `HubAPI.teleportToLobby`。因为玩家此时已从 `playerArena` 摘掉，`MurderMysteryListener` 加了
    `awaitingLobby` 判定：`onAnyDamage` 对排队玩家一律取消伤害（互砍 / 摔落 / 虚空都不掉血）、
    `onDamage` 再补一道 `isReturningToLobby` 守卫、`inArena` 对排队玩家仍返回 true（破坏 / 放置 /
    杀怪 / 饥饿保护照旧）。排队会被 `join()`（进新局）、`leave()`、`handleQuit()`、
    `cancelAllTasksAndGlow()`（`/mm reload` / 关服）取消；`/mm leave` 时若还在排队则立即传送
    （`leave(p, true)` 静默路径只取消不传送，避免 HubCore lobby handler 递归）。
  - **2026-11-22 顺带修：没装 Simple Voice Chat 时插件 enable 直接崩**：之前 `MurderMysteryManager`
    里 `new VoiceChatHook(plugin)` 一加载就抛 `NoClassDefFoundError: de/maxhenkel/voicechat/api/VoicechatPlugin`
    （`VoiceChatHook` 内部类 implements `VoicechatPlugin`，SVC 不在就整个类加载失败），比语音开关还早，
    导致没装 SVC 的服务器上整个 MurderMystery 起不来。现在改成接口 `VoiceChatBridge`（真实实现
    `VoiceChatHook implements VoiceChatBridge`，`shutdown/load/enter/exit/eliminated` 改 public）：
    `VoiceChatBridge.create(plugin)` 先在 `getPlugin("voicechat") == null` 时直接返回 `Noop` 空实现，
    有 SVC 才 `Class.forName("com.murdermystery.VoiceChatHook")` 反射实例化，失败也退回空实现并打 warning，
    这样语音彻底是可选的了。
    编译：`D:\minigame\server\jdk\bin\javac.exe`（JDK 25.0.4.1，`--release 21`）+ 103 个 server/libraries
    jar + MiniGameLib + HubCore + annotations + voicechat-api → `D:\mm\.build-check\classes-lobby2`
    （27 个 class，exit 0，仅 3 条 deprecation 警告）；`jar` 打 `classes-lobby2` + `src/main/resources`
    → 137,907 B（上一版 136,014 B / 更早 135,538 B），SHA256 `0D667E99…B072A6`，已复制到
    `D:\minigame\server\plugins\MurderMystery.jar` 与 `D:\mm\新服务器小游戏插件\MurderMystery.jar`
    （三处 SHA256 一致）。未改 items/mechanisms/config，未上传 `mechanisms-data.yml`。
    已做服务端加载冒烟（Paper 26.2，`D:\mm\.build-check\smoke-lobby.ps1` → `smoke-lobby.log`）：
    `Done (22.653s)`、`MurderMystery v1.1.0` 正常 Enabling、日志出现「未安装 Simple Voice Chat，跳过语音集成」、
    4 个场地 / 8 把匕首 / 10 条汽水事件正常加载、`stop` 后正常 Disabling、退出码 0，全程无本插件异常。
    **未进游戏实测**（没有真人玩家跑完一局）——验证方法第 29 条（详情见其末尾）够验这次改动。
  - **2026-11-22 淘汰后停在死亡位置（不再传送到场地中心）**：`eliminate()` 里删掉无条件的
    `p.teleport(a.getCenter())`，改成 `if (!a.contains(p.getLocation())) p.teleport(a.getCenter());`
    ——被杀/中毒/山芋/迈达斯之手等**场地内**死亡一律原地变旁观者（尸体、掉的金锭都在原地），
    只有**掉出世界/走出边界**（`handleMove` 先淘汰再拉回）这种才传送到中心，避免旁观者卡在虚空。
    `handleMidasAttack` 的 `deathLoc` 注释同步改成「出界死亡会被拉回中心，所以先记下来」，行为不变。
    编译：`D:\minigame\server\jdk\bin\javac.exe`（JDK 25）不加 `--release`、103 个 server/libraries jar +
    MiniGameLib + HubCore + annotations + voicechat-api；`jar --update` 只替换 `com/` 下的 class，
    其余资源条目（config/items/mechanisms/plugin/rooms.yml、manifest）原样保留；产物 135,538 B
    （旧 135,305 B，25 个 class，SHA256 `AA3E818B…93A8`），已复制到
    `D:\mm\新服务器小游戏插件\MurderMystery.jar` 与 `D:\minigame\server\plugins\MurderMystery.jar`
    （两侧 SHA256 一致）。
    未改 items/mechanisms/config，**未进游戏实测**——验证方法第 31 条。（注：这条的 135,538 B 版本已被
    上一条 137,907 B 的「3 秒后回大厅 + 语音可选加载」版本覆盖，行为改动仍包含在当前 jar 里。）
  - **2026-11-22 开局/结算体验调整**：①去掉 OP `/mm start` 后的 3 秒「游戏即将开始」倒计时：删掉
    `com.hub.util.StartCountdown` 的 import、字段、懒加载方法和 `load()` 预热调用，`start()` 里不再有
    `startCountdown().begin(...)` 守卫，直接 `setState(RUNNING)` 进入原有的两段式身份倒数；源码模板本来就没有
    `countdown` 段，线上 `plugins/MurderMystery/config.yml` 里旧插件自动写入的 `countdown` 段也已删除。②OP 在等待队列时只暂停自动倒计时、
    不再反复刷「OP 在队列中，等待手动开始」：`WaitCountdown.Host#opQueuedMessage()` 返回空串，`onMessage` 跳过空白文案。
    ③`endGame` 删掉 `Top3.broadcast(...)` 的三行前三名展示，保留 `Top3.award(...)` 静默记分（避免外部排行丢数据）；
    在第二行 `detail` 后调 `broadcastTitle(..., 5)`，每个在场/已淘汰且在线玩家屏幕中央显示 5 秒大字幕：第一行
    `&a&l无辜者胜利！` / `&c&l杀手胜利！`，第二行就是 `detail`（谁杀了谁 / 时间到剩几人 / 杀手退出…）。
    ④结算时不再刷「玩家离开了（剩 N 人）」：`endGame` 两个还原循环先把玩家从 `playerArena` 摘掉再
    `HubAPI.teleportToLobby`；`leave()` / `handleQuit()` 也提前摘映射并在传送前 `HubAPI.clearGame(id)`，
    这样 HubCore 的 lobby handler 再触发 `leave(p,true)` 时走 `name == null` 的静默早退；等待室正常离开仍会提示。
    编译：JDK 25 的 `javac --release 21` + 103 个 server/libraries jar + MiniGameLib + annotations；产物
    `MurderMystery-20261122.jar` 129,078 B（23 个 class，major 65），已复制到
    `D:\minigame\server\plugins\MurderMystery.jar` 与 `D:\mm\新服务器小游戏插件\MurderMystery.jar`
    （SHA256 `C04A3B92…AB610`）。未改 items/mechanisms/config，未上传 `mechanisms-data.yml`。
    已做服务端加载冒烟：Paper 26.2 启动后 MurderMystery v1.1.0 正常启用、`plugins` 列表可见、`stop` 正常停用，
    日志没有本插件的加载异常；**未进游戏实测**（没有真人玩家跑完一局）——验证方法第 29 条 + 开局/等待室各看一遍。
  - **2026-11-21 结算公屏改成两行（第一行谁赢，第二行谁杀谁）**：`endGame(a, innocentsWin, detail)` 现在固定播
    `========== 游戏结束 ==========` → 第一行 `&a&l无辜者获胜！` / `&c&l杀手获胜！` → 第二行 `detail`；12 处调用点全部改成只写原因
    （原来的「XX获胜！」前缀删掉，避免和第一行重复）。杀手获胜的第二行由新增 `murdererWinDetail(a)` 生成，依赖
    `MurderMysteryArena` 新增的 `lastKiller` / `lastVictim` 两个 UUID 字段与 `noteKill / getLastKiller / getLastVictim / clearLastKill`
    （`endGame` 清场时 `clearLastKill()`）：杀手杀最后一人 / 最后一人倒下 / 两人同归于尽三种文案。`noteKill` 打点在
    `handleMurdererKill / handleInnocentKill / handleMutualDeath / murdererEliminated / handleMove / detonateRpg /
    explodeHotPotato / applySpeedDoom / handleMidasAttack`。**同归于尽**的判定看淘汰顺序：好人对砍时先淘汰受害者、再淘汰动手的人，
    所以要求「倒数第二条 = 最后受害者、最后一条 = 动手的人（且不是杀手）」，弓/弩误伤与迈达斯之手"好人互杀"都走这条。
    已编译部署（`MurderMystery.jar 129,324 B`，旧 128,790 B；需要配套带 `GameTitles` 的新版 MiniGameLib），
    **未进游戏实测**——验证方法第 29 条。
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
    含掉出世界的那句），只在后台打一行 `淘汰：<名字> 场地=…`；同时铁剑/弓/留人剑/弩/真理之石/指南针全部 `setUnbreakable(true)`（`upgradeSword` 也补一次）。
    已编译部署 + 反编译核对（`MurderMystery.jar 61,272 B`，旧 61,362 B；死亡文案在 jar 里 0 处命中），但**未进游戏实测**。
    副作用：被淘汰者没有任何文字反馈（突然变旁观）；想知道谁死了只能买死亡名单 / 看尸体 / 看"剩余无辜者"。

# 密室杀手（MurderMystery）小游戏插件

适配本服务器（Paper 26.2 + HubCore 大厅）的密室杀手插件，玩法参照
[Plugily-Projects/MurderMystery](https://github.com/Plugily-Projects/MurderMystery/) 并做了本服定制。

## 玩法

- **角色**：每局随机分配 `murderer-count` 名杀手（默认 1）、`detective-count` 名**侦探**、`hunter-count` 名**猎人**（都可在 config.yml 调整），其余玩家为普通无辜者。**侦探与猎人都属于无辜者阵营**，胜负判定与普通无辜者一致。开局至少 `min-players`（线上 3）人。
- **猎人**：**身份公布后倒数 3 秒**发 **1 把「猎人之弓」**。弓是**原版弓 + 无限箭**：按住右键蓄力，松开发射；背包里被塞进 1 支不可丢弃、永不消耗的「无限箭弹」（`EntityShootBowEvent#setConsumeArrow(false)`），所以箭永远是∞。箭矢**命中杀手一击必杀**（→ 无辜者获胜）、**打到好人（无辜者/侦探/猎人）两人同归于尽**——和商店弩一个道理，只是提示写「弓」。弓本身买不到，也带「职业装备」标记，局内一律丢不掉（按 Q 只会收到私聊提示，物品留在背包里）。商店卖的是**一击必杀弩**（消耗普通箭，原版装填/发射）。
- **侦探**：每 `detective-stone-seconds`（默认 30 秒）自动获得一颗**真理之石**——左键点击玩家探测身份（杀手→红石块，无辜者→钻石块），道具会变成对应的方块（红石块显示名「**杀手罪证**」（红）、钻石块「**无辜证明**」（绿），介绍写明探测对象与身份，可丢给队友分享；不能再用来探测）。**第一颗从"身份公布"那一刻起算**（`revealRoles` 里写 `detectiveStoneAt`）。
- **杀手**：**不隐身**（可见），**身份公布后倒数 3 秒**拿到 1 把铁剑，近战一击必杀（**无冷却**，可以连续刀人），右键可投掷飞剑（剑气，**5 秒冷却**），无加速。
  **投出的飞剑外形是一把铁剑**（剑尖朝着飞行方向；判定实体仍是隐形的雪球，伤害规则不变）。
  **只有手持铁剑命中才能秒人**——弓、徒手、捡到的其他武器一律不生效（也不能买弓和箭）；此外快捷栏第 9 格有**追踪指南针**，会自动指向最近的存活无辜者（右键显示距离并重新锁定最近目标）。
  每击杀一人剑升级（锋利 +1，上限 5 级）。**杀光所有无辜者才算赢，撑到时间结束不算**（时间到是无辜者获胜）。
- **全体玩家**：开局（身份倒数第一段就）获得**商店道具**（绿宝石，栏位 4 = 快捷栏正中间，**发放时就拿在手上**、**不可丢弃**，不暴露身份所以不跟着职业道具延后），右键打开金锭商店：

  | 商品 | 价格 | 效果 |
  | --- | --- | --- |
  | 一击必杀弩 | 12 金锭 | 远程：弩矢命中杀手一击必杀；**误伤无辜者两人同归于尽**（杀手买不了，也买不了箭） |
  | 箭 ×1 | 2 金锭 | 搭配弩使用 |
  | 留人剑（木剑，一次性） | 2 金锭 | 近战：**命中任意玩家** → 对方 3 秒内无法移动（只锁水平移动，仍能转身/攻击/用道具）；**不造成伤害**，命中后剑立即消失。杀手不能买 |
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
- **丢弃**：对局中金锭与**商店买来的**道具（留人剑/弩/箭/真理之石/死亡名单/断头饭）均可丢弃分享；
  **不可丢弃**：①商店道具（绿宝石）；②**职业装备**——杀手铁剑、追踪指南针、猎人弓。
  职业装备靠物品上的 `mm-role-item` 标记判定（`MurderMysteryManager#isRoleItem`，物品构造时就打上），
  拦截在 `MurderMysteryListener#onDrop`，只私聊提示本人（不会暴露身份）。理由：丢到地上别人能捡，等于把职业武器送给对手。
  商店里另外买来的箭不带标记，仍可丢。
- **不掉耐久**：局内由插件发放的道具**全部不可毁**（杀手铁剑、追踪指南针、猎人弓、商店留人剑/弩、真理之石、死亡名单、商店绿宝石）——铁剑砍人、弓/弩射箭、真理之石探测、飞剑投掷都不掉耐久、不会打坏。玩家自己带进场的东西不受保护。
- **匿名**：对局中所有玩家的名字标签隐藏（含已淘汰的观战者），只能靠外观辨认**主动切出铁剑**的杀手（刚发完道具时所有人手上都是绿宝石），局末恢复；**死亡不上公屏**——被杀/误伤/掉出世界都**不播报**，后台只打一行 `淘汰：<名字> 场地=…`；想知道谁出局了只能靠**状态栏的"剩余无辜者"**、地上的尸体，或者花金锭买**死亡名单**。但**死者本人会在聊天栏收到两行私聊**（2026-10-18 加）：一行「你被打死了！你现在是旁观者…」，一行告诉 TA 用 `/mm watch` 打开存活玩家菜单点头像传送。**淘汰后会原地变成旁观者，停在死亡位置**（2026-11-22 改：不再传送到场地中心），想看谁自己飞过去或用 `/mm watch` 点人传送。
  **tab 也不区分死活**：被淘汰的人依然挂在 `■ 密室杀手` 分区的 `[已加入]` 名单里，名字格式与存活者完全一致（`eliminate()` 里改成 `HubAPI.setPlaying(...)`，不再 `clearGame`），否则一死就掉到「大厅」分区 = 当众公布谁死了；局末回主城时 `teleportToLobby` 会自动清掉游戏分区。
- **尸体**：被击杀的玩家会在原地留下一具躺倒的尸体——用 Paper 的 `Mannequin`（**完整玩家模型，整身都是死者的皮肤**，不是盔甲架拼的），不带名字标签；右键尸体可在聊天框显示「这具尸体是 XXX 的！」，局末自动清理。
- **胜负**：**结算公屏固定两行**——第一行只写谁赢（`无辜者获胜！` / `杀手获胜！`），第二行写关键获胜信息（谁杀了谁 / 时间到还剩几人 / 杀手退出…）。具体地：杀手被消灭 → 无辜者获胜（第二行写明**杀手是谁、被谁消灭的**：`杀手 X 被 Y 的弓消灭了。`；取不到凶手时写「凶手未知」）；
  **时间到（默认 180 秒）→ 无辜者获胜**（第二行写「时间到，杀手没能杀光所有人（还剩 N 名无辜者）」）；无辜者全灭 → 杀手获胜（第二行写「杀手 X 击杀了最后一名无辜者 Y」；环境致死写「最后一名无辜者 Y 倒下了」，两人同归于尽写「最后两名无辜者 X 与 Y 同归于尽」）。两行之后仍会**揭晓本局杀手是谁**；随后公屏提示「对局结束，3 秒后返回大厅」，每人 actionbar 倒数 3/2/1，**3 秒后才传回主城，期间不掉血**（2026-11-22 改）。**每个在场玩家屏幕中央同时显示 5 秒大字幕（第一行胜负、第二行关键信息）；结算不再展示前三名。** 等待室的自动开始提示保持原样，但队列里有 OP 时只暂停自动倒计时、不再刷「OP 在队列中，等待手动开始」。
- 出界 / 掉出世界 → 淘汰为观战者（只有这一种会额外把旁观者拉回场地中心，避免卡在虚空里出不来）。
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
| `/mm vending add\|remove\|list` | 对准「钻石块 + 按钮」登记 / 取消售货机，或列出已登记按钮；货物权重在 `mechanisms.yml` |
| `/mm slot add\|remove\|list` | 对准拉杆登记 / 取消老虎机，或列出已登记拉杆 |
| `/mm rooms` | 查看每个房间当前生效的金锭刷新设置（在 `rooms.yml` 修改） |
| `/mm teleporter add\|remove\|list` | 对准拉杆登记 / 取消传送器；`list` 列出传送器拉杆与落点 |
| `/mm teleporter point [remove <编号>]` | 把脚下位置登记为传送落点，或按编号删除落点 |
| `/mm reload` | 重载 config.yml、items.yml、mechanisms.yml 并**重新扫描所有场地的金块**（场地坐标等改动仍需重启生效） |
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
| sword-cost | 2 | 商店：留人剑（木剑，一次性）价格 |
| crossbow-cost | 8 | 商店：一击必杀弩价格（旧 `bow-cost` 仍作兜底） |
| arrow-cost | 2 | 商店：每支箭价格 |
| steak-cost / steak-amount | 1 / 5 | 商店「断头饭」：花几金锭买几块牛排（纯食物，无特殊效果） |
| voice-chat.enabled | true | 启用 Simple Voice Chat 集成（装了 SVC 才生效）：进局退组 + 局内禁组，让语音保持距离麦 |
| voice-chat.block-groups | true | 局内禁止创建/加入语音组；设为 false 时只保证进局瞬间退组，玩家仍可重新建组绕过距离衰减 |
| voice-chat.dead-group.enabled | true | 被淘汰时把人拉进隐藏的「死者频道」组：组语音恒定音量、无距离限制；false = 退回旧行为（只退组，靠 SVC 原生旁观者范围） |
| voice-chat.dead-group.isolated | false | 死者频道是否隔离：`false`（默认）= NORMAL 类型，死者除了互相能听见，还能听见附近活人说话（按距离衰减），活人仍然听不见死者；`true` = ISOLATED，只听死者 |

### 道具配置（items.yml）

`server/plugins/MurderMystery/items.yml` 记录本游戏所有道具，改完 `/mm reload` 生效：

- `items.<id>`：`enabled`、`type`（`gt-soda` / `shop-sword` / `shop-crossbow` / `arrow` / `truth-stone` / `death-note` / `steak` / `rpg` / `plain`）、`amount`、`name`、`lore`；`shop-sword`（留人剑）额外有 `freeze-seconds`（命中后定身秒数，默认 3）；`murderer-only: true` 的道具只有杀手能抽到。
- RPG 专属配置（`items.rpg`）：`explosion-radius`（爆炸半径）、`max-distance`、`speed`、`hit-radius`、`consume`、`cooldown-seconds`、`damage-shooter`。
- `effects.gt-soda.<事件>`：每个事件都有 `enabled`、`weight`、`allow-murderer`，以及各自的 `duration-seconds` / `amplifier` / 金锭数 / 音效 / 文案等；事件 key 与实现的对应关系：
  `speed-burst`、`speed-long`、`speed-doom`、`midas`、`faction-sound`、`global-howl`、`cola`、`chicken`、`inflate`、`invisibility`（`hide-held-item` 控制是否隐藏手中物品）。
- **售货机货物、价格、冷却已移到 `mechanisms.yml` 的 `vending` 段**；旧版仍放在这里的配置会继续兼容读取并给出提示。

### 机关配置（mechanisms.yml）

`server/plugins/MurderMystery/mechanisms.yml` 只放机关参数，由插件读取、不会被改写：

- `vending.enabled` / `vending.cost-gold` / `vending.cooldown-seconds`：售货机开关、每次抽货扣多少金锭（默认 2）、冷却。
- `vending.goods.<货物>`：`item` 指向 `items.yml` 里的 id，`weight` 是出现权重（0 = 不出现）。默认含 `rpg`（`items.yml` 里标了 `murderer-only: true`，只有杀手能抽到）。
- `teleporter.enabled` / `cost-gold` / `cooldown-seconds` / `min-distance`：传送器开关、每次扣多少金锭（默认 5）、冷却、落点最小距离。
- `teleporter.message` / `no-target-message` / `sound`：成功提示 / 无落点提示 / 传送音效。
- `slot-machine.enabled` / `cost-gold` / `cooldown-seconds`：老虎机开关、每次启动扣多少金锭（默认 2）、冷却。
- `slot-machine.effects.<效果>`：`enabled`、`weight`、`broadcast`（是否全房间播报）、`message`，以及各效果自己的 `min-reward` / `amount` / `duration-seconds` / `broadcast-message`。
  目前效果：`double-gold`（翻倍，保底 6）、`gain-5`、`gain-3`、`lose-2`、`hot-potato`。

已登记的拉杆、传送点、售货机按钮统一写入同目录 `mechanisms-data.yml`（命令自动维护，不需要手改）。数据按「机关类型 -> 场地名」分组，例如 `levers.teleporter.<场名>` / `levers.slot-machine.<场名>` / `points.teleporter.<场名>` / `vending-machines.<场名>`；登记时会绑定到坐标所在的场地，使用时只读取当前场地的数据，**不同地图的机关不会串**。旧版没有场地分组的数据会在加载时按坐标自动迁移，迁移不到任何场地的条目会记日志并丢弃；旧版存在 `config.yml` 的 `vending-machines` 也会自动迁进这个文件。

**上传注意**：`mechanisms-data.yml` 是运行时数据（保存线上登记的坐标），`pack-upload.ps1` / `server-pack-full.ps1` 默认**不会**把它打进上传包；只有显式加 `-WithMechanismData` 才会覆盖线上登记。`config.yml` 里的场地坐标同理不要用开发机版本直接覆盖线上。

### 房间配置（rooms.yml）

`server/plugins/MurderMystery/rooms.yml` 按场地独立配置金锭刷新，改完 `/mm reload` 生效：

- `rooms.<场名>.gold-spawn-enabled`：该场地是否自动补金锭（默认 true）。
- `rooms.<场名>.gold-spawn-interval`：补货间隔（秒）。
- `rooms.<场名>.gold-spawn-amount`：每次补几个金块。
- `rooms.<场名>.gold-per-death`：该场地每名玩家死亡掉几个金锭。

没写的场地或字段沿用 `config.yml` 的全局值；`/mm rooms` 可以查看每个房间当前实际生效的值。

### 语音（Simple Voice Chat，可选）

**不需要改 SVC 的源码**，两边都是官方插件 + 官方 API：

1. 服务端 `plugins/` 放入 SVC 的 Bukkit/Paper 版 `voicechat-bukkit-*.jar`（Modrinth 页面 `henkelmax/simple-voice-chat`，选 game version 26.2/26.3；本服 Paper 26.2 已核对）。首次启动会生成 `plugins/voicechat/voicechat-server.properties`。
2. MM 的 `plugin.yml` 已把 `voicechat` 加进 `softdepend`（没装 SVC 时插件照常启用、只是没有语音逻辑），`pom.xml` 里是 provided 的 `de.maxhenkel.voicechat:voicechat-api`。启动后本插件日志出现「已接入 Simple Voice Chat…」即接通。
3. 客户端需要装同版本 SVC mod 并能在 24454/UDP 连上服务器。

MM 侧实际做的事（`com.murdermystery.VoiceChatHook`）：

- `join` 进局 → 把人踢出当前语音组（`setGroup(null)`），之后声音只按 SVC 的距离衰减传播。
- 局内 `block-groups: true` → 取消 `JoinGroupEvent` / `CreateGroupEvent`（语音组无方向、恒定音量，等于全图通话）。
- **淘汰 → 拉进「死者频道」组**（`eliminated()` → `setGroup(死人组)`）：固定 UUID `8a6f0f4c-…6f10`、`hidden`（不出现在客户端组列表里，活人误入不了）、`persistent`（人走光也不会被 SVC 的 `cleanupGroups` 回收）、默认 `NORMAL` 类型（`dead-group.isolated: false`：死者听得到附近活人的距离麦，但活人听不到死者）；组语音恒定音量、无距离限制，所以死者隔着半张图也能商量。仍保留「局内」标记，死者不能自己建组。
  - **JoinGroupEvent 豁免**：`setGroup(已存在的组)` 在 SVC 里走 `ServerGroupManager#joinGroup` → 派发 `JoinGroupEvent`，不豁免就会被 `block-groups` 的逻辑取消、死者进不去自己的频道。豁免条件：`事件里的组 ID == 死人组 ID && 连接对应玩家在 dead 集合里`。
  - **LeaveGroupEvent 拦截**：死者自己退组会掉回旁观者距离麦，`spectator_interaction=false` 并不能阻止旁观者的声音被附近活人听见（SVC 的 `processProximityPacket` 只把 soundPacket 从「旁观者专用」降级成普通邻近广播），等于给活人报点。所以死者退不出死人组；`exit()` 里**先**把 UUID 从 dead 集合摘掉**再** `setGroup(null)`，否则同一道规则会把正常离场也拦掉。
- `leave` / 结算回主城 / 退出 → `exit()`：摘掉局内 + 死者标记并退组，可以重新自由建组。
- `/mm reload` 把 `dead-group.enabled` 改成 `false` → 现场所有死者会被放回自由状态（先清标记再退组，免得被上面那条规则拦住）。

SVC 服务端参数（`plugins/voicechat/voicechat-server.properties`）不需要改；想调「多远听不见」改 `max_voice_distance`（默认 48），调近聊改 `whisper_distance`（默认 24，按 Shift 触发）。**不要**把 `spectator_interaction` 改成 true，否则淘汰者能对活人说话（报点）。注意「死者频道」的组类型在**建组那一刻**按配置决定，改了 `dead-group.isolated` 要重启服务器（或等这个组被回收）才生效；当前默认 `isolated: false`（NORMAL：死者之间 + 附近活人都能听见，活人听不见死者）。



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

本机实际用的手工流程（`pom.xml` 在，但 `com.hub:minigame-lib` 没有公共仓库，`build.ps1` / `verify.ps1` 也不在这个仓库里，Maven 离线打不出来）：

```powershell
# 1) 编译（JDK 25，--release 21；argfile 已列好 classpath 与全部源文件）
D:\minigame\server\jdk\bin\javac.exe '@D:\mm\.build-check\javac-args.txt'
# → D:\mm\.build-check\classes-voice（28 个 class）

# 2) 打包（classes + src/main/resources）
D:\minigame\server\jdk\bin\jar.exe --create --file D:\mm\.build-check\MurderMystery-20261127.jar `
  --manifest D:\mm\.build-check\manifest-voice.txt `
  -C D:\mm\.build-check\classes-voice . -C D:\mm\MurderMystery\src\main\resources .
```

产物约 140 KB（Paper API 26.2、Java 21 字节码），复制到 `D:\mm\新服务器小游戏插件\MurderMystery.jar` 与线上 `D:\minigame\server\plugins\MurderMystery.jar`；覆盖前先把旧 jar 备份到 `.build-check\MurderMystery-live-before-*.jar`。

冒烟（可选，会真的启动一次服务器再 `stop`）：`.build-check\smoke-normal.ps1` → 日志 `smoke-normal.log`，期望 `Done (...s)`、MurderMystery 正常 Enabling/Disabling、`### exit code: 0`。两个坑：直接 `& .\smoke-*.ps1` 会被执行策略拦（未签名），要用 `& ([scriptblock]::Create((Get-Content 脚本 -Raw)))` 跑；服务端必须能写 `world/session.lock` / `logs/latest.log` / `server.properties`，以受限沙箱运行时启动会直接 `AccessDeniedException`。

改 `src/main/resources/config.yml` 后要重新打包才会进 jar，但**不要整份覆盖线上 config**（线上有 `crossbow-cost: 12`、`death-note-cost: 3` 等本地化值），只手术式改对应段落，改完用 PyYAML 校验三份文件。

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
