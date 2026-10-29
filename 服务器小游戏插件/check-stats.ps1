# BuildBattle: offline self-test for the single player-record file.
# ASCII only (PS 5.1 reads .ps1 as ANSI unless it has a BOM).
#
# Usage:  powershell -ExecutionPolicy Bypass -File D:\BuildBattle\check-stats.ps1
#
# Why: every plugin's records now live in server\plugins\MiniGameLib\players.yml.
# A bug in that merge logic would silently eat player data, so this test copies the
# CURRENT server\plugins\<Plugin>\data.yml files into a throwaway folder and checks:
#   1. each plugin's data.yml migrates into players.yml, entry count unchanged
#   2. saving one plugin's block does NOT erase the other five (merge-on-save)
#   3. reloading returns the merged values; unknown namespaces load empty
# Nothing under server\ is modified. Requires build.ps1 to have run at least once.

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$java = Join-Path $root 'server\jdk\bin\java.exe'
$javac = Join-Path $root 'server\jdk\bin\javac.exe'
$libClasses = Join-Path $root '.build\out\MiniGameLib.classes'

if (-not (Test-Path $libClasses)) {
    throw "MiniGameLib is not built yet - run build.ps1 first (or build.bat)."
}
. (Join-Path $root 'paper-classpath.ps1')

$work = Join-Path $root '.build\statstest'
$classes = Join-Path $work 'classes'
$legacy = Join-Path $work 'legacy'
$run = Join-Path $work 'run'
foreach ($d in @($work, $classes, $legacy, $run)) {
    if (Test-Path $d) { Remove-Item $d -Recurse -Force }
    New-Item -ItemType Directory -Path $d -Force | Out-Null
}
$src = Join-Path $work 'StatsSelfTest.java'

# 1. keep a copy of the live legacy files (names match the test's namespace table)
$legacyMap = [ordered]@{
    'DuelArena'      = 'DuelArena.yml'
    'GuessTheBuild'  = 'GuessTheBuild.yml'
    'HotPotato'      = 'HotPotato.yml'
    'TntRun'         = 'TntRun.yml'
    'MurderMystery'  = 'MurderMystery.yml'
    'SkyPit'         = 'SkyPit.yml'
}
foreach ($p in $legacyMap.Keys) {
    $from = Join-Path $root "server\plugins\$p\data.yml"
    if (-not (Test-Path $from)) { throw "missing legacy file: $from" }
    Copy-Item $from (Join-Path $legacy $legacyMap[$p]) -Force
}

# 2. the test itself (generated, safe to delete)
$code = @'
import com.hub.data.PlayerStats;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Offline regression test for MiniGameLib PlayerStats (the single players.yml). */
public class StatsSelfTest {

    private static final String[][] NAMESPACES = {
            {"duel", "DuelArena.yml"},
            {"gtb", "GuessTheBuild.yml"},
            {"hotpotato", "HotPotato.yml"},
            {"tntrun", "TntRun.yml"},
            {"murdermystery", "MurderMystery.yml"},
            {"skypit", "SkyPit.yml"},
    };

    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        File legacyDir = new File(args[0]);
        File runDir = new File(args[1]);
        if (!runDir.isDirectory()) {
            throw new IllegalStateException("run dir missing: " + runDir);
        }

        // --- 1. migration: every plugin's legacy data.yml -> one shared file ---
        Map<String, YamlConfiguration> views = new LinkedHashMap<>();
        for (String[] ns : NAMESPACES) {
            File legacy = new File(legacyDir, ns[1]);
            int expected = YamlConfiguration.loadConfiguration(legacy).getValues(true).size();
            YamlConfiguration view = PlayerStats.load(ns[0], legacy);
            views.put(ns[0], view);
            int got = view.getValues(true).size();
            check("migrate [" + ns[0] + "]: " + got + " entries (legacy had " + expected + ")",
                    got == expected && expected > 0);
        }

        File store = PlayerStats.file();
        check("single file created at " + store.getPath(), store.isFile());
        check("no leftover .tmp file next to it",
                !new File(store.getParentFile(), "players.yml.tmp").exists());

        YamlConfiguration root = YamlConfiguration.loadConfiguration(store);
        ConfigurationSection games = root.getConfigurationSection("games");
        check("players.yml holds all 6 namespaces",
                games != null && games.getKeys(false).size() == NAMESPACES.length);

        // --- 2. merge on save: touch duel, the other five must survive ---
        YamlConfiguration duel = views.get("duel");
        ConfigurationSection totals = duel.getConfigurationSection("wins.total");
        String path = "wins.total.";
        String uuid = totals.getKeys(false).iterator().next();
        int before = duel.getInt(path + uuid);
        duel.set(path + uuid, before + 1);
        PlayerStats.save("duel", duel);

        YamlConfiguration after = YamlConfiguration.loadConfiguration(store);
        List<String> keys = new ArrayList<>(after.getConfigurationSection("games").getKeys(false));
        check("after saving duel, all 6 namespaces still there: " + keys, keys.size() == NAMESPACES.length);

        YamlConfiguration reread = PlayerStats.load("duel", null);
        check("duel value persisted (" + before + " -> " + reread.getInt(path + uuid) + ")",
                reread.getInt(path + uuid) == before + 1);

        YamlConfiguration gtb = PlayerStats.load("gtb", null);
        check("gtb block untouched by the duel save",
                gtb.getValues(true).size() == views.get("gtb").getValues(true).size());

        // --- 3. a namespace with no legacy file must not blow up ---
        YamlConfiguration empty = PlayerStats.load("nosuchgame", null);
        check("unknown namespace loads empty", empty.getKeys(false).isEmpty());

        // --- 4. clearing stats (e.g. /duel stats reset) must NOT resurrect the old data.yml ---
        YamlConfiguration cleared = PlayerStats.load("duel", null);
        for (String k : new ArrayList<>(cleared.getKeys(false))) {
            cleared.set(k, null);
        }
        PlayerStats.save("duel", cleared);
        YamlConfiguration rereload = PlayerStats.load("duel", new File(legacyDir, "DuelArena.yml"));
        check("cleared namespace stays cleared (no re-migration from the legacy file)",
                rereload.getKeys(false).isEmpty());

        System.out.println("summary: " + PlayerStats.summary());
        System.out.println(failed == 0 ? "RESULT: ALL PASS" : "RESULT: " + failed + " FAILED");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void check(String what, boolean ok) {
        if (!ok) {
            failed++;
        }
        System.out.println((ok ? "  [ok]   " : "  [FAIL] ") + what);
    }
}
'@
[System.IO.File]::WriteAllText($src, $code, (New-Object System.Text.UTF8Encoding($false)))

# 3. compile + run
$cp = (@($classes, $libClasses) + $paperCp) -join ';'
& $javac -nowarn -encoding UTF-8 -cp $cp -d $classes $src
if ($LASTEXITCODE -ne 0) { throw "StatsSelfTest did not compile." }

Write-Host "== PlayerStats self-test (working dir: .build\statstest\run) ==" -ForegroundColor Cyan
Push-Location $run
try {
    & $java -cp "$classes;$libClasses;$($paperCp -join ';')" StatsSelfTest $legacy $run
    $exit = $LASTEXITCODE
} finally {
    Pop-Location
}

Write-Host ""
if ($exit -eq 0) {
    Write-Host "[OK] player records: migration + merge-on-save verified" -ForegroundColor Green
} else {
    Write-Host "[BAD] PlayerStats self-test failed - do NOT upload the jars" -ForegroundColor Red
}
Write-Host "     artifact: .build\statstest\run\plugins\MiniGameLib\players.yml" -ForegroundColor DarkGray
exit $exit
