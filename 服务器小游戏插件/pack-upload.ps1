# BuildBattle: make a clean upload package for the panel server (ASCII only, PS 5.1 safe)
# Usage:
#   powershell -ExecutionPolicy Bypass -File pack-upload.ps1              -> plugins + configs + server.jar + Paper runtime caches
#   powershell -ExecutionPolicy Bypass -File pack-upload.ps1 -WithWorlds  -> also include world/
#   powershell -ExecutionPolicy Bypass -File pack-upload.ps1 -WithPlayers -> also overwrite the panel's player records
#   powershell -ExecutionPolicy Bypass -File pack-upload.ps1 -SkipRuntime -> leave out cache\ versions\ libraries\ (small zip, panel re-downloads)
#
# What it does:
#   1. Refuses to run while a java server is still alive (plugins save their configs on shutdown).
#   2. Snapshots the ONE player-record file (plugins/MiniGameLib/players.yml) into backups\ and
#      EXCLUDES it from the archive, so uploading never wipes the panel's player records.
#      Pass -WithPlayers when you really want to push the local records to the panel.
#   3. Zips ONLY the files a panel needs, with the SERVER ROOT as the archive root
#      (so extracting into /home/container lands plugins/ and server.jar directly there).
#      cache\ (mojang_26.2.jar) + versions\ (patched paper-26.2.jar) + libraries\ (103 jars)
#      are the Paper bootstrap download cache: ship them and the panel NEVER prints
#      "Downloading mojang_26.2.jar" / "Applying patches" again.
#   4. Prints the key config files (size + mtime) so you can compare them on the panel.

param(
    [string]$ServerDir = 'D:\BuildBattle\server',
    [string]$OutFile   = 'D:\BuildBattle\upload-package.zip',
    [switch]$WithWorlds,
    [switch]$WithPlayers,
    [switch]$SkipRuntime
)

$ErrorActionPreference = 'Stop'

# 1. server must be stopped
$java = Get-Process java -ErrorAction SilentlyContinue
if ($java) {
    Write-Host "[STOP] java.exe is still running (PID: $($java.Id -join ', '))." -ForegroundColor Red
    Write-Host "       Stop the local server and WAIT for the process to exit, then run this again."
    Write-Host "       (plugins such as MurderMystery / SkyPit / TntRun / Duel / HotPotato write their"
    Write-Host "        config files on shutdown - zipping while it runs gives you half-written files.)"
    exit 1
}

# 2. collect items that must be uploaded
$items = @('plugins', 'server.jar', 'server.properties', 'bukkit.yml', 'spigot.yml', 'eula.txt')
if (Test-Path (Join-Path $ServerDir 'config'))        { $items += 'config' }
if (Test-Path (Join-Path $ServerDir 'paper-global.yml')) { $items += 'paper-global.yml' }
if ($WithWorlds)                                      { $items += 'world' }

# 2a. Paper bootstrap download cache (cache/mojang_26.2.jar, versions/26.2/paper-26.2.jar,
#     libraries/*.jar). Without these the panel prints "Downloading mojang_26.2.jar" /
#     "Applying patches" on first start and needs another ~169 MB of downloads.
foreach ($rt in 'cache', 'versions', 'libraries') {
    $rtAbs = Join-Path $ServerDir $rt
    if (-not (Test-Path $rtAbs)) { continue }
    $rtMb = [math]::Round((Get-ChildItem $rtAbs -Recurse -File -Force -ErrorAction SilentlyContinue |
                           Measure-Object Length -Sum).Sum / 1MB, 1)
    if ($SkipRuntime) {
        Write-Host "[runtime] $rt\ ($rtMb MB) skipped by -SkipRuntime - the panel WILL re-download it." -ForegroundColor Yellow
    } else {
        $items += $rt
        Write-Host "[runtime] $rt\ ($rtMb MB) included - no re-download on the panel." -ForegroundColor Green
    }
}

$present = @()
foreach ($i in $items) {
    if (Test-Path (Join-Path $ServerDir $i)) { $present += $i }
    else { Write-Host "[skip] $i (not found)" -ForegroundColor DarkGray }
}

# 2b. player records: one file, snapshotted here, not shipped unless asked for
$playersRel = 'plugins/MiniGameLib/players.yml'
$playersAbs = Join-Path $ServerDir 'plugins\MiniGameLib\players.yml'
if (Test-Path $playersAbs) {
    $backupDir = Join-Path (Split-Path $ServerDir -Parent) 'backups'
    if (-not (Test-Path $backupDir)) { New-Item -ItemType Directory -Path $backupDir -Force | Out-Null }
    $snap = Join-Path $backupDir ('players-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.yml')
    Copy-Item $playersAbs $snap -Force
    Copy-Item $playersAbs (Join-Path $backupDir 'players-latest.yml') -Force
    Write-Host "[players] snapshot -> $snap" -ForegroundColor Green
    if ($WithPlayers) {
        Write-Host "[players] -WithPlayers: the archive WILL overwrite the panel's player records." -ForegroundColor Yellow
    } else {
        Write-Host "[players] excluded from the archive (the panel keeps its own records)." -ForegroundColor Green
    }
} else {
    Write-Host "[players] $playersAbs not found yet - run the local server once." -ForegroundColor DarkGray
}

if (Test-Path $OutFile) { Remove-Item $OutFile -Force }

# 3. zip with tar.exe (Windows built-in): -a = infer format from extension, UTF-8 names preserved
$tar = Join-Path $env:SystemRoot 'system32\tar.exe'
if (-not (Test-Path $tar)) {
    Write-Host "[ERR] tar.exe not found; use 7-Zip instead and keep the archive root = server root." -ForegroundColor Red
    exit 1
}

Write-Host "Packing $($present.Count) item(s) from $ServerDir ..."
$tarArgs = @('-a', '-c', '-f', $OutFile, '-C', $ServerDir)
if (-not $WithPlayers) { $tarArgs += @('--exclude', $playersRel) }
$tarArgs += $present
& $tar @tarArgs
if ($LASTEXITCODE -ne 0) { Write-Host "[ERR] tar failed with code $LASTEXITCODE" -ForegroundColor Red; exit 1 }

$mb = [math]::Round((Get-Item $OutFile).Length / 1MB, 1)
Write-Host ""
Write-Host "[OK] $OutFile  ($mb MB)" -ForegroundColor Green
Write-Host "     Archive root = server root. Extract it INTO the panel's server directory (overwrite)."
Write-Host "     NOT uploaded (on purpose): .000*, *backup*, v*.zip, jdk/, logs/"
if ($SkipRuntime) {
    Write-Host "     NOT uploaded (on purpose): cache/, versions/, libraries/  <- the panel WILL download them again" -ForegroundColor Yellow
} else {
    Write-Host "     INCLUDED: cache/, versions/, libraries/  <- panel boots without Downloading/Applying patches" -ForegroundColor Green
}
if (-not $WithPlayers) { Write-Host "     NOT uploaded (on purpose): $playersRel  <- player records stay on the panel" }
Write-Host ""

# 4. print key files for a quick compare on the panel
Write-Host "Compare these on the panel (size / last write time):"
$key = @(
    'plugins\GameWorlds\config.yml',
    'plugins\MurderMystery\config.yml',
    'plugins\SkyPit\config.yml',
    'plugins\GuessTheBuild\config.yml',
    'plugins\GuessTheBuild\words.yml'
)
foreach ($k in $key) {
    $p = Join-Path $ServerDir $k
    if (Test-Path $p) {
        $f = Get-Item $p
        '{0,-46} {1,7} B  {2}' -f $k, $f.Length, $f.LastWriteTime
    } else {
        '{0,-46} (missing)' -f $k
    }
}
Get-ChildItem (Join-Path $ServerDir 'plugins\GuessTheBuild') -Filter 'arena-*.snap' -ErrorAction SilentlyContinue |
    ForEach-Object { '{0,-46} {1,7} B  {2}' -f ('plugins\GuessTheBuild\' + $_.Name), $_.Length, $_.LastWriteTime }
if (Test-Path $playersAbs) {
    $f = Get-Item $playersAbs
    '{0,-46} {1,7} B  {2}  <- player records (not in the zip)' -f $playersRel, $f.Length, $f.LastWriteTime
}
Write-Host ""
Write-Host "After upload, check the panel log for (exact lines: see the plugin READMEs):"
Write-Host "  [GameWorlds]   ... world world_duel ... pvp=true ..."
Write-Host "  [MurderMystery] loaded 1 arena: ..."
Write-Host "  [MiniGameLib]  player records ...players.yml -> duel=3, gtb=5, ..."
