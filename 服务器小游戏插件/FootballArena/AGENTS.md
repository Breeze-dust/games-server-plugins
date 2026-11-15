# FootballArena 独立插件目录

本目录是「绿茵足球（FootballArena）」的**独立副本**，只包含这一个插件。
目的是让后续会话只读/只改本目录，不再读整个 monorepo。

## 规则

1. **只读/只改本目录**：不要读 `D:\BuildBattle\Football\games-server-plugins` 或其它项目文件。
2. **构建**：`.\build.ps1`，产物在 `out\FootballArena.jar`。
   部署到服务器：`.\build.ps1 -Deploy 'D:\path\to\server\plugins'`。
3. **依赖**：
   - Paper API 26.2：`paper-classpath.ps1` 从 `%USERPROFILE%\.m2` 找（首次需 Maven 缓存）。
   - MiniGameLib：`lib\MiniGameLib.jar`。MiniGameLib 更新后替换这个 jar 再重编。
   - JDK：优先 `-Jdk <home>` 或 `JAVA_HOME`；否则回退到本机 `D:\BuildBattle\server\jdk`（JDK 25）。
4. **改完**：至少编译通过；报告 `FootballArena.jar` 的字节大小；未在游戏内复测要写明。
5. **交付**：直接把 `out\FootballArena.jar` 给用户；不要回写 monorepo，除非用户明确要求。

## 这个插件是什么

红蓝两队足球赛：等待期右键红/蓝旗选队，开赛自动补队/调平；自写物理的方块展示球；
任何物品或空手左键都能踢球；先进球数或时间到点获胜，平局进金球加时；
对局玩家按队伍颜色发光描边（颜色取客户端当前记分板上的队伍色，见 `refreshTeamColors`）；
对局玩家自动穿着对应队色的皮革胸甲「队服」；物品栏最后一格另有一个同款锁死的「队服」展示道具（不能穿、不能丢，每秒兜底补穿/补放）。

源码入口：`src\main\java\com\football\FootballPlugin.java`。
