# BuildBattle pre-flight check (ASCII only: PS 5.1 reads .ps1 as ANSI without BOM)
# Run this BEFORE uploading to the panel. It catches the classes of problems that
# otherwise cost a full upload/restart/login round trip:
#   1. YAML syntax errors in any plugin config (e.g. "key:value" without the space)
#   2. critical values (GameWorlds pvp, Paper explosion knockback, MM arenas)
#   3. a nested "server/" folder (the classic wrong-depth upload trap)
#   4. jar fingerprints to compare against the panel (name / size / time)
#
# Usage:  powershell -ExecutionPolicy Bypass -File D:\BuildBattle\verify.ps1

param([string]$ServerDir = 'D:\BuildBattle\server')

$ErrorActionPreference = 'Continue'
$problems = 0
function Ok($m)   { Write-Host "  [ok]   $m" -ForegroundColor Green }
function Bad($m)  { Write-Host "  [BAD]  $m" -ForegroundColor Red; $script:problems++ }
function Warn($m) { Write-Host "  [warn] $m" -ForegroundColor Yellow }
function Head($m) { Write-Host ""; Write-Host "== $m ==" -ForegroundColor Cyan }

# ---------- 1. YAML syntax ----------
Head "YAML syntax"

$jdkJava = Join-Path $ServerDir 'jdk\bin\java.exe'
$jdkJavac = Join-Path $ServerDir 'jdk\bin\javac.exe'
$snake = Get-ChildItem (Join-Path $env:USERPROFILE '.m2\repository\org\yaml\snakeyaml') -Recurse -Filter 'snakeyaml-*.jar' -ErrorAction SilentlyContinue |
         Sort-Object Name -Descending | Select-Object -First 1

if ((Test-Path $jdkJava) -and $snake) {
    $work = 'D:\BuildBattle\.build\verify'
    New-Item -ItemType Directory -Path $work -Force | Out-Null
    $java = Join-Path $work 'YamlCheck.java'
    if (-not (Test-Path $java)) {
        $code = @'
import org.yaml.snakeyaml.Yaml;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
public class YamlCheck {
    public static void main(String[] a) {
        int bad = 0;
        for (String p : a) {
            try (InputStreamReader r = new InputStreamReader(Files.newInputStream(Paths.get(p)), StandardCharsets.UTF_8)) {
                new Yaml().load(r);
                System.out.println("OK   " + p);
            } catch (Exception e) {
                bad++;
                String msg = String.valueOf(e.getMessage()).replace("\n", " | ");
                System.out.println("FAIL " + p + "  ->  " + msg);
            }
        }
        System.exit(bad == 0 ? 0 : 1);
    }
}
'@
        [System.IO.File]::WriteAllText($java, $code, (New-Object System.Text.UTF8Encoding($false)))
        & $jdkJavac -nowarn -cp $snake.FullName -d $work $java 2>$null | Out-Null
    }
    $targets = @()
    $targets += Get-ChildItem (Join-Path $ServerDir 'plugins') -Recurse -Include 'config.yml','words.yml','data.yml','players.yml' -File -ErrorAction SilentlyContinue |
                ForEach-Object { $_.FullName }
    $targets += Get-ChildItem (Join-Path $ServerDir 'config') -Include '*.yml' -File -ErrorAction SilentlyContinue |
                ForEach-Object { $_.FullName }
    foreach ($f in @('bukkit.yml','spigot.yml','server.properties')) {
        $p = Join-Path $ServerDir $f
        if ($f -like '*.yml' -and (Test-Path $p)) { $targets += $p }
    }
    $targets = $targets | Sort-Object -Unique
    if ($targets.Count -gt 0) {
        $out = & $jdkJava -cp "$($snake.FullName);$work" YamlCheck @targets 2>&1
        foreach ($line in $out) {
            if ($line -like 'FAIL*') { Bad $line } elseif ($line -like 'OK*') { Ok ($line -replace '^OK\s+', '') }
        }
        Write-Host "  (checked $($targets.Count) yml files)"
    } else {
        Warn "no yml files found under $ServerDir"
    }
} else {
    Warn "skip: need $jdkJava and snakeyaml jar in .m2 to check syntax"
}

# ---------- 2. critical values ----------
Head "critical values"

$gw = Join-Path $ServerDir 'plugins\GameWorlds\config.yml'
if (Test-Path $gw) {
    $txt = Get-Content $gw -Raw -Encoding UTF8
    foreach ($w in @('world_duel','world_mm','world_lp')) {
        $m = [regex]::Match($txt, "(?ms)^\s{2}$w\s*:\s*$(if($true){''})?(.*?)(?=^\s{2}\S|\z)")
        if ($m.Success -and $m.Groups[1].Value -match 'pvp:\s*true') { Ok "$w pvp=true" }
        else { Bad "$w should have pvp: true (duel/mm/luckypillar need it)" }
    }
    $m = [regex]::Match($txt, "(?ms)^  world_lp\s*:(.*?)(?=^  \S|\z)")
    if ($m.Success -and $m.Groups[1].Value -match 'keep-inventory:\s*false') { Ok "world_lp keep-inventory=false (LuckyPillar death drops work)" }
    else { Bad "world_lp needs keep-inventory: false (otherwise LuckyPillar deaths drop nothing)" }
} else { Bad "missing $gw" }

$lp = Join-Path $ServerDir 'plugins\LuckyPillar\config.yml'
if (Test-Path $lp) {
    $txt = Get-Content $lp -Raw -Encoding UTF8
    $missing = @()
    foreach ($k in @('pillar-depth','pillar-spacing','loot-interval','disaster-interval',
                     'loot-gear-chance','loot-enchant-chance','loot-enchant-max-level',
                     'shrink-start-gap','shrink-delay-seconds','shrink-target-radius',
                     'out-of-zone-damage','final-tnt-fuse','final-max-seconds')) {
        if ($txt -notmatch ('(?m)^' + [regex]::Escape($k) + ':\s')) { $missing += $k }
    }
    if ($missing.Count -eq 0) { Ok "LuckyPillar config has all gameplay keys" }
    else { Bad ("LuckyPillar config missing key(s): " + ($missing -join ', ')) }
    if ($txt -match '(?m)^arenas:\s*\r?\n\s{2}\S') { Ok "LuckyPillar has at least one arena" }
    else { Warn "LuckyPillar has no arena yet (in game: /lp create <name> + /lp setup <name>)" }
} else { Warn "missing plugins\LuckyPillar\config.yml (generated on first start)" }

$paper = Join-Path $ServerDir 'config\paper-world-defaults.yml'
if (Test-Path $paper) {
    $line = Select-String -Path $paper -Pattern 'disable-explosion-knockback' -Encoding UTF8 | Select-Object -First 1
    if ($line -and $line.Line -match 'false') { Ok "paper disable-explosion-knockback=false (explosion knockback allowed)" }
    else { Bad "paper disable-explosion-knockback is not false -> wind charge / TNT will not push players" }
} else { Bad "missing $paper (upload the server\config\ folder!)" }

$mm = Join-Path $ServerDir 'plugins\MurderMystery\config.yml'
if (Test-Path $mm) {
    $txt = Get-Content $mm -Raw -Encoding UTF8
    if ($txt -match '(?m)^arenas:\s*\r?\n\s{2}\S') { Ok "MurderMystery has at least one arena" }
    else { Bad "MurderMystery config has no arena (arenas is empty)" }
} else { Bad "missing $mm" }

$words = Join-Path $ServerDir 'plugins\GuessTheBuild\words.yml'
if (Test-Path $words) { Ok "GuessTheBuild words.yml present" } else { Warn "GuessTheBuild words.yml missing (jar default will be used)" }

# ---------- 2b. player records ----------
Head "player records (one file for all games)"

$players = Join-Path $ServerDir 'plugins\MiniGameLib\players.yml'
if (Test-Path $players) {
    $kb = [math]::Round((Get-Item $players).Length / 1KB, 1)
    Ok "plugins\MiniGameLib\players.yml present ($kb KB) - the ONLY player-record file"
    $txt = Get-Content $players -Raw -Encoding UTF8
    foreach ($ns in @('duel', 'gtb', 'hotpotato', 'tntrun', 'murdermystery', 'skypit', 'football', 'colorparty', 'buildrun', 'luckypillar')) {
        # handles both "duel:" blocks and the inline "duel: {}" form of a cleared namespace
        $m = [regex]::Match($txt, "(?ms)^  $ns\s*:(.*?)(?=^  \S|\z)")
        if (-not $m.Success) {
            Warn ('  {0,-15} no records yet' -f $ns)
            continue
        }
        $ids = [regex]::Matches($m.Groups[1].Value, '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}') |
               ForEach-Object { $_.Value.ToLower() } | Sort-Object -Unique
        if ($ids.Count -gt 0) {
            Ok ('  {0,-15} {1} player(s)' -f $ns, $ids.Count)
        } else {
            Warn ('  {0,-15} empty (cleared, or nobody has played yet)' -f $ns)
        }
    }
    $legacy = Get-ChildItem (Join-Path $ServerDir 'plugins') -Recurse -Filter 'data.yml' -File -ErrorAction SilentlyContinue
    if ($legacy) {
        Warn "$($legacy.Count) legacy data.yml file(s) still on disk - kept as backup, no longer read (safe to delete)"
    }
} else {
    Warn "plugins\MiniGameLib\players.yml not generated yet - it appears on the first start (auto-migrates every data.yml)"
    Warn "before uploading: run check-stats.ps1 to verify the migration/merge logic"
}

# ---------- 3. nested folder trap ----------
Head "layout"

if (Test-Path (Join-Path $ServerDir 'server')) {
    Bad "found a nested 'server' folder inside $ServerDir -> uploads may be landing one level too deep"
} else { Ok "no nested server/ folder" }
foreach ($need in @('plugins','server.jar','server.properties','bukkit.yml','spigot.yml','config')) {
    if (Test-Path (Join-Path $ServerDir $need)) { Ok "has $need" } else { Warn "missing $need" }
}
$java = Get-Process java -ErrorAction SilentlyContinue
if ($java) { Warn "java.exe is running (PID $($java.Id -join ', ')) - stop it before zipping" } else { Ok "no java process running" }

# ---------- 4. jar fingerprints ----------
Head "jar fingerprints (compare these on the panel)"

Get-ChildItem (Join-Path $ServerDir 'plugins\*.jar') -ErrorAction SilentlyContinue | Sort-Object Name | ForEach-Object {
    '{0,-20} {1,9:N0} B  {2}' -f $_.Name, $_.Length, $_.LastWriteTime
}

Head "result"
if ($problems -eq 0) { Write-Host "  all good ($problems problems)" -ForegroundColor Green }
else { Write-Host "  $problems problem(s) - fix above before uploading" -ForegroundColor Red }
