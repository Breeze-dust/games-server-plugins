# BuildBattle: keep / move the ONE player-record file (server\plugins\MiniGameLib\players.yml).
# ASCII only (PS 5.1 reads .ps1 as ANSI unless it has a BOM).
#
# All player stats (GTB / Duel / HotPotato / TntRun / MurderMystery / SkyPit wins,
# kills, first places, player names) live in that single file, so backing up or
# moving player records is a one-file copy.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File players-backup.ps1 -Action Save
#       snapshot the local players.yml into backups\players-<timestamp>.yml
#   powershell -ExecutionPolicy Bypass -File players-backup.ps1 -Action List
#       list every snapshot (size + distinct players)
#   powershell -ExecutionPolicy Bypass -File players-backup.ps1 -Action Restore -File latest
#       put a snapshot back into server\plugins\MiniGameLib\players.yml
#       (e.g. after downloading the panel's file into backups\)
#   powershell -ExecutionPolicy Bypass -File players-backup.ps1 -Action Restore -File C:\path\players.yml

param(
    [ValidateSet('Save', 'List', 'Restore')]
    [string]$Action = 'Save',
    [string]$File = 'latest',
    [string]$ServerDir = '',
    [string]$BackupDir = ''
)

$ErrorActionPreference = 'Stop'
$root = if ($PSScriptRoot) { $PSScriptRoot } else { (Get-Location).Path }
if (-not $ServerDir) { $ServerDir = Join-Path $root 'server' }
if (-not $BackupDir) { $BackupDir = Join-Path $root 'backups' }

$live = Join-Path $ServerDir 'plugins\MiniGameLib\players.yml'

function Get-PlayerCount([string]$path) {
    $txt = Get-Content $path -Raw -Encoding UTF8
    $ids = [regex]::Matches($txt, '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}') |
           ForEach-Object { $_.Value.ToLower() } | Sort-Object -Unique
    return $ids.Count
}

function Show-File([string]$path) {
    $f = Get-Item $path
    '{0,-46} {1,7} B  {2}  players={3}' -f $f.Name, $f.Length, $f.LastWriteTime, (Get-PlayerCount $path)
}

switch ($Action) {

    'Save' {
        if (-not (Test-Path $live)) {
            Write-Host "[ERR] $live not found - start the local server once so the plugins write it." -ForegroundColor Red
            exit 1
        }
        if (-not (Test-Path $BackupDir)) { New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null }
        $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
        $snap = Join-Path $BackupDir "players-$stamp.yml"
        Copy-Item $live $snap -Force
        Copy-Item $live (Join-Path $BackupDir 'players-latest.yml') -Force
        Write-Host "[OK] snapshot saved" -ForegroundColor Green
        Show-File $snap
    }

    'List' {
        if (-not (Test-Path $BackupDir)) { Write-Host "(no backups folder yet: $BackupDir)"; exit 0 }
        $all = Get-ChildItem (Join-Path $BackupDir 'players-*.yml') -ErrorAction SilentlyContinue |
               Sort-Object LastWriteTime -Descending
        if (-not $all) { Write-Host "(no snapshots in $BackupDir)"; exit 0 }
        foreach ($f in $all) { Show-File $f.FullName }
        Write-Host ""
        if (Test-Path $live) { Write-Host "live file:"; Show-File $live }
    }

    'Restore' {
        if ($File -eq 'latest') {
            $cand = Get-ChildItem (Join-Path $BackupDir 'players-*.yml') -ErrorAction SilentlyContinue |
                    Sort-Object LastWriteTime -Descending | Select-Object -First 1
            if (-not $cand) { Write-Host "[ERR] no snapshot found in $BackupDir" -ForegroundColor Red; exit 1 }
            $File = $cand.FullName
        }
        if (-not (Test-Path $File)) { Write-Host "[ERR] $File not found" -ForegroundColor Red; exit 1 }
        if (Get-Process java -ErrorAction SilentlyContinue) {
            Write-Host "[STOP] java.exe is running: a running server keeps its stats in memory and" -ForegroundColor Red
            Write-Host "       overwrites players.yml on shutdown. Stop the server first." -ForegroundColor Red
            exit 1
        }
        $dir = Split-Path $live -Parent
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        if (Test-Path $live) {
            # prefix must NOT match players-*.yml, otherwise it would show up as a snapshot
            $bak = Join-Path $BackupDir ("replaced-players-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.yml')
            if (-not (Test-Path $BackupDir)) { New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null }
            Copy-Item $live $bak -Force
            Write-Host "(previous live file kept at $bak)"
        }
        Copy-Item $File $live -Force
        Write-Host "[OK] restored" -ForegroundColor Green
        Show-File $live
    }
}
