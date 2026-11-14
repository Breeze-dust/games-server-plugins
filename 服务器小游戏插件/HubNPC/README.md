# HubNPC —— 主城 NPC

> 用 Paper 26.2 自带的 `Mannequin`（人形实体）做 NPC：不会掉、不会被打、不会被实体推挤，
> 右键执行任意指令，皮肤走 SkinsRestorer。

## 速查卡

- 入口：`HubNPCPlugin` → `NpcManager`（生成/持久化/皮肤/右键指令）、`NpcCommand`（`/npc`）、
  `NpcListener`（右键与保护）、`Text`（颜色与渐变解析）、`SkinService`（SkinsRestorer）。
- 数据文件：`server/plugins/HubNPC/npcs.yml`（**运行时由服务器自己保存，上传时不要覆盖**）。
- 依赖：Paper 26.2；`softdepend: [SkinsRestorer]`（没有皮肤插件时只能生成默认皮肤）。
- 命令：`/npc`，别名 `/hubnpc`，权限 `hubnpc.admin`（默认 OP）。

## 命令

```
/npc create <id> <名字> [位置]        生成 NPC；不写位置=玩家脚下，并转身面向玩家
/npc skin <id> <玩家名>               加载该玩家皮肤（SkinsRestorer）
/npc skin <id> reset                  恢复默认皮肤
/npc command add <id> <指令>          添加右键执行的指令（可多条，按顺序执行）
/npc command list <id>                查看右键指令
/npc command remove <id> <序号>       删除某一条
/npc command clear <id>               清空
/npc rename <id> <新名字>             改名（同样支持颜色与渐变）
/npc move <id> [位置]                 移动 / 改朝向
/npc list                             列出全部 NPC（含是否已加载）
/npc info <id>                        详情
/npc tp <id>                          传送到 NPC
/npc remove <id>                      删除 NPC 与实体
```

`位置` 的写法：

```
here                                  玩家当前位置
<x> <y> <z> [yaw] [pitch]             当前世界的坐标（yaw/pitch 不写就面向发指令的玩家）
<世界> <x> <y> <z> [yaw] [pitch]      指定世界（如 world、world_gtb）
at <世界> <x> <y> <z> ...             同上，`at` 可省略
```

`<id>` 是管理用的唯一标识（不能有空格和点，最长 32），和显示名字无关。
带空格的名字用英文双引号包起来，例如 `/npc create shop "商店 老板"`。

## 名字颜色与渐变

NPC 名字、`/npc rename`、提示消息共用 `Text` 的解析，支持混用：

| 写法 | 效果 |
| --- | --- |
| `&6` `&l` `&r` … | 传统颜色/格式代码 |
| `&#FFAA00` | 单色十六进制 |
| `{#FFAA00}` | 单色十六进制（简写） |
| `<gradient:#FF0000:#00AAFF>文字</gradient>` | 渐变（MiniMessage 语法） |
| `{#FF0000:#00AAFF}文字{/}` | 渐变简写 |

例子：

```
/npc create guide &6&l新手向导
/npc create shop &#FFAA00杂货店
/npc create master "{#FF5555:#55AAFF}技能大师{/}" 100 64 200 180 0
```

## 右键指令

- 默认以**玩家身份**执行；以 `console:` 开头则由后台执行。
- 也可以显式写 `player:` 前缀。
- 占位符：`{player}` 玩家名、`{npc}` NPC 的 id、`{world}` 玩家所在世界。

```
/npc command add shop console: say 欢迎来到杂货店
/npc command add shop shop open
/npc command add guide tell {player} 你好，我是新手向导
```

NPC 右键有 400ms 防连点冷却；只处理主手。

## 生成后的实体属性

`apply()` 里统一设置（区块重新加载时会再套一遍）：

- `setImmovable(true)`：**不会被其他实体/玩家推挤**（NBT `immovable:1b`）
- `setGravity(false)`、`setSilent(true)`、`setInvulnerable(true)`、`setCollidable(false)`
- `setPersistent(true)`、`setRemoveWhenFarAway(false)`：重进服务器还在
- `setSkinParts(allParts)`、`setDescription(null)`（隐藏名字下方的描述行）、`setAI(false)`
- 名字组件 + 可见名牌；皮肤 profile 存在实体 NBT 和 `npcs.yml` 里，重启后不用重新联网拉取。

## 皮肤联动

`/npc skin <id> <玩家名>` 通过 SkinsRestorer 的 `SkinStorage#getPlayerSkin` 读取
textures 值+签名，异步请求后回主线程写入 `Mannequin#setProfile`。
取到的值同时存进 `npcs.yml`，皮肤插件暂时不可用时也能保持原皮肤。

## 本地验证（已做过）

在本地 `server/` 启动 Paper 26.2 实测：

- `/npc create testnpc &6Test 10 100 10` → 生成成功；
- `/data get entity <uuid>` 返回 `immovable:1b`、`NoGravity:1b`、`Invulnerable:1b`、`Silent:1b`、
  `CustomNameVisible:1b`、`BukkitValues.hubnpc:npc-id: testnpc`；
- 重启服务器后日志显示 `HubNPC 已启用，共 1 个 NPC`（数据从 `npcs.yml` 正常加载）；
- `/npc skin testnpc Notch` → 成功取到皮肤并写入；
- `/npc rename testnpc <gradient:#FF0000:#00AAFF>Grad</gradient>` → 控制台输出逐字渐变；
- `/npc command add/list/remove` 与 `/npc remove` 均正常，`npcs.yml` 随之更新。

未验证：真实客户端右键执行（本地无玩家客户端接入），逻辑走标准 `PlayerInteractAtEntityEvent`。

## 注意

- `Mannequin` 是 26.2 的实体；在旧服务端上生成会提示“当前服务端不支持 Mannequin 实体”。
- 无人在附近时区块会卸载，`/npc list` 显示“未加载”是正常的；玩家靠近或执行
  `/npc move`、`/npc remove` 时会自动加载区块。
- 上传时只传 `HubNPC.jar`；`plugins/HubNPC/npcs.yml` 属于运行数据，**不要覆盖线上文件**。
