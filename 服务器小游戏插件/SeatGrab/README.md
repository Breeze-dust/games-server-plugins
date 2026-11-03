# SeatGrab（抢位占座）—— 速查卡

- **一句话职责**：每回合随机点亮场地里的若干展示框作为“座位”，玩家把自己的彩色收纳袋放进框里；回合结束没坐下的人淘汰，直到最后 3 人决出前三。可以用**击退笔**把对手打飞，也可以**空手偷走**别人框里的袋子，偷来的袋子按住右键**蓄力扔远**。已接入街机派对（key `seatgrab`）。
- **命令**（别名 `/sg`、`/grabseat`、`/chair`）：
  - 玩家：`/seatgrab join`、`leave`、`list`、`help`
  - 管理（OP 或 `seatgrab.admin`）：
    - `/seatgrab pos1` / `pos2`：站到场地两个对角执行（只取 XZ；pos1 的脚部 Y 记为地面）
    - `/seatgrab setlobby`：等待点 / 淘汰旁观点
    - `/seatgrab setspawn` / `addspawn` / `clearspawns`：出生点（不配就在场地内随机找落脚点）
    - `/seatgrab save`、`start`、`stop`、`reload`、`frames`（检测场地内展示框数量）、`debug`
- **核心规则**：
  - **展示框由管理员提前摆在场地里**（固定位置）；每回合代码扫描 `pos1/pos2` 范围（XZ 取场地，Y 从角点向上自动延伸 `arena.scan-height` 格），随机挑出本回合的座位，其余展示框自动填上**没有染色的收纳袋**当占位。
  - **座位数**：存活 ≥5 人 → `存活-2`（每回合淘汰 2 人）；剩 4 人 → 3 个、剩 3 人 → 2 个（各只淘汰 1 人）；剩 2 人或单人测试 → 1 个。**最后 3 人的回合结束后整局结束**：先放进框的两人按先后为第 1/2 名，没放进去的是第 3 名。
  - 每回合开始：清背包、冒险模式、传送到出生点，发一个**自己颜色的收纳袋**和一根**击退笔**（击退 II 的末地烛，名字“击退笔”）。
  - 右键**本回合的空展示框**放入自己的袋子；框会发出**袋子颜色的光**（队伍发光颜色是原版 16 色里最接近的颜色，见下）。
  - 空手右键别人框里的袋子 → 偷到手里，框恢复成白色空座位；**被偷的袋子锁在手上**（不能换手、不能丢、不能放进框），只能按住右键蓄力、松开扔出。按得越久扔得越远；被击退笔打中会**原地掉落**（不会飞出去）。
  - 掉在地上的袋子**只有失主能捡回**，捡起来后可以重新放进空座位；回合结束时地上没被捡回的袋子消失。
  - **击退笔**：左键攻击玩家或右键玩家都能触发；**0 伤害**，但对方会有原版受击动画（别人视角里变红）、击退和音效。对局中 PvP 暂时开启（结束后恢复原状），伤害事件被取消，击退由插件自己施加，因此不受天坑等插件 PvP 优先级影响。
  - 回合时长 `round-seconds`（默认 45 秒）；到时间结算，期间可以一直偷/扔/击退。
  - 出场地/掉下去会被拉回场地内；对局中禁止破坏/放置、禁止丢弃、禁止打开背包操作。
  - 单人强制开局 = 测试局，不记成绩、不上报街机派对。
- **配置**：`server/plugins/SeatGrab/config.yml`
  - 人数：`min-players`、`max-players`（最多 16，对应 16 种袋子颜色）
  - 对局：`round-seconds`（45）、`round-end-delay-seconds`（4）、`end-delay-seconds`（5）、`max-rounds`（15）
  - 击退笔：`pen.knockback-horizontal`（1.05）、`knockback-vertical`（0.42）、`cooldown-ticks`（10）
  - 蓄力：`charge.max-seconds`（2.0）、`min-speed`（0.6）、`max-speed`（1.9）、`up`（0.25）
  - 场地：`arena.pos1 / pos2 / lobby / spawns / ground-y / scan-height`（扫描高度默认 10）
  - 提示文字：`messages.*`、`sidebar.*`、`wait-items.*`
- **常改文件**：
  | 需求 | 文件 |
  | --- | --- |
  | 回合/座位/偷袋/蓄力/击退 | `SeatGrab/src/main/java/com/seatgrab/SeatGrabManager.java` |
  | 场地/展示框扫描范围 | `SeatGrab/src/main/java/com/seatgrab/SeatGrabArena.java` |
  | 事件（交互/伤害/保护） | `SeatGrab/src/main/java/com/seatgrab/SeatGrabListener.java` |
  | 命令/权限/tab | `SeatGrab/src/main/java/com/seatgrab/SeatGrabCommand.java` |
  | 配置模板 | `SeatGrab/src/main/resources/config.yml` |
- **建场步骤**：
  1. 搭好场地，在场地内**提前摆好展示框**（空框或放着普通收纳袋都可以；每回合会被重置）。展示框建议固定在墙/柱子上，别放在会被玩家碰到的位置。
  2. 站到场地两个对角：`/seatgrab pos1`、`/seatgrab pos2`（地面高度取 pos1 的 Y；展示框扫描从角点向上延伸 `scan-height`）。
  3. 站到等待点：`/seatgrab setlobby`；可选 `/seatgrab addspawn` 设多个出生点（不设就随机找安全落脚点）。
  4. `/seatgrab save`，再 `/seatgrab frames` 确认扫描到的展示框数量 ≥ 所需座位数。
  5. 至少 2 人 `/seatgrab join` → `/seatgrab start`，或走街机派对。
- **注意 / 已知限制**：
  - **展示框数量必须够**：开局时会检查，少于 `max(2, 存活-2)` 个会拒绝开始；每回合的座位数不够也会中止。
  - **队伍发光颜色只有原版 16 色**：收纳袋有 16 色，但团队发光只支持 16 种文字颜色，所以粉/品红都会接近淡紫色、浅灰会接近灰色、棕色接近金色。袋子的物品本身颜色是准确的。
  - **PvP**：对局开始时把场地世界的 PvP 打开（`world.setPVP(true)`，和 HillKing 同做法），结束后恢复。场地世界别和其它玩法共用。
  - **捡取**：只有失主能捡回自己的袋子；别人踩上去不会捡走。
  - **玩家退出**：会清掉背包里的局内道具，避免收纳袋/击退笔被写进玩家存档。
  - **街机派对**：`games.seatgrab`（`seatgrab join` / `seatgrab start`），结算通过 `ArcadeBridge.report("seatgrab", …)` 上报前三名。
- **玩家记录**：命名空间 `seatgrab`，`games.seatgrab.firsts/seconds/thirds/points.<uuid>`，文件 `server/plugins/MiniGameLib/players.yml`。
- **验证**：
  1. 建好场后 `/seatgrab frames` 能看到展示框数量；`/seatgrab debug` 看座位规则和当前状态。
  2. 2 人开局 → 每人拿到彩色收纳袋 + 击退笔；场上有 1 个发白光的空框，其余是普通收纳袋。
  3. 右键空框放入袋子 → 框变成袋子颜色的光；空手右键别人的框 → 袋子到手、框变白、手被锁住；按住右键蓄力、松开飞出（远近距离不同）；被击退笔打中 → 原地掉落。
  4. 45 秒结束 → 没坐下的人淘汰；人数减少后座位数按规则变化；最后 3 人结束 → 播报前三名；街机派对里能看到星星。
