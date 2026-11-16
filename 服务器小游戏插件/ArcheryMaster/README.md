# ArcheryMaster（箭术大师）—— 速查卡

> AI 先读这一段，能省掉通读源码。

- **一句话职责**：主城随时可玩的单人限时射箭。`/archery` 直接开始，在配置时间内用弓射中圆柱体区域里不断刷新的靶子，
  按靶子类型加分，时间到结算并把最高分记进排行榜。没有等待室，不注册进 HubCore 的游戏列表。
- **定位**：主城活动（同 CloudWalk / LobbyToys），只依赖 `MiniGameLib`（`HubAPI` + `PlayerStats` + `Chat`）。
  没装 HubCore 也能玩，只是 `/hub` 不会帮你退出（本插件会自己处理传送事件）。
- **进入 / 退出**：`/archery`（别名 `/am`、`/箭术大师`）→ 保存**进入前的位置和视角**、保存物品栏 →
  （如果配置了 `start`）传到射击位 → 发一把无限耐久 + 无限箭的弓（冲击 `INFINITY` 附魔，箭不消耗）。
  时间到 / 主动退出 / 传送 / 下线 / 死亡 / 飞行 / 切创造旁观 / `/hub` 都结束本局：
  清掉靶子和飞在空中的箭 → 还原物品栏 → **回到进入前的位置和视角**（这就是防止玩家每次都要从大厅传送点跑回 NPC 的关键）。
- **靶子区域**：一个圆柱体。`/archery setarea <中心X> <中心Z> <半径> <最低Y> <最高Y>`；
  也可以站到中心执行 `/archery setarea <半径> <最低Y> <最高Y>`。区域世界取执行命令时所在的世界。
  靶子中心只会落在 `y-min ~ y-max`，水平位置在半径内均匀随机。
- **射击站位（可选）**：`/archery setstart`，站在射击位执行；记录位置和视角（yaw/pitch）。
  配置后开局会传送过去；不配置时玩家就在执行 `/archery` 的位置开玩（适合把 NPC 放在靶场正前方）。
  配置了站位时，区域和站位必须在同一个世界；不配置时要求玩家自己就在区域所在的世界。
  `/archery info` 可以查看当前区域和站位。
- **靶子类型**：固定 / 移动；正常方块（1.0）/ 缩小方块（`targets.small-scale`，默认 0.5）。
  移动靶速度在 `min-speed ~ max-speed`（格/秒）之间逐只随机，速度 `>= fast-speed` 算「快速移动靶」。
  移动靶只做水平移动，撞到圆柱边缘会反弹，Y 不变。
- **计分**（全部可在 `targets.scores` 改）：
  | 靶子 | 分数 |
  | --- | --- |
  | 正常固定靶 | +1 |
  | 缩小固定靶 | +3 |
  | 正常移动靶 | +3 |
  | 缩小移动靶 | +5 |
  | 正常快速移动靶 | +5 |
  | 缩小快速移动靶 | +10 |
- **外观**：靶子是 `BlockDisplay`（固定靶 `TARGET`、移动靶 `REDSTONE_BLOCK`、快速移动靶 `GOLD_BLOCK`，可配）。
  移动靶带发光轮廓（普通黄色 / 快速红色）。**每名玩家有自己的一套靶子**，靶子只对本人可见
  （生成时对其他人 `hideEntity`，中途进服的玩家在 `PlayerJoinEvent` 里补隐藏），所以多人同时玩互不干扰。
- **命中判定**：不依赖 `ProjectileHitEvent` 的原版碰撞（靶子是没有碰撞箱的展示实体）。
  监听 `ProjectileLaunchEvent` 记录本人射出的箭；每 tick 用「上一刻位置 → 当前位置」的线段
  对每个靶子的 AABB 做 `BoundingBox#rayTrace` 扫掠检测，避免高速箭穿过小靶子漏判。
  命中后：加分、移除靶子、`respawn-delay-ticks` 后补一个新靶、发出粒子和音效。
  箭落地/命中方块时由 `ProjectileHitEvent` 立即移除，不会在场地里越积越多。
- **场上靶数**：`targets.max`（默认 8）；`targets.lifetime-seconds` > 0 时靶子会超时换新，0 = 不自动消失。
- **物品保护**：弓/箭带 PDC 标记，局内不能丢弃；每 20 tick 检查一次，丢了会自动补回。
  弓带 `INFINITY` + 无限耐久，所以箭射不完。
- **排行榜**：`/archery top` 聊天前十；`/archery top place` 在当前位置放 `TextDisplay` 全息榜
  （固定朝向、吸附正方向、每 5 秒刷新，没有记录的格子显示「空位」）；`/archery top remove` 移除全部。
  位置/UUID 存在 `config.yml` 的 `leaderboards` 段，重启后按 UUID 找回。
- **玩家记录**：命名空间 `archery`，结构 `best.<uuid>` / `bestAt.<uuid>` / `names.<uuid>`；
  唯一文件 `server/plugins/MiniGameLib/players.yml`，旧 `ArcheryMaster/data.yml` 首次加载自动迁移。
  同分按 `bestAt` 升序（先达成者排前）。
- **入口 / 主流程**：`ArcheryMaster/src/main/java/com/archery/ArcheryPlugin.java#onEnable`
  → `ArcheryManager#load`（读 config + `PlayerStats.load("archery", …)` + 启动 tick）→
  `ArcheryCommand`（`/archery` 开局）→ `ArcheryManager#join` → 每 tick `tickSessions`（计时 / 移动靶 / 命中）→
  `ArcheryListener`（各种退出、箭矢事件）。
- **命令**：
  - 玩家：无参数 / `join` / `start` / `play` 开始；`leave` 退出；`top` 排行榜；`stats [玩家]` 最高分和名次；`help`
  - 管理（OP 或 `archery.admin`）：`setarea`、`setstart`、`info`、`reload`、`reset <在线玩家|all>`、
    `debug`（局内看靶子位置和速度）、`top place` / `top remove`
- **配置**：`server/plugins/ArcheryMaster/config.yml`（模板 `ArcheryMaster/src/main/resources/config.yml`）
  - `region` / `start` / `leaderboards`：由命令写入；**命令会立即 `saveConfig`**，手改请在停服后改或 `/archery reload`
  - `game.time-seconds`：每局时长（默认 60 秒）
  - `targets.*`：靶数、刷新间隔、大小概率、移动速度、快速线、外观方块、六种得分
  - `effects.sound|particle`：命中音效 / 粒子
  - `top-size`：排行榜显示几名
- **构建 / 交付**：`.\build.ps1 -Only ArcheryMaster`（自动带 MiniGameLib）；
  产物 `新服务器小游戏插件/ArcheryMaster.jar`，**不要**把 `config.yml` 打进交付包
  （里面有线上区域/站位/排行榜位置）。
- **常见坑**：
  - 区域没设置时 `/archery` 只会提示，不会开局；先 `/archery info` 看一遍。
  - 配置了射击站位时，区域和站位世界不一致不能开局；不配置站位时，玩家必须站在区域所在的世界。
  - 展厅里已经存在的方块不会被清除；靶子是展示实体，结束时移除，不会留下真实方块。
  - 服务器崩溃时未结束的靶子会在重启后消失（展示实体不持久化）；排行榜是持久化的。
  - 局内会取消伤害、禁止丢弃弓/箭；使用末影珍珠/烟花会直接结束本局。
