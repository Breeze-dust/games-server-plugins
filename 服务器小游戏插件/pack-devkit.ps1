<#
    BuildBattle: build the "move to a new PC" dev kit (one .zip), ASCII only.
    (Windows PowerShell 5.1 reads a BOM-less .ps1 as ANSI, so Chinese comments
     would break this file - keep the docs in docs\*.md instead.)

    Usage (from the repo root):
        .\pack-devkit.bat
        powershell -ExecutionPolicy Bypass -File pack-devkit.ps1
        powershell -ExecutionPolicy Bypass -File pack-devkit.ps1 -OutFile E:\BuildBattle-DevKit.zip
        powershell -ExecutionPolicy Bypass -File pack-devkit.ps1 -SkipToolchain

    Included (everything needed to build + run the server on a fresh Windows PC):
        * every plugin folder that has src\main\java (all 13, incl. LuckyPillar)
        * docs\, backups\ (player-record snapshots), AGENTS.md, README.md
        * build / verify / check-stats / pack-upload / server-backup scripts
        * server\ : server.jar, start scripts, all YAML/JSON configs, config\,
          plugins\ (jars + per-plugin config/data/arena snapshots + players.yml),
          world\, jdk\ (Temurin 25 with javac), cache\ versions\ libraries\
        * toolchain\.m2\repository : the 24 compile-time jars (paper-classpath.ps1
          falls back to them when %USERPROFILE%\.m2 is empty)

    Excluded on purpose:
        .build\, any *.log, server\logs\, server\world_backup_*, server\usercache.json,
        world\session.lock, <plugin>\META-INF\ (a stray copy of paper-api),
        server\jdk\lib\src.zip (52 MB of JDK sources, not needed to build/run),
        old *.zip upload packages in the repo root.

    Output: BuildBattle-DevKit-yyyyMMdd.zip in the repo root.
#>
param(
    [string]$Root = $PSScriptRoot,
    [string]$OutFile = '',
    [switch]$SkipToolchain
)

$ErrorActionPreference = 'Stop'

if (-not $OutFile) {
    $OutFile = Join-Path $Root ('BuildBattle-DevKit-' + (Get-Date -Format 'yyyyMMdd') + '.zip')
}

# 0. The local server must be stopped: several plugins rewrite config.yml on
#    shutdown, so zipping while it runs can capture half-written configs.
$java = Get-Process java -ErrorAction SilentlyContinue
if ($java) {
    Write-Host "[STOP] java.exe is still running (PID: $($java.Id -join ', '))." -ForegroundColor Red
    Write-Host "       Stop the local server, wait for the process to exit, then run this again."
    exit 1
}

# 1. Refresh the repo-local compile dependencies from %USERPROFILE%\.m2.
$toolchain = Join-Path $Root 'toolchain\.m2\repository'
if (-not $SkipToolchain) {
    . (Join-Path $Root 'paper-classpath.ps1')
    $userM2 = Join-Path $env:USERPROFILE '.m2\repository'
    $copied = 0
    foreach ($rel in $paperCpRel) {
        $dst = Join-Path $toolchain $rel
        if (Test-Path -LiteralPath $dst) { continue }
        $src = Join-Path $userM2 $rel
        if (-not (Test-Path -LiteralPath $src)) {
            throw "Compile dependency missing from both .m2 and toolchain: $rel"
        }
        New-Item -ItemType Directory -Path (Split-Path $dst -Parent) -Force | Out-Null
        Copy-Item -LiteralPath $src -Destination $dst
        $copied++
    }
    Write-Host ("[toolchain] {0} jars ready ({1} copied from .m2)" -f $paperCpRel.Count, $copied)
} elseif (-not (Test-Path -LiteralPath $toolchain)) {
    Write-Host "[WARN] -SkipToolchain and toolchain\ is missing: the new PC will need .m2 populated." -ForegroundColor Yellow
}

# 2. Decide what goes in. Explicit roots only - nothing else is even considered.
$rootFiles = @(
    'AGENTS.md', 'README.md',
    'build.bat', 'build.ps1', 'verify.ps1', 'check-stats.ps1', 'paper-classpath.ps1',
    'pack-upload.bat', 'pack-upload.ps1', 'players-backup.ps1',
    'server-backup.bat', 'server-backup.ps1',
    'server-pack-full.bat', 'server-pack-full.ps1',
    'pack-devkit.bat', 'pack-devkit.ps1'
)

$pluginDirs = Get-ChildItem -LiteralPath $Root -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'src\main\java') }

$serverDir = Join-Path $Root 'server'
$serverItems = @(
    'server.jar', 'start.bat', 'start.sh',
    'server.properties', 'bukkit.yml', 'spigot.yml', 'commands.yml', 'eula.txt',
    'ops.json', 'whitelist.json', 'banned-ips.json', 'banned-players.json',
    'server-icon.png', 'build_gtb_map.ps1', 'verify_map.ps1',
    'config', 'plugins', 'world', 'jdk', 'cache', 'versions', 'libraries'
)

$roots = @()
foreach ($f in $rootFiles) {
    $p = Join-Path $Root $f
    if (Test-Path -LiteralPath $p) { $roots += $p }
    else { Write-Host "[skip] $f (not found)" -ForegroundColor DarkGray }
}
foreach ($d in @('docs', 'backups', 'toolchain')) {
    $p = Join-Path $Root $d
    if (Test-Path -LiteralPath $p) { $roots += $p }
}
foreach ($d in $pluginDirs) { $roots += $d.FullName }
foreach ($i in $serverItems) {
    $p = Join-Path $serverDir $i
    if (Test-Path -LiteralPath $p) { $roots += $p }
    else { Write-Host "[skip] server\$i (not found)" -ForegroundColor DarkGray }
}

# 3. Walk the roots and drop the known-useless bits.
$excludeDirs = @('logs', '.build', 'META-INF')
function Test-Included([System.IO.FileInfo]$file) {
    $rel = $file.FullName.Substring($Root.Length).TrimStart('\')
    foreach ($part in ($rel -split '\\')) {
        if ($excludeDirs -contains $part) { return $false }
        if ($part -like 'world_backup_*') { return $false }
    }
    $name = $file.Name
    if ($name -like '*.log' -or $name -like '*.tmp') { return $false }
    if ($name -eq 'session.lock' -or $name -eq 'usercache.json') { return $false }
    # JDK sources: 52 MB, never needed to compile or run. The JDK itself stays complete.
    if ($name -eq 'src.zip' -and $rel -like 'server\jdk\lib\*') { return $false }
    return $true
}

$files = New-Object System.Collections.Generic.List[System.IO.FileInfo]
foreach ($r in $roots) {
    $item = Get-Item -LiteralPath $r -Force
    if ($item.PSIsContainer) {
        Get-ChildItem -LiteralPath $item.FullName -Recurse -File -Force -ErrorAction SilentlyContinue |
            ForEach-Object { if (Test-Included $_) { $files.Add($_) } }
    } elseif (Test-Included $item) {
        $files.Add($item)
    }
}
$files = $files | Sort-Object FullName -Unique
$totalBytes = ($files | Measure-Object Length -Sum).Sum
Write-Host ("[scan] {0} files, {1:N1} MB before compression" -f $files.Count, ($totalBytes / 1MB))

# 4. Write the zip (.NET, so UTF-8 names like docs\<Chinese>.md survive).
Add-Type -AssemblyName System.IO.Compression -ErrorAction SilentlyContinue
Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction Stop
$outDir = Split-Path $OutFile -Parent
if ($outDir -and -not (Test-Path -LiteralPath $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}
if (Test-Path -LiteralPath $OutFile) { Remove-Item -LiteralPath $OutFile -Force }

$zip = [System.IO.Compression.ZipFile]::Open($OutFile, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    $i = 0
    foreach ($f in $files) {
        $entryName = $f.FullName.Substring($Root.Length).TrimStart('\').Replace('\', '/')
        [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $zip, $f.FullName, $entryName, [System.IO.Compression.CompressionLevel]::Optimal)
        $i++
        if ($i % 500 -eq 0) { Write-Host ("[zip] {0}/{1}" -f $i, $files.Count) }
    }
} finally {
    $zip.Dispose()
}

# 5. Report.
$sizeMb = [math]::Round((Get-Item -LiteralPath $OutFile).Length / 1MB, 1)
Write-Host ""
Write-Host ("[OK] {0}  ({1} MB, {2} files)" -f $OutFile, $sizeMb, $files.Count) -ForegroundColor Green

$byTop = $files | Group-Object { ($_.FullName.Substring($Root.Length).TrimStart('\') -split '\\')[0] } |
    ForEach-Object {
        [pscustomobject]@{
            Item = $_.Name
            MB   = [math]::Round((($_.Group | Measure-Object Length -Sum).Sum) / 1MB, 1)
            Files = $_.Count
        }
    } | Sort-Object MB -Descending
$byTop | Format-Table -AutoSize | Out-String | Write-Host

Write-Host "Excluded on purpose: .build\, *.log, server\logs\, server\world_backup_*, usercache.json,"
Write-Host "                    world\session.lock, <plugin>\META-INF\, server\jdk\lib\src.zip, old *.zip."
Write-Host "Next: copy the zip to the new PC, extract so that AGENTS.md sits in D:\BuildBattle,"
Write-Host "      then read docs\<migration doc>.md."
