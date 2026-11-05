# MurderMystery AGENTS —— 道具 / 机关配置同步规则

本文件只约束密室杀手插件（MurderMystery）。每次改道具或机关都必须遵守。

## 1. 道具改动必须同步配置文件

新增或修改道具时，一次改完下面这些地方，不能只改 Java：

1. **`items.yml` 的 `items.<id>`**：必须有对应的道具定义（`type` / `material` / `name` / `lore` / `amount` 等）。
2. **获得渠道的配置**：
   - 能从售货机获得 → 在 `mechanisms.yml` 的 `vending.goods` 里加一条 `item: <id>` + `weight`。
   - 能从老虎机获得 → 在 `mechanisms.yml` 的 `slot-machine.effects` 里加效果并给权重。
   - 只给特定阵营 → 在 `items.yml` 里写 `murderer-only: true`（目前售货机抽货会按这个过滤）。
3. **`MurderMysteryItems#configuredItem`**：新增 `type` 时在 switch 里加分支，否则配置存在也生成不出物品。
4. **`MurderMystery/README.md`**：更新速查卡、配置说明和验证方法。

## 2. 更新道具时自动加入相关配置

- 不要在代码里硬编码道具的名额/权重/价格；这些默认值必须同时写进 `items.yml` / `mechanisms.yml`。
- 新道具默认要**有配置、有获得渠道、有权重**，不要留下"代码能生成但配置里抽不到"的状态。
- 改完默认值后，把同样的内容同步到线上 `server/plugins/MurderMystery/` 下的同名文件。

## 3. 收尾检查

```powershell
build.ps1 -Only MurderMystery -Deploy
verify.ps1
```

汇报时要写清楚：改了哪些配置、道具从哪个渠道获得、默认权重/价格、怎么在游戏里验证。
