# ArcheryMaster（箭术大师）—— 速查卡

> AI 先读这一段，能省掉通读源码。

- **一句话职责**：主城随时可玩的单人限时射箭。`/archery` 先选难度（正常 / 困难 / 恶意），
  每个难度有**独立的区域列表和靶子生成参数**；靶子从区域里刷新，命中得分 =
  **区域基础分 + 移动 +1 + 快速 +2 + 缩小 +2**（三项独立叠加）。时间到结算并把最高分记进该难度排行榜。
  没有等待室，不注册进 HubCore 的游戏列表。
- **定位**：主城活动（同 CloudWalk / LobbyToys），只依赖 `MiniGameLib`（`HubAPI` + `PlayerStats` + `Chat`）。
- **进入 / 退出**：`/archery` 打开 27 格难度菜单；`/archery normal|hard|evil` 直接开始。
  → 保存物品栏 →（如果配置了 `start`）传到射击位 → 发无限耐久 + 无限箭的弓。
  时间到 / 主动退出 / 传送 / 下线 / 死亡 / 飞行 / 切创造旁观 / `/hub` 都结束本局：
  清掉靶子和飞在空中的箭 → 还原物品栏 → **留在原地，不再传送回原位**（需要回大厅用 `/hub`）。
- **缩小药水**：局内不能使用 LobbyToys 的缩小药水；开局会把 `Attribute.SCALE` 重置为 1.0，
  局内每秒兜底一次。开局向 HubAPI 上报 `已加入·箭术大师`，结束/退出时 `clearGame`。
- **靶场区域（按难度独立）**：`/archery edit <normal|hard|evil>` 进入对应难度的编辑模式：
  - **木棍「区域选择棒」**：左键 = 对角 1，右键 = 对角 2（必须对着方块点）。
  - **下界之星「确认区域」**：两个对角都设好后右键 → 聊天栏输入 `基础分 [x|z]`（例如 `3 x`，默认 X 轴）
    → 再输入一行区域参数：
    `<靶子总数> <固定权重> <移动权重> <快速权重> <正常权重> <缩小权重> <固定保底> <移动保底> <快速保底> <缩小保底>`
    （空格分隔，后面的可省略；直接输入 `done` 用默认 `4 5 3 2 65 35 0 0 0 0`；`cancel` 取消）。
  - **绿宝石「保存场地」**：右键把当前难度的所有区域写入 `difficulties.<难度>.regions`。
  - **屏障「退出编辑模式」**：右键退出并还原物品栏；**未保存的修改丢弃**。
  每个难度可以设置多个区域，各区域的基础分、靶子总数、五项权重、四项保底、移动轴都独立。
- **射击站位（可选）**：`/archery setstart`；不配置时玩家就在执行 `/archery` 的位置开玩。
- **靶子**：`BlockDisplay` 方块。
  - **运动类型**：固定 / 移动 / 快速。按 `weights.fixed|moving|fast` 随机，先补未达
    `guaranteed.fixed|moving|fast` 保底的类型；快速靶速度高于 `fast-speed`。
  - **大小**：正常 / 缩小。按 `weights.normal|small` 随机，先补 `guaranteed.small` 保底；
    缩小靶按 `small-scale` 缩放，并用 `small-glow-color` 发光（正常靶不发光）。
  - 移动靶只沿区域 `axis`（x/z）往返，撞到区域边界反弹；另一轴固定，Y 不变。
  - 每名玩家一套靶子，只对本人可见（`hideEntity`），多人同时玩互不干扰。
- **计分**：`总分 = 区域基础分 + 移动加成 + 快速加成 + 缩小加成`，三项独立叠加；
  基础分在编辑区域时输入，加成在全局 `targets.bonuses` 配置。命中后动作栏显示
  `+总分 (基础 X + 移动 1 + ...)`，先算完总分再显示。
- **命中判定**：靶子是无碰撞的 `BlockDisplay`。监听 `ProjectileLaunchEvent` 记录本人射出的箭；
  每 tick 用「上一刻 → 当前位置」的线段对靶子 AABB 做 `BoundingBox#rayTrace` 扫掠检测。
  命中后加分、移除靶子、`respawn-delay-ticks` 后补靶、发粒子和音效。
- **残留清理**：靶子是持久化实体并带 PDC 标记；结束/命中按 UUID 删除，区块已卸载时先加载再删；
  结束按标记扫残留，启动/关服清理全部带标记实体，避免「只有贴图没有实体」。
- **物品保护**：弓/箭带 PDC 标记，局内不能丢弃；每 20 tick 检查一次，丢了自动补回。编辑道具同样不能丢弃。
- **排行榜**：`/archery top [难度]` 聊天前十；`/archery top place [难度]` 在当前位置放 `TextDisplay`
  全息榜（固定朝向、每 5 秒刷新）。榜底有「下一页 >>>」，右键在这三个难度的榜之间循环翻页；
  `/archery top remove` 移除全部。位置 / UUID / 当前难度 / 页脚点击区存在 `config.yml`。
- **玩家记录**：命名空间 `archery`，结构 `best.<难度>.<uuid>` / `bestAt.<难度>.<uuid>` / `names.<uuid>`；
  唯一文件 `server/plugins/MiniGameLib/players.yml`。旧版 `best.<uuid>` 首次加载自动迁到 `normal`。
- **入口 / 主流程**：`ArcheryPlugin#onEnable` → `ArcheryManager#load` → `ArcheryCommand` / `DifficultyMenu`
  → `ArcheryManager#join` / `#enterEdit` → 每 tick `tickSessions` → `ArcheryListener`。
- **命令**：
  - 玩家：`/archery`（菜单）、`normal|hard|evil`、`leave`、`top [难度]`、`stats [玩家]`、`help`
  - 管理：`edit <难度>`、`regions <难度>`、`region remove <难度> <序号>`、
    `region set <难度> <序号> count|score|axis|weight|guarantee ...`、
    `setstart`、`info [难度]`、`reload`、`reset <玩家|all>`、`debug`、`top place [难度]` / `top remove`
- **配置**：`server/plugins/ArcheryMaster/config.yml`（模板 `ArcheryMaster/src/main/resources/config.yml`）
  - `targets`：全局靶子参数（安全上限、刷新、速度、缩小比例/发光色、方块、三项加成）
  - `difficulties.<normal|hard|evil>`：`name` / `color` / `enabled` / `targets`（可覆盖全局）/ `regions`
  - `leaderboard.default-difficulty`：`/archery top` 默认难度
- **构建 / 交付**：`.\build.ps1 -Only ArcheryMaster`（自动带 MiniGameLib）；
  产物 `新服务器小游戏插件/ArcheryMaster.jar`，**不要**把 `config.yml` 打进交付包。
- **常见坑**：
  - `/archery` 提示“这个难度在这个世界还没有配置靶场区域”：用 `/archery edit <难度>` 在该世界划区域并保存。
  - 编辑模式必须先 **保存场地** 再退出；直接右键屏障退出会丢弃本次修改。
  - 结束不会传送回原位；需要回大厅用 `/hub`。
  - 旧配置里顶层的 `regions` 会自动迁移到 `normal`；旧 `targets.max` / `moving-chance` / `small-chance` 已废弃。
