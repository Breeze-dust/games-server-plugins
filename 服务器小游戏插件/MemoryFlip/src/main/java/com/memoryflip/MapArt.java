package com.memoryflip;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;

/** 问号 / 对勾两张地图画（地图上写字，物品展示框里显示）。 */
final class MapArt {

    private MapArt() {
    }

    static MapView create(World world, boolean check) {
        MapView view = Bukkit.createMap(world);
        view.getRenderers().forEach(view::removeRenderer);
        view.addRenderer(new TextRenderer(check));
        view.setScale(MapView.Scale.CLOSEST);
        view.setTrackingPosition(false);
        view.setLocked(true);
        return view;
    }

    static ItemStack item(MapView view) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        if (item.getItemMeta() instanceof MapMeta meta) {
            meta.setMapView(view);
            meta.setScaling(false);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static final class TextRenderer extends MapRenderer {
        private final boolean check;
        private boolean drawn;

        TextRenderer(boolean check) {
            super(false);
            this.check = check;
        }

        @Override
        public void render(MapView view, MapCanvas canvas, org.bukkit.entity.Player player) {
            if (drawn) {
                return;
            }
            drawn = true;
            for (int x = 0; x < 128; x++) {
                for (int y = 0; y < 128; y++) {
                    canvas.setPixelColor(x, y, new Color(38, 40, 48));
                }
            }
            if (check) {
                Color green = new Color(90, 230, 120);
                drawThickLine(canvas, 30, 70, 56, 96, 5, green);
                drawThickLine(canvas, 56, 96, 100, 34, 5, green);
            } else {
                String[] pattern = {
                        "01110",
                        "10001",
                        "00001",
                        "00011",
                        "00110",
                        "00100",
                        "00000",
                        "00100"
                };
                drawPattern(canvas, pattern, 13, new Color(255, 220, 80));
            }
        }
    }

        private static void drawThickLine(MapCanvas canvas, int x0, int y0, int x1, int y1,
                                          int radius, Color color) {
            int dx = Math.abs(x1 - x0);
            int dy = Math.abs(y1 - y0);
            int sx = x0 < x1 ? 1 : -1;
            int sy = y0 < y1 ? 1 : -1;
            int err = dx - dy;
            int x = x0;
            int y = y0;
            while (true) {
                for (int ox = -radius; ox <= radius; ox++) {
                    for (int oy = -radius; oy <= radius; oy++) {
                        int px = x + ox;
                        int py = y + oy;
                        if (px >= 0 && px < 128 && py >= 0 && py < 128 && ox * ox + oy * oy <= radius * radius) {
                            canvas.setPixelColor(px, py, color);
                        }
                    }
                }
                if (x == x1 && y == y1) {
                    break;
                }
                int e2 = 2 * err;
                if (e2 > -dy) {
                    err -= dy;
                    x += sx;
                }
                if (e2 < dx) {
                    err += dx;
                    y += sy;
                }
            }
        }

        private static void drawPattern(MapCanvas canvas, String[] pattern, int scale, Color color) {
            int width = pattern[0].length() * scale;
            int height = pattern.length * scale;
            int ox = (128 - width) / 2;
            int oy = (128 - height) / 2;
            for (int row = 0; row < pattern.length; row++) {
                String line = pattern[row];
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) != '1') {
                        continue;
                    }
                    for (int dx = 0; dx < scale; dx++) {
                        for (int dy = 0; dy < scale; dy++) {
                            int px = ox + col * scale + dx;
                            int py = oy + row * scale + dy;
                            if (px >= 0 && px < 128 && py >= 0 && py < 128) {
                                canvas.setPixelColor(px, py, color);
                            }
                        }
                    }
                }
            }
        }
}
