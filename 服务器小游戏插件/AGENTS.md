# AGENTS.md —— AI 项目入口（BuildBattle）

> 用户的开场话术通常是：**读 `docs/开发须知.md`，并遵守它第 8 节的约定；本次只做一件事：`<需求>`**。
> 本文件是浓缩版入口（约 1k token）。**先读这里**，需要细节再按 §3 路由表只读那一个文件，
> 不要整份重读文档、不要为了"了解上下文"通读插件源码。

## 1. 六条铁律（每次会话都生效）

1. **先要证据再改代码**：需要日志/配置/文件大小时直接问用户；不许连续猜超过两轮；
   机制不确定就去读服务器 jar 的字节码（`javap -c`），别凭记忆。找不到原因就加一行临时诊断日志。
2. **范围锁定**：只改用户指定的文件/插件，不顺手动别的插件或 MiniGameLib/HubCore；要扩大范围先问。
3. **有歧义先问**：游戏规则类歧义（误伤算谁死、谁算赢、平局怎么算）先问清楚再动手。
4. **改完必须**：`build.ps1 -Deploy` → `verify.ps1`（涉及玩家记录再加 `check-stats.ps1`），
   并报告「传哪几个 jar + 各自字节大小 + 在游戏里怎么验证」。
5. **回答尽量短**：细节写进文件（插件 README / docs），不要重复贴用户已知的代码；
   要改大文件前先说明"只需读哪几段"，不要整份重读。
6. **一轮只做一件事**，做完收尾；用户要做第二件事时提醒他新开会话（同一 bug 的改→验证→微调除外）。

## 2. 项目是什么（30 秒）

Paper 26.2 / Java 25，端口 25560，`online-mode=false`，一个主城 + 9 个小游戏，共 12 个自研插件：

```
MiniGameLib（公共库：HubAPI / Chat / SetupMode / WaitCountdown / PlayerStats）
    ↑                      ↑
HubCore（主城：菜单、tab、游戏注册表、回主城道具）   GameWorlds（多世界托管：建/加载 world_xxx 并套规则）
    ↑
9 款小游戏：GuessTheBuild(建筑猜猜乐) DuelArena(单挑 PvP) TntRun HotPotato(烫手山芋)
           MurderMystery(密室杀手) SkyPit(天坑) FootballArena(绿茵足球) ColorParty(色盲派对)
           BuildRun(你建我跑)
```

- 依赖方向：**小游戏 → MiniGameLib**，**HubCore → MiniGameLib**；GameWorlds 独立。
- **改了 MiniGameLib 或 HubCore ⇒ 全部游戏 jar 都要一起重传**。
- 玩家记录（战绩/击杀/第一名/玩家名）**只有一个文件**：`server/plugins/MiniGameLib/players.yml`。

## 3. 路由表：要做的事 → 只读这些

| 要做的事 | 只读这个 | 大概成本 |
| --- | --- | --- |
| 改某个小游戏的玩法/事件 | `<插件>/README.md` 顶部的「速查卡」（一段话 + 关键文件索引） | ~400 token |
| 全局约定 / 命令 / 会话规矩 | `docs/开发须知.md`（本文的完整版） | ~1.5k token |
| "本地好、上传就坏"、某个游戏不对劲 | `docs/已知坑.md`（症状→根因→怎么确认） | ~1k token |
| 玩家记录 / 战绩 / 备份上传记录 | `docs/玩家记录.md` | ~1k token |
| 主城菜单/物品/场地羊毛颜色 | `HubCore/README.md` 速查卡 + `server/plugins/HubCore/config.yml` 的 `menu` 段（改菜单不用改代码） | — |
| 接入一个新小游戏 | `HubCore/docs/游戏插件接入指南.md` | — |
| 上一件事的结论、最近改了什么 | `docs/改动记录.md` | ~300 token |
| 世界规则（pvp/难度/刷怪） | `server/plugins/GameWorlds/config.yml`（真实生效的那份） | — |

## 4. 命令（复制即用，全部相对仓库根 `D:\BuildBattle`）

```powershell
D:\BuildBattle\build.bat                      # 编译全部 12 个插件（产物 .build\out\）
D:\BuildBattle\build.bat -Only SkyPit         # 只编一个（自动带上 MiniGameLib/HubCore）
D:\BuildBattle\build.bat -Deploy              # 编译并复制到 server\plugins\
powershell -ExecutionPolicy Bypass -File verify.ps1        # 上传前预检：YAML 语法 + 关键配置 + jar 指纹
powershell -ExecutionPolicy Bypass -File check-stats.ps1   # 玩家记录自检：迁移 + 合并写盘
D:\BuildBattle\pack-upload.bat                # 打包上传（双击可用）；默认含 cache/versions/libraries，排除玩家记录
powershell -ExecutionPolicy Bypass -File pack-upload.ps1 -WithWorlds   # 同上 + 世界（其余开关见脚本头部）
powershell -ExecutionPolicy Bypass -File players-backup.ps1 -Action Save   # 单独存档玩家记录
D:\BuildBattle\server-backup.bat              # 一键快照整个 server\ -> server-backup-<时间>.zip
                                              # （含 world + cache/versions/libraries，面板不再 Downloading mojang_26.2.jar；
                                              #   只跳过 jdk\ logs\ world_backup_*；玩家记录只进 backups\，不进 zip）
```

## 5. 硬性约定（违反了就会"本地行、上传不行"）

1. **`.ps1` 必须是纯 ASCII**（PS 5.1 无 BOM 时按 ANSI 读，中文注释直接语法错误）；中文写进 `.md`。
2. **YAML 的 `键: 值` 冒号后必须有空格**：`detective-stone-seconds:90` 会让整份配置加载失败（表现为"场地全没了"）。`verify.ps1` 会检查。
3. `gamehub:` 命名空间**不要改名**（写在各插件 kit 存档的物品数据里）。
4. 插件数据目录是 `server/plugins/<插件名>/`；源码里的 `src/main/resources/config.yml` 只是模板，
   **改配置要连线上那份一起改**。关服时会用内存回写配置的插件：`MurderMystery / SkyPit / TntRun / DuelArena / HotPotato / BuildRun`（GTB 不写）；
   **所以改线上配置的顺序是"先停服 → 再覆盖 → 再启动"**，运行中覆盖会在停服时被旧值写回去（BuildRun 已改成只重写 `arenas` 段并先 `reloadConfig`）。
5. **同优先级（HIGHEST）事件拦截必须限定自己的世界**：Bukkit 同优先级按插件注册顺序调用，而这个顺序
   Windows/Linux 不同（真实事故：GTB 无条件取消所有世界的爆炸，吃掉了单挑的风弹）。
   写法：动手前先判断"是不是我的世界/我的场地"，不是就 `return`。
6. **上传面板铁律**：先停面板服 → 再覆盖 → 再启动；不能只传 `plugins/*.jar`，
   `config/` 与各插件的 `config.yml / data.yml / words.yml / arena-*.snap` 都要在。

## 6. 汇报格式（用户要的收尾）

```
改了什么：<文件列表，一句话一个>
编译：build.ps1 -Deploy 成功
要传的 jar：MiniGameLib.jar (18,532 B) + SkyPit.jar (26,533 B)   ← 逐字节大小
游戏里怎么验证：<具体命令/操作 + 期望现象>
写进文档：<更新了哪个 README / docs 文件>
下一步：<建议是否新开会话>
```
