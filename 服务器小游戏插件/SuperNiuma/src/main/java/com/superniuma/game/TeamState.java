package com.superniuma.game;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 团队模式的一支队伍。 */
public final class TeamState {

    private final int id;
    private final String name;
    private final ChatColor color;
    private final Material wool;
    private final Material stainedGlass;
    private final List<UUID> members = new ArrayList<>();
    private double score;

    public TeamState(int id, String name, ChatColor color, Material wool, Material stainedGlass) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.wool = wool;
        this.stainedGlass = stainedGlass;
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public ChatColor color() {
        return color;
    }

    public Material wool() {
        return wool;
    }

    public Material stainedGlass() {
        return stainedGlass;
    }

    public List<UUID> members() {
        return members;
    }

    public double score() {
        return score;
    }

    public void addScore(double amount) {
        this.score += amount;
    }

    public void resetScore() {
        this.score = 0.0;
    }

    public String coloredName() {
        return color + name;
    }

    public static List<TeamState> palette(int count) {
        String[] names = {"红队", "蓝队", "绿队", "黄队"};
        ChatColor[] colors = {ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN, ChatColor.YELLOW};
        Material[] wools = {Material.RED_WOOL, Material.BLUE_WOOL, Material.GREEN_WOOL, Material.YELLOW_WOOL};
        Material[] glasses = {Material.RED_STAINED_GLASS, Material.BLUE_STAINED_GLASS,
                Material.GREEN_STAINED_GLASS, Material.YELLOW_STAINED_GLASS};
        List<TeamState> out = new ArrayList<>();
        for (int i = 0; i < Math.max(1, Math.min(4, count)); i++) {
            out.add(new TeamState(i, names[i], colors[i], wools[i], glasses[i]));
        }
        return out;
    }
}
