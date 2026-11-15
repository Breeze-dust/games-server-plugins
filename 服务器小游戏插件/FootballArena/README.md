# FootballArena（绿茵足球）

> **独立副本**：本目录只含 FootballArena 一个插件，不依赖 monorepo。
> 构建：`.\build.ps1` → `out\FootballArena.jar`；依赖 `lib\MiniGameLib.jar` + `paper-classpath.ps1`。
> **不要再读 `D:\BuildBattle\Football\games-server-plugins` 或其它项目文件。**

> 速查卡（AI 先读这里，别先读源码）

- **一句话职责**：红蓝两队足球赛：等待期右键红/蓝旗选队（开赛时未选的自动补进人少的一队，两边人数差强制 ≤1）；比赛用自写物理的展示球（BlockDisplay 方块模型，服务端算重力/反弹/滚动），**任何物品（含空手）左键都能踢球**；先进 `goals-to-win` 球或 `match-seconds` 到点进球多者胜，平局进金球加时；**没有出界**，球到场地边界会被隐形墙弹回；进球后全员回出生点重开（失球方开球）；获胜队成员计 1 胜场写进玩家记录。**对局中全队带对应队伍颜色的发光描边（隔墙可见），全员自动穿上对应队色的皮革胸甲「队服」；物品栏最后一格另有一个同款锁死的「队服」展示道具（不能穿、不能丢）。**
  - 加入播报：**全服公屏**播「`[绿茵足球][场名] 玩家名 加入了游戏`」——`HubAPI.announceJoin(plugin.getName(), name, arenaName)` 三参数版。游戏名/颜色来自 HubCore `config.yml` `games` 段（`name: 绿茵足球`、`color: a`）；`plugin.yml` 的 `minigame: {name: 绿茵足球}` 段建"插件名 FootballArena → 显示名"映射，**少了就会播成 [FootballArena]**。
- **入口 / 主流程**：`FootballArena/src/main/java/com/football/FootballPlugin.java#onEnable` → `FootballManager#load`（读 config 的 arenas/join-signs + `PlayerStats.load("football", data.yml)`（新插件无旧文件，自动为空）+ 启动全局球物理任务 `startBallTask`）。`/fb join` → `join` → 公共库 `WaitCountdown` → `startMatch` → `prepareMatch`（`balanceTeams` 补队调平（保留等待期选的队）+ 给双方点亮队伍高亮 → 传送本队出生点 → 全员发队服/道具 → 上比分板）→ `StartCountdown` 倒计时（队伍/站位此时已定，倒计时期间冻结）→ `launchMatch`（比赛计时从这一刻起 → `spawnBall` → `startKickoff`）→ 阶段 `KICKOFF`（球冻结在开球点、开球方全队拿力度 1~4 的开球靴（快捷栏前 4 格，按档位绿/黄/橙/红染色），动作栏每秒倒计时，10 秒不开自动往对方半场开出）→ `PLAY`（踢球/进球/边界反弹）→ 进球 `handleGoal`（`GOAL_PAUSE` 3 秒 → 失球方开球、全员回出生点）→ 计时到点或达到目标球数 `endMatch`（按队伍发胜场 → 全员回主城 → 清队/清比分）。关服 `onDisable` → `cleanupBalls()`（清展示实体）+ `saveAll()`。
- **玩法要点（改行为先看这里）**：
  - **功能道具全带 PDC 标记**（`fb-item`），识别只认标记：冲刺（糖）/高跳（羽毛）右键，5 秒效果/5 秒冷却；**对局中任何物品（甚至空手）左键都能踢球**，统一按"击球"效果（水平 `forward-power` 1.0 + 垂直 `forward-lift` 0.45，半人高的平抽，无冷却）——已取消击球鞋和挑高靴两种道具。开球靴 = 快捷栏 1~4 格，力度**沿用旧 8 档刻度**线性映射 `kickoff-min-power`~`kickoff-max-power`（1~4 档数值与旧版完全一致，别按 1~4 铺满 min~max——那力度 4 = 2.2 会近乎垂直飞；档位数常量 `FootballItems.KICKOFF_MAX_LEVEL`），垂直分量 = 水平 × `kickoff-lift-ratio`（力度越大越高越远）。**身上那件队服是普通皮革胸甲、不带 PDC 标记，开赛直接穿在胸甲位**；**物品栏最后一格（快捷栏第 9 格）另放一个同款「队服」展示道具，带 PDC 标记（`JERSEY`），事件层锁死不能穿/不能丢/不能挪，每秒 `ensureJerseyItem` 兜底补回，离场/结算/掉线/重进服时清掉**。**对局快捷栏布局：1 冲刺糖 / 2 羽毛高跳 / 3~8 全空 / 9 队服**（对局中不再发退出床，离场用 `/fb leave`）。
  - **踢球判定**：`PlayerAnimationEvent`（`ARM_SWING`，忽略副手）+ 球心距离玩家"脚+1 格" ≤ `kick.range`(3.0)，**不要求手持任何道具**；只有开球阶段手里是开球靴才走开球逻辑。挥空不提示（防刷屏）。
  - **球与玩家无实体碰撞**：已删掉"身体带球/贴身推球"（原 `pushPlayersNear` 与 `ball.push-speed`），球员站在球旁或球路上都不会改变球速，只有左键击球能把球打走。
  - **球视觉/物理**（`FootballBall`）：BlockDisplay（`setBlock` 整块渲染 + 全亮度）+ 每 2 tick `step()`——重力/落地反弹+摩擦、撞墙按命中面法线反射（`World#rayTraceBlocks`），低于 `rest-speed` 静止；**变换统一走 `centeredTransformation(rot)`：平移必须是 (-s/2)³ 经同一旋转四元数转后的向量**（展示变换是"先转模型、后平移"，固定平移只在零旋转时居中——滚动时方块绕模型原点角翻转、整块甩到实体位置斜下沉进地里，"旋转时只露一个角"真事，2026-10-17 修；改旋转时平移/旋转必须一起动）；**碰撞箱跟随旋转后的方块**：贴地/贴墙距离 = 旋转方块沿该方向最深的角（`rotatedCorners`/`extentAlong`，随翻滚在 scale/2 ~ 0.87*scale 间变化，与修后渲染严格一致），球心高度也随之起伏（方块"翻滚"效果）——按固定半径算会让旋转方块的角扎进地里（"球滚到地下去"真事，2026-10-17 修）；踢球判定仍用球心 + `getRadius()`（= scale/2）；`setTeleportDuration(2)` 让客户端平滑插值，滚动时转 leftRotation（**只在水平速度 >0.05 时转**，原因见常见坑⑨）。**别改回原版实体**（展示实体没有可用的被击退物理）。**水平边界是隐形墙**：`FootballBall#step` 收到 `Boundary`，球心到 pos1/pos2 矩形边线（已扣球半径）时把朝外的水平速度按 `ball.bounce` 反向，球门区域（`Boundary#goalZone`，由 `goalAt(loc, 球半径)` 把门盒水平放宽一个球半径）不设墙，所以球能越过门线进球、但不会飞出场地。
  - **对局全员免伤**（`EntityDamageEvent` HIGHEST 一刀切，含摔落/饥饿）+ **场内禁挖方块**（`BlockBreak` 只拦场内、拦等待中对局中全部成员）+ 带标记道具禁丢禁放（红床放置是单挑踩过的坑）。
  - **不再判出界**：`isOutOfBounds`/`handleOut`/`clampKickoffSpot`/`teleportRandomKicker` 已删，球到边界只会反弹；`tickAll` 里进球判定仍先做（球门区域不设墙）。球万一掉到世界最低高度以下，`recoverIfFellOutOfWorld` 把它放回开球点（不重置站位、不换开球方）。
  - **队色 / 高亮**：记分板队伍 `fb_red`/`fb_blue` 只 `team.color(RED/BLUE)` 不设前缀（不碰 HubCore tab 前缀，同 HotPotato 红名做法）；**tab 名字颜色**走 `HubAPI.setTabNameColor`（红 `c`/蓝 `9`；HubCore 每秒重刷 tab 会盖掉记分板队伍色，必须走这个通道）：等待期 `pickTeam` 选队即染，开局 `balanceTeams` 按最终队伍重染（被调剂的也会变色）。**队伍高亮**：`applyTeamColor`/`collectTeam` 里 `setGlowing(true)`，原版发光描边取**客户端当前那块记分板**上的队伍色 —— 注意 `LiveSidebar` 开赛会给玩家 `setScoreboard` 一块独立比分板，队伍只挂主记分板时客户端看不到、发光就是**白色**（2026-11-15 真事），所以 `refreshLiveBoard` 在每次 `liveSidebar.update` 之后调 `refreshTeamColors()`，把主记分板 + 每个玩家当前记分板都挂上队伍（多场取并集）。离开/结算/掉线经 `clearTeamColor` 里 `setGlowing(false)` + 从当前板与主板摘掉，HubCore 回主城时再兜底清一次。等待期不点亮，开赛 `balanceTeams` 后才点亮。
- **命令**：`/football`（别名 `/fb`、`/soccer`；权限 `fb.use` 默认所有人，管理子命令要 OP 或 `fb.admin`）
  - 玩家：`join <场名>`、`leave`、`list`、`stats [玩家]`、`help`；`stats reset` 要 OP（清空全部胜场）
  - 管理：`create <名>`、`setup <名>`（道具：木棍范围左1右2、金锄开球点、红/蓝羊毛出生点、红砖/石砖球门左1右2、绿宝石保存、屏障退出）、`setup-exit`、`pos1/pos2/center/lobby/spawn-red/spawn-blue/goal-red-1/goal-red-2/goal-blue-1/goal-blue-2`、`save`、`start`、`edit <名>`、`rename <旧> <新>`、`remove <名>`、`joinsign <名>`
- **配置**：`server/plugins/FootballArena/config.yml`（模板在源码 resources；`/fb create` 等会回写）
  - `min-players`(2)/`max-players`(10)、`goals-to-win`(3)、`match-seconds`(300)、`golden-goal-seconds`(120)、`sprint-seconds`(5)/`sprint-cooldown`(5)/`jump-cooldown`(5)
  - `ball.*`：material(BLACK_CONCRETE)/scale(0.7)/gravity/drag/ground-friction/bounce/rest-speed（scale 改了不用动代码，碰撞半径永远 = scale/2；`bounce` 同时用于方块碰撞、落地和场地边界）
  - `kick.*`：range(3.0)/forward-power(1.0)/forward-lift(0.45)/kickoff-min-power(0.6)/kickoff-max-power(2.2)/kickoff-lift-ratio(0.45)（没有 vertical-power 了，挑高靴已取消）
  - `arenas.<场名>.pos1/pos2/center/lobby/spawn-red/spawn-blue/goal-red-1/goal-red-2/goal-blue-1/goal-blue-2`；`isReady` = 除 lobby 外九点齐全。pos1/pos2 的水平矩形同时是球的反弹边界；球门盒要**跨过/贴住**边界线，否则球会被边界挡在门线外
- **玩家记录**：命名空间 `football`，结构 `wins.<uuid>`、`names.<uuid>`；`PlayerStats.load("football", data.yml)`，落在唯一文件 `server/plugins/MiniGameLib/players.yml` 的 `games.football`；查询 `/fb stats [玩家]`，清空 `/fb stats reset`（OP）。**verify.ps1 的命名空间清单已加 football**。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 玩法/阶段/开球/进球/边界反弹/计时/战绩 | `FootballArena/src/main/java/com/football/FootballManager.java` |
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
    球心不会恰好落在零厚度盒上。正确圈法：**一角点门线上、另一角点门线后方 1~2 格（网里/界外那侧）**；
    球门盒伸出 pos1/pos2 边界是允许的，但**必须与边界线重叠/跨过**：边界隐形墙只对 `goalAt(loc, 球半径)`
    命中的区域放行，门盒完全缩在边界外（和边界隔一格）时球会先被墙弹回。门后要有实体墙/网挡球。
  - ② `PlayerAnimationEvent` 只有 `ARM_SWING` 才算踢球（26.2 没有 LEFT_CLICK_ARM_SWING 这个值，别照新版 Paper 文档写）。
  - ③ 开球阶段对方踢球无效靠"阶段 + 开球方"双重判断；`releaseKickoff` 会给**全员**补普通装备——改开局发放逻辑时别漏这步。
  - ④ 没有出界流程：只有进球/开局才 `resetPositions(a)`，球到 pos1/pos2 水平边界会按 `ball.bounce` 弹回场内，不换开球方、不传球。**别把"贴边线的球"当成出界**去加回 `handleOut`；球门贴边线时靠边界墙的 goalZone 开口放行。开球期间动作栏显示"开球倒计时 Xs"（`tickActionBar`，KICKOFF 阶段）。
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
  - ⑪ 球与玩家**没有实体碰撞**是设计如此（`pushPlayersNear`/`ball.push-speed` 已删），别再按"人撞球/带球"加回推送；踢球只认左键（`PlayerAnimationEvent#ARM_SWING`），任何手持物甚至空手都按击球参数出脚。
- **验证方法**：
  1. `build.bat -Deploy` 后重启：控制台出现 `FootballArena 已启用。` 与 `[GameWorlds] 世界 world_fb 就绪`。
  2. `/fb create 测试场` → `/fb setup 测试场` 圈地（木棍对角、金锄开球点、红/蓝羊毛出生点、红砖/石砖圈两个球门）→ 绿宝石保存；`/fb list` 应显示"已配置"。
  3. 两人 `/fb join 测试场`：等待期右键红/蓝旗选队（提示当前红蓝人数）；**按 Tab 看名单：选了队的名字变红/蓝，没选的保持黄色**；等待期第 9 格仍是退出床（右键退出排队）；满 2 人 30 秒倒计时自动开局（OP 在队列则等开始纸）。
  4. 开局：**先分队/传送**——全员瞬间站到本队出生点、发好队服/道具、上比分板，然后 `StartCountdown` 倒计时（默认 3 秒、冻结移动，`countdown.*` 可配），倒计时结束才生成球、开始比赛计时；球出现在开球点并冻结（看得见的黑色方块带白色发光描边，只见阴影 = 服务器还在跑旧 jar），开球方快捷栏前 4 格有"开球·力度 N"靴（绿/黄/橙/红）；左键踢出后换回普通装备，动作栏出现"红 x : 蓝 y | 剩余时间"；**全员按最终队伍红/蓝发光描边（红队红边、蓝队蓝边，隔墙可见，且不随比分板刷新变回白色）**；**全员胸甲位自动穿着对应队色的皮革胸甲「队服」，最后一格（第 9 格）是锁死的队服展示道具：右键/拖拽穿不上、Q 丢不掉、Shift/数字键挪不走，3~8 格全空**。球停在地面上时底面应正好贴地不陷入；贴墙停下时方块面应正好贴墙不嵌入。
  5. 踢球：空手或任意物品左键球附近都能踢出半人高的平抽（水平 1.0、垂直 0.45）；**站在球旁/球路上不会把球顶走**（无实体碰撞）；冲刺糖给速度 I（5s，冷却 5s），羽毛高跳（冷却 5s）。
  6. 把球踢进对方球门圈：GOAL 提示 + 比分 +1 + 3 秒后全员回出生点、失球方开球；把球往边线/底线踢：球到 pos1/pos2 边界会被弹回场内（**不再出界、不换开球方、不传人**）；开球期间动作栏显示"开球倒计时 Xs"，10 秒不开球自动往对方半场开出；对局中要离场用 `/fb leave`（第 9 格已不是退出床，是锁死的队服）。
  7. 进 3 球（或 5 分钟到点）结算：获胜方播报 + 全员回主城；`/fb stats <胜者>` 胜场 +1，`server/plugins/MiniGameLib/players.yml` 出现 `games.football.wins.<uuid>`。
  8. 上传前照常 `verify.ps1`（会多一行 `football  no records yet` 的 warn，属正常）。
- **未确认 / TODO**：
  - 2026-10-17：球"只见阴影"已定位根因（滚动旋转的 NaN 四元数，见常见坑⑨）并修复；同日把碰撞半径对齐到视觉半宽（scale/2）、球改黑色并缩小到 0.7；随后按反馈把碰撞改为跟随旋转方块（修"踢出去滚到地下去"——旋转方块的角扎进地面），射门靴改成普通踢球弧线（垂直 0.45，顶点约眼高）；再按反馈修"旋转时只露一个角其他沉地里"（平移改为跟随旋转四元数，见常见坑⑩）。**均未游戏内复测**。
  - 2026-10-17（第二批，未复测）：开球靴从 8 档减到 4 档（力度值**沿用旧 8 档刻度**，1~4 与旧版完全一致；曾误改成 1~4 铺满 min~max 导致力度 4 近乎垂直飞，用户反馈后改回）并按档位染色（1 绿/2 黄/3 橙/4 红）；"射门靴"改名"击球"并把水平初速 1.15 → 1.0（距离略减，弧线不变）。
  - 2026-10-17（第三批，未复测）：冲刺糖速度 II → 速度 I（`FootballManager#useSprint` 药水增幅 1 → 0）。
  - 2026-10-17（第四批，未复测）：挑高靴从快捷栏第 4 格挪到第 5 格（index 3 → 4，第 4 格留空）——开球靴力度 4 与挑高靴同格，开球踢出 `releaseKickoff` 换装的瞬间手里第 4 格立刻变成挑高靴，补一脚就把刚开出的球垂直踢飞，用户反馈为"开球选第四双靴子可能触发挑高靴"。
  - 2026-11-15（第五批）：按需求取消出界——球到 `pos1/pos2` 水平边界按 `ball.bounce` 反弹（球门区域不设墙）；取消"击球/挑高靴"两种道具，**任何物品或空手左键都按击球效果出脚**；取消球与玩家的实体碰撞（删 `pushPlayersNear` 与 `ball.push-speed`）。**仅编译通过，未游戏内复测**。
  - 2026-11-15（第六批，未复测）：对局玩家加**对应队伍颜色的发光描边**（`applyTeamColor` 里 `setGlowing(true)`，原版发光取记分板队伍色；`clearTeamColor` 里摘掉）；对局第 9 格退出床换成**队服**（对应队色的皮革胸甲，右键可穿），等待期退出床保留用于退出排队，对局中离场用 `/fb leave`。**仅编译通过，未游戏内复测**。
  - 本卡写于首次实现后，**仅通过编译 + 启动冒烟**（server 起得来、无报错、world_fb 已建）；完整对局流程（选队/开球/进球/金球/战绩）未在游戏内实测。
  - 2026-11-15（第七批，按游戏内反馈，未复测）：① **队服改为只穿不发**——开赛 `giveMatchKit` 直接 `setChestplate`，背包不再塞第二份（旧版那份胸甲位已穿着同款，右键穿不上、对局中又丢不掉，纯占格）；`ensureJerseys` 每秒兜底补穿（脱甲/重生/被别的插件扒甲都会穿回）。② **修复发光描边是白色**——`LiveSidebar` 开赛会给玩家换独立记分板，队伍只挂主记分板客户端看不到；现在 `refreshLiveBoard` 在每次 `liveSidebar.update` 之后 `refreshTeamColors()`，把主板 + 每个玩家当前板都挂上 `fb_red`/`fb_blue`，并每秒重挂。**仅编译通过，未游戏内复测**。
  - 2026-11-15（第八批，未复测）：
    ① **开赛倒计时改到分队+传送之后**——`startMatch` 拆成 `prepareMatch`（分队调平、传送本队出生点、发队服/道具、上比分板）和 `launchMatch`（倒计时结束才启动比赛计时、生成球、开球），倒计时期间玩家已站在本队半场并穿好队服；`countdown.enabled=false` 时跳过倒计时直接开球。倒计时期间有人 `/fb leave` 会重建倒计时（`StartCountdown` 的冻结名单在 begin 时固定，不重建离场者回大厅还动不了），`endMatch` 会先取消倒计时再送回主城。
    ② **物品栏最后一格恢复「队服」展示道具**（对应队色的皮革胸甲，名字"队服"），带 PDC 标记 `JERSEY`：`FootballListener` 拦右键/Shift 点/拖拽/数字键/副手交换，`PlayerDropItemEvent` 拦丢弃，做到**不能穿、不能丢、不能挪**；`ensureJerseyItem` 每秒补回（顺带清掉其它格子的复制品），离场/结算/掉线/重进服时由 `removeJerseyItem` 清掉。身上那件仍是普通皮革胸甲、不带标记。
    ③ 顺手修：`FootballArena#resetMatch` 不再清空 `teamRed/teamBlue`（等待期右键选的队能保留到开赛 `balanceTeams`，之前开赛就丢光、等于没选）；`endMatch` 改为发完终场比分/胜场后再 `clearTeams()` + `resetMatch()`（之前一进门就 reset，终场比分永远播 0:0、胜场也发不出去）。
    **仅编译通过，未游戏内复测**。
  - 球物理手感（gravity/bounce 等数值）是拍脑袋初版，实际玩起来大概率要调 config。
  - 等待期选队不做人数限制（只提示），平衡在开赛 `balanceTeams` 强制（差 ≤1）——"每边不超过总等待人数的一半"按这个解释实现，若要"选队时就锁满"再说。
  - `/fb start` 与开始纸开局路径只测了编译；`/fb rename` 对菜单 `sub` 段的清理依赖 HubAPI.removeArena + reportArena（与 HotPotato 同套路，未实测）。
