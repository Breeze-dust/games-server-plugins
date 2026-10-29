@echo off
rem BuildBattle - one-click "move to a new PC" dev kit.
rem Runs pack-devkit.ps1 with the execution policy bypassed (no signature required).
rem   pack-devkit.bat                 -> BuildBattle-DevKit-<date>.zip in this folder
rem   pack-devkit.bat -SkipToolchain  -> do not refresh toolchain\.m2 from %USERPROFILE%\.m2
rem Refuses to run while a local java server is still up (plugins save configs on shutdown).
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0pack-devkit.ps1" %*
pause
