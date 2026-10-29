# AnimalSlaughter（动物屠宰）—— 速查卡

> AI 先读这一段，别先读源码

- **一句话职责**：圆形场地里定时刷**鸡/猪/牛**，玩家拿到伤害固定 1 的无冷却铁剑砍动物抢分；场上还混着**干扰项**，砍到会扣 50% 分数；限时 `game-seconds`（默认 60 秒），结束按分数排前三，分别 **+3/+2/+1 积分**。单场地，命令 `/animal`（别名 `/as`、`/slaughter`），显示名 `动物屠宰`。
- **规则**：
  - 开局至少 `min-players`（默认 2），最多 `max-players`（12）；等待倒计时用公共库 `WaitCountdown`，OP 在队列时暂停自动开局（右键开始纸或 `/animal start`）。
  - **开局分两段**：`/animal start` 后玩家先被传进场地、拿到剑、**可以自由移动**，动物**已经在场上刷出来了**但砍不动（伤害被取消），计时也没开始；同时屏幕中央每秒播 `游戏即将开始：3 / 2 / 1`（title）。`countdown-seconds`（默认 3）走完才允许砍、开始计时。
  - **剑**：铁剑，固定 **1 点伤害**（伤害事件里强制 `setDamage(1)`，和武器附魔/伤害加成无关），攻击速度属性拉到 1024 → **没有攻击冷却**。
  - **动物**：鸡 1 血 +1 分（上限最多）、猪 2 血 +3 分、牛 4 血 +5 分（上限最少）。上限 `mobs.limits.*`，到上限就不再生成。名字为绿色分值（`+1` / `+3` / `+5`），头顶常显。
  - **无无敌帧**：动物受伤后的无敌帧 `mobs.invulnerable-ticks` 默认 **0**（没有无敌帧，连点每一下都掉血）；想保留原版手感可以设 1~20（20 tick = 1 秒）。
  - **不击飞 / 不横扫**：所有动物的 `KNOCKBACK_RESISTANCE` 设为 1.0，砍中只掉血、不会被打飞；玩家的 `SWEEPING_DAMAGE_RATIO` 设为 0 并在事件里丢掉 `ENTITY_SWEEP_ATTACK` 伤害，**没有横扫之刃**，不会误伤旁边的干扰项。
  - **干扰项**：三种动物各有一个干扰版本，固定 1 血，名字红色 `-50%`（`mobs.decoy-percent` 可调），上限 `mobs.decoy-limits.*`。砍死干扰项按**当前分数的百分比扣分**，最低扣到 0。
  - **乱走**：任何动物被玩家打到后不再朝远离玩家的方向跑，而是在**被攻击点附近**随机乱走 `flee.duration-ms` 毫秒：每 `flee.repath-ms`（默认 400ms）换一个目标点，活动范围 `flee.wander-radius`（默认 4 格），不会离被攻击点太远，也不会跑出圆。牛用 `flee.cow-speed`（默认 2.4）走得比其它快，其它用 `flee.speed`（默认 1.5）。
  - **掉落物**：动物正常掉自己的战利品，但掉落物**不可拾取**（`canPlayerPickup=false`），`drops.clear-seconds`（默认 8 秒）后自动清理。
  - **玩家无伤**：场内玩家的所有伤害都被取消（PvP、摔落、火焰等），不会死；掉出场地（低于圆心 12 格）会被送回随机出生点。
  - **右侧计分板**：开局自动给所有参与者挂一个侧边栏，**只显示每名玩家的分数**（顶部/底部行默认不显示，分数那一列只有玩家自己的分）；想要的标题、顶部行、玩家行、底部行全部可在 `sidebar.*` 里改，`header-score`/`footer-score` 设 0 = 隐藏该行、设非 0 = 显示并把它作为右侧数字；标题支持 `{time}`/`{phase}`（倒计时期间自动变「准备」）。`sidebar.enabled: false` 关闭计分板，结算/离场自动还原主计分板。
  - 结算：按分数降序、同分比击杀数、再按名字排序；**只有分数 > 0 的玩家参与排名**，第 1/2/3 名分别 +3/+2/+1 积分，同时累计 `firsts/seconds/thirds`。写入 `players.yml` 的 `games.animalslaughter.points / firsts / seconds / thirds`。
- **命令**：`/animal`（别名 `as` / `slaughter`）
  - 玩家：`join`、`leave`、`list`、`stats [玩家]`、`help`
  - OP（`isOp()` 或权限 `animal.admin`）：**`setarena <半径>`**（站圆心执行：写入圆心+半径、**自动铺一格厚草方块圆盘**、清空旧出生点）、`setlobby`、`addspawn`、`clearspawns`、**`genspawns [数量]`**（自动生成出生点，默认最多人数）、`save`、`start`、`stop`、`reload`、`stats reset`
- **配置**：`server/plugins/AnimalSlaughter/config.yml`（源码模板 `AnimalSlaughter/src/main/resources/config.yml`）
  - `min-players` / `max-players` / `game-seconds` / `countdown-seconds`
  - `mobs.spawn-interval-ticks` / `mobs.spawn-per-cycle` / `mobs.limits.{chicken,pig,cow}` / `mobs.decoy-limits.*` / `mobs.health.*` / `mobs.score.*` / `mobs.decoy-percent` / `mobs.invulnerable-ticks`
  - `flee.speed` / `flee.cow-speed` / `flee.duration-ms` / `flee.wander-radius` / `flee.repath-ms`
  - `sidebar.enabled` / `sidebar.title`（支持 `{time}` `{phase}`）/ `sidebar.header` + `sidebar.header-score` / `sidebar.line` / `sidebar.footer` + `sidebar.footer-score`（`header-score`/`footer-score` **为 0 时整行不显示**，右侧数字就是这两个值；`line` 可用 `{name}` `{score}` `{kills}`，不含 `{name}` 会自动补名字）
  - `drops.clear-seconds`
  - `arena.center` / `arena.radius` / `arena.lobby` / `arena.spawns`（用命令写，别手写坐标）
- **建场步骤（OP，第一次上线）**：
  1. 找一个空旷位置，站到**圆心**上执行 `/animal setarena 16`（半径 4~64）。它按真实圆的距离铺一格厚草方块圆盘（四个方向的边缘长度一致，r=16 时每侧边缘 7 格；左右/上下/斜向都对称），并清掉圆盘上方 7 格；**如果之前已经建过圆盘（换位置或换半径），会先把旧圆盘里不属于新圆盘的草方块清掉**，避免两个圆叠在一起看起来不对称。圆心会自动对齐到所站方块的**方块中心**。
  2. `/animal genspawns 12` 自动生成出生点（圆内随机点位，站在草方块上，点间距 ≥1.5 格）。
  3. 站到场地外：`/animal setlobby`。
  4. `/animal save`；`/animal list` 应显示「已配置」。
  5. `/animal start`（或右键开始纸）测试；`/hub games` 应出现 **动物屠宰**。
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 刷怪/计分/扣分/逃跑/结算/战绩 | `AnimalSlaughter/src/main/java/com/animalslaughter/AnimalSlaughterManager.java` |
  | 圆形范围/平台生成/出生点/地面检测 | `AnimalSlaughter/src/main/java/com/animalslaughter/AnimalSlaughterArena.java` |
  | 伤害/死亡/掉落物/场地保护/道具 | `AnimalSlaughter/src/main/java/com/animalslaughter/AnimalSlaughterListener.java` |
  | 命令/权限/tab | `AnimalSlaughter/src/main/java/com/animalslaughter/AnimalSlaughterCommand.java` |
  | 默认配置 | `AnimalSlaughter/src/main/resources/config.yml` |
  | 显示名/图标/颜色 | `AnimalSlaughter/src/main/resources/plugin.yml` 的 `minigame:` 段 |
- **常见坑**：
  - **`setarena` 会改世界**：它按半径铺草方块、清空上方 7 格，重建时还会清掉旧圆盘残留；别在已有建筑里执行。
  - **圆看起来歪**：多半是上一版留下的旧圆盘没清。1.0.2 起 `setarena` 会自动清旧圆盘；如果之前手动改过地形，可能仍需要手动补一下。
  - **出生点为 0**：先 `setarena`（会铺平台），再 `genspawns`；圆里没地面时生成不出来。
  - **动物不掉落/不掉血**：其它插件若在 LOWEST 取消 `EntityDamageEvent` 会吃掉伤害；本插件在 HIGHEST 处理，若仍异常检查该世界是否有保护插件。
  - **菜单入口**：HubCore 主菜单不会自动加。先在 HubCore 的 `games` 段加一条 `name: 动物屠宰` / `icon: IRON_SWORD` / `color: 'c'` / `command: animal join` / `join-prefix: animal join`，再用 `/hub menu set <槽位> IRON_SWORD game:动物屠宰 &c&l动物屠宰` 摆一格。
  - 玩家攻击速度属性在结算时会被还原成 4.0（原版默认）；如果别的插件也改过这个属性，需要各自重设。
- **验证方法**：
  1. `/animal list` → 场地已配置、半径/出生点正确；`/hub games` 有 **动物屠宰**。
  2. 2 人 `/animal join` → OP 右键开始纸：两人被传进场地、拿到铁剑，场上已经有鸡/猪/牛，但砍不动（伤害被取消），可以自由走动；屏幕中央依次出现「游戏即将开始：3/2/1」，右侧计分板底行显示「准备 3s」。
  3. 3 秒后开始计时：动物头顶绿色 `+1/+3/+5`；砍一下 → 动物**只掉血不位移**，然后在原地附近乱走（牛明显更快）；每下只掉 1 血（猪两下、牛四下），**连续点击每一下都生效**（没有无敌帧）。
  4. 砍死动物：聊天栏上方出现 `+N 分`，右侧计分板该玩家分数同步增加；掉落物出现但不能捡，约 8 秒后消失。
  5. 砍到红色 `-50%` 干扰项：分数按当前分数的一半扣，最低到 0。
  6. 60 秒到 → 广播前三名（分数/击杀数）并分别 +3/+2/+1；`/animal stats <玩家>` 能查到积分与名次。
  7. 被打/掉出场地不会死；掉出平台会被送回出生点。
- **未确认 / TODO**：
  - 乱走手感（速度 1.5 / 牛 2.4、范围 4 格、换点间隔 400ms）需进服实测，可在 `flee` 段调。
  - 本地没有真实玩家，只做了启动 + 控制台 `/animal list`、`/animal help` 冒烟；刷怪/计分/掉落需进服验证。
