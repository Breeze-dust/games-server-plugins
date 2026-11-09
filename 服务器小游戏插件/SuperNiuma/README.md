# SuperNiuma（超级牛马）

> 多回合小游戏骨架：**选机关 → 摆机关 → 跑酷 → 记分**，支持个人/团队、多地图、多房间。

## 速查卡

| 项目 | 内容 |
| --- | --- |
| 入口 | `com.superniuma.SuperNiumaPlugin` |
| 命令 | `/sn`（别名 `/niuma`、`/superniuma`） |
| 编译依赖 | MiniGameLib（`HubAPI`）、HubCore（运行期） |
| 数据目录 | `plugins/SuperNiuma/` |
| 配置文件 | `config.yml`（流程/记分）、`mechanisms.yml`（机关）、`arenas.yml`（地图，运行时写入） |
| 主类关键字段 | `arenas()` / `mechanisms()` / `mechanismManager()` / `rooms()` |
| 回合状态机 | `game/GameRoom.java`（`Phase`：WAITING → SELECTING → PLACING → RUNNING → SCORING） |

## 关键文件索引

| 文件 | 职责 |
| --- | --- |
| `game/GameRoom.java` | 房间、回合流程、记分、场地还原、玩家状态恢复 |
| `game/RoomManager.java` | 房间登记、加入/离开、房间号 |
| `game/Participant.java` | 玩家分数与回合临时数据 |
| `game/TeamState.java` | 队伍颜色/羊毛/计分 |
| `mechanism/MechanismRegistry.java` | 读取 `mechanisms.yml`、按权重抽机关 |
| `mechanism/MechanismManager.java` | 半透明预览、旋转/移动、放置、运行判定、炸弹、还原 |
| `mechanism/MechanismInstance.java` | 已放置机关（方块、实体、归属、朝向） |
| `menu/*` | 房间菜单、创建房间菜单、选机关菜单 |
| `listener/GameListener.java` | 左右键、滚轮、Q、跑酷判定、伤害/拾取拦截 |
| `command/SnCommand.java` | `/sn` 与地图配置命令 |

## 游戏流程

1. **创建房间**：`/sn menu` 选地图 → 设置模式（个人 / 团队 2/3/4 队）、目标分数、最高回合 → 创建。
2. **等待**：其他玩家 `/sn join <房间号>` 或从菜单加入；房主 `/sn start`。
3. **选择机关（默认 20 秒）**：每回合只随机一次槽位，所有玩家看到同一套排布；每人一个机关，被选走的机关会标注「该机关已被【玩家】选择」；界面不可自行关闭。所有人都选好后，再过 `selection.auto-close-seconds`（默认 2 秒）自动关闭并进入摆放；20 秒是兜底上限，时间到会随机分配剩余机关。
4. **摆放机关（默认 90 秒）**：可飞行；飞到想要的位置后，第一次左键/右键/滚轮会把机关锁定在面前，之后机关不再跟随玩家：
   - 左键：水平旋转 90°（尖刺这类只能上下翻转的机关改为 180° 翻转）
   - 右键：竖直翻转 90°（平台会立起来；尖刺这类只有上下方向的机关每次 180°）
   - 滚轮：沿视线方向整格移动；平视时走水平轴，抬头/低头时走 Y 轴（向上/向下）
   - Q 两次：确认放置（第一次 Q 后进入待确认，期间不能调整；放置失败会自动解除，可继续调整）
   所有人确认后立即进入跑酷，不必等满 90 秒。
5. **跑酷（默认 180 秒）**：冒险模式，到终点过关；过关/失败都切观察者。
6. **记分（默认 5 秒）**：按规则结算，展示分数，进入下一回合或结束。
7. **结束**：评选冠军，移除所有机关、还原方块、玩家回主城。

已放置的机关会跨回合保留；被吃掉的金币在下回合开始时重新出现（机关实例保留、展示实体重建）。

## 记分规则

可在 `config.yml` 的 `scoring` 段调整。

**团队模式**

- 队内任意一人到终点即该队过关。
- 没人过关：不得分。
- 只有一队过关：该队 `only-pass-points`（默认 1.5）。
- 多队过关：每队 `pass-points`（默认 1.0）。
- 最快过关队伍额外 `fastest-bonus`（默认 0.5）。
- 过关人数最多的队伍额外 `most-finishers-bonus`（默认 0.5，并列都加）。
- 金币分加到队伍。

**个人模式**

- 没人到终点：不得分。
- 只有一人到终点：该玩家 `only-pass-points`（默认 1.5）。
- 多人到终点：每人 `pass-points`（默认 1.0）。
- 最快到终点额外 `fastest-bonus`（默认 0.5）。
- 自己的机关害最多人失败：额外 `trap-bonus`（默认 0.5，并列都加）。
- 金币分加到个人。

**胜负**：达到目标分数且唯一最高 → 冠军；并列或最高回合后并列 → 加赛回合；加赛 10 回合仍并列时按当前最高分强制结束。

## 地图配置

```text
/sn arena create <名字>        在当前世界创建地图
/sn arena setspawn <名字>      等待/摆放区出生点
/sn arena setstart <名字>      跑酷起点（所有玩家从这里出发）
/sn arena setpos1 <名字>       可建造范围角 1
/sn arena setpos2 <名字>       可建造范围角 2
/sn arena setfinish1 <名字>    终点范围角 1
/sn arena setfinish2 <名字>    终点范围角 2
/sn arena setvoid <名字>       虚空高度（玩家 Y 低于该值判失败）
/sn arena info <名字>          查看配置是否完整
/sn arena list
```

`setpos1`/`setpos2`、`setfinish1`/`setfinish2` 必须成对执行；角点取玩家当前位置。

## 机关配置（`mechanisms.yml`）

每种机关支持：`enabled`、`weight`、`icon`、`name`、`lore`、`rotation` 和各自参数。
`weight: 0` 等于不出现。当前内置：

| 机关 | 行为 | 关键参数 |
| --- | --- | --- |
| `spike` 尖刺 | 滴水石 + 钟乳石；碰到任意一格失败；只能上下翻转 | — |
| `platform` 平台 | 羊毛平台，按队伍染色；多种尺寸子类 | `variants.small/medium/large.size` |
| `dispenser` 发射器 | 定时朝朝向发射箭，命中失败 | `interval-ticks`、`arrow-range`、`arrow-speed` |
| `coin` 金币 | 缩小的金块展示实体，无碰撞；碰到消失并记分 | `points`、`scale` |
| `bomb` 炸弹 | 放置时炸掉范围内所有机关（含金币），不破坏地图方块 | `radius`、`low-clear-*` |

炸弹保底：若上一回合通关率低于 `low-clear-rate`，下一回合按 `low-clear-weight` 提高权重，并保证至少 `low-clear-min-count` 个。

## 接入 HubCore

`plugin.yml` 已带 `minigame-game: true`，HubCore 会自动登记「超级牛马」。
要在主城指南针菜单里点开房间界面，在 `server/plugins/HubCore/config.yml` 的菜单项里加一条：

```yaml
menu:
  items:
    '<槽位>':
      material: LEATHER_HORSE_ARMOR
      name: '&e超级牛马'
      lore: ['&7点击创建/加入房间']
      action: 'cmd sn menu'
```

然后 `/hub reload`。游戏内的场地状态会通过 `HubAPI.setArena` 上报（等待中/对战中）。

## 当前限制（骨架阶段）

- **未在真实服务器实测**，只通过编译与 `verify.ps1`。
- 平台尺寸在选机关时按权重随机，尚未做「先选平台再选尺寸」的二次选择。
- 滚轮用快捷栏槽位变化模拟；调整道具使用机关自身的图标（金币=金块、尖刺=滴水石锥、平台=队伍羊毛），不再用木棍，避免与投影类模组抢物品。
- 预览用染色玻璃 `BlockDisplay`；正式放置后才变成真实机关方块。
- 可移动机关（旋转扫帚、摆锤、升降平台）尚未实现，扩展点已留在
  `MechanismManager.tick` / `MechanismInstance`：视觉用 `BlockDisplay` + 插值，
  碰撞判定建议走自定义包围盒，而不是依赖真方块。
- 机关不参与物理碰撞；尖刺/箭矢/金币都是自定义判定。
- 玩家之间的碰撞通过计分板 `COLLISION_RULE = NEVER` 关闭，离开房间时恢复原计分板。
