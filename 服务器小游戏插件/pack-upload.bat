@echo off
rem BuildBattle - one-click UPLOAD PACKAGE for the panel server.
rem Runs pack-upload.ps1 with the execution policy bypassed (no signature required).
rem   pack-upload.bat               -> upload-package.zip = plugins + configs + server.jar
rem                                    + cache/ versions/ libraries/ (no re-download on the panel)
rem                                    player records stay on the panel (excluded on purpose)
rem   pack-upload.bat -WithWorlds   -> also include world/
rem   pack-upload.bat -WithPlayers  -> also put players.yml in (OVERWRITES the panel's records!)
rem   pack-upload.bat -SkipRuntime  -> smaller zip, the panel downloads ~169 MB again
rem Refuses to run while a local java server is still up.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0pack-upload.ps1" %*
pause
