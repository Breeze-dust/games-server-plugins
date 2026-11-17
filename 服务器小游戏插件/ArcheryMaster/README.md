# ArcheryMaster（箭术大师）—— 速查卡

> AI 先读这一段，能省掉通读源码。

- **一句话职责**：主城随时可玩的单人限时射箭。`/archery` 直接开始，靶子从多个长方体区域里随机刷新，
  每个区域有自己的**基础分**；命中得分 = **区域基础分 + 移动 +1 + 快速 +2 + 缩小 +2**（三项独立叠加）。
  时间到结算并把最高分记进排行榜，没有等待室，不注册进 HubCore 的游戏列表。
- **定位**：主城活动（同 CloudWalk / LobbyToys），只依赖 `MiniGameLib`（`HubAPI` + `PlayerStats` + `Chat`）。
  没装 HubCore 也能玩，只是 `/hub` 不会帮你退出（本插件会自己处理传送事件）。
- **进入 / 退出**：`/archery`（别名 `/am`、`/箭术大师`）→ 保存物品栏 →
  （如果配置了 `start`）传到射击位 → 发一把无限耐久 + 无限箭的弓（`INFINITY` 附魔，箭不消耗）。
  时间到 / 主动退出 / 传送 / 下线 / 死亡 / 飞行 / 切创造旁观 / `/hub` 都结束本局：
  清掉靶子和飞在空中的箭 → 还原物品栏 → **留在原地，不再传送回原位**（需要回大厅用 `/hub`）。
- **缩小药水**：局内不能使用 LobbyToys 的缩小药水；开局时会把 `Attribute.SCALE` 基础值重置为 1.0，
  局内每秒兜底一次。实现方式是开局向 HubAPI 上报 `已加入·箭术大师`，LobbyToys 的 `isPlaying` 检查
  会因此拒绝缩小药水；结束/退出时 `HubAPI.clearGame`。局内 tab 会显示 `[已加入·箭术大师]`。
- **靶场区域（编辑模式）**：`/archery edit` 进入编辑模式，保存并清空物品栏，发四个道具：
  - **木棍「区域选择棒」**：左键 = 对角 1，右键 = 对角 2（必须对着方块点）。
  - **下界之星「确认区域」**：两个对角都设好后右键 → 在**聊天栏输入基础分和移动轴**，例如 `3 x` 或 `3 z`
    （只写数字默认 X 轴），紧接着再输入该区域的**靶子参数**：`<靶子总数> <固定权重> <移动权重> <快速权重> <固定保底> <移动保底> <快速保底>`
    （空格分隔，后面的可以省略；直接输入 `done` 用默认值 `4 5 3 2 0 0 0`；`cancel` 取消）。
  - **绿宝石「保存场地」**：右键把当前所有区域写入 `config.yml` 的 `regions` 段。
  - **屏障「退出编辑模式」**：右键退出并还原物品栏；**未保存的修改丢弃**。
  可连续确认多个区域，每个区域各自的基础分、靶子总数、类型权重和保底都独立；
  区域可以跨世界，但开局只会用玩家/射击位所在世界的区域。
  `/archery regions` 查看全部区域（含参数），`/archery region remove <序号>` 删除；
  已保存的区域也可以在线改：`/archery region set <序号> count|weight|guarantee|score ...`。
- **射击站位（可选）**：`/archery setstart`，站在射击位执行；记录位置和视角（yaw/pitch）。
  配置后开局会传送过去；不配置时玩家就在执行 `/archery` 的位置开玩（适合把 NPC 放在靶场正前方）。
  `/archery info` 可以查看区域、站位、时长、加成和当前记录人数。
- **靶子**：`BlockDisplay` 方块。固定 / 移动 / 快速三种靶子用 `targets.blocks.fixed|moving|fast` 指定材质
  （默认 `TARGET` / `REDSTONE_BLOCK` / `GOLD_BLOCK`）。
  - 每个区域按自己的 `count` 刷靶子；类型先补未达 `guaranteed` 保底的类型，再按 `weights` 权重随机。
    三种权重全为 0 时该区域只刷固定靶（`count=0` 则不刷）。
  - **缩小靶**按 `targets.small-scale`（默认 0.5）缩放，并用 `targets.small-glow-color`（默认 `#FF0000`）发光；
    正常靶不发光，所以一眼能区分大小。缩小概率由全局 `targets.small-chance` 决定。
  - 移动靶速度在 `min-speed ~ fast-speed` 之间随机；快速靶速度在 `max(min-speed, fast-speed) ~ max-speed` 之间随机。
  - 移动靶只沿该区域设置的 **`axis`（x 或 z）** 来回运动，撞到区域边界反弹；另一轴保持不动，Y 不变。
  - **每名玩家有自己的一套靶子**，靶子只对本人可见（生成时对其他人 `hideEntity`，
    中途进服的玩家在 `PlayerJoinEvent` 里补隐藏），所以多人同时玩互不干扰。
- **计分**：`总分 = 区域基础分 + 移动加成 + 快速加成 + 缩小加成`，三项加成互相独立、可叠加；
  基础分在编辑模式确认区域时输入，加成在 `targets.bonuses` 里配置（默认 1 / 2 / 2）。
  命中后动作栏显示 `+总分 (基础 X + 移动 1 + ...)`，先算完总分再显示。
- **命中判定**：靶子是无碰撞的 `BlockDisplay`，所以不依赖原版命中事件。
  监听 `ProjectileLaunchEvent` 记录本人射出的箭；每 tick 用「上一刻位置 → 当前位置」的线段
  对每个靶子的 AABB 做 `BoundingBox#rayTrace` 扫掠检测，避免高速箭穿过小靶子漏判。
  命中后：加分、移除靶子、`respawn-delay-ticks` 后补一个新靶、发出粒子和音效。
  箭落地/命中方块时由 `ProjectileHitEvent` 立即移除，不会在场地里越积越多。
- **场上靶数**：每个区域的 `count` 决定该区域刷几个靶子；`targets.safety-max`（默认 256）只是全场安全上限；
  `targets.lifetime-seconds` > 0 时靶子会超时换新，0 = 不自动消失。
- **物品保护**：弓/箭带 PDC 标记，局内不能丢弃；每 20 tick 检查一次，丢了会自动补回。
  弓带 `INFINITY` + 无限耐久，所以箭射不完。编辑模式的道具同样不能丢弃。
- **排行榜**：`/archery top` 聊天前十；`/archery top place` 在当前位置放 `TextDisplay` 全息榜
  （固定朝向、吸附正方向、每 5 秒刷新，没有记录的格子显示「空位」）；`/archery top remove` 移除全部。
  位置/UUID 存在 `config.yml` 的 `leaderboards` 段，重启后按 UUID 找回。
- **玩家记录**：命名空间 `archery`，结构 `best.<uuid>` / `bestAt.<uuid>` / `names.<uuid>`；
  唯一文件 `server/plugins/MiniGameLib/players.yml`，旧 `ArcheryMaster/data.yml` 首次加载自动迁移。
  同分按 `bestAt` 升序（先达成者排前）。
- **入口 / 主流程**：`ArcheryMaster/src/main/java/com/archery/ArcheryPlugin.java#onEnable`
  → `ArcheryManager#load`（读 config + `PlayerStats.load("archery", …)` + 启动 tick）→
  `ArcheryCommand`（`/archery` 开局、`/archery edit` 编辑）→ `ArcheryManager#join` / `#enterEdit` →
  每 tick `tickSessions`（计时 / 移动靶 / 命中）→ `ArcheryListener`（各种退出、箭矢、编辑道具、聊天输入）。
- **命令**：
  - 玩家：无参数 / `join` / `start` / `play` 开始；`leave` 退出；`top` 排行榜；`stats [玩家]` 最高分和名次；`help`
  - 管理（OP 或 `archery.admin`）：`edit`（编辑靶场）、`regions`（列出区域和参数）、
    `region remove <序号>`、`region set <序号> count|weight|guarantee|score|axis ...`、
    `setstart`、`info`、`reload`、`reset <在线玩家|all>`、`debug`、`top place` / `top remove`
- **配置**：`server/plugins/ArcheryMaster/config.yml`（模板 `ArcheryMaster/src/main/resources/config.yml`）
  - `regions` / `start` / `leaderboards`：由命令写入；**命令会立即 `saveConfig`**，
    手改请在停服后改或 `/archery reload`
  - `game.time-seconds`：每局时长（默认 60 秒）
  - `targets.*`：刷新间隔、缩小概率、移动速度、快速线、缩小比例、缩小发光色、三种方块、三项加成；
    `targets.safety-max` 只是全场安全上限，实际数量由每个区域的 `count` 决定
  - `effects.sound|particle`：命中音效 / 粒子
  - `top-size`：排行榜显示几名
- **构建 / 交付**：`.\build.ps1 -Only ArcheryMaster`（自动带 MiniGameLib）；
  产物 `新服务器小游戏插件/ArcheryMaster.jar`，**不要**把 `config.yml` 打进交付包
  （里面有线上区域/站位/排行榜位置）。
- **常见坑**：
  - 区域没配置时 `/archery` 只会提示，不会开局；先 `/archery info` 或 `/archery regions` 看一遍。
  - **结束时不再传送回原位**：时间到/退出后玩家留在结束时的位置，需要回大厅用 `/hub`。
  - 编辑模式必须先 **保存场地** 再退出；直接右键屏障退出会丢弃本次修改。
  - 区域和射击站位/玩家世界不一致时不能开局：开局只用玩家（或 `start`）所在世界的区域。
  - 靶子是展示实体，结束时移除，不会留下真实方块或掉落物。
  - 服务器崩溃时未结束的靶子会在重启后消失（展示实体不持久化）；排行榜是持久化的。
  - 局内会取消伤害、禁止丢弃弓/箭；使用末影珍珠/烟花会直接结束本局。
