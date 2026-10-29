# GuessTheBuild（建筑猜猜乐）—— 速查卡

> AI 先读这一段，能省掉读 4000 行源码

- **一句话职责**：Hypixel Guess the Build 风格的中文猜词小游戏（包 `com.buildbattle.gtb`）：每局轮流由 1 人当建造者、用创造模式搭出题目词，其余人旁观并在聊天框猜词，建造结束后进入「评价 + 0~10 打分」，最后公布猜词排名与建筑评分排名。
- **入口 / 主流程**：`GuessTheBuild/src/main/java/com/buildbattle/gtb/GuessBuildPlugin.java#onEnable` 装配 `WordManager`/`ArenaManager`/`GameManager` 并注册 `GameListener`；玩法全在 `GuessTheBuild/src/main/java/com/buildbattle/gtb/game/GameManager.java`——加入/开局 `join()`/`start()`/`beginRound()`；出题 `WordManager.pickWords()` → `selectTheme()`/`startCustomWord()`/`autoPickTheme()` → `startBuildRound()`/`sendHint()`；猜词 `handleChat()`/`processCorrect()`/`maybeShortenRound()`；评分 `endRound()`/`startEvaluate()`/`handleEvalScore()`/`finishEvaluation()`；结算 `nextRoundOrEnd()`/`endGame()`；逐秒约束 `tick()`/`enforceStates()`。
- **命令**：`/gtb help|info|join [场地名]|leave|start|stop|theme <1~6|题目名>|floor [1~9|npc|sign]|stats [玩家]|stats reset|lobby|joinsign|reload|list|remove|del|delete <场地名>|setup [wand|save|cancel|clear|info|<场地名>]|arena list|add|edit|select|use|remove|del|delete|rename|info|word add|remove|list|blacklist add|remove|list|blacklist word add|remove|list`；需 `gtb.admin`（默认 op）的是 `start/stop/reload/joinsign/list/remove/setup/arena/word/blacklist/stats reset/lobby/floor npc|floor sign`；免权限的是 `help/info/join/leave/theme/stats <玩家>` 和「仅当前建造者可用的 `floor [1~9]`」；`plugin.yml` 里另有 `gtb.play`（default true）；别名 `/gb`，额外命令 `/leave`（别名 `quit`、`gtbleave`）。
- **配置 / 数据**：`server/plugins/GuessTheBuild/config.yml`（`game.*`：`min-players`/`max-players`/`rounds`/`build-time-seconds`/`theme-select-seconds`/`evaluate-seconds`/`max-build-height`/`hint-time-seconds`/`guess-order-scores`/`early-bonus`/`guess-cooldown-ms`/`build-margin`/`floor-npc`/`selfmade-blacklist`/`selfmade-word-blacklist`/`score-blacklist-below`/`score-library-above`，以及 `arenas.<场地名>`/`active-arena`/`lobby-point`/`join-sign`/`floor-sign`）；`words.yml`（题库：分类 → 词表，由 `WordManager` 读写，`/gtb word add|remove` 与自创词达标入库都写它）；`arena-<场名>.snap`（`Arena.saveSnapshotFile()` 写的 `x,y,z;BlockData` 文本快照，`ArenaManager` 管理，每轮 `Arena.restore()` 还原）。
- **玩家记录**：命名空间 `gtb`，结构 `firsts.<uuid>`（第一名次数）；代码是 `GameManager` 里 `PlayerStats.load("gtb", new File(plugin.getDataFolder(), "data.yml"))`，落盘唯一文件 `server/plugins/MiniGameLib/players.yml`（实现见 `MiniGameLib/src/main/java/com/hub/data/PlayerStats.java`）；写入靠 `recordFirst()` 里即时 `PlayerStats.save("gtb", data)`，**本插件没有 `saveAll()`**，关服只 `restoreAllOnDisable()` 还原玩家状态；清空命令 `/gtb stats reset`（OP 或 `gtb.admin`）。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 轮次流程 / 计分 / 评价 / 自创词结算 | `GuessTheBuild/src/main/java/com/buildbattle/gtb/game/GameManager.java` |
  | 爆炸与方块保护 / 等待道具 / 告示牌 / 聊天拦截 | `GuessTheBuild/src/main/java/com/buildbattle/gtb/listener/GameListener.java` |
  | 场地选区、多场地、快照与地板商店 | `GuessTheBuild/src/main/java/com/buildbattle/gtb/arena/ArenaManager.java`、`.../arena/Arena.java` |
  | 词库读写与自创词黑名单 | `GuessTheBuild/src/main/java/com/buildbattle/gtb/words/WordManager.java` |
  | 子命令 / 权限 / 提示文案 | `GuessTheBuild/src/main/java/com/buildbattle/gtb/command/GTBCommand.java` |
  | 配置键默认值与线上值 | `server/plugins/GuessTheBuild/config.yml`（真实生效）；模板 `GuessTheBuild/src/main/resources/config.yml` |
- **常见坑**：①**事件拦截必须先判断"是不是自己的世界"**：`GameListener.onExplode()/onBlockExplode()` 都在 `HIGHEST`，入口先 `inArenaWorld()`，不是自家场地世界就直接 `return`——历史上它无条件取消所有世界的爆炸，把单挑（Duel）的风弹吃掉了（详见 `docs/开发须知.md` 第 5 节）；②GTB 关服**不**回写 `config.yml`（`onDisable` 只还原玩家），但 `/gtb arena *`、`/gtb setup save`、自创词黑名单、`/gtb lobby`、`joinsign` 等操作会立刻 `saveConfig()`；③场地数据在 `config.yml` 的 `arenas` 段 + `arena-<场名>.snap`，不在 `data.yml`，上传面板必须一起带；④`config.yml`/`words.yml` 冒号后必须有空格，否则整份配置加载失败（表现为"场地全没了"）；⑤快照丢失时启动日志会警告「没有快照」，每轮只会重置默认地板（橡木原木）。
- **验证方法**：`/gtb info` 看状态/场地/词库，`/gtb arena list` 看场地是否「已设置/使用中」；≥3 人 `/gtb join` 后 `/gtb start`（或 OP 右键等待道具 纸 → 绿宝石确认）走完一轮：建造者收到私聊题目 + ActionBar「题目…剩余 N 秒」并可点选 1~5/6，猜中者公屏「猜对了！（第 N 个猜中，+X 分）」，时间到进入评价（建造者转生存留在原地，其他人创造 + 隐身，聊天框输入 `0~10` 公屏显示「XX 给建造者 Y 打了 Z 分」），评价结束大字幕显示本轮得分，整局结束打印两榜；`/gtb stats <玩家>` 的第一名次数应 +1；改词库/场地后 `/gtb reload` 生效。
- **未确认 / TODO**：①`GuessTheBuild/README.md` 的命令表未列 `floor`/`stats`/`lobby`/`joinsign`/`arena rename`/`setup <场地名>`（代码里有，README 未更新）；②`lobby`/`stats`/`joinsign`/`del`/`delete` 不在 Tab 补全的 `SUB_COMMANDS` 里；③`gtb.play`（plugin.yml 声明为"允许加入/开始游戏"）在代码中未被检索，实际只校验 `gtb.admin`；④`words.yml` 本地现有 9 个分类（动物/食物/物品/建筑/自然/职业/网络热词/抽象/自创）、502 行词条，与 README「内置 8 类 499 个词」的口径未核对（「自创」为运行期自动写入）；⑤`server/plugins/MiniGameLib/` 目前为空（`players.yml` 未生成），旧 `server/plugins/GuessTheBuild/data.yml` 仍在（首次加载会迁移、旧文件保留），是否已迁移需看开服日志；⑥（已核实并更正）`docs/玩家记录.md`、`docs/已知坑.md`、`docs/改动记录.md` 均已存在，按速查卡里的引用查即可。

---

# 建筑猜猜乐 GuessTheBuild

Hypixel **Guess the Build** 风格的中文猜词小游戏插件（**Paper 26.x 年度版本号体系** / Java 25）。

每轮 1 名玩家当**建造者**（创造模式全方块），其他玩家在场地四周**旁观**，通过聊天框猜主题词；轮流当建造者，游戏结束后按得分排名。

---

## 一、安装

1. 服务端要求：**Paper 26.2+**（年度版本号体系，非 1.x），**Java 25**。
2. 放入 `plugins` 的 jar：**`MiniGameLib.jar`（公共库）+ `HubCore.jar`（主城）+ `GuessTheBuild.jar`**，三者缺一不可
   （`plugin.yml` 里 `depend: [MiniGameLib, HubCore]`）。用仓库根目录的 `build.ps1 -Deploy` 可以一次性编译并部署全部插件。
3. 首次启动会在 `plugins/GuessTheBuild/` 生成 `config.yml`（含内置 8 类 499 个中文词）。

## 二、快速上手

### 管理员：设置场地（支持多个场地）

```
/gtb arena add 场地A      # 添加新场地并自动进入选区模式
# 进入选区模式会临时接管物品栏（原背包会被备份，退出选区模式时自动还原）：
#   木棍 左键=第一角、右键=对角   纸 右键=保存   屏障 右键=退出选区模式
/gtb setup save           # 也可用命令保存到当前编辑的场地，插件会自动记录当前方块作为“初始快照”
/gtb arena add 场地B      # 再添加一个场地（同样选区保存）
/gtb arena list           # 查看全部场地（标记使用中/编辑中）
/gtb arena select 场地B   # 切换游戏使用的生效场地
/gtb arena edit 场地A     # 重新编辑指定场地（选区后 /gtb setup save 覆盖保存）
/gtb arena remove 场地B   # 删除指定场地（配置与快照）
/gtb info                 # 确认生效场地
```

> 场地限制：体积 16 ~ 200,000 格；每个场地独立保存快照（`arena-<名称>.snap`），每轮结束插件会自动把生效场地还原成初始状态，**无需 WorldEdit**。
> 旧版单场地配置会在首次启动时自动迁移为「默认场地」。
> 选区模式由公共库 MiniGameLib 的 `SetupMode` 提供（备份/还原物品栏 + 工具分发），
> 与其它小游戏插件的建场方式一致；`/gtb setup wand` 可单独重新进入选区模式，`/gtb setup cancel` 取消。

### 玩家：开一局

```
/gtb join      # 加入游戏队列（3~16 人）
/gtb start     # 开始游戏（需要管理员：OP 或 gtb.admin 权限）
/gtb leave     # 中途离开
```

开始后循环进行：**建造（猜词）→ 评价（打分）→ 换人**，直到每人当过一轮建造者，公布**猜词排名与建筑评分排名**并恢复所有人状态（背包/装备/位置/模式）。

## 三、玩法与计分

| 规则 | 说明 |
| --- | --- |
| 选题 | 每轮开始前，建造者从随机 5 个**具体题目**中点击或输入 `1~5` 选一个（第 6 项为**自创词**）；超时（15 秒，可配置）自动随机分配 |
| 自创词 | 建造者选 6 后公屏告知「选择了自创词」，在聊天框**连续输入两次相同词语**确定题目（其他玩家看不到输入内容）；本轮结束按**平均评分**结算：**高于 6 分**该词加入题库（分类“自创”），**低于 4 分**该词进入词黑名单、且建造者进入自创词玩家黑名单（无法再选 6），4~6 分保持现状；OP 可用 `/gtb blacklist` 管理玩家黑名单、`/gtb blacklist word` 管理词黑名单 |
| 建造 | 建造者创造模式（不强制飞行，双击空格可落地），开局直接站在场地中央；超边界/超高度会被传回**场地中央** |
| 场地清空 | 每轮开始/结束自动清空整个建造空间（从地板到最高建造高度），换人后场地是干净的；**同一 tick 内瞬间完成**（只重写真正变过的方块），不会出现"地板先消失、再慢慢长回来"的过程 |
| 屏幕显示 | 建造者物品栏上方常驻显示**题目+分类+剩余时间**；猜测者常驻显示对应字数的下划线，提示出现后第一个下划线替换为提示字 |
| 猜词 | 其余玩家为旁观模式，出生在建造者位置自由观看；在聊天框输入完整词语即算猜中（忽略空格和标点，不分大小写） |
| 时间缩短 | 达到一半的人猜对且剩余时间大于 30 秒 → 缩短到 30 秒（公屏提示）；达到三分之二猜对 → 缩短到 10 秒（公屏提示）；不足对应时间也公屏提示即将结束 |
| 提示 | 开局广播字数（如 `_ _ _`），60 秒后揭示第一个字（可配置）；**单字题不提示**；"提示前猜中"以提示实际是否发出为准 |
| 评价 | 建造时间到后进入评价阶段：建造者生存留在原地（高亮可见、可被鸡蛋/雪球命中，**命中时会红屏受击 + 被击退**）、观众创造+隐身，观众获得无限道具（鸡蛋/雪球/烟花/TNT，投掷后自动补满）用于表达看法，同时可在聊天框输入 **0~10 打分**（每个猜测者每轮一次，分数公屏公开）；**评价期间建造者免疫 TNT 与爆炸伤害，不会被炸死**；10 秒结束清场前，屏幕中央大字幕显示建造者本轮得分（所有玩家评分平均值） |
| 地板商店 | 开服在建筑场地西南角生成"地板商店"NPC（村民，`game.floor-npc: false` 可关闭；**只会在场地里生成，主城不会再冒出来**，每轮清场也不会把它清掉），**只有当前建造者**右键可打开 9 格 GUI 界面选择地板：草方块/冰/白色羊毛/水/岩浆/沙子/石头/泥土/恢复默认；也可用 `/gtb floor` 命令直接打开；想手动另放一个用 `/gtb floor npc`；默认地板为**橡木原木**，每轮结束清场后地板重置为橡木原木 |
| 评价期防炸 | 观众手里的 TNT/烟花**不会破坏地板那一层**（地板材质可以被建造者换过，所以按"层"保护而不是按快照比对），也不会把建造者炸死；只有建造者本轮新搭的方块会被炸掉 |
| 方块保护 | 未加入游戏的玩家不能破坏任何方块；游戏内非建造者不能破坏；建造者不能挖场地地板层；**OP 全部豁免**，可以自由破坏 |
| 加入方式 | 主城（0,-60,-6）有"建筑猜猜乐 · 右键加入游戏"告示牌，右键即可加入；也可 `/gtb join` |
| 等待大厅 | 加入后清空背包，获得等待道具：主手"开始游戏"（纸，右键变绿宝石"确定开始？"，再右键确认即开始）、右下角"退出游戏"（床，右键离开）；道具只在等待阶段存在，开始后回收 |
| Tab 列表 | 玩家列表分 [游戏]（黄色，名字后带分数）和 [主城] 两组；本轮猜对者的名字变绿色，下一轮自动恢复 |
| 防刷屏 | 同一玩家两次猜词至少间隔 1.5 秒（可配置） |
| 计分 | 猜对按顺序给分：第 1 个 +20、第 2 个 +15、第 3 个 +10、其余 +5，提示前猜中再 +5；建造者不得分 |
| 提前结束 | 全部猜中时本轮立即结束 |
| 游戏结束 | 公布猜词排名与**建筑评分排名**（按每轮玩家打分平均分，**只公布最高分 1 人、最低分 1 人**，各占一行，同一玩家多轮取平均；最高/最低不重叠）后，所有人调回生存模式，传送回加入前的位置（主城） |

## 四、命令

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/gtb help` | 帮助 | 所有人 |
| `/gtb info` | 插件状态（人数/场地/词库） | 所有人 |
| `/gtb join` | 加入游戏 | 所有人 |
| `/gtb leave` | 离开游戏 | 所有人 |
| `/gtb start` | 开始游戏 | `gtb.admin` |
| `/gtb stop` | 强制结束游戏 | `gtb.admin` |
| `/gtb arena list` | 查看全部场地 | `gtb.admin` |
| `/gtb arena add <名称>` | 添加新场地并进入选区模式 | `gtb.admin` |
| `/gtb arena edit <名称>` | 重新编辑指定场地（选区后 `/gtb setup save` 覆盖保存） | `gtb.admin` |
| `/gtb arena select <名称>` | 切换游戏使用的生效场地 | `gtb.admin` |
| `/gtb arena remove <名称>` | 删除指定场地（配置与快照） | `gtb.admin` |
| `/gtb arena info [名称]` | 查看指定场地详情 | `gtb.admin` |
| `/gtb setup [wand\|save\|cancel\|clear\|info]` | 场地选区管理（保存到当前编辑目标） | `gtb.admin` |
| `/gtb word add <词> [分类]` | 添加词语（默认分类“其他”） | `gtb.admin` |
| `/gtb word remove <词>` | 删除词语 | `gtb.admin` |
| `/gtb word list [分类]` | 查看词库 | `gtb.admin` |
| `/gtb reload` | 重载配置/词库/场地 | `gtb.admin` |
| `/gtb blacklist add/remove/list <玩家>` | 管理自创词玩家黑名单 | `gtb.admin` |
| `/gtb blacklist word add/remove/list <词>` | 管理自创词词黑名单 | `gtb.admin` |
| `/gtb theme <编号>` | 建造者选择本轮主题（也可直接点击选项） | 所有人 |

别名：`/gb`。

## 五、配置（`plugins/GuessTheBuild/config.yml`）

```yaml
game:
  min-players: 3          # 最少几人开始
  max-players: 16         # 最多几人
  rounds: 0               # 总轮数，0 = 每人当一次建造者
  build-time-seconds: 150 # 每轮时间（秒）
  theme-select-seconds: 15 # 建造者选题时间（秒），0 = 关闭
  max-word-length: 5      # 题目最大字数
  max-build-height: 40    # 建造高度上限（超过会被拉回）；换人时清空到该高度
  hint-time-seconds: 60   # 多少秒后提示第一个字，-1 关闭
  round-delay-seconds: 5  # 轮间间隔（现被评价阶段取代，仅留作配置保留）
  evaluate-seconds: 10     # 评价阶段时长：建造时间到后建造者生存、观众创造，可扔鸡蛋/雪球/烟花并在聊天框 0~10 打分
  selfmade-blacklist: []    # 自创词玩家黑名单（UUID）
  selfmade-word-blacklist: [] # 自创词词黑名单（词）
  score-blacklist-below: 4  # 自创词平均分低于此值（不含）→ 词进黑名单 + 建造者禁自创词
  score-library-above: 6    # 自创词平均分高于此值（不含）→ 加入题库
  guess-order-scores: [20, 15, 10, 5] # 按猜中顺序给分：第1/2/3/其余
  early-bonus: 5          # 提示前猜中额外分
  guess-cooldown-ms: 1500 # 猜词防刷屏间隔
  build-margin: 5         # 建造者水平可超出的边缘
  floor-npc: true         # 开服是否在场地西南角自动生成"地板商店"村民；false = 只保留场地里已有的 NPC（主城残留仍会清掉）

arenas: {}                # 场地列表（由 /gtb arena 命令自动写入，无需手改）
active-arena: ''          # 当前生效场地名（由 /gtb arena select 设置）
```

词库**不在 config.yml 里**，而是独立放在同目录的 `words.yml`（分类标题 + 每行一个词）：

```yaml
# plugins/GuessTheBuild/words.yml
动物:
  - 蝌蚪
  - 蟋蟀
食物:
  - 猕猴桃
```

- `/gtb word add|remove` 直接改这个文件，自创词评分达标后也会自动写进「自创」分类；
- 改完 `/gtb reload` 即可重新载入（无需重启）；
- 升级说明：如果旧的 config.yml 里还留着 `words:` 段，插件首次启动会自动并入 `words.yml` 并把该段从 config.yml 删除（不会丢词）。
```

## 六、已知限制

- 同一时刻只进行**一局游戏**，但可配置多个场地（`/gtb arena select` 切换）轮换使用。
- 猜词为“完整词匹配”，不会做模糊匹配或同义词判断。
- 无法阻止玩家用方块**搭字**（Hypixel 同样靠规则约束），可在开局聊天中提醒。
- 场地外放置的方块不属于场地快照，不会被还原。
- 服务端关闭或 `/gtb stop` 时，所有玩家状态会被自动恢复。

## 七、重新编译

整个仓库统一用一个脚本编译（含本插件）：

```powershell
# 仓库根目录，编译 8 个插件到 .build\out\
.\build.ps1
# 编译并直接复制到 server\plugins\
.\build.ps1 -Deploy
```

脚本用仓库自带的 `server\jdk`（Java 25）编译，目标字节码 Java 21，依赖从本机 `.m2` 仓库读取 Paper 26.2 API。
也可以用各插件自己的 Maven 工程：`mvn -DskipTests package`（产物 `target/GuessTheBuild.jar`）。
