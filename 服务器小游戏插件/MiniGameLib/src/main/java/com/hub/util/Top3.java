package com.hub.util;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 小游戏结算的前三名统一处理：记分（firsts / seconds / thirds + points 3/2/1）和 ★ 播报。
 *
 * <p>各游戏在自己的 data.yml（PlayerStats 命名空间）里已经用 firsts/second/thirds/points 这套键，
 * 直接复用；没有这些键的插件也会被新建，不影响原有数据。
 *
 * <p>用法（结算处）：
 * <pre>
 *   Top3.award(data, firsts, seconds, thirds);
 *   Top3.broadcast(msg -&gt; broadcast(a, msg), this::nameOf, firsts, seconds, thirds);
 *   saveData();
 * </pre>
 */
public final class Top3 {

    /** 星星数量对应的符号串。 */
    public static String stars(int count) {
        return "★".repeat(Math.max(0, count));
    }

    /** 给前三名加分：第一 +3、第二 +2、第三 +1，并累加对应名次次数。 */
    public static void award(YamlConfiguration data, List<UUID> firsts, List<UUID> seconds, List<UUID> thirds) {
        if (data == null) {
            return;
        }
        awardRank(data, "firsts", firsts, 3);
        awardRank(data, "seconds", seconds, 2);
        awardRank(data, "thirds", thirds, 1);
    }

    private static void awardRank(YamlConfiguration data, String key, List<UUID> ids, int points) {
        for (UUID id : ids) {
            if (id == null) {
                continue;
            }
            data.set(key + "." + id, data.getInt(key + "." + id, 0) + 1);
            data.set("points." + id, data.getInt("points." + id, 0) + points);
        }
    }

    /** 三行标准播报（带 ★），空名次自动跳过。 */
    public static void broadcast(Consumer<String> out,
                                 Function<UUID, String> nameOf,
                                 List<UUID> firsts, List<UUID> seconds, List<UUID> thirds) {
        line(out, nameOf, "第一名", firsts, 3);
        line(out, nameOf, "第二名", seconds, 2);
        line(out, nameOf, "第三名", thirds, 1);
    }

    private static void line(Consumer<String> out, Function<UUID, String> nameOf,
                             String label, List<UUID> ids, int stars) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        StringBuilder names = new StringBuilder();
        for (UUID id : ids) {
            if (id == null) {
                continue;
            }
            if (names.length() > 0) {
                names.append("&7、&f");
            }
            names.append(nameOf.apply(id));
        }
        if (names.length() == 0) {
            return;
        }
        out.accept("&e" + label + "：&f" + names + " &6" + stars(stars));
    }

    /** 标准结算标题行。 */
    public static void header(Consumer<String> out, String gameName) {
        out.accept("&6&l✦  ✦  ✦   " + gameName + " 结束   ✦  ✦  ✦");
    }
}
