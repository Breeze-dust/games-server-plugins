# AGENTS.md — games-server-plugins 仓库级约定

本文件管整个 monorepo。各子工程自己的规则见其目录下的 `README.md` / `AGENTS.md`
（BuildBattle 的入口是 [服务器小游戏插件/AGENTS.md](服务器小游戏插件/AGENTS.md)）。

## 子仓库（git submodule）改动规矩

`paper-hold-the-fort/` 是独立仓库，以 submodule 引入；以后新增的 submodule 同样适用。

1. **不要在 `master` / `main` 上直接改。** 任何修改都先自建分支。
2. **每次修改前先拉取上游**，确保基于最新提交：
   ```bash
   cd paper-hold-the-fort
   git fetch origin
   git switch master
   git pull --ff-only
   ```
3. **在自建分支上提交和推送**：
   ```bash
   git switch -c work/<主题>
   # ...修改...
   git add -A && git commit -m "feat: <主题>"
   git push -u origin work/<主题>
   ```
4. **主仓库只记录子仓库的 commit 指针。** 子仓库的改动必须先推到它自己的远端，
   再回主仓库提交 `paper-hold-the-fort` 的指针变化；不要只提交主仓库、
   让子仓库的改动停留在本地未推送状态。
5. 主仓库的主分支上不要直接改子仓库里的文件——那样既不在子仓库的分支流程里，
   也很容易在别人拉取时丢失。
