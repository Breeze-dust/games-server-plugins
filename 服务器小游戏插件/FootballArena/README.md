# FootballArena（绿茵足球）

> 速查卡（AI 先读这里，别先读源码）

- **一句话职责**：红蓝两队足球赛：等待期右键红/蓝旗选队（开赛时未选的自动补进人少的一队，两边人数差强制 ≤1）；比赛用自写物理的展示球（BlockDisplay 方块模型，服务端算重力/反弹/滚动），左键拿鞋踢球；先进 `goals-to-win` 球或 `match-seconds` 到点进球多者胜，平局进金球加时；出界由"最后触球队的对方"在出界点开球（不重置站位，随机传送一名开球队员到球旁），进球后全员回出生点重开（失球方开球）；获胜队成员计 1 胜场写进玩家记录。
  - 加入播报：**全服公屏**播「`[绿茵足球][场名] 玩家名 加入了游戏`」——`HubAPI.announceJoin(plugin.getName(), name, arenaName)` 三参数版。游戏名/颜色来自 HubCore `config.yml` `games` 段（`name: 绿茵足球`、`color: a`）；`plugin.yml` 的 `minigame: {name: 绿茵足球}` 段建"插件名 FootballArena → 显示名"映射，**少了就会播成 [FootballArena]**。
- **入口 / 主流程**：`FootballArena/src/main/java/com/football/FootballPlugin.java#onEnable` → `FootballManager#load`（读 config 的 arenas/join-signs + `PlayerStats.load("football", data.yml)`（新插件无旧文件，自动为空）+ 启动全局球物理任务 `startBallTask`）。`/fb join` → `join` → 公共库 `WaitCountdown` → `startMatch`（`balanceTeams` 补队调平 → 全员发队服/道具 → `spawnBall` → `startKickoff`）→ 阶段 `KICKOFF`（球冻结在开球点、开球方全队拿力度 1~4 的开球靴（快捷栏前 4 格，按档位绿/黄/橙/红染色），动作栏每秒倒计时，10 秒不开自动往对方半场开出）→ `PLAY`（踢球/带球/进球/出界）→ 进球 `handleGoal`（`GOAL_PAUSE` 3 秒 → 失球方开球、全员回出生点）/ 出界 `handleOut`（球放在出界点——水平夹回场内 1 格 `clampKickoffSpot`、对方开球、不重置站位、`teleportRandomKicker` 随机传一名开球队员到**界外**球旁、面朝球，人→球的踢球方向自然朝场内）→ 计时到点或达到目标球数 `endMatch`（获胜队全队 `recordWin` → 全员回主城）。关服 `onDisable` → `cleanupBalls()`（清展示实体）+ `saveAll()`。
- **玩法要点（改行为先看这里）**：
  - **道具全带 PDC 标记**（`fb-item`），识别只认标记：冲刺（糖）/高跳（羽毛）右键，5 秒效果/5 秒冷却；击球（`KICK_FORWARD`，原"射门靴"）左键 = 普通踢球弧线（水平 1.0 + 垂直 0.45，最高点约到玩家眼睛高度）；挑高靴（`KICK_VERTICAL`）左键 ≈ 纯垂直 0.95；两者无冷却。开球靴 = 快捷栏 1~4 格，力度**沿用旧 8 档刻度**线性映射 `kickoff-min-power`~`kickoff-max-power`（1~4 档数值与旧版完全一致，别按 1~4 铺满 min~max——那力度 4 = 2.2 会近乎垂直飞；档位数常量 `FootballItems.KICKOFF_MAX_LEVEL`），垂直分量 = 水平 × `kickoff-lift-ratio`（力度越大越高越远）。**对局快捷栏布局：1 冲刺糖 / 2 羽毛高跳 / 3 击球 / 4 留空 / 5 挑高靴 / 9 退出床——第 4 格必须留空**：开球靴占前 4 格，开球踢出换装瞬间手里那格被原位顶替，若挑高靴也在这格，补一脚会把刚开出的球垂直踢飞（2026-10-17 真事修）。
  - **踢球判定**：`PlayerAnimationEvent`（`ARM_SWING`，忽略副手）+ 主手道具标记 + 球心距离玩家"脚+1 格" ≤ `kick.range`(3.0)。挥空不提示（防刷屏）。
  - **带球**：球慢速且玩家贴近（水平 < 球半径+0.9、垂直差 <1.6）时每 tick 把球沿"玩家→球 + 玩家视线"方向推 `ball.push-speed`；一 tick 只推最近一人。
  - **球视觉/物理**（`FootballBall`）：BlockDisplay（`setBlock` 整块渲染 + 全亮度）+ 每 2 tick `step()`——重力/落地反弹+摩擦、撞墙按命中面法线反射（`World#rayTraceBlocks`），低于 `rest-speed` 静止；**变换统一走 `centeredTransformation(rot)`：平移必须是 (-s/2)³ 经同一旋转四元数转后的向量**（展示变换是"先转模型、后平移"，固定平移只在零旋转时居中——滚动时方块绕模型原点角翻转、整块甩到实体位置斜下沉进地里，"旋转时只露一个角"真事，2026-10-17 修；改旋转时平移/旋转必须一起动）；**碰撞箱跟随旋转后的方块**：贴地/贴墙距离 = 旋转方块沿该方向最深的角（`rotatedCorners`/`extentAlong`，随翻滚在 scale/2 ~ 0.87*scale 间变化，与修后渲染严格一致），球心高度也随之起伏（方块"翻滚"效果）——按固定半径算会让旋转方块的角扎进地里（"球滚到地下去"真事，2026-10-17 修）；踢球/带球判定仍用球心 + `getRadius()`（= scale/2）；`setTeleportDuration(2)` 让客户端平滑插值，滚动时转 leftRotation（**只在水平速度 >0.05 时转**，原因见常见坑⑨）。**别改回原版实体**（展示实体没有可用的被击退物理）。
  - **对局全员免伤**（`EntityDamageEvent` HIGHEST 一刀切，含摔落/饥饿）+ **场内禁挖方块**（`BlockBreak` 只拦场内、拦等待中对局中全部成员）+ 带标记道具禁丢禁放（红床放置是单挑踩过的坑）。
  - **球出界判定**：球心不在 `pos1/pos2` 盒内或低于世界最低高度+2；**先判球门再判出界**（球门圈可能贴边线）。
  - **队色**：记分板队伍 `fb_red`/`fb_blue` 只 `team.color(RED/BLUE)` 不设前缀（不碰 HubCore tab 前缀，同 HotPotato 红名做法）；**tab 名字颜色**走 `HubAPI.setTabNameColor`（红 `c`/蓝 `9`；HubCore 每秒重刷 tab 会盖掉记分板队伍色，必须走这个通道）：等待期 `pickTeam` 选队即染，开局 `balanceTeams` 按最终队伍重染（被调剂的也会变色），离开/结算/掉线经 `clearTeamColor` 清除，HubCore `clearGame` 兜底再清一次。
- **命令**：`/football`（别名 `/fb`、`/soccer`；权限 `fb.use` 默认所有人，管理子命令要 OP 或 `fb.admin`）
  - 玩家：`join <场名>`、`leave`、`list`、`stats [玩家]`、`help`；`stats reset` 要 OP（清空全部胜场）
  - 管理：`create <名>`、`setup <名>`（道具：木棍范围左1右2、金锄开球点、红/蓝羊毛出生点、红砖/石砖球门左1右2、绿宝石保存、屏障退出）、`setup-exit`、`pos1/pos2/center/lobby/spawn-red/spawn-blue/goal-red-1/goal-red-2/goal-blue-1/goal-blue-2`、`save`、`start`、`edit <名>`、`rename <旧> <新>`、`remove <名>`、`joinsign <名>`
- **配置**：`server/plugins/FootballArena/config.yml`（模板在源码 resources；`/fb create` 等会回写）
  - `min-players`(2)/`max-players`(10)、`goals-to-win`(3)、`match-seconds`(300)、`golden-goal-seconds`(120)、`sprint-seconds`(5)/`sprint-cooldown`(5)/`jump-cooldown`(5)
  - `ball.*`：material(BLACK_CONCRETE)/scale(0.7)/gravity/drag/ground-friction/bounce/push-speed/rest-speed（scale 改了不用动代码，碰撞半径永远 = scale/2）
  - `kick.*`：range(3.0)/forward-power(1.0)/forward-lift(0.45)/vertical-power/kickoff-min-power(0.6)/kickoff-max-power(2.2)/kickoff-lift-ratio(0.45)
  - `arenas.<场名>.pos1/pos2/center/lobby/spawn-red/spawn-blue/goal-red-1/goal-red-2/goal-blue-1/goal-blue-2`；`isReady` = 除 lobby 外九点齐全
- **玩家记录**：命名空间 `football`，结构 `wins.<uuid>`、`names.<uuid>`；`PlayerStats.load("football", data.yml)`，落在唯一文件 `server/plugins/MiniGameLib/players.yml` 的 `games.football`；查询 `/fb stats [玩家]`，清空 `/fb stats reset`（OP）。**verify.ps1 的命名空间清单已加 football**。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 玩法/阶段/开球/进球/出界/计时/战绩 | `FootballArena/src/main/java/com/football/FootballManager.java` |
  | 球的物理（重力/反弹/滚动/踢球距离） | `FootballArena/src/main/java/com/football/FootballBall.java` |
  | 道具外观/发放/识别（加新道具先改 Kind） | `FootballArena/src/main/java/com/football/FootballItems.java` |
  | 事件拦截（免伤/踢球/防丢防放/告示牌） | `FootballArena/src/main/java/com/football/FootballListener.java` |
  | 命令与 `/fb help` 文案 | `FootballArena/src/main/java/com/football/FootballCommand.java` |
  | 场地字段/球门判定 `goalAt`/存档结构 | `FootballArena/src/main/java/com/football/FootballArena.java` |
  | 线上真实生效配置 | `server/plugins/FootballArena/config.yml` |
  | 足球世界（world_fb 平地，启动自动建） | `server/plugins/GameWorlds/config.yml` |
  | 菜单入口（icon/name/alias/command） | `server/plugins/HubCore/config.yml` |
- **常见坑**：
  - ① 球门判定用球心 double 坐标 `inBox`，圈球门要含门框内空间 + 底线；圈太小球从角上飞进不判。
    **两角不能都点在门线所在的同一个面上**：球每 2 tick 离散采样（`step(2)` = 位置一跳 velocity×2），
    球心不会恰好落在零厚度盒上，一越过门线就先判"出界"（真事：门贴边线、两角 z 同值 → 踢进球播"球出界了"）。
    正确圈法：**一角点门线上、另一角点门线后方 1~2 格（网里/界外那侧）**；球门盒伸出 pos1/pos2 边界是允许的
    （每 tick 先判球门再判出界，见 `FootballManager#tickAll`）。门后要有实体墙/网挡球，否则满力射门一跳 4+ 格可能跳过薄盒。
  - ② `PlayerAnimationEvent` 只有 `ARM_SWING` 才算踢球（26.2 没有 LEFT_CLICK_ARM_SWING 这个值，别照新版 Paper 文档写）。
  - ③ 开球阶段对方踢球无效靠"阶段 + 开球方"双重判断；`releaseKickoff` 会给**全员**补普通装备——改开局发放逻辑时别漏这步。
  - ④ 出界开球不重置站位是设计如此（真实足球边线球）；只有进球/开局才 `resetPositions(true)`。出界点会由 `clampKickoffSpot` 水平夹回场内 1 格（不然球还在界外，切回 PLAY 立刻再判出界）、Y 夹在场高内留 0.5 余量；开球队员传到**界外**距球约 2 格处（从 2 格起逐格试能站的空位，被墙/网挡住就退远，最远 4 格）、面朝球——踢球方向=人→球=朝场内，所以人必须站台内侧的反面（界外）。开球期间动作栏显示"开球倒计时 Xs"（`tickActionBar`，KICKOFF 阶段）。
  - ⑤ 免伤是"在场就免"（含等待中），别的插件世界玩家不受影响；不要改成无条件。
  - ⑥ 展示球（BlockDisplay）`setPersistent(false)`：不会写进世界存档，关服/崩溃后下次开赛重新生成；别加回持久（会残留幽灵球）。`ball.material` 必须是方块材质（非方块会被回退成 WHITE_CONCRETE）。
  - ⑨ **展示实体的变换四元数绝不能是 NaN**：滚动旋转的轴向量来自 `(vz,0,-vx).normalize()`，水平速度≈0 时是零向量，JOML `rotateAxis` 会写出**全 NaN 四元数**（joml 1.10.8 实测），发给客户端后**模型永久不渲染、只剩阴影**（阴影走实体位置，不受变换影响；插值也从 NaN 出发，之后怎么动都不恢复）。2026-10-17 用"9 色差分测试块"定位（同数据同行为的测试块全可见、唯独球不可见），修法=水平速度 >0.05 才更新旋转。当天曾误判为"ItemDisplay 不渲染"而换成 BlockDisplay——真正元凶是 NaN；BlockDisplay 保留（渲染正常，也是玩家要的方块外观）。
  - ⑦ 关服 `onDisable` 会 `saveAll()` 回写 config（arenas/join-signs）→ 运行中上传配置会被旧内存覆盖，先停服再覆盖。
  - ⑩ 展示变换是**"先转模型、后平移"**（渲染位 = 平移 + 旋转·缩放·模型坐标）：想让方块绕自身中心转，
    平移必须是 `(-s/2)³` 经**同一四元数**转后的向量（`centeredTransformation`）。固定平移 + 只转 leftRotation
    会让方块绕模型原点角（渲染上的最低角）翻转，旋转累积后整块甩到实体位置斜下方沉进地里——
    表现为"球滚动时只露出一个角，其他都埋进地下"（2026-10-17 真事修）。碰撞侧 `rotatedCorners()` 算的
    就是 rot·(±s/2)³，与修后渲染严格一致；以后改旋转逻辑时，平移和旋转必须一起更新（都走 `centeredTransformation`）。
  - ⑧ `handleJoin` 会把"不在场但身处足球世界"的玩家送回主城（防"站在场上打不了"）。
- **验证方法**：
  1. `build.bat -Deploy` 后重启：控制台出现 `FootballArena 已启用。` 与 `[GameWorlds] 世界 world_fb 就绪`。
  2. `/fb create 测试场` → `/fb setup 测试场` 圈地（木棍对角、金锄开球点、红/蓝羊毛出生点、红砖/石砖圈两个球门）→ 绿宝石保存；`/fb list` 应显示"已配置"。
  3. 两人 `/fb join 测试场`：等待期右键红/蓝旗选队（提示当前红蓝人数）；**按 Tab 看名单：选了队的名字变红/蓝，没选的保持黄色**；满 2 人 30 秒倒计时自动开局（OP 在队列则等开始纸）。
  4. 开局：球出现在开球点并冻结（看得见的黑色方块带白色发光描边，只见阴影 = 服务器还在跑旧 jar），开球方快捷栏前 4 格有"开球·力度 N"靴（绿/黄/橙/红）；左键踢出后换回普通装备，动作栏出现"红 x : 蓝 y | 剩余时间"；**tab 名字按最终队伍（含被自动调剂的）红/蓝**。球停在地面上时底面应正好贴地不陷入；贴墙停下时方块面应正好贴墙不嵌入。
  5. 踢球：击球平抽（半人高抛物线，比之前略近）、挑高靴垂直击飞；贴近球慢走球会往前滚（带球）；冲刺糖给速度 I（5s，冷却 5s），羽毛高跳（冷却 5s）。
  6. 把球踢进对方球门圈：GOAL 提示 + 比分 +1 + 3 秒后全员回出生点、失球方开球；把球踢出场：球放在出界点附近（夹回场内 1 格）、对方开球、站位不动、**随机一名开球队员被传到场外球旁、面朝球**（聊天播报名字）；开球期间动作栏显示"开球倒计时 Xs"，10 秒不开球自动往对方半场开出。
  7. 进 3 球（或 5 分钟到点）结算：获胜方播报 + 全员回主城；`/fb stats <胜者>` 胜场 +1，`server/plugins/MiniGameLib/players.yml` 出现 `games.football.wins.<uuid>`。
  8. 上传前照常 `verify.ps1`（会多一行 `football  no records yet` 的 warn，属正常）。
- **未确认 / TODO**：
  - 2026-10-17：球"只见阴影"已定位根因（滚动旋转的 NaN 四元数，见常见坑⑨）并修复；同日把碰撞半径对齐到视觉半宽（scale/2）、球改黑色并缩小到 0.7；随后按反馈把碰撞改为跟随旋转方块（修"踢出去滚到地下去"——旋转方块的角扎进地面），射门靴改成普通踢球弧线（垂直 0.45，顶点约眼高）；再按反馈修"旋转时只露一个角其他沉地里"（平移改为跟随旋转四元数，见常见坑⑩）。**均未游戏内复测**。
  - 2026-10-17（第二批，未复测）：开球靴从 8 档减到 4 档（力度值**沿用旧 8 档刻度**，1~4 与旧版完全一致；曾误改成 1~4 铺满 min~max 导致力度 4 近乎垂直飞，用户反馈后改回）并按档位染色（1 绿/2 黄/3 橙/4 红）；"射门靴"改名"击球"并把水平初速 1.15 → 1.0（距离略减，弧线不变）。
  - 2026-10-17（第三批，未复测）：冲刺糖速度 II → 速度 I（`FootballManager#useSprint` 药水增幅 1 → 0）。
  - 2026-10-17（第四批，未复测）：挑高靴从快捷栏第 4 格挪到第 5 格（index 3 → 4，第 4 格留空）——开球靴力度 4 与挑高靴同格，开球踢出 `releaseKickoff` 换装的瞬间手里第 4 格立刻变成挑高靴，补一脚就把刚开出的球垂直踢飞，用户反馈为"开球选第四双靴子可能触发挑高靴"。
  - 本卡写于首次实现后，**仅通过编译 + 启动冒烟**（server 起得来、无报错、world_fb 已建）；完整对局流程（选队/开球/进球/出界/金球/战绩）未在游戏内实测。
  - 球物理手感（gravity/bounce/push-speed 等数值）是拍脑袋初版，实际玩起来大概率要调 config。
  - 等待期选队不做人数限制（只提示），平衡在开赛 `balanceTeams` 强制（差 ≤1）——"每边不超过总等待人数的一半"按这个解释实现，若要"选队时就锁满"再说。
  - `/fb start` 与开始纸开局路径只测了编译；`/fb rename` 对菜单 `sub` 段的清理依赖 HubAPI.removeArena + reportArena（与 HotPotato 同套路，未实测）。
