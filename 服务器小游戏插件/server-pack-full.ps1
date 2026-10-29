# BuildBattle: pack the WHOLE local server folder into one .zip, ready to upload.
# ASCII only (PS 5.1 reads a BOM-less .ps1 as ANSI, so Chinese comments would break it).
#
# Usage (normally via server-pack-full.bat - just double-click it):
#   powershell -ExecutionPolicy Bypass -File server-pack-full.ps1
#   powershell -ExecutionPolicy Bypass -File server-pack-full.ps1 -WithPlayers
#   powershell -ExecutionPolicy Bypass -File server-pack-full.ps1 -AllowRunning
#   powershell -ExecutionPolicy Bypass -File server-pack-full.ps1 -OutFile D:\x.zip
#
# WHAT IS IN THE ZIP (archive root = server root, so extract INTO the server folder):
#   start.sh  start.bat  server.jar  server.properties  bukkit.yml  spigot.yml  eula.txt
#   paper-global.yml  commands.yml  *.json  server-icon.png  .paper/
#   plugins/   config/   world/
#   cache/     -> cache/mojang_26.2.jar      (Paper bootstrap download cache)
#   versions/  -> versions/26.2/paper-26.2.jar
#   libraries/ -> the Maven jars Paper fetched once
#   Those three together are why a fresh panel prints "Downloading mojang_26.2.jar" +
#   "Applying patches". Shipped in the zip, the panel boots straight away.
#   start.sh IS included on purpose: this zip is meant to be uploaded to the Linux panel,
#   where that file is the start command.
# WHAT IS NOT IN THE ZIP (on purpose):
#   jdk/     -> local Windows JDK (~289 MB); the panel has its own Java, useless there
#   logs/    -> old logs
#   world_backup_* -> our own old snapshots (would double the size)
#   plugins/MiniGameLib/players.yml -> the ONLY player-record file (stats / kills / wins /
#                 player names). It is copied to backups\players-<stamp>.yml +
#                 backups\players-latest.yml instead, so uploading this zip can never
#                 overwrite the panel's records. Use -WithPlayers to include it anyway.
#   plugins/<6 games>/data.yml -> PRE-migration record snapshots (DuelArena GuessTheBuild
#                 HotPotato MurderMystery SkyPit TntRun). Their content already lives in
#                 players.yml, but on an EMPTY panel (no players.yml in the zip) each
#                 game's first PlayerStats.load() re-migrates these snapshots and the old
#                 leaderboard comes back. Kept out of the zip; see $legacyDataRel below.
#
# NOTE: the zip is written with .NET (System.IO.Compression), not with tar.exe. bsdtar drops
# libraries/net/java/dev/jna/jna/5.19.1/jna-5.19.1.jar ("Can't add archive to itself", a folder
# named like its parent) in some runs and keeps it in others, so it cannot be trusted for a
# package that must be complete. Step 6 compares the file list against the finished zip.

param(
    [string]$ServerDir   = 'D:\BuildBattle\server',
    [string]$OutFile     = '',
    [string[]]$SkipDir   = @('jdk', 'logs'),
    [string[]]$SkipGlob  = @('world_backup_*'),
    [switch]$WithPlayers,
    [switch]$AllowRunning
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $ServerDir)) {
    Write-Host "[ERR] server folder not found: $ServerDir" -ForegroundColor Red
    exit 1
}
$ServerDir = (Resolve-Path $ServerDir).Path
$root      = Split-Path $ServerDir -Parent

# 1. a running server rewrites configs / world data on shutdown: zipping it now gives half-written files.
#    Only THIS server's java counts: the Minecraft CLIENT also runs on java.exe, and a blanket
#    "Get-Process java" refused to pack while the player was simply in game. The local server is
#    started by start.bat as "jdk\bin\java.exe ... -jar server.jar", so a java process counts only
#    when its exe lives under the server folder or it was started with -jar server.jar.
if (-not $AllowRunning) {
    $serverJava = @()
    foreach ($p in (Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'")) {
        $exeUnderServer = $p.ExecutablePath -and $p.ExecutablePath.StartsWith($ServerDir + '\', [StringComparison]::OrdinalIgnoreCase)
        $runsOurJar     = $p.CommandLine -and ($p.CommandLine -match '-jar\s+\S*server\.jar')
        if ($exeUnderServer -or $runsOurJar) { $serverJava += $p }
    }
    if ($serverJava.Count -gt 0) {
        Write-Host "[STOP] the local server is still running:" -ForegroundColor Red
        foreach ($p in $serverJava) {
            Write-Host ("       PID {0}: {1}" -f $p.ProcessId, $p.CommandLine)
        }
        Write-Host "       Stop the local server and WAIT for the process to exit, then run this again."
        Write-Host "       (-AllowRunning skips this check if you really need a snapshot right now.)"
        exit 1
    }
}

# 2. output path (default: next to the server folder, timestamped)
if (-not $OutFile) { $OutFile = Join-Path $root ('server-pack-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.zip') }
$outFull = [System.IO.Path]::GetFullPath($OutFile)
if ($outFull.StartsWith($ServerDir + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    Write-Host "[ERR] -OutFile must be OUTSIDE $ServerDir (otherwise the zip would contain itself)." -ForegroundColor Red
    exit 1
}

# 3. player records: the one file, snapshotted into backups\, kept out of the archive
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
        Write-Host "[players] -WithPlayers: the records WILL be inside the zip (overwrites the panel's)." -ForegroundColor Yellow
    } else {
        Write-Host "[players] excluded from the zip; also kept in backups\." -ForegroundColor Green
    }
} else {
    Write-Host "[players] $playersAbs not found - run the server once if you expect records." -ForegroundColor DarkGray
}

# 3b. legacy record snapshots, kept out of the zip for the reason in the header.
#     Exact paths on purpose, NOT a plugins/*/data.yml glob: a future plugin whose
#     data.yml holds live state (arenas, kits) must still ship.
$legacyDataRel = @(
    'plugins/DuelArena/data.yml',
    'plugins/GuessTheBuild/data.yml',
    'plugins/HotPotato/data.yml',
    'plugins/MurderMystery/data.yml',
    'plugins/SkyPit/data.yml',
    'plugins/TntRun/data.yml'
)

# 4. top-level items: everything except the local-only folders / old snapshots
$skip  = @()
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
if ($items -notcontains 'start.sh') {
    Write-Host "[warn] start.sh not found in $ServerDir - the panel needs it to start." -ForegroundColor Yellow
}

# 4b. explicit file list: every file under the kept items, minus the player records.
#     Writing our own list is also what makes the count check in step 6 exact.
$list = New-Object System.Collections.Generic.List[string]
foreach ($item in $items) {
    $p = Join-Path $ServerDir $item
    if (Test-Path $p -PathType Container) {
        foreach ($f in (Get-ChildItem $p -Recurse -File -Force)) {
            $rel = $f.FullName.Substring($ServerDir.Length + 1) -replace '\\', '/'
            if (-not $WithPlayers -and $rel -eq $playersRel) { continue }
            if ($legacyDataRel -contains $rel) { continue }
            $list.Add($rel)
        }
    } else {
        $list.Add($item)
    }
}
if ($list.Count -eq 0) { Write-Host "[ERR] nothing to pack in $ServerDir" -ForegroundColor Red; exit 1 }

# 5. build the zip with .NET, NOT with tar.exe.
#    tar.exe (bsdtar) is unsafe here: it prints "Can't add archive to itself" for the folder
#    libraries\net\java\dev\jna\jna (a folder whose name equals its parent) and SILENTLY DROPS
#    libraries/net/java/dev/jna/jna/5.19.1/jna-5.19.1.jar; in one run it kept the file, in the
#    next it dropped it - not something an upload package may depend on. .NET writes exactly
#    the file list below, appends nothing else, and stores Chinese file names as UTF-8.
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

if (Test-Path $outFull) { Remove-Item $outFull -Force }
$zipStream = [System.IO.File]::Open($outFull, [System.IO.FileMode]::CreateNew)
$zip = New-Object System.IO.Compression.ZipArchive($zipStream, [System.IO.Compression.ZipArchiveMode]::Create)
$utf8 = New-Object System.Text.UTF8Encoding($false)
$seen = New-Object 'System.Collections.Generic.HashSet[string]'
$failed = New-Object System.Collections.Generic.List[string]

Write-Host ""
Write-Host "Packing $($list.Count) file(s) from $ServerDir ..."
Write-Host "  in : $($items -join ', ')"
Write-Host "  out: $($skip -join ', ')"
$i = 0
foreach ($rel in $list) {
    $i++
    if ($i % 50 -eq 0) { Write-Host ("  ... {0}/{1}" -f $i, $list.Count) }
    $src = Join-Path $ServerDir ($rel -replace '/', '\')
    if (-not (Test-Path -LiteralPath $src -PathType Leaf)) {
        Write-Host "[warn] listed but missing now: $rel" -ForegroundColor Yellow
        continue
    }
    try {
        $entry = $zip.CreateEntry($rel, [System.IO.Compression.CompressionLevel]::Optimal)
        $out = $entry.Open()
        $in  = [System.IO.File]::Open($src, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        $in.CopyTo($out)
        $in.Dispose()
        $out.Dispose()
        [void]$seen.Add($rel)
    } catch {
        $failed.Add($rel)
        Write-Host "[ERR] $rel : $($_.Exception.Message)" -ForegroundColor Red
    }
}
$zip.Dispose()
$zipStream.Dispose()

if ($failed.Count -gt 0) {
    Write-Host "[ERR] $($failed.Count) file(s) could not be added - zip is incomplete, do not upload it." -ForegroundColor Red
    Remove-Item $outFull -Force -ErrorAction SilentlyContinue
    exit 1
}

# 6. verify: reopen the archive and compare the file list entry by entry
$entries  = @()
$zipRead  = [System.IO.Compression.ZipFile]::OpenRead($outFull)
foreach ($e in $zipRead.Entries) { $entries += $e.FullName }
$zipRead.Dispose()
$mb     = [math]::Round((Get-Item $outFull).Length / 1MB, 1)
$inZip  = { param($p) [bool]($entries -contains $p) }
$missed = @($list | Where-Object { -not $seen.Contains($_) })

Write-Host ""
Write-Host "[OK] $outFull  ($mb MB, $($entries.Count) files)" -ForegroundColor Green
Write-Host ("     start.sh                               : {0}" -f (& $inZip 'start.sh'))
Write-Host ("     server.jar                             : {0}" -f (& $inZip 'server.jar'))
Write-Host ("     plugins/MiniGameLib.jar                : {0}" -f (& $inZip 'plugins/MiniGameLib.jar'))
Write-Host ("     world/level.dat                        : {0}" -f (& $inZip 'world/level.dat'))
Write-Host ("     cache/mojang_26.2.jar                  : {0}" -f (& $inZip 'cache/mojang_26.2.jar'))
Write-Host ("     versions/26.2/paper-26.2.jar           : {0}" -f (& $inZip 'versions/26.2/paper-26.2.jar'))
Write-Host ("     libraries/net/java/dev/jna/jna/...jar  : {0}" -f (& $inZip 'libraries/net/java/dev/jna/jna/5.19.1/jna-5.19.1.jar'))
Write-Host ("     plugins/GuessTheBuild/arena-*.snap     : {0}" -f ([bool]($entries | Where-Object { $_ -like 'plugins/GuessTheBuild/arena-*.snap' })))
Write-Host ("     players.yml in zip                     : {0}" -f ([bool]($entries | Where-Object { $_ -like '*players.yml' })))
Write-Host ("     legacy <game>/data.yml in zip          : {0} (expect 0)" -f @($entries | Where-Object { $legacyDataRel -contains $_ }).Count)
Write-Host ("     listed {0} files  ->  in zip {1}          : {2}" -f $list.Count, $entries.Count, ($list.Count -eq $entries.Count))
if ($missed.Count -gt 0) {
    Write-Host "[ERR] $($missed.Count) file(s) missing from the zip:" -ForegroundColor Red
    $missed | Select-Object -First 10 | ForEach-Object { Write-Host "        $_" -ForegroundColor Red }
    Write-Host "      Do NOT upload this zip; run the script again." -ForegroundColor Red
    exit 1
}
Write-Host ""
Write-Host "Upload: stop the panel server -> extract this zip INTO the panel's server folder (overwrite) -> start."
Write-Host "        Archive root = server root, so start.sh / plugins/ / server.jar land where they belong."
Write-Host "        NOT included: $($skip -join ', ')"
Write-Host "        NOT included: 6 legacy plugins/<game>/data.yml (old records cannot re-migrate on an empty panel)"
if (-not $WithPlayers) { Write-Host "        NOT included: $playersRel  <- the panel keeps its own records" }
