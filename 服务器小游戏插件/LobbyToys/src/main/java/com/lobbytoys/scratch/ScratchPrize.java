package com.lobbytoys.scratch;

import org.bukkit.Material;
import org.bukkit.Sound;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * 一个奖品：卡面外观、实际掉落物、抽取权重，以及可选的「事件效果」。
 * 事件效果通过 commands（控制台命令，%player% / %prize% 会被替换）接入，
 * 同时每揭开一格都会触发 {@link ScratchCardPrizeEvent}，其他插件可以直接监听。
 */
public record ScratchPrize(
        String id,
        String name,
        Material material,
        int amount,
        boolean blank,
        double weight,
        byte color,
        String label,
        Sound sound,
        String message,
        List<String> commands,
        BufferedImage image) {

    public boolean givesItem() {
        return !blank && material != null && material != Material.AIR && amount > 0;
    }
}
