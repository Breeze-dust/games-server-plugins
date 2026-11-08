# LobbyToys（主城小玩具）—— 速查卡

> AI 先读这一段，别先读源码。

- **一句话职责**：主城专用的「小玩具」系统。右键玩具道具 → 生成一个**用你自己皮肤的人形分身**（26.2 原生 `Mannequin` 实体），
  分身跟着你移动、原地自转、整体缩放弹跳；你的本体被隐身藏起来，**你的镜头/朝向完全不动**。
  默认 `hide-from-owner: false`，所以你在第三人称（F5）也能看到自己的分身；其他玩家同样看得到。
  再右键一次收回。主菜单里点「小玩具」可打开切换菜单（目前有 **分身玩具** 与 **缩小药水** 两种）。
- **缩小药水**：右键把自己的 `Attribute.SCALE` 设成 `shrink-scale`（默认 0.5），**模型和碰撞箱一起缩小**；
  再右键、离开主城、进入小游戏、死亡、下线、关服都会自动恢复原大小。分身也会跟着你一起缩小。
- **刮刮乐（实验功能，不在菜单里）**：`/lobbytoys scratch [行 列]` 在玩家面前生成一个**固定的 item frame**，
  里面是一张 128×128 的地图：上层是灰色涂层，下层是 `行 × 列` 的奖品格子。**按住左键**在卡面上刮，
  涂层按笔刷半径被擦掉；某格被刮掉 `reveal-threshold` 比例后算揭开，立刻发该格奖品并触发事件效果。
  再执行 `/lobbytoys scratch close`、离开 6 格、下线、换世界或超时都会自动收回。
  权限 `lobbytoys.scratch`（默认 op）；**故意不加入「小玩具」菜单和 HubCore 主菜单，普通玩家看不到入口**。
  - 配置段 `scratch`：`rows` / `columns`（1-6，**默认行列就在这里改**）、`brush-radius`、`reveal-threshold`、
    `session-seconds`、`screen-distance`、`max-distance`、`max-sessions`、`hide-from-others`，以及 `prizes.<id>`。
  - `hide-from-others: true`（默认）时只有本人看得到卡面，其他玩家被 `hideEntity`；改成 `false` 则附近玩家都能看到
    （适合活动围观，但别人也能看到你刮开的进度）。
  - 每个奖品可配 `name` / `material` / `amount` / `weight` / `color`（#RRGGBB）/ `label`（卡面 ASCII 文字）/
    `sound` / `message` / `image`（`plugins/LobbyToys/scratch-images/` 下的 PNG）/ `commands`。
  - **事件效果**：`commands` 会以控制台身份执行，支持 `%player%` / `%uuid%` / `%prize%`；
    每次揭开还会触发自定义事件 `ScratchCardPrizeEvent`（`com.lobbytoys.scratch`），其他插件可直接监听。
  - 屏幕只对本人可见（其他玩家被 `hideEntity`，可用 `scratch.hide-from-others: false` 改成所有人可见），
    地图 id 按 `max-sessions` 建池复用，不会无限增长。
  - 地图像素字体只认 ASCII，所以 `label` 里的中文会被过滤；中文奖品名仍会显示在聊天/动作栏里。
- **入口**：`LobbyToys/src/main/java/com/lobbytoys/LobbyToysPlugin.java#onEnable`
  （`saveDefaultConfig` → `Settings` + `ToyRegistry` → `CloneManager` 每刻 tick 任务 → 注册 `ToyListener` + `ToyMenu` →
  `/lobbytoys` 命令 → 每 100 刻 `ToyListener#refillAll` 给主城玩家补道具）。
- **命令**：`/lobbytoys menu`、`/lobbytoys give [玩家]`、`/lobbytoys off`、`/lobbytoys scratch [行 列|close]`、
  `/lobbytoys debug`、`/lobbytoys reload`、`/lobbytoys help`；
  别名 `toys` / `toy`。权限 `lobbytoys.use`（默认 true）、`lobbytoys.admin`（默认 op）、`lobbytoys.scratch`（默认 op）。
  `/lobbytoys debug` 会输出插件版本、手持道具上的 `toy-id` / `toy-look` / 旧标记，以及当前配置的期望指纹，排障先用它。
- **HubCore 集成**：`HubCore` 的 `MenuLayout#ensureToysMenuEntry()` 在检测到本插件时，自动在主菜单第一个空槽放
  「小玩具」（PLAYER_HEAD，动作 `cmd:lobbytoys menu`，所有人可见）；已有同动作条目就不重复添加。
  **改 HubCore 的这处逻辑 ⇒ 按仓库规矩所有 jar 要一起重传。**
- **配置**：`server/plugins/LobbyToys/config.yml`
  - `worlds`（允许使用的世界，默认 `world`）、`give-on-join`（自动发道具）、`item-slot`（优先槽位 0-8）
  - `spin-degrees-per-tick`（4.0 = 4.5 秒一圈）
  - `squash-amount`（弹跳时模型最多缩小多少，0.15 = 最扁时 85%）、`squash-period-ticks`（弹跳周期，40 刻 = 2 秒）；
    `squash-amount: 0` = 关闭弹跳
  - `shrink-scale`（缩小药水把玩家缩到原来的多少，0.2–1.0，默认 0.5）
  - `cooldown-millis`（右键开关冷却）
  - `hide-from-owner`（默认 **false**：第三人称能看到自己的分身；设为 true 只给其他玩家看）。
    **注意**：这个默认值只在配置里没有该键时生效；旧版生成的 `plugins/LobbyToys/config.yml` 里可能还是 `true`，
    需要手动改成 `false` 再 `/lobbytoys reload`（reload 会立刻对已在场的分身生效，不用重新开关）。
  - `toys.<id>`：**名字 `name`、代表道具 `material`、药水 `potion`、描述 `lore` 都在这里改**，支持 `&` 颜色代码；
    名字与 lore 统一用 `Chat.noItalic`（**正体**，Minecraft 默认把物品名渲染成斜体）。
    材质名写错/留空会自动回退默认值**并在控制台打警告**（不会加载失败）。键名（`clone`）是玩具 id，不要改。
    注意**没有 `WATER_BOTTLE` 这个材质**：水瓶要写 `material: POTION` + `potion: WATER`；`potion` 只对
    `POTION / SPLASH_POTION / LINGERING_POTION` 生效。
- **玩具切换菜单**：`/lobbytoys menu` 或主菜单「小玩具」。图标来自 `ToyType` + `ToyRegistry`；点一下即切换，
  选中项存在玩家 PDC（`selected-toy`），换服/重进后仍然记得；物品栏里已有的玩具会原地替换，没有就补一个。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 分身生成/隐身/跟随/自转/缩放弹跳/清理 | `LobbyToys/src/main/java/com/lobbytoys/CloneManager.java` |
  | 缩小药水的缩放切换与自动恢复 | `LobbyToys/src/main/java/com/lobbytoys/ShrinkManager.java` |
  | 玩具种类与缺省名字/道具/描述 | `LobbyToys/src/main/java/com/lobbytoys/ToyType.java` |
  | 从 config 读 `toys.<id>`（外观覆盖） | `LobbyToys/src/main/java/com/lobbytoys/ToyRegistry.java` |
  | 道具/菜单图标/选中持久化 | `LobbyToys/src/main/java/com/lobbytoys/ToyItems.java` |
  | 玩具切换 GUI | `LobbyToys/src/main/java/com/lobbytoys/ToyMenu.java` |
  | 右键/丢弃/进世界/进出服/补发道具 | `LobbyToys/src/main/java/com/lobbytoys/ToyListener.java` |
  | 命令与补全 | `LobbyToys/src/main/java/com/lobbytoys/ToyCommand.java` |
  | 刮刮乐会话/屏幕生成/奖品发放/事件 | `LobbyToys/src/main/java/com/lobbytoys/scratch/ScratchManager.java` |
  | 刮刮乐地图像素、涂层、格子揭晓判定 | `LobbyToys/src/main/java/com/lobbytoys/scratch/ScratchCard.java` |
  | 刮刮乐配置解析与权重抽奖 | `LobbyToys/src/main/java/com/lobbytoys/scratch/ScratchSettings.java` |
  | 刮刮乐左键/误触事件接入 | `LobbyToys/src/main/java/com/lobbytoys/scratch/ScratchListener.java` |
  | 刮刮乐奖品揭晓事件（给其他插件监听） | `LobbyToys/src/main/java/com/lobbytoys/scratch/ScratchCardPrizeEvent.java` |
  | 配置读取 | `LobbyToys/src/main/java/com/lobbytoys/Settings.java` |
  | 主菜单自动加入口 | `HubCore/src/main/java/com/hub/menu/MenuLayout.java#ensureToysMenuEntry` |
- **加一个新玩具**：`ToyType` 加一项（缺省外观）→ 在 `config.yml` 的 `toys.<新id>` 里配名字/道具/描述 →
  `ToyMenu` 自动多一个图标（自动居中排布）→ 在 `ToyListener#onInteract` 的 `switch (type)` 里加分支实现效果。
- **实现要点（为什么不用封包库）**：`Mannequin` 是服务端实体，它的位置/朝向本来就是服务端状态，附近所有玩家都会同步看到；
  所以「在别人眼里旋转」不需要 PacketEvents/ProtocolLib。真人身形只用 `INVISIBILITY` 药水（无粒子、无图标、无 HUD 图标）藏起来，
  **从不调用 `player.setRotation(...)`**，所以玩家本人和旁观者的镜头都不会被转动。
- **常见坑 / 能力边界**：
  - **原版客户端对玩家类实体只有等比缩放**（`Attribute.SCALE`），没有分轴缩放，所以做不到「垂直压扁的同时变宽」。
    现在的弹跳是整体缩小再恢复（`squash-amount`），视觉上是「压一下」；要真正的 squash-stretch 只能上资源包 +
    `ItemDisplay` 自定义人形模型（还需为非等比 `Transformation` 做模型），本插件没有走这条路。
  - **右键只触发玩具**：`onInteract` 注册在 `LOWEST` 且不跳过已取消，先于其他插件取消并 `DENY` 交互/使用，
    所以水瓶不会喝水、食物不会进食、方块不会放置；另有 `PlayerItemConsumeEvent` 兜底。
  - **进游戏自动结束**：`HubAPI.gameOf(uuid)` 非空（任意小游戏上报了状态）时，`CloneManager` 每 20 刻收回分身并移除隐身，
    `refillAll` / `giveLater` 不再补发玩具。天坑的战斗区域就在主城世界 `world` 内，换世界检测不到，所以用 HubAPI 的游戏状态判断。
  - **分身不参与推挤**：分身与玩家同坐标，所以 `configure` 里同时设了 `setCollidable(false)`、`setNoPhysics(true)`，
    并把它加入计分板队伍 `lobbytoys`（`COLLISION_RULE = NEVER`），否则原版实体分离会把玩家顶开。
  - 分身**空手**（`configure` 里 `getEquipment().clear()`），不会拿着物品栏里的玩具。
  - **改道具构造后一定要确认 `stack.setItemMeta(meta)`**：1.1.0–1.1.3 的 `base()` 漏了这一句，
    导致道具丢显示名/头颅皮肤（客户端显示成「玩家的头」），而外观指纹仍与配置一致、刷新逻辑不会重建——
    1.1.4 已修复，并用 `v2|` 指纹前缀强制旧道具重建。
  - **旧道具自动换新**：道具 PDC 里存了一个「外观指纹」（`lobbytoys:toy-look` = 材质|名字|描述）。
    进服时和每 5 秒的兜底任务里，只要指纹和当前配置不一致就原地重建；1.0.0 用的旧标记 `lobbytoys:clone-toy`
    也认，会被当作分身在第一次检查时迁移成新道具。所以改完配置**不 reload 也会在 5 秒内生效**（reload 是立刻生效）。
  - 分身位置 = 玩家位置（每刻 teleport），所以玩家与分身在同一个点。`hide-from-owner: false` 时第一人称可能看到分身模型内壁，
    受不了就改回 `true`（代价是 F5 看不到自己）。
  - 隐身是药水效果：如果玩家原本就处于隐身（记录在 `Session.hadInvisibility`），收回时不会移除它。
  - 离开 `worlds` 列表的世界、死亡、退出、插件卸载都会自动收回并恢复可见。
  - 分身 `persistent=false`，重启服务器不会残留；卡在世界里可用 `/lobbytoys off` 或直接 `kill @e[type=mannequin]`。
  - 主城 `lock-inventory` 关闭时玩家能挪道具，但丢弃已被拦截（`PlayerDropItemEvent` 取消）。
  - 菜单里点击全部被取消（含玩家背包格），不会把图标带出去。
  - 和 HubCore 无代码依赖：本插件只读自己的 `worlds`；`/hub` 清背包后，5 秒内的兜底任务会把道具补回第 `item-slot` 格。
- **验证方法**：
  1. 主城进服，快捷栏第 9 格（slot 8）出现配置里指定的小玩具道具（默认玩家头颅）。
  2. 右键：**F5 第三人称能看到分身跟着你走、原地自转、周期性地压扁/恢复**，分身空手；本人镜头方向不会被转动。
  3. 再右键收回，分身消失、本体恢复可见。
  4. 打开主菜单，点「小玩具」→ 弹出切换菜单，点图标提示「已切换」，物品栏里的玩具被替换；
     **切换玩具会把上一个玩具的效果收掉**（例如从分身切到缩小药水，分身会先收回）。
  5. 改 `config.yml` 的 `toys.clone.name/material/lore` → `/lobbytoys reload`：在线玩家物品栏里的玩具会被**原地重建**成新外观
     （`ItemStack` 材质创建时固定，所以必须重建；reload 时会遍历在线玩家替换，无需手动重新拿）。
  6. 在非 `world` 世界右键：动作栏提示「分身玩具只能在主城使用」。
  7. 缩小药水：右键身形变小、碰撞箱同步变小；再右键恢复；离开主城/进游戏会自动恢复。
     `/lobbytoys debug` 里 `shrink-scale` 相关行为不单独输出，但可在 `config.yml` 核对。
  8. 刮刮乐：`/lobbytoys scratch` → 面前 2.5 格处出现一张灰色卡面（3 行 × 3 列）；
     **按住左键**扫过卡面，涂层被擦掉、露出底色；刮开某格到阈值后动作栏提示「第 x 行第 y 列：奖品」，
     物品进背包、配置的 `sound/message/commands` 生效。
     `/lobbytoys scratch 2 4` 可临时改成 2 行 4 列；`/lobbytoys scratch close` 收回。
     无 `lobbytoys.scratch` 权限的玩家执行会提示没有权限。
- **未确认 / TODO**：
  - 无客户端环境，**缩放弹跳的实际手感、第一人称穿模程度未实机验证**；太跳就调小 `squash-amount`、
    调大 `squash-period-ticks`，或设 `squash-amount: 0` 关掉。
  - 未确认离线模式 + SkinsRestorer 下 `player.getPlayerProfile()` 是否总能带上皮肤纹理；若分身是默认皮肤，
    先查 SkinsRestorer 是否已经给该玩家注入纹理。
