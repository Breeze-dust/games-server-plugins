# LobbyToys（主城小玩具）—— 速查卡

> AI 先读这一段，别先读源码。

- **一句话职责**：主城专用的「小玩具」系统。右键玩具道具 → 生成一个**用你自己皮肤的人形分身**（26.2 原生 `Mannequin` 实体），
  分身跟着你移动、原地自转、上下按正弦弹动；你的本体被隐身藏起来，**你的镜头/朝向完全不动**。
  默认 `hide-from-owner: false`，所以你在第三人称（F5）也能看到自己的分身；其他玩家同样看得到。
  再右键一次收回。主菜单里点「小玩具」可打开切换菜单。
- **入口**：`LobbyToys/src/main/java/com/lobbytoys/LobbyToysPlugin.java#onEnable`
  （`saveDefaultConfig` → `Settings` → `CloneManager` 每刻 tick 任务 → 注册 `ToyListener` + `ToyMenu` →
  `/lobbytoys` 命令 → 每 100 刻 `ToyListener#refillAll` 给主城玩家补道具）。
- **命令**：`/lobbytoys menu`、`/lobbytoys give [玩家]`、`/lobbytoys off`、`/lobbytoys reload`、`/lobbytoys help`；
  别名 `toys` / `toy`。权限 `lobbytoys.use`（默认 true）、`lobbytoys.admin`（默认 op）。
- **HubCore 集成**：`HubCore` 的 `MenuLayout#ensureToysMenuEntry()` 在检测到本插件时，自动在主菜单第一个空槽放
  「小玩具」（PLAYER_HEAD，动作 `cmd:lobbytoys menu`，所有人可见）；已有同动作条目就不重复添加。
  **改 HubCore 的这处逻辑 ⇒ 按仓库规矩所有 jar 要一起重传。**
- **配置**：`server/plugins/LobbyToys/config.yml`
  - `worlds`（允许使用的世界，默认 `world`）、`give-on-join`（自动发道具）、`item-slot`（优先槽位 0-8）
  - `spin-degrees-per-tick`（4.0 = 4.5 秒一圈）、`bob-amplitude`（0.18 格）、`bob-period-ticks`（60 刻 = 3 秒）
  - `cooldown-millis`（右键开关冷却）
  - `hide-from-owner`（默认 **false**：第三人称能看到自己的分身；设为 true 只给其他玩家看，第一人称不会被模型挡住）
- **玩具切换菜单**：`/lobbytoys menu` 或主菜单「小玩具」。图标来自 `ToyType` 枚举；点一下即切换，
  选中项存在玩家 PDC（`selected-toy`），换服/重进后仍然记得；物品栏里已有的玩具会原地替换，没有就补一个。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 分身生成/隐身/跟随/自转弹动/清理 | `LobbyToys/src/main/java/com/lobbytoys/CloneManager.java` |
  | 玩具清单（名字、图标、lore） | `LobbyToys/src/main/java/com/lobbytoys/ToyType.java` |
  | 道具/菜单图标/选中持久化 | `LobbyToys/src/main/java/com/lobbytoys/ToyItems.java` |
  | 玩具切换 GUI | `LobbyToys/src/main/java/com/lobbytoys/ToyMenu.java` |
  | 右键/丢弃/进世界/进出服/补发道具 | `LobbyToys/src/main/java/com/lobbytoys/ToyListener.java` |
  | 命令与补全 | `LobbyToys/src/main/java/com/lobbytoys/ToyCommand.java` |
  | 配置读取 | `LobbyToys/src/main/java/com/lobbytoys/Settings.java` |
  | 主菜单自动加入口 | `HubCore/src/main/java/com/hub/menu/MenuLayout.java#ensureToysMenuEntry` |
- **加一个新玩具**：`ToyType` 加一项 → `ToyMenu` 自动多一个图标（自动居中排布）→ 在 `ToyListener#onInteract`
  的 `switch (type)` 里加分支实现效果。
- **实现要点（为什么不用封包库）**：`Mannequin` 是服务端实体，它的位置/朝向本来就是服务端状态，附近所有玩家都会同步看到；
  所以「在别人眼里旋转」不需要 PacketEvents/ProtocolLib。真人身形只用 `INVISIBILITY` 药水（无粒子、无图标、无 HUD 图标）藏起来，
  **从不调用 `player.setRotation(...)`**，所以玩家本人和旁观者的镜头都不会被转动。
- **常见坑**：
  - 分身位置 = 玩家位置（每刻 teleport），所以玩家与分身在同一个点。`hide-from-owner: false` 时第一人称可能看到分身模型内壁，
    受不了就改回 `true`（代价是 F5 看不到自己）。
  - 隐身是药水效果：如果玩家原本就处于隐身（记录在 `Session.hadInvisibility`），收回时不会移除它。
  - 离开 `worlds` 列表的世界、死亡、退出、插件卸载都会自动收回并恢复可见。
  - 分身 `persistent=false`，重启服务器不会残留；卡在世界里可用 `/lobbytoys off` 或直接 `kill @e[type=mannequin]`。
  - 主城 `lock-inventory` 关闭时玩家能挪道具，但丢弃已被拦截（`PlayerDropItemEvent` 取消）。
  - 菜单里点击全部被取消（含玩家背包格），不会把图标带出去。
  - 和 HubCore 无代码依赖：本插件只读自己的 `worlds`；`/hub` 清背包后，5 秒内的兜底任务会把道具补回第 `item-slot` 格。
- **验证方法**：
  1. 主城进服，快捷栏第 9 格（slot 8）出现「分身玩具」玩家头颅。
  2. 右键：**F5 第三人称能看到分身跟着你走、原地自转 + 上下弹动**；本人镜头方向不会被转动。
  3. 再右键收回，分身消失、本体恢复可见。
  4. 打开主菜单，点「小玩具」→ 弹出切换菜单，点分身图标显示「已切换」，物品栏里的玩具被替换。
  5. 在非 `world` 世界右键：动作栏提示「分身玩具只能在主城使用」。
  6. `/lobbytoys reload` 后改 `spin-degrees-per-tick` 立即生效。
- **未确认 / TODO**：
  - 无客户端环境，**旋转/弹动的实际手感（20 tick 传送的平滑度）与第一人称穿模程度未实机验证**；
    太跳就调小 `spin-degrees-per-tick`、调大 `bob-period-ticks`，或改成每 2 刻更新一次。
  - 未确认离线模式 + SkinsRestorer 下 `player.getPlayerProfile()` 是否总能带上皮肤纹理；若分身是默认皮肤，
    先查 SkinsRestorer 是否已经给该玩家注入纹理。
