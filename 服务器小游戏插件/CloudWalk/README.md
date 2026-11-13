# CloudWalk（云中漫步）—— 速查卡

> AI 先读这一段，能省掉通读源码。

- **一句话职责**：主城随时可玩的无尽跑酷。玩家 `/cloudwalk` 直接开跑，脚下是起始方块，前方预生成 3 块；
  每成功跳到下一块 +1 分，掉落 / 传送 / 下线 / 长时间不动本局结束；个人最高分和全服前十永久记录。
- **定位**：主城活动（类似 SkyPit / LobbyToys），**不注册进 HubCore 的 9 个游戏列表**，没有等待室和场地流程。
  只依赖 `MiniGameLib`（`HubAPI` + `PlayerStats`），可选主城；没装 HubCore 时用 `/cloudwalk setlobby` 的回城点。
- **通道怎么排**：每名玩家一份区域副本，沿 `lane.axis`（默认 X）横向并排，间隔 `lane.gap`（默认 3 格），
  每条起点再抬高 `lane.height-step`（默认 2 格）。所以相邻两人互相看得见、路径永远不会重叠；
  上下错开 2 格后跳上对方通道也是不可能的（只能往下掉，掉下去就结束）。**不要**改成纯垂直叠放：
  每跳高度差 -1/0/+1，路径会垂直漂移，只隔 2~3 格的话几跳之内两条路就会交叉。
- **真实方块**：路径全是 `path.block` 材质的真实方块，结束后逐块清成空气；玩家之间看得见。
  区域必须是**专用空间**（建议主城高空），不要和建筑重叠。
- **生成算法**：从路径末端按「前 / 左 / 右 × 水平 3/4 格 × 高度 -2/-1/0/+1」列候选，洗牌后逐个过滤
  （在通道盒内、不与当前路径重叠）；`allow4-jump-up: false` 时排除「水平4+上升」（原版跳不上去）。
  没有合法位置就结束本局（不做提前转向）。
- **方块池与物品栏**：`path.blocks` 是方块池，默认 16 色羊毛 + 16 色带釉陶瓦；每生成一块从玩家已启用的方块里随机挑一个。
  进跑酷时保存物品栏并清空，第 1 格换成「选择跑酷方块」（烈焰棒）道具：右键打开 54 格菜单，
  点击方块切换启用/禁用，底部「全部启用」「关闭并保存」。选择按玩家存进 `players.yml` 的 `blocks.<uuid>`。
  结束/下线/关服时原样还原物品栏；跑酷中这个道具不能丢弃。
- **落地判定**：每 tick 检查一次：脚部与队列第一块顶面中心水平距离 ≤ 0.85、脚部 Y 在顶面 -0.1~+0.6；
  命中后 +1 分、删旧块、队尾补一块。不判断 Y 速度，所以落地后立刻起跳的连跳也能正常计分。
  XZ 出界判定留了 2 格余量，站在区域最边上的方块不会误判；服务端位置校正（TeleportCause.UNKNOWN）也不会被当成传送。
- **下一块标记**：`run.target-particle: true` 时每 0.5 秒在下一个目标方块上方打一圈只有本人可见的 END_ROD 粒子。
- **结束条件**：Y 低于当前方块 Y-3、离开通道 XZ（含 1 格余量）、下线、传送、死亡、
  飞行/滑翔、末影珍珠/烟花、切创造/旁观、`/hub` 或右键回主城道具、服务器关闭（不记分）。
  **没有挂机超时**：站在方块上可以无限挂机，不会因为长时间不动被遣返。
- **入口 / 主流程**：`CloudWalk/src/main/java/com/cloudwalk/CloudWalkPlugin.java#onEnable`
  → `CloudWalkManager#load`（读 config + `PlayerStats.load("cloudwalk", …)` + 启动 2 tick 循环）
  → `CloudWalkCommand`（`/cloudwalk` 无参数 = 加入）→ `CloudWalkListener`（各种出口）。
- **命令**：`/cloudwalk`（别名 `/parkour`、`/云中漫步`）
  - 玩家：无参数 / `join` 加入、`leave` 退出、`top` 前十、`stats [玩家]`、`help`
  - 管理（OP 或 `cloudwalk.admin`）：`pos1`、`pos2`（站到对角按）、`setstart`（站到起点按，取脚下那块）、
    `setlobby`、`info`、`reload`、`reset <在线玩家>`
- **配置**：`server/plugins/CloudWalk/config.yml`（模板 `CloudWalk/src/main/resources/config.yml`）
  - `region` / `start` / `lobby`：由命令写入；**关服时会回写这三段**，手改请在停服后改，或改完 `/cloudwalk reload`
  - `lane.axis|gap|height-step|max`：通道方向、间隔、每层抬高、最多同时人数
  - `path.distances|heights|queue-length|allow4-jump-up|blocks`
  - `run.sound|particle|target-particle`、`top-size`
- **玩家记录**：命名空间 `cloudwalk`，结构 `best.<uuid>` / `bestAt.<uuid>` / `names.<uuid>` / `blocks.<uuid>`；
  唯一文件 `server/plugins/MiniGameLib/players.yml`，旧 `CloudWalk/data.yml` 首次加载自动迁移。
  同分按 `bestAt` 升序（先达成者排前）。
- **构建 / 交付**：`.\build.ps1 -Only CloudWalk`（自动带 MiniGameLib）；
  产物 `新服务器小游戏插件/CloudWalk.jar`，**不要**把 `config.yml` 打进交付包（里面有线上区域数据）。
- **常见坑**：
  - 区域 / 起点没设置时任何人 `/cloudwalk` 都会提示未配置；`/cloudwalk info` 先看一遍。
  - 区域必须是纯空气空间；如果里面本来有方块，跑酷结束只会清掉自己放的块，原有方块不动。
  - 服务器崩溃时已放置的方块会留在世界里（正常关服会清）；重进区域手动清理即可。
  - 跑酷期间伤害被取消、跳跃提升 / 漂浮 / 缓降会被移除；双击空格起飞会被取消但不会结束本局，
    真正飞起来（isFlying）才会结束。
  - `lane.max` 调大后新通道会占用更多横向空间，确认世界边缘和渲染距离够。
