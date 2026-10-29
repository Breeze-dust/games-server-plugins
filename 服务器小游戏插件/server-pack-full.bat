@echo off
rem BuildBattle - one-click FULL server package for the panel (whole server folder -> one .zip).
rem Runs server-pack-full.ps1 with the execution policy bypassed (no signature required).
rem   server-pack-full.bat                 -> server-pack-<timestamp>.zip in D:\BuildBattle\
rem   server-pack-full.bat -WithPlayers    -> put players.yml inside the zip too
rem   server-pack-full.bat -AllowRunning   -> also works while a local java server is up
rem   server-pack-full.bat -OutFile D:\x.zip
rem IN : start.sh start.bat server.jar all root configs plugins\ config\ world\
rem      + cache\ versions\ libraries\ (no re-download on the panel)
rem OUT: jdk\ logs\ world_backup_* the player records plugins\MiniGameLib\players.yml
rem      (records are copied to backups\ instead, so uploads never wipe them), and the
rem      6 pre-migration plugins\<game>\data.yml snapshots (DuelArena GuessTheBuild
rem      HotPotato MurderMystery SkyPit TntRun), so an empty panel cannot resurrect
rem      old leaderboard stats via re-migration.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0server-pack-full.ps1" %*
pause
