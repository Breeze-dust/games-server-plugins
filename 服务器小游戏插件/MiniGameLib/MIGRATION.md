# MiniGameLib 公共库 —— 小游戏插件迁移说明

公共库把 6 个小游戏插件里重复抄写的 4 样东西收拢到一处：**HubAPI（主城对接）、Chat、建场模式、等待倒计时**。

依赖关系（由 `plugin.yml` 的 `depend` 保证加载顺序）：

```
MiniGameLib  ←  HubCore  ←  各小游戏插件
     ↑___________________________|
```

- 小游戏插件**编译期只需要 MiniGameLib**（`com.hub.api.HubAPI` 就在这里，包名没变，所以原来调 HubAPI 的代码一行都不用改）。
- 运行期还需要 HubCore 在服上（不然 HubAPI 的调用会静默变成空操作）。

---

## 1. plugin.yml / pom.xml

```yaml
depend: [MiniGameLib, HubCore]     # 原来写的是 depend: [GameHub]
softdepend: [GameWorlds]           # 保持不变
```

如果插件里有给主城插件看的接入标记，键名从 `gamehub` 改成 `minigame`（中性名字，不会再随主城插件改名而失效）：

```yaml
minigame-game: true
minigame:
  name: 建筑猜猜乐      # tab 分区显示名
  icon: GRASS_BLOCK    # 中央菜单图标
  color: '6'           # tab 分区标题颜色
```

pom.xml 里把 `com.hub:game-hub` 依赖换成：

```xml
<dependency>
    <groupId>com.hub</groupId>
    <artifactId>minigame-lib</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

## 2. Chat（消息）

删除插件自己的 Chat 实现，改用：

```java
import com.hub.util.Chat;

Chat.send(player, "&a已保存");            // 支持 CommandSender（玩家或控制台）
Chat.broadcast("&6游戏开始！");
Chat.title(player, "&e3", "&7准备");
Chat.title(player, "&a开始！", "&f击杀对手", 5);   // 第 4 个参数 = 停留秒数
Chat.actionBar(player, "&e剩余 10s");
Component c = Chat.color("&6[加入]");      // & / § 都认
String legacy = Chat.toLegacy(c);          // 转回 § 文本（告示牌用）
```

需要给自己消息加前缀的插件（只有建筑猜猜乐有），保留一个很薄的本地包装类，
内部调用 `Chat.color(...)` 拼前缀即可，**不要**再自己写 `LegacyComponentSerializer`。

## 3. 建场模式（SetupMode）

公共库负责：备份背包/护甲/模式/位置 → 清空背包 → 发放带 PDC 标记的工具 →
按工具 id 回调插件 → 退出时还原背包。公共库自己处理保留 id `save` 与 `exit`。

```java
private final SetupMode setup = new SetupMode(this, "tr-setup-tool");   // keyName 每个插件不同

// 进入配置模式
setup.enter(player, arenaName, List.of(
        SetupMode.Tool.both(Material.STICK,      "pos",   "&b范围工具", "&7左键=范围角1&8/&7右键=范围角2"),
        SetupMode.Tool.right(Material.GOLDEN_HOE,"spawn", "&e出生点工具", "&7右键=出生点"),
        SetupMode.Tool.right(Material.PAPER,     "lobby", "&f等待点工具", "&7右键=等待点"),
        SetupMode.Tool.right(Material.EMERALD,   "save",  "&6保存场", "&7右键=保存"),
        SetupMode.Tool.right(Material.BARRIER,   "exit",  "&c退出配置模式", "&7右键=退出并恢复背包")
), new SetupMode.Handler() {
    @Override
    public void onTool(Player p, String arena, String toolId, Action action, Block clicked) {
        switch (toolId) {
            case "pos"   -> { /* 左键=pos1，右键=pos2 */ }
            case "spawn" -> { /* 用 p.getLocation() */ }
            default      -> { }
        }
    }

    @Override
    public void onSave(Player p, String arena) {
        saveAll();
        Chat.send(p, "&a已保存场 &e" + arena + "&a。");
    }
});

setup.exit(player);                       // 退出并还原（命令 /setup-exit 用）
setup.isEditing(player)                   // 是否在配置模式
setup.editingArena(player)                // 正在配置哪个场
setup.handleInteract(p, action, item, clicked)   // 监听器入口，true = 已消费该次交互
setup.isTool(item)                        // 拦截丢弃/移动配置工具
setup.exitAll();                          // onDisable 兜底（也可以不写，公共库关服会统一还原）
```

要点：

- 工具最多 9 件（放在快捷栏 0~8）。
- 插件专属工具（单挑的 kit GUI、建筑猜猜乐的选区木棍）也走这套：加一件 `Tool`，在 `onTool` 里
  按 `toolId` 分派到插件自己的逻辑。
- 只有工具声明的点击方式会触发回调（`Tool.right(...)` 只认右键，`Tool.both(...)` 左右都认）；
  用错键时这次交互同样会被吞掉（返回 true）但不做任何事 —— 与各插件原来 `switch` 的写法一致。
- `save` / `exit` 由公共库处理：`onSave` 回调插件落盘，`exit` 直接还原背包。
- 插件原有的 `enterSetup/exitSetup/handleSetupTool` 这类**公开方法名建议保留**，内部改成调用 SetupMode，
  这样 Command / Listener 不用跟着改。
- **已知限制**：SetupMode 没有"把正在配置的场地改名"的 API。如果插件支持在配置模式中重命名场地，
  需要自己处理（常见做法：让 handler 持有场地对象，而不是只认名字；或退出再重新进入配置模式）。

## 4. 等待倒计时（WaitCountdown）

公共库封装了原来各插件逐字相同的那套规则：

- 队列里有 OP → 暂停自动倒计时，等 OP 手动开始；
- 人数 < min → 取消；
- 人数 ≥ max → 3 秒后开始（已在跑的 30 秒倒计时会缩短为 3 秒）；
- 人数达标但未满 → 30 秒倒计时；**加人/退人都不重置**正在跑的倒计时。

```java
private final Map<String, WaitCountdown> waits = new LinkedHashMap<>();

private WaitCountdown wait(TntRunArena a) {
    return waits.computeIfAbsent(a.getName(), n -> new WaitCountdown(plugin, new WaitCountdown.Host() {
        @Override public Collection<UUID> waitingPlayers() { return a.getPlayers(); }
        @Override public int minPlayers() { return plugin.getConfig().getInt("min-players", 2); }
        @Override public int maxPlayers() { return plugin.getConfig().getInt("max-players", 12); }
        @Override public boolean hasOpQueued() {
            for (UUID id : a.getPlayers()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOp()) return true;
            }
            return false;
        }
        @Override public void onTick(long secondsLeft) {
            for (UUID id : a.getPlayers()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) Chat.actionBar(p, "&6开始倒计时：&e" + secondsLeft + "s");
            }
        }
        @Override public void onMessage(String message) { broadcast(a, message); }
        @Override public void onStart() {
            // 这里调插件内部"无权限校验"的开局方法，不要调命令层的那个
            Player any = a.getPlayers().isEmpty() ? null : Bukkit.getPlayer(a.getPlayers().get(0));
            if (any != null) start(any, a.getName());
        }
    }));
}

// 原来 updateWaitCountdown(a) 的位置改成：
wait(a).update();
// 场地删除/结束时：
wait(a).cancel();
```

`Host` 里的 `startMessage(int seconds)` 与 `opQueuedMessage()` 有默认实现
（`"&6Ns 后自动开始…"` / `"&7OP 在队列中，等待手动开始。"`），需要别的文案就覆盖它们。
每次 `update()` 都会在"队列里有 OP"时提示一次 —— 与四个插件原来的写法一致。

## 5. 迁移后的自检

```powershell
# 只编译你这个插件（会自动先编 MiniGameLib 与 HubCore）
powershell.exe -NoProfile -ExecutionPolicy Bypass -File D:\BuildBattle\build.ps1 -Only <插件名> -OutDir .build\check-<插件名>
```

看到 `[built] <插件名>` 就说明通过（**不要**用默认的 `.build\out`，避免多人同时编译互相覆盖）。
验证完把 `build\check-<插件名>` 目录删掉。

## 6. 不允许改的东西

- 只能改自己插件目录里的文件（含它自己的 `plugin.yml` / `pom.xml`）。
- 不要动 `MiniGameLib/`、`HubCore/`、`build.ps1`、`server/`。
- 不要改玩法、时长、人数、权限与提示文案的含义；这次迁移只换实现位置。
