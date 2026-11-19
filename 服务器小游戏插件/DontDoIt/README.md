# DontDoIt（不要做挑战）—— 速查卡

> AI 先读这一段，能省掉读全部源码。

- **一句话职责**：每队随机分到一条「禁止词条」（别人看得见、自己看不见），做出词条描述的事就触发一次，
  规则数量 -1；规则数量归零即淘汰，最后活着的队伍获胜。词条库、触发条件、地形生成、钻石行为、
  猜词条、自定义词条、换词条的 120 秒节奏都照官方 Paper 插件 `NMB-Court-Team/DontDoIt` 移植。
- **队伍**：默认 16 队（前 8 队是彩虹色，其余按羊毛色补足）；`max-teams` 最大 48，17~32 队用陶瓦、
  33~48 队用混凝土做菜单图标，侧边栏用 `#RRGGBB` 十六进制颜色区分。
- **入口 / 主流程**：`DontDoItPlugin#onEnable` → `DontDoItManager#load`（读 config + criteria.yml +
  场地 + `PlayerStats` 战绩）→ `/ddi join` 进等待室发道具（槽 1 选队菜单、2 准备、3 书与笔、4 开始纸、
  8 退出床）→ `WaitCountdown` 倒计时或 OP 右键开始纸 → `Game#start`（按石层生成场地 → 世界边界 →
  传送到场地 → 分配词条）→ `Game#tickTask` 每 5 tick 判定状态型词条、每 20 tick 刷侧边栏/动作栏、
  到点自动换词条 → 触发 `Game#trigger` → 淘汰 `Game#eliminate` → 结算 `Game#endGame` → 5 秒后 `Game#reset`。
- **命令**：`/ddi`（别名 `dontdoit`、`byz`）
  - 玩家：`join [场地]`、`leave`、`list`、`stats [玩家]`、`custom <文字>`、`guess <队伍> <true|false>`、
    `trigger <队伍>`（触发别人的自定义词条）
  - 管理（OP 或 `ddi.admin`）：`create <名>`、`remove <名>`、`setcenter/setarea/setlobby/setspectator`
    （都针对当前场地，站在目标位置执行）、`gen <场地>`（只跑一遍地形生成，检查石层）、`save`、`reload`、
    `start/stop <场地>`、`change <场地> <队伍>`、`grant <场地> <队伍> <词条名>`、`setlife <场地> <队伍> <数>`、
    `settings`（打开设置菜单）、`settings <custom|guess|diamond-behavior|nightvision|border|ylevel>`、
    `settings diamond <add|reduce>`、`words [关键字]`、`selftest <场地>`（不需要真人，跑一遍开局→触发→淘汰→结算→重置）、`savemap <场地>`（把当前场地存成地图基线）、`rules <场地>`（查看世界 PvP/刷怪/死亡掉落）
- **自己的词条不可见**：侧边栏按队伍分别下发——每个队伍只看到**其他存活队伍**的词条；
  自己的队伍名/规则数量在动作栏里显示。队伍名、词条、tab 名字都用同一队伍颜色。
- **方块破坏放行**：GuessTheBuild 在 NORMAL 优先级全局取消非 OP 的方块破坏，
  本插件在 HIGHEST 优先级对自己场地内（且在对局中）的破坏/放置显式 `setCancelled(false)`，
  与 DigToBottom / BuildRun 的处理一致。
- **选队菜单**（用户要求的等待厅适配）：等待室槽 1 的白羊毛右键打开一屏队伍图标（16~48 队），
  显示每队人数与成员，点一下换队；`allow-unbalanced-teams: false` 时只能加入人数最少的队伍。
- **词条库**：`server/plugins/DontDoIt/criteria.yml`（模板 = 官方中文版原文件，145 条，29 种类型）。
  支持官方全部字段：`*` 通配、`#tag`、`!排除`、`a..b` 范围、`reversed`。
  **禁用词条**：在条目里加 `disabled: true`（本插件扩展），或直接删掉该条目/整段类型，再 `/ddi reload`
  （对局中不能 reload，先 `/ddi stop <场地>`）。想降低出现率可以调低 `difficulty`。
- **世界规则**：对局开始时按配置把场地所在世界设为 `pvp-enabled`、`mob-spawning-enabled`、
  `keep-inventory-on-death`（false = 死亡掉落），结束时还原世界原来的值。注意这些是世界级设置，
  同一世界的其他区域在对局期间也会受影响；建议把场地放在单独世界。
- **地图还原**：`restore-map: true` 时，每局开始前先还原基线（第一次开局自动保存到
  `plugins/DontDoIt/arena-<场地>.baseline`，含 `restore-top-offset` 格地表），开局生成矿石，
  对局结束再还原一次；**方块和实体（猪牛羊等非玩家实体）一起还原**。还原范围 =
  场地边长 + 世界边界留白（`world-border-padding`）× 同宽，底部到基岩、顶部到 `restore-top-offset`；
  这个高度以上禁止放方块（会提示超出可还原高度）。基线只存在磁盘，重开服也不会丢。
  基线尺寸和当前场地不一致时会在控制台提示，清理残留后重新 `/ddi savemap`。
- **地形生成**：照官方 `GameAreaGenerator`——从场地中心往下最多 30 格找「≥25% 石头类方块」的一层作为石层，
  石层下方放基岩（`bedrock-depth`），石层以上按深度比例把石头换成安山岩/煤矿/铁矿/钻石矿。
  **OP 必须先有一块石头地形**（例如中心下方挖出来的石层），否则开局会报「找不到石层」。
- **配置**：`server/plugins/DontDoIt/config.yml`（模板 `DontDoIt/src/main/resources/config.yml`）——
  `min-teams/min-players/max-teams/max-players-per-team/allow-unbalanced-teams/lives/criteria-change-seconds`；
  玩法开关 `custom-criteria-enabled / guess-criteria-enabled / diamond-behavior-enabled / diamond-behavior /
  diamond-behavior-disabled-threshold / guess-cooldown-seconds / guess-success-lives / guess-failed-lives /
  night-vision / use-world-border / enable-y-level-criteria`；地形 `bedrock-depth` + 四组 `*-generation`；
  场地 `arenas.<名>.{world,center,area-size,lobby,spectator}`。
- **玩家记录**：命名空间 `dontdoit`，结构 `games.dontdoit.firsts.<uuid>`；唯一文件
  `server/plugins/MiniGameLib/players.yml`。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 对局流程/触发/淘汰/结算/侧边栏 | `Game.java` |
  | 词条触发条件 | `criteria/CriterionMatcher.java` |
  | 时间型状态（不移动/疾跑/滞空…） | `criteria/PlayerState.java` |
  | 结算权重、随机抽取 | `criteria/CriterionLibrary.java` |
  | 场地字段与配置读写 | `DontDoItArena.java` |
  | 地形生成 | `ArenaGenerator.java` |
  | 队伍颜色/图标（16→48 队） | `TeamColor.java` |
  | 等待室道具、选队菜单、设置菜单 | `DontDoItManager.java` / `menu/*.java` |
  | 命令 | `DontDoItCommand.java` |
- **验证方法**（游戏内）：
  1. `/ddi create 矿井` → 站到石头地形上方 `/ddi setcenter 矿井` → `/ddi setarea 矿井 32` →
     站到等待位置 `/ddi setlobby 矿井` → `/ddi gen 矿井`（应报「石层 Y=…」）。
  2. `/ddi join 矿井` → 槽 1 白羊毛右键 → 选两支不同队伍（`allow-unbalanced-teams: false` 时第二人只能去另一队）。
  3. 两名玩家都右键槽 2 准备 → 3 秒后自动开局；或 OP 右键槽 4 开始纸。
  4. 开局后右侧侧边栏显示每队「❤规则数量 + 当前词条」；故意做词条描述的事 → 全服播报 + 屏幕标题 + 规则数量 -1。
  5. 等 120 秒（或 `/ddi change 矿井 <队伍>`）→ 公告「更换了词条，上一个词条是…」。
  6. `/ddi setlife 矿井 <队伍> 1` 后再触发一次 → 该队淘汰转旁观；剩一队时结算并记胜场（`/ddi stats`）。
  7. `/ddi settings` 打开设置菜单，点击开关应立即生效并写入 config.yml。
  8. 没人时可用 `/ddi selftest <场地>` 自检：期望依次出现「触发了规则」「已淘汰」「无人获胜/获得胜利」，最后 `[WAITING]`。
  9. 地图还原：`/ddi savemap <场地>` 保存基线（写入 `plugins/DontDoIt/arena-<场地>.baseline`），
     手动挖掉几个方块后 `/ddi selftest <场地>`，日志应出现「开局前还原了 N 个方块」「结束后还原了 N 个方块」，
     被挖掉的方块恢复原样。第一次开局若没有基线会自动保存当时的场地；改过地图要重新 `savemap`。
- **未确认**：
  - 未做真人联机实测：选队菜单/开局/触发/结算路径已通过编译与启动冒烟，但点击链路需要在游戏内手工验证。
  - 世界边界默认会改 **arena 所在世界** 的边界（开 `use-world-border` 时）；多个场地共用同一世界会互相影响。
  - `transfer_item`（熔炉取放）用点击事件近似判断，未覆盖所有拖拽/快捷键操作。
  - 官方「自定义词条侧边栏点击触发」在本插件里改为 `/ddi trigger <队伍>` 命令。
