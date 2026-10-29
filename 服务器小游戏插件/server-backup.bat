@echo off
rem BuildBattle - one-click snapshot of the whole server folder into a single .zip.
rem Runs server-backup.ps1 with the execution policy bypassed (no signature required).
rem   server-backup.bat                 -> server-backup-<timestamp>.zip in D:\BuildBattle\
rem   server-backup.bat -AllowRunning   -> also works while a local java server is up
rem   server-backup.bat -WithPlayers    -> put players.yml inside the zip too
rem   server-backup.bat -OutFile D:\x.zip
rem Skips jdk / libraries / cache / versions / logs / world_backup_*.
rem Player records are copied to backups\ instead of being zipped.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0server-backup.ps1" %*
pause
