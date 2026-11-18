package com.dontdoit;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * 队伍颜色。前 16 支用羊毛（与原版一致），17~32 用陶瓦，33~48 用混凝土；
 * 侧边栏一律用十六进制颜色区分。
 */
public final class TeamColor {

    /** 16 种基础颜色的定义：id、显示名、旧版颜色代码、羊毛色、陶瓦色、混凝土色。 */
    private static final String[][] BASE = {
            // id, 名称, legacy, hex(羊毛), terracotta hex, concrete hex
            {"red", "红队", "c", "#FF5555", "#8F3D2E", "#8E2121"},
            {"orange", "橙队", "6", "#FF9D00", "#A15325", "#E06101"},
            {"yellow", "黄队", "e", "#FFFF55", "#B98423", "#F1AF15"},
            {"lime", "绿队", "a", "#55FF55", "#677534", "#5EA918"},
            {"light_blue", "蓝队", "b", "#55FFFF", "#716A8D", "#2389C6"},
            {"cyan", "青队", "3", "#00AAAA", "#575B5B", "#157788"},
            {"pink", "粉队", "d", "#FFAAD2", "#A14E4E", "#D5658E"},
            {"purple", "紫队", "5", "#AA00AA", "#764656", "#64209C"},
            {"white", "白队", "f", "#FFFFFF", "#E9ECEC", "#CFD5D6"},
            {"gray", "灰队", "7", "#AAAAAA", "#392A23", "#36393D"},
            {"light_gray", "淡灰队", "7", "#D8D8D8", "#8E6B5D", "#7D7D73"},
            {"magenta", "品红队", "d", "#FF55FF", "#95576C", "#A9319E"},
            {"green", "深绿队", "2", "#00AA00", "#4C532A", "#495B24"},
            {"brown", "棕队", "6", "#8B5A2B", "#4D3323", "#603B1F"},
            {"black", "黑队", "0", "#000000", "#251610", "#080A0F"},
            {"blue", "深蓝队", "9", "#5555FF", "#4A3B5B", "#2D2E8E"}
    };

    private static final List<TeamColor> ALL = buildAll();

    public final String id;
    public final String name;
    public final String legacy;
    public final String hex;
    public final Material icon;

    private TeamColor(String id, String name, String legacy, String hex, Material icon) {
        this.id = id;
        this.name = name;
        this.legacy = legacy;
        this.hex = hex;
        this.icon = icon;
    }

    private static List<TeamColor> buildAll() {
        List<TeamColor> list = new ArrayList<>(48);
        for (String[] b : BASE) {
            list.add(new TeamColor(b[0], b[1], b[2], b[3], woolOf(b[0])));
        }
        for (String[] b : BASE) {
            list.add(new TeamColor("terracotta_" + b[0], "陶瓦·" + b[1], b[2], b[4], terracottaOf(b[0])));
        }
        for (String[] b : BASE) {
            list.add(new TeamColor("concrete_" + b[0], "混凝土·" + b[1], b[2], b[5], concreteOf(b[0])));
        }
        return List.copyOf(list);
    }

    public static List<TeamColor> all() {
        return ALL;
    }

    /** 按配置的队数取前 n 支（顺序：彩虹 8 色 → 其余羊毛色 → 陶瓦 → 混凝土）。 */
    public static List<TeamColor> first(int n) {
        int limit = Math.max(1, Math.min(n, ALL.size()));
        return ALL.subList(0, limit);
    }

    public static TeamColor byId(String id) {
        if (id == null) {
            return null;
        }
        for (TeamColor c : ALL) {
            if (c.id.equalsIgnoreCase(id) || c.name.equals(id)) {
                return c;
            }
        }
        return null;
    }

    /** 带颜色的名字，例如 &#FF5555红队。 */
    public String coloredName() {
        return "&#" + hex.substring(1) + name;
    }

    /** 侧边栏里用的名字（原样 hex 代码）。 */
    public String hexCode() {
        return "&#" + hex.substring(1);
    }

    /** 计分板条目用的 §x§R§R§G§G§B§B 形式（侧边板不解析 & 代码）。 */
    public String sectionColor() {
        StringBuilder sb = new StringBuilder("§x");
        for (char ch : hex.substring(1).toCharArray()) {
            sb.append('§').append(ch);
        }
        return sb.toString();
    }

    private static Material woolOf(String id) {
        return Material.valueOf(id.toUpperCase() + "_WOOL");
    }

    private static Material terracottaOf(String id) {
        return Material.valueOf(id.toUpperCase() + "_TERRACOTTA");
    }

    private static Material concreteOf(String id) {
        return Material.valueOf(id.toUpperCase() + "_CONCRETE");
    }
}
