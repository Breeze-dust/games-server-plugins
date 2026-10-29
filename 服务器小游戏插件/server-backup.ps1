# BuildBattle: one-click SNAPSHOT of the whole local server folder into a single .zip.
# ASCII only (PS 5.1 reads a BOM-less .ps1 as ANSI, so Chinese comments would break it).
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File server-backup.ps1
#   powershell -ExecutionPolicy Bypass -File server-backup.ps1 -AllowRunning
#   powershell -ExecutionPolicy Bypass -File server-backup.ps1 -WithPlayers
#   powershell -ExecutionPolicy Bypass -File server-backup.ps1 -Slim      (leave out the download caches)
#   powershell -ExecutionPolicy Bypass -File server-backup.ps1 -OutFile D:\snap.zip
#
# WHAT IS IN THE ZIP (archive root = server root, so extract INTO the server folder):
#   world/  server.jar  plugins/  config/  paper-global.yml  every *.yml / *.json / *.bat
#   cache/    -> cache/mojang_26.2.jar, the vanilla jar Paper downloaded once
#   versions/ -> versions/26.2/paper-26.2.jar, the patched server jar
#   libraries/-> the 103 Maven jars Paper fetched once
#   Those three are plain Java jars (no .dll/.so) and are the reason a fresh server prints
#   "Downloading mojang_26.2.jar" + "Applying patches". Shipped in the zip, a restored or
#   freshly uploaded server starts WITHOUT downloading anything again.
# WHAT IS NOT IN THE ZIP (on purpose):
#   jdk/     -> your LOCAL Windows JDK; the panel has its own Java, this is useless there
#   logs/    -> old logs
#   world_backup_* -> old snapshots of ours (would double the size)
#   plugins/MiniGameLib/players.yml -> player records, see the note below
#
# Player records: the ONE file plugins\MiniGameLib\players.yml is EXCLUDED from the zip
# (so a snapshot can never overwrite the panel's records by accident). It is copied to
# backups\players-<stamp>.yml + backups\players-latest.yml instead, so nothing is lost.
# Pass -WithPlayers only when you really want the records inside the archive.

param(
    [string]$ServerDir   = 'D:\BuildBattle\server',
    [string]$OutFile     = '',
    [string[]]$SkipDir   = @('jdk', 'logs'),
    [string[]]$SkipGlob  = @('world_backup_*'),
    [switch]$Slim,
    [switch]$WithPlayers,
    [switch]$AllowRunning
)

$ErrorActionPreference = 'Stop'

# -Slim = old behaviour: only sources/configs, no download caches (panel re-downloads on first start)
if ($Slim) { $SkipDir = @('jdk', 'libraries', 'cache', 'versions', 'logs') }

if (-not (Test-Path $ServerDir)) {
    Write-Host "[ERR] server folder not found: $ServerDir" -ForegroundColor Red
    exit 1
}
$ServerDir = (Resolve-Path $ServerDir).Path
$root      = Split-Path $ServerDir -Parent

# 1. a running server holds configs and world data in memory; zipping then gives half-written files
if (-not $AllowRunning) {
    $java = Get-Process java -ErrorAction SilentlyContinue
    if ($java) {
        Write-Host "[STOP] java.exe is still running (PID: $($java.Id -join ', '))." -ForegroundColor Red
        Write-Host "       The server rewrites configs/world data on shutdown, so a snapshot taken now"
        Write-Host "       can be inconsistent. Stop the server and wait for the process to exit."
        Write-Host "       (If you really want a snapshot anyway: add -AllowRunning.)"
        exit 1
    }
}

# 2. output path (default: next to the server folder, timestamped)
if (-not $OutFile) { $OutFile = Join-Path $root ('server-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.zip') }
$outFull = [System.IO.Path]::GetFullPath($OutFile)
if ($outFull.StartsWith($ServerDir + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    Write-Host "[ERR] -OutFile must be OUTSIDE $ServerDir (otherwise the zip would contain itself)." -ForegroundColor Red
    exit 1
}

# 3. player records: snapshot into backups\, keep out of the archive
$playersRel = 'plugins/MiniGameLib/players.yml'
$playersAbs = Join-Path $ServerDir 'plugins\MiniGameLib\players.yml'
if (Test-Path $playersAbs) {
    $backupDir = Join-Path $root 'backups'
    if (-not (Test-Path $backupDir)) { New-Item -ItemType Directory -Path $backupDir -Force | Out-Null }
    $snap = Join-Path $backupDir ('players-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.yml')
    Copy-Item $playersAbs $snap -Force
    Copy-Item $playersAbs (Join-Path $backupDir 'players-latest.yml') -Force
    Write-Host "[players] snapshot -> $snap" -ForegroundColor Green
    if ($WithPlayers) {
        Write-Host "[players] -WithPlayers: the records WILL be inside the zip." -ForegroundColor Yellow
    } else {
        Write-Host "[players] excluded from the zip (records also kept in backups\)." -ForegroundColor Green
    }
} else {
    Write-Host "[players] $playersAbs not found - run the server once if you expect records." -ForegroundColor DarkGray
}

# 3b. report the Paper download caches: without them the panel downloads ~169 MB again
foreach ($c in 'cache', 'versions', 'libraries') {
    $p = Join-Path $ServerDir $c
    if (-not (Test-Path $p)) {
        Write-Host "[runtime] $c\ missing - a server restored from this zip WILL re-download it." -ForegroundColor Yellow
        continue
    }
    $mb = [math]::Round((Get-ChildItem $p -Recurse -File -Force -ErrorAction SilentlyContinue |
                         Measure-Object Length -Sum).Sum / 1MB, 1)
    if ($SkipDir -contains $c) {
        Write-Host "[runtime] $c\ ($mb MB) skipped by -SkipDir/-Slim - the panel will re-download it." -ForegroundColor Yellow
    } else {
        Write-Host "[runtime] $c\ ($mb MB) included - no re-download on the target server." -ForegroundColor Green
    }
}

# 4. top-level items to zip (skip the big local-only folders + old world snapshots)
$skip = @()
$items = @()
foreach ($e in (Get-ChildItem $ServerDir -Force | Sort-Object Name)) {
    $name = $e.Name
    if ($SkipDir -contains $name) { $skip += $name; continue }
    $globbed = $false
    foreach ($g in $SkipGlob) { if ($name -like $g) { $globbed = $true; break } }
    if ($globbed) { $skip += $name; continue }
    $items += $name
}
if ($items.Count -eq 0) { Write-Host "[ERR] nothing to pack in $ServerDir" -ForegroundColor Red; exit 1 }

# 5. zip with tar.exe (Windows built-in): -a = format from the extension, UTF-8 names kept
$tar = Join-Path $env:SystemRoot 'system32\tar.exe'
if (-not (Test-Path $tar)) {
    Write-Host "[ERR] tar.exe not found; use 7-Zip instead and keep the archive root = server root." -ForegroundColor Red
    exit 1
}
if (Test-Path $outFull) { Remove-Item $outFull -Force }

Write-Host ""
Write-Host "Packing $($items.Count) item(s) from $ServerDir ..."
Write-Host "  in : $($items -join ', ')"
Write-Host "  out: $($skip -join ', ')"
$tarArgs = @('-a', '-c', '-f', $outFull, '-C', $ServerDir)
if (-not $WithPlayers) { $tarArgs += @('--exclude', $playersRel) }
$tarArgs += $items
& $tar @tarArgs
if ($LASTEXITCODE -ne 0) { Write-Host "[ERR] tar failed with code $LASTEXITCODE" -ForegroundColor Red; exit 1 }

# 6. verify the archive really opened and holds what we asked for
$entries = & $tar -tf $outFull
$count   = ($entries | Measure-Object).Count
$mb      = [math]::Round((Get-Item $outFull).Length / 1MB, 1)
$inZip   = { param($p) [bool]($entries | Where-Object { $_ -eq $p -or $_ -eq "./$p" }) }

Write-Host ""
Write-Host "[OK] $outFull  ($mb MB, $count entries)" -ForegroundColor Green
Write-Host ("     world/level.dat                        : {0}" -f (& $inZip 'world/level.dat'))
Write-Host ("     server.jar                             : {0}" -f (& $inZip 'server.jar'))
Write-Host ("     plugins/ (folder)                      : {0}" -f (& $inZip 'plugins/'))
Write-Host ("     cache/mojang_26.2.jar                  : {0}" -f (& $inZip 'cache/mojang_26.2.jar'))
Write-Host ("     versions/26.2/paper-26.2.jar           : {0}" -f (& $inZip 'versions/26.2/paper-26.2.jar'))
Write-Host ("     libraries/ (folder)                    : {0}" -f (& $inZip 'libraries/'))
Write-Host ("     players.yml in zip                     : {0}" -f ([bool]($entries | Where-Object { $_ -like '*players.yml' })))
Write-Host ""
Write-Host "Restore: stop the server, extract this zip INTO the server folder (overwrite)."
Write-Host "         Archive root = server root, so plugins/ and server.jar land where they belong."
Write-Host "         With cache/ + versions/ + libraries/ inside, the server boots straight to"
Write-Host "         'Starting org.bukkit.craftbukkit.Main' - no 'Downloading mojang_...' line."
Write-Host "         NOT included: $($skip -join ', ')"
if (-not $WithPlayers) { Write-Host "         NOT included: $playersRel  <- keep it from backups\ if you must rebuild" }
