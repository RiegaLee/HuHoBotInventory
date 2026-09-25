package cn.huohuas001.huhobot.inventory.renderer;

import cn.huohuas001.huhobot.inventory.model.InventorySlot;
import cn.huohuas001.huhobot.inventory.model.SlotType;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Builds a desktop-like layer: wallpaper below isolated rounded icon selection cards. */
public final class LayeredBackground {
    private static final long MAX_FILE_BYTES = 16L * 1024L * 1024L;
    private static final long MAX_PIXELS = 32L * 1024L * 1024L;
    private static final Color CARD_FILL = new Color(22, 29, 40, 174);
    private static final Color CARD_EDGE = new Color(238, 246, 250, 118);
    private static final Color CARD_INNER_EDGE = new Color(0, 0, 0, 90);
    private static final Color PLAYER_FILL = new Color(16, 23, 32, 192);

    private LayeredBackground() {}

    public static BufferedImage forInventory(Theme theme, Path userImage, String fit) {
        return inventory(theme, readUserImage(userImage), fit);
    }

    /** Temporary neutral wallpaper until the project-owned default artwork is supplied. */
    public static BufferedImage placeholderInventory(Theme theme) {
        return inventory(theme, solidWallpaper(), "stretch");
    }

    private static BufferedImage inventory(Theme theme, BufferedImage userImage, String fit) {
        Objects.requireNonNull(theme, "theme");
        Layout layout = theme.getLayout();
        BufferedImage result = wallpaper(userImage, layout.getWidth(), layout.getHeight(), fit);
        Graphics2D graphics = result.createGraphics();
        try {
            configure(graphics);
            for (int index = 0; index < 27; index++) {
                drawSelectionCard(
                    graphics,
                    inset(layout.slotBounds(InventorySlot.empty(SlotType.STORAGE, index)), 2),
                    CARD_FILL
                );
            }
            for (int index = 0; index < 9; index++) {
                drawSelectionCard(
                    graphics,
                    inset(layout.slotBounds(InventorySlot.empty(SlotType.HOTBAR, index)), 2),
                    CARD_FILL
                );
            }
            for (SlotType type : new SlotType[] {
                SlotType.ARMOR_HEAD,
                SlotType.ARMOR_CHEST,
                SlotType.ARMOR_LEGS,
                SlotType.ARMOR_FEET,
                SlotType.OFFHAND
            }) {
                drawSelectionCard(
                    graphics,
                    inset(layout.slotBounds(InventorySlot.empty(type, 0)), 2),
                    CARD_FILL
                );
            }
            Rectangle player = layout.getPlayerPreview();
            if (player != null) drawSelectionCard(graphics, player, PLAYER_FILL, 14);
            drawOuterFrame(graphics, layout.getWidth(), layout.getHeight());
        } finally {
            graphics.dispose();
        }
        return result;
    }

    public static BufferedImage forEnderChest(Theme theme, Path userImage, String fit) {
        return enderChest(theme, readUserImage(userImage), fit);
    }

    /** Temporary neutral wallpaper until the project-owned default artwork is supplied. */
    public static BufferedImage placeholderEnderChest(Theme theme) {
        return enderChest(theme, solidWallpaper(), "stretch");
    }

    private static BufferedImage enderChest(Theme theme, BufferedImage userImage, String fit) {
        Objects.requireNonNull(theme, "theme");
        EnderChestLayout layout = theme.getEnderChestLayout();
        BufferedImage result = wallpaper(userImage, layout.getWidth(), layout.getHeight(), fit);
        Graphics2D graphics = result.createGraphics();
        try {
            configure(graphics);
            for (int index = 0; index < 27; index++) {
                int x = layout.getStartX() + index % 9 * layout.getStepX();
                int y = layout.getStartY() + index / 9 * layout.getStepY();
                drawSelectionCard(
                    graphics,
                    inset(new Rectangle(x, y, layout.getSlotSize(), layout.getSlotSize()), 2),
                    CARD_FILL
                );
            }
            drawOuterFrame(graphics, layout.getWidth(), layout.getHeight());
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static BufferedImage wallpaper(BufferedImage source, int width, int height, String fit) {
        String normalizedFit = Objects.requireNonNull(fit, "fit").trim().toLowerCase(Locale.ROOT);
        if (!"cover".equals(normalizedFit) && !"stretch".equals(normalizedFit)) {
            throw new IllegalArgumentException("render.custom-background.fit must be cover or stretch");
        }
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        try {
            configure(graphics);
            float inset = 2.0f;
            float arc = outerArc(width, height);
            Shape clip = new RoundRectangle2D.Float(
                inset,
                inset,
                width - inset * 2.0f,
                height - inset * 2.0f,
                arc,
                arc
            );
            graphics.clip(clip);
            if ("stretch".equals(normalizedFit)) {
                graphics.drawImage(source, 0, 0, width, height, null);
            } else {
                drawCover(graphics, source, width, height);
            }
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static void drawSelectionCard(Graphics2D graphics, Rectangle bounds, Color fill) {
        drawSelectionCard(graphics, bounds, fill, 10);
    }

    private static void drawSelectionCard(
        Graphics2D graphics,
        Rectangle bounds,
        Color fill,
        int arc
    ) {
        graphics.setColor(new Color(0, 0, 0, 62));
        graphics.fillRoundRect(bounds.x + 2, bounds.y + 3, bounds.width, bounds.height, arc, arc);
        graphics.setColor(fill);
        graphics.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, arc, arc);
        graphics.setStroke(new BasicStroke(1.5f));
        graphics.setColor(CARD_EDGE);
        graphics.drawRoundRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1, arc, arc);
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.setColor(CARD_INNER_EDGE);
        graphics.drawRoundRect(
            bounds.x + 3,
            bounds.y + 3,
            bounds.width - 7,
            bounds.height - 7,
            7,
            7
        );
    }

    private static Rectangle inset(Rectangle bounds, int amount) {
        return new Rectangle(
            bounds.x + amount,
            bounds.y + amount,
            bounds.width - amount * 2,
            bounds.height - amount * 2
        );
    }

    private static void drawOuterFrame(Graphics2D graphics, int width, int height) {
        float arc = outerArc(width, height);
        graphics.setStroke(new BasicStroke(4.0f));
        graphics.setColor(new Color(245, 252, 253, 210));
        graphics.draw(new RoundRectangle2D.Float(2, 2, width - 5, height - 5, arc, arc));
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.setColor(new Color(20, 35, 47, 210));
        graphics.draw(new RoundRectangle2D.Float(0.5f, 0.5f, width - 2, height - 2, arc + 2, arc + 2));
    }

    private static float outerArc(int width, int height) {
        return 60.0f;
    }

    private static void drawCover(Graphics2D graphics, BufferedImage source, int width, int height) {
        double scale = Math.max(
            (double) width / (double) source.getWidth(),
            (double) height / (double) source.getHeight()
        );
        int scaledWidth = Math.max(1, (int) Math.ceil(source.getWidth() * scale));
        int scaledHeight = Math.max(1, (int) Math.ceil(source.getHeight() * scale));
        int x = (width - scaledWidth) / 2;
        int y = (height - scaledHeight) / 2;
        graphics.drawImage(source, x, y, scaledWidth, scaledHeight, null);
    }

    private static BufferedImage readUserImage(Path path) {
        Path normalized = Objects.requireNonNull(path, "custom background").toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Missing custom background: " + normalized);
        }
        try {
            if (Files.size(normalized) > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("custom background exceeds 16 MiB: " + normalized);
            }
            BufferedImage image = ImageIO.read(normalized.toFile());
            if (image == null) {
                throw new IllegalArgumentException("Unreadable custom background: " + normalized);
            }
            long pixels = (long) image.getWidth() * (long) image.getHeight();
            if (image.getWidth() < 1 || image.getHeight() < 1 || pixels > MAX_PIXELS) {
                throw new IllegalArgumentException("custom background has unsafe dimensions: " + normalized);
            }
            return image;
        } catch (IOException error) {
            throw new IllegalArgumentException("Could not read custom background: " + normalized, error);
        }
    }

    private static BufferedImage solidWallpaper() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, new Color(45, 58, 72).getRGB());
        return image;
    }

    private static void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR
        );
    }
}
