<#
    FootballArena - standalone build (ASCII only).

    Compiles ONLY this plugin against:
      - Paper API 26.2 jars listed in paper-classpath.ps1
      - lib\MiniGameLib.jar (the only cross-plugin dependency)

    Usage:
        .\build.ps1
        .\build.ps1 -Deploy 'D:\path\to\server\plugins'
        .\build.ps1 -Jdk 'C:\path\to\jdk'
#>
param(
    [string]$Deploy = '',
    [string]$Jdk = ''
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$out = Join-Path $root 'out'
$classes = Join-Path $out 'classes'

function Find-Jdk {
    if ($Jdk) { return $Jdk }
    $candidates = @(
        (Join-Path $root 'jdk'),
        $env:JAVA_HOME,
        'D:\BuildBattle\server\jdk'
    )
    foreach ($c in $candidates) {
        if ($c -and (Test-Path (Join-Path $c 'bin\javac.exe')) -and (Test-Path (Join-Path $c 'bin\jar.exe'))) {
            return $c
        }
    }
    $javac = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($javac) {
        $bin = Split-Path $javac.Source -Parent
        if (Test-Path (Join-Path $bin 'jar.exe')) { return (Split-Path $bin -Parent) }
    }
    throw 'JDK not found. Pass -Jdk <jdk-home> or set JAVA_HOME.'
}

$jdkHome = Find-Jdk
$javacExe = Join-Path $jdkHome 'bin\javac.exe'
$jarExe = Join-Path $jdkHome 'bin\jar.exe'

. (Join-Path $root 'paper-classpath.ps1')
$lib = Join-Path $root 'lib\MiniGameLib.jar'
if (-not (Test-Path $lib)) { throw "Missing $lib" }
$cp = (@($paperCp) + $lib) -join ';'

if (Test-Path $classes) { Remove-Item $classes -Recurse -Force }
New-Item -ItemType Directory -Path $classes -Force | Out-Null

$sources = Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
if (-not $sources) { throw 'No .java sources found under src\main\java' }

$prevEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& $javacExe --release 21 -encoding UTF-8 -nowarn `
    '-Xlint:-removal' '-Xlint:-deprecation' -cp $cp -d $classes $sources
$javacExit = $LASTEXITCODE
$ErrorActionPreference = $prevEap
if ($javacExit -ne 0) { throw 'javac failed.' }

Copy-Item (Join-Path $root 'src\main\resources\*') -Destination $classes -Force

$jarPath = Join-Path $out 'FootballArena.jar'
& $jarExe --create --file $jarPath -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'jar failed.' }

Write-Host ("[built] {0} ({1:N0} B)" -f $jarPath, (Get-Item $jarPath).Length)

if ($Deploy) {
    if (-not (Test-Path $Deploy)) { throw "Deploy target not found: $Deploy" }
    $target = Join-Path $Deploy 'FootballArena.jar'
    Copy-Item $jarPath $target -Force
    Write-Host ("[deploy] {0}" -f $target)
}
