# SeatGrab（抢位占座）—— 速查卡

- **一句话职责**：积分制抢座小游戏。每回合把背包放进展示框抢座位，座位离讲台越近分数越高；固定回合（大一~大六，默认 4 回合）结束后按总积分决出前三。可以用**击退笔**把对手打飞，也可以**空手偷走**别人框里的背包，偷来的背包按住右键**蓄力扔远**。已接入街机派对（key `seatgrab`）。
- **命令**（别名 `/sg`、`/grabseat`、`/chair`）：
  - 玩家：`/seatgrab join`、`leave`、`list`、`help`
  - 管理（OP 或 `seatgrab.admin`）：
    - `/seatgrab pos1` / `pos2`：站到场地两个对角执行（只取 XZ；pos1 的脚部 Y 记为地面）
    - `/seatgrab setlobby`：等待点 / 死亡后的旁观点
    - `/seatgrab setpodium [x|z]`：**讲台正面线**（一条沿 X 或 Z 轴的线；展示框按到这条线的**垂直距离**分排计分）
    - `/seatgrab setspawn` / `addspawn` / `clearspawns`：出生点（不配就在场地内随机找落脚点）
    - `/seatgrab save`、`start`、`stop`、`reload`、`frames`（检测场地内展示框数量）、`debug`
- **核心规则（1.1.2 积分制）**：
  - **展示框由管理员提前摆在场地里**（固定位置）；每回合代码扫描 `pos1/pos2` 范围（XZ 取场地，Y 从角点向上自动延伸 `arena.scan-height` 格），按展示框到**讲台正面线**的垂直距离分排，**每排随机挑配置数量的展示框作为可用座位**，其余展示框从 `placeholder-items` 里**随机放物品**（默认：被黏在桌子上的书包、水壶、衣服、课本、食品包装袋，名称可配置）。讲台不是圆心，而是一条沿 X 或 Z 轴的直线：`podium-axis: z` 表示排向沿 Z 推进（线沿 X，距离取 `|框Z-讲台Z|`），`x` 则相反。
  - 默认排配置（`score.rows`）：第一排（≤6 格）1 个座位、**+5 分**；第二排（≤12 格）3 个座位、**+3 分**；第三排及以后 5 个座位、**+1 分**。距离、座位数、分数都能在配置里改。
  - 每回合开始：清背包、冒险模式、传送到出生点，发一个**自己颜色的背包**（物品名“背包”，名字颜色就是玩家/背包颜色）和一根**击退笔**（击退 II 的末地烛，名字“击退笔”）。
  - 右键**本回合的空展示框**放入自己的背包；框会发出**背包颜色的光**（队伍发光颜色是原版 16 色里最接近的颜色，见下）。
  - **回合结束时**：背包在展示框里的玩家按该座位的排加分；**没放进去的玩家不淘汰**，只在该玩家位置触发一个**纯视觉爆炸效果**（粒子 + 音效，不破坏方块、不造成伤害、不影响积分），本回合 0 分。所有玩家继续下一回合。
  - 空手右键别人框里的背包 → 偷到手里，框恢复成白色空座位；**自己的背包已经放进展示框后，不能再取别人的背包**。**被偷的背包锁在手上**（不能换手、不能丢、不能放进框），只能按住右键蓄力、松开扔出。按得越久扔得越远；被击退笔打中会**原地掉落**（不会飞出去）。
  - 掉在地上的背包**只有失主能捡回**，捡起来后可以重新放进空座位；回合结束时地上没被捡回的背包消失。
  - **击退笔**：左键攻击玩家或右键玩家都能触发；**0 伤害**，但对方会有原版受击动画（别人视角里变红）、击退和音效。对局中 PvP 暂时开启（结束后恢复原状），伤害事件被取消，击退由插件自己施加，因此不受天坑等插件 PvP 优先级影响。
  - **固定回合数**：`rounds.total`（默认 4，最多 6，对应大一~大六）。每回合开始都会用 title 播报：主标题是 **大X**（大一/大二/…），副标题来自 `rounds.subtitles`，每回合可以不同。
  - **最终排名**：所有回合结束后统计总积分，按分数从高到低取前三名；**同分并列共享名次**（例如两人并列第一，下一名直接是第三名），街机派对会给并列玩家相同星星。
  - 回合时长 `round-seconds`（默认 45 秒）；出场地/掉下去会被拉回场地内；对局中禁止破坏/放置、禁止丢弃、禁止打开背包操作。
  - 单人强制开局 = 测试局，不记成绩、不上报街机派对。
- **配置**：`server/plugins/SeatGrab/config.yml`
  - 人数：`min-players`、`max-players`（最多 16，对应 16 种背包颜色）
  - 对局：`round-seconds`（45）、`round-end-delay-seconds`（4）、`end-delay-seconds`（5）
  - 回合：`rounds.total`（4，1~6）、`rounds.names`（大一~大六）、`rounds.subtitles`（每回合副标题）
  - 排/分数：`score.rows`（`max-distance` / `seats` / `score`，按距离从小到大；最后一段建议写 `9999`）
  - 占位物品：`placeholder-items`（`material` + `name`，默认 被黏在桌子上的书包 / 水壶 / 衣服 / 课本 / 食品包装袋，可增删改）
  - 击退笔：`pen.knockback-horizontal`（1.05）、`knockback-vertical`（0.42）、`cooldown-ticks`（10）
  - 蓄力：`charge.max-seconds`（2.0）、`min-speed`（0.6）、`max-speed`（1.9）、`up`（0.25）
  - 场地：`arena.pos1 / pos2 / lobby / podium / podium-axis / spawns / ground-y / scan-height`
  - 提示文字：`messages.round-title`（`{name}`）、`messages.round-subtitle`、`messages.round-end-*`、`sidebar.*`、`wait-items.*`
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 回合/计分/偷背包/蓄力/击退 | `SeatGrab/src/main/java/com/seatgrab/SeatGrabManager.java` |
  | 场地/讲台/展示框扫描范围 | `SeatGrab/src/main/java/com/seatgrab/SeatGrabArena.java` |
  | 事件（交互/伤害/保护） | `SeatGrab/src/main/java/com/seatgrab/SeatGrabListener.java` |
  | 命令/权限/tab | `SeatGrab/src/main/java/com/seatgrab/SeatGrabCommand.java` |
  | 配置模板 | `SeatGrab/src/main/resources/config.yml` |
- **建场步骤**：
  1. 搭好场地，在场地内**提前摆好展示框**（空框或放着普通背包都可以；每回合会被重置）。展示框建议固定在墙/柱子上。
  2. 站到场地两个对角：`/seatgrab pos1`、`/seatgrab pos2`（地面高度取 pos1 的 Y；展示框扫描从角点向上延伸 `scan-height`）。
  3. 站到讲台位置、面朝座位：`/seatgrab setpodium`（按面朝方向自动判定排向沿 X 还是 Z；也可 `/seatgrab setpodium x` 或 `z` 强制）。
  4. 站到等待点：`/seatgrab setlobby`；可选 `/seatgrab addspawn` 设多个出生点。
  5. `/seatgrab save`，再 `/seatgrab frames` 确认扫描到的展示框数量；用 `/seatgrab debug` 看排配置和讲台距离。
  6. 按实际场地改 `score.rows` 的 `max-distance` / `seats` / `score`，并改 `rounds.total` / `rounds.subtitles`。
  7. 至少 2 人 `/seatgrab join` → `/seatgrab start`，或走街机派对。
- **注意 / 已知限制**：
  - **讲台是必填项**：没设讲台时 `arena.ready()` 为 false，无法开局。讲台是沿 X 或 Z 轴的**直线**，距离取展示框到线的垂直距离（不是到讲台点的半径）；排向不对时用 `/seatgrab setpodium x|z` 重新指定。
  - **排是按到讲台线的垂直距离分段**，不是按建筑层数；如果场地不是标准教室排布，按实际距离调 `max-distance` 即可。
  - **每排座位数固定**（默认 1/3/5）：该排展示框不够时会有多少用多少；所有长度为 0 会中止本局。
  - **队伍发光颜色只有原版 16 色**：背包有 16 色，但团队发光只支持 16 种文字颜色，所以粉/品红都会接近淡紫色、浅灰会接近灰色、棕色接近金色。背包物品本身的颜色是准确的。
  - **PvP**：对局开始时把场地世界的 PvP 打开（`world.setPVP(true)`，和 HillKing 同做法），结束后恢复。场地世界别和其它玩法共用。
  - **没放背包不淘汰**：只扣本回合分数（0 分）并播放爆炸效果；只有死亡（正常对局不会掉血）或主动退出才会转旁观/离开。
  - **玩家退出**：会清掉背包里的局内道具，避免背包/击退笔被写进玩家存档。
  - **街机派对**：`games.seatgrab`（`seatgrab join` / `seatgrab start`），结算通过 `ArcadeBridge.report("seatgrab", …)` 上报前三名。
- **玩家记录**：命名空间 `seatgrab`，`games.seatgrab.firsts/seconds/thirds/points.<uuid>`，文件 `server/plugins/MiniGameLib/players.yml`。
- **验证**：
  1. 建好场后 `/seatgrab frames` 能看到展示框数量；`/seatgrab debug` 看讲台和排配置。
  2. 2 人开局 → 每人拿到彩色背包（名字为“背包”，颜色跟自己的背包颜色一致）+ 击退笔；第一排应有 1 个发白光的空框、第二排 3 个、第三排及以后 5 个（按配置和实际框数），其余展示框随机出现水壶/衣服/课本/食品包装袋/被黏在桌子上的书包等占位物品。
  3. 右键空框放入背包 → 框变成背包颜色的光；自己的背包放进框后再空手右键别人的框 → 会被拒绝；别人先空手右键 → 背包到手、框变白、手被锁住；按住右键蓄力、松开飞出；被击退笔打中 → 原地掉落。
  4. 45 秒结束 → 放进框的玩家按排加分，没放的玩家位置有爆炸效果、本回合 0 分但**不淘汰**；标题出现“大二/大三…”和对应副标题。
  5. 4 回合结束 → 聊天栏公布最终积分和前三名；街机派对里能看到星星；同分玩家共享名次。
