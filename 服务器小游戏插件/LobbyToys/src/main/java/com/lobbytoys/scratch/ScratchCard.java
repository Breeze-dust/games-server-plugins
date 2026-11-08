package com.lobbytoys.scratch;

import org.bukkit.Location;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.bukkit.util.Vector;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 一张刮刮乐的完整状态：一张 128x128 的地图、一层刮刮涂层、底层奖品格子和揭晓进度。
 * 屏幕是一个固定（fixed）的 item frame，玩法全靠服务端算，客户端只能看到已经刮开的部分。
 */
public final class ScratchCard {

    public static final int SIZE = 128;

    private final Player player;
    private final ScratchSettings settings;
    private final int rows;
    private final int columns;
    private final ItemFrame frame;
    private final MapView mapView;

    /** 地图正面所在的平面：中心点、界面右方向、世界上方向、朝玩家方向。 */
    private final Vector center;
    private final Vector right;
    private final Vector up;
    private final Vector normal;

    private final ScratchPrize[][] prizes;

    private final byte[] base = new byte[SIZE * SIZE];
    private final byte[] foil = new byte[SIZE * SIZE];
    private final byte[] shown = new byte[SIZE * SIZE];
    private final boolean[] scratched = new boolean[SIZE * SIZE];

    private final int[] cellTotal;
    private final int[] cellRemaining;
    private final boolean[] cellRevealed;
    private final boolean[] labelDrawn;

    private MapCanvas canvas;
    private boolean fullBlitNeeded = true;

    private final long createdAt = System.currentTimeMillis();
    private long lastSwingAt;
    private long lastStrokeAt;
    private long lastMapSentAt;
    private double lastU;
    private double lastV;
    private boolean hasLastPoint;

    private final Random random = new Random();
    private final MapRenderer renderer = new MapRenderer() {
        @Override
        public void render(MapView view, MapCanvas mapCanvas, Player viewer) {
            if (canvas != mapCanvas) {
                canvas = mapCanvas;
                fullBlitNeeded = true;
            }
            if (fullBlitNeeded) {
                blit();
                fullBlitNeeded = false;
                drawLabels();
            }
        }
    };

    public ScratchCard(Player player, ScratchSettings settings, int rows, int columns,
                       ItemFrame frame, MapView mapView,
                       Vector center, Vector right, Vector up, Vector normal) {
        this.player = player;
        this.settings = settings;
        this.rows = rows;
        this.columns = columns;
        this.frame = frame;
        this.mapView = mapView;
        this.center = center;
        this.right = right;
        this.up = up;
        this.normal = normal;
        this.prizes = new ScratchPrize[rows][columns];
        this.cellTotal = new int[rows * columns];
        this.cellRemaining = new int[rows * columns];
        this.cellRevealed = new boolean[rows * columns];
        this.labelDrawn = new boolean[rows * columns];

        assignPrizes();
        buildCells();
        buildBase();
        buildFoil();
        System.arraycopy(foil, 0, shown, 0, SIZE * SIZE);
    }

    public Player player() {
        return player;
    }

    public ItemFrame frame() {
        return frame;
    }

    public MapView mapView() {
        return mapView;
    }

    public MapRenderer renderer() {
        return renderer;
    }

    public ScratchPrize prizeAt(int row, int column) {
        if (row < 0 || row >= rows || column < 0 || column >= columns) {
            return null;
        }
        return prizes[row][column];
    }

    public void markSwing() {
        lastSwingAt = System.currentTimeMillis();
    }

    /** 准星离开卡面时断笔，重新进入不会和上一笔连成一条穿过卡面的直线。 */
    public void breakStroke() {
        hasLastPoint = false;
    }

    public long lastSwingAt() {
        return lastSwingAt;
    }

    public long lastStrokeAt() {
        return lastStrokeAt;
    }

    public long lastMapSentAt() {
        return lastMapSentAt;
    }

    public void markMapSent(long now) {
        lastMapSentAt = now;
    }

    public boolean expired() {
        return System.currentTimeMillis() - createdAt > settings.sessionSeconds * 1000L;
    }

    public boolean outOfRange() {
        if (!player.isOnline() || !frame.isValid()) {
            return true;
        }
        if (!player.getWorld().equals(frame.getWorld())) {
            return true;
        }
        double limit = settings.maxDistance;
        return player.getLocation().distanceSquared(frame.getLocation()) > limit * limit;
    }

    /** 玩家视线和地图平面的交点，返回归一化后的 (u, v)，不在屏幕内返回 null。 */
    public double[] screenCoords(Player viewer) {
        Location eye = viewer.getEyeLocation();
        Vector origin = eye.toVector();
        Vector direction = eye.getDirection();
        // normal 指向卡面看得见的那一侧：玩家在正面时 (眼睛 - 中心)·normal > 0；站在背面不算
        double viewerSide = origin.clone().subtract(center).dot(normal);
        if (viewerSide <= 0) {
            return null;
        }
        double denominator = direction.dot(normal);
        if (Math.abs(denominator) < 1.0E-6) {
            return null;
        }
        double distance = -viewerSide / denominator;
        if (distance <= 0 || distance > settings.maxDistance) {
            return null;
        }
        Vector hit = origin.clone().add(direction.clone().multiply(distance));
        Vector relative = hit.subtract(center);
        double a = relative.dot(right);
        double b = relative.dot(up);
        if (Math.abs(a) > 0.5 || Math.abs(b) > 0.5) {
            return null;
        }
        double u = Math.max(0.0, Math.min(0.999999, a + 0.5));
        double v = Math.max(0.0, Math.min(0.999999, 0.5 - b));
        return new double[]{u, v};
    }

    /**
     * 在 (u, v) 处按笔刷半径刮一笔；从上一笔的位置连线补点，避免长按时留下断点。
     * 返回本次新揭开的格子 {row, column}。
     */
    public List<int[]> scratch(double u, double v, int radius) {
        int px = clampPixel((int) (u * SIZE));
        int py = clampPixel((int) (v * SIZE));
        long now = System.currentTimeMillis();

        int fromX = px;
        int fromY = py;
        if (hasLastPoint && now - lastStrokeAt < 250L) {
            fromX = clampPixel((int) (lastU * SIZE));
            fromY = clampPixel((int) (lastV * SIZE));
        }

        double distance = Math.hypot(px - fromX, py - fromY);
        int steps = Math.max(1, (int) Math.ceil(distance / Math.max(2.0, radius * 0.5)));
        for (int step = 0; step <= steps; step++) {
            int x = fromX + (int) Math.round((px - fromX) * (double) step / steps);
            int y = fromY + (int) Math.round((py - fromY) * (double) step / steps);
            stamp(x, y, radius);
        }

        lastU = u;
        lastV = v;
        lastStrokeAt = now;
        hasLastPoint = true;

        return collectRevealed();
    }

    private void stamp(int centerX, int centerY, int radius) {
        int radiusSquared = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            int y = centerY + dy;
            if (y < 0 || y >= SIZE) {
                continue;
            }
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy > radiusSquared) {
                    continue;
                }
                int x = centerX + dx;
                if (x < 0 || x >= SIZE) {
                    continue;
                }
                int index = y * SIZE + x;
                if (scratched[index]) {
                    continue;
                }
                scratched[index] = true;
                shown[index] = base[index];
                if (canvas != null) {
                    canvas.setPixel(x, y, base[index]);
                }
                int cell = (y * rows / SIZE) * columns + (x * columns / SIZE);
                cellRemaining[cell]--;
            }
        }
    }

    private List<int[]> collectRevealed() {
        List<int[]> result = new ArrayList<>();
        for (int index = 0; index < cellTotal.length; index++) {
            if (cellRevealed[index]) {
                continue;
            }
            double remainingAllowed = cellTotal[index] * (1.0 - settings.revealThreshold);
            if (cellRemaining[index] <= remainingAllowed) {
                cellRevealed[index] = true;
                int row = index / columns;
                int column = index % columns;
                drawLabel(row, column);
                result.add(new int[]{row, column});
            }
        }
        return result;
    }

    private void blit() {
        if (canvas == null) {
            return;
        }
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                canvas.setPixel(x, y, shown[y * SIZE + x]);
            }
        }
    }

    /** 已揭晓的格子在卡面上画出 ASCII 名称（地图像素字体不支持中文，配置里可写 label）。 */
    private void drawLabels() {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                drawLabel(row, column);
            }
        }
    }

    private void drawLabel(int row, int column) {
        if (canvas == null) {
            fullBlitNeeded = true;
            return;
        }
        int index = row * columns + column;
        if (!cellRevealed[index] || labelDrawn[index]) {
            return;
        }
        ScratchPrize prize = prizes[row][column];
        String label = prize == null ? null : prize.label();
        if (label == null || label.isEmpty() || !MinecraftFont.Font.isValid(label)) {
            labelDrawn[index] = true;
            return;
        }
        int cellX = column * SIZE / columns;
        int cellY = row * SIZE / rows;
        int cellWidth = (column + 1) * SIZE / columns - cellX;
        int cellHeight = (row + 1) * SIZE / rows - cellY;
        while (label.length() > 1 && MinecraftFont.Font.getWidth(label) > cellWidth - 2) {
            label = label.substring(0, label.length() - 1);
        }
        int textWidth = MinecraftFont.Font.getWidth(label);
        int textHeight = MinecraftFont.Font.getHeight();
        int x = cellX + Math.max(0, (cellWidth - textWidth) / 2);
        int y = cellY + Math.max(0, (cellHeight - textHeight) / 2);
        canvas.drawText(x, y, MinecraftFont.Font, label);
        copyBack(x, y, textWidth, textHeight);
        labelDrawn[index] = true;
    }

    private void copyBack(int x, int y, int width, int height) {
        if (canvas == null) {
            return;
        }
        for (int dy = 0; dy < height; dy++) {
            int py = y + dy;
            if (py < 0 || py >= SIZE) {
                continue;
            }
            for (int dx = 0; dx < width; dx++) {
                int px = x + dx;
                if (px < 0 || px >= SIZE) {
                    continue;
                }
                shown[py * SIZE + px] = canvas.getPixel(px, py);
            }
        }
    }

    private void assignPrizes() {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                prizes[row][column] = settings.pick(random);
            }
        }
    }

    private void buildCells() {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int x0 = column * SIZE / columns;
                int x1 = (column + 1) * SIZE / columns;
                int y0 = row * SIZE / rows;
                int y1 = (row + 1) * SIZE / rows;
                int total = (x1 - x0) * (y1 - y0);
                int index = row * columns + column;
                cellTotal[index] = total;
                cellRemaining[index] = total;
            }
        }
    }

    private void buildBase() {
        byte background = MapPalette.matchColor(new Color(0x1E1E1E));
        for (int i = 0; i < base.length; i++) {
            base[i] = background;
        }
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                ScratchPrize prize = prizes[row][column];
                int x0 = column * SIZE / columns;
                int x1 = (column + 1) * SIZE / columns;
                int y0 = row * SIZE / rows;
                int y1 = (row + 1) * SIZE / rows;

                byte color = prize.color();
                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        base[y * SIZE + x] = color;
                    }
                }
                // 1 像素深色描边，让格子边界在刮开后仍然清楚
                byte border = darker(color, 0.55f);
                for (int x = x0; x < x1; x++) {
                    base[y0 * SIZE + x] = border;
                    base[(y1 - 1) * SIZE + x] = border;
                }
                for (int y = y0; y < y1; y++) {
                    base[y * SIZE + x0] = border;
                    base[y * SIZE + x1 - 1] = border;
                }
                if (prize.image() != null) {
                    drawImage(prize.image(), x0 + 2, y0 + 2, x1 - x0 - 4, y1 - y0 - 4);
                }
            }
        }
    }

    private void buildFoil() {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean stripe = (((x + y) / 8) & 1) == 0;
                int value = stripe ? 190 : 168;
                int noise = ((x * 31 + y * 17) % 9) - 4;
                int v = Math.max(0, Math.min(255, value + noise));
                foil[y * SIZE + x] = MapPalette.matchColor(v, v, Math.min(255, v + 8));
            }
        }
        byte grid = MapPalette.matchColor(120, 120, 132);
        for (int column = 1; column < columns; column++) {
            int x = column * SIZE / columns;
            for (int y = 0; y < SIZE; y++) {
                foil[y * SIZE + x] = grid;
            }
        }
        for (int row = 1; row < rows; row++) {
            int y = row * SIZE / rows;
            for (int x = 0; x < SIZE; x++) {
                foil[y * SIZE + x] = grid;
            }
        }
        byte edge = MapPalette.matchColor(96, 96, 108);
        for (int i = 0; i < SIZE; i++) {
            foil[i] = edge;
            foil[(SIZE - 1) * SIZE + i] = edge;
            foil[i * SIZE] = edge;
            foil[i * SIZE + SIZE - 1] = edge;
        }
    }

    private void drawImage(BufferedImage image, int x0, int y0, int width, int height) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        if (imageWidth <= 0 || imageHeight <= 0 || width <= 0 || height <= 0) {
            return;
        }
        double scale = Math.min((double) width / imageWidth, (double) height / imageHeight);
        int drawWidth = Math.max(1, (int) Math.round(imageWidth * scale));
        int drawHeight = Math.max(1, (int) Math.round(imageHeight * scale));
        int offsetX = x0 + (width - drawWidth) / 2;
        int offsetY = y0 + (height - drawHeight) / 2;

        for (int y = 0; y < drawHeight; y++) {
            int py = offsetY + y;
            if (py < 0 || py >= SIZE) {
                continue;
            }
            int sourceY = Math.min(imageHeight - 1, y * imageHeight / drawHeight);
            for (int x = 0; x < drawWidth; x++) {
                int px = offsetX + x;
                if (px < 0 || px >= SIZE) {
                    continue;
                }
                int sourceX = Math.min(imageWidth - 1, x * imageWidth / drawWidth);
                int rgb = image.getRGB(sourceX, sourceY);
                int alpha = (rgb >>> 24) & 0xFF;
                if (alpha < 128) {
                    continue;
                }
                base[py * SIZE + px] = MapPalette.matchColor(
                        (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            }
        }
    }

    private static byte darker(byte color, float factor) {
        Color baseColor = MapPalette.getColor(color);
        if (baseColor == null) {
            return color;
        }
        return MapPalette.matchColor(new Color(
                Math.max(0, Math.min(255, (int) (baseColor.getRed() * factor))),
                Math.max(0, Math.min(255, (int) (baseColor.getGreen() * factor))),
                Math.max(0, Math.min(255, (int) (baseColor.getBlue() * factor)))));
    }

    private static int clampPixel(int value) {
        return Math.max(0, Math.min(SIZE - 1, value));
    }
}
