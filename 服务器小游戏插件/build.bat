@echo off
rem BuildBattle - one-click build wrapper.
rem Runs build.ps1 with the execution policy bypassed (no signature required).
rem   build.bat            build only  -> .build\out\
rem   build.bat -Deploy    build + copy into server\plugins\
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1" %*
pause
