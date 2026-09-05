package cn.huohuas001.huhobot.inventory.renderer;

import cn.huohuas001.huhobot.inventory.model.InventorySlot;
import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;
import cn.huohuas001.huhobot.inventory.model.ItemSnapshot;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Compact 9x3 renderer used only for a player's private Ender Chest contents. */
public final class EnderChestRenderer implements InventoryRenderer {
    public static final int WIDTH = 704;
    public static final int HEIGHT = 308;
    private static final int START_X = 28;
    private static final int START_Y = 68;
    private static final int SLOT_SIZE = 72;
    private static final int ITEM_SIZE = 64;
    private static final Color COUNT = new Color(250, 250, 250);
    private static final Color SHADOW = new Color(0, 0, 0, 210);

    private final Theme theme;
    private final BufferedImage background;

    public EnderChestRenderer(Theme theme, Path backgroundPath) {
        this(theme, loadBackground(backgroundPath));
    }

    EnderChestRenderer(Theme theme, BufferedImage background) {
        this.theme = Objects.requireNonNull(theme, "theme");
        this.background = Objects.requireNonNull(background, "background");
        if (background.getWidth() != WIDTH || background.getHeight() != HEIGHT) {
            throw new IllegalArgumentException("Ender Chest background must be 704x308");
        }
    }

    @Override
    public RenderResult render(InventorySnapshot snapshot) {
        return render(snapshot, null, InventoryRenderMetadata.realtime(snapshot.getCapturedAt()));
    }

    @Override
    public RenderResult render(
        InventorySnapshot snapshot,
        BufferedImage ignoredPlayerPreview,
        InventoryRenderMetadata metadata
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        BufferedImage canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            configure(graphics);
            graphics.drawImage(background, 0, 0, null);
            drawFreshness(graphics, metadata);
            for (InventorySlot slot : snapshot.getStorage()) drawSlot(graphics, slot);
        } finally {
            graphics.dispose();
        }
        return encode(canvas);
    }

    private void drawSlot(Graphics2D graphics, InventorySlot slot) {
        ItemSnapshot item = slot.getItem();
        if (item == null) return;
        int column = slot.getIndex() % 9;
        int row = slot.getIndex() / 9;
        int slotX = START_X + column * SLOT_SIZE;
        int slotY = START_Y + row * SLOT_SIZE;
        int itemX = slotX + (SLOT_SIZE - ITEM_SIZE) / 2;
        int itemY = slotY + (SLOT_SIZE - ITEM_SIZE) / 2;
        TextureResolver.ResolvedTexture texture = theme.getTextures().resolve(item);
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.drawImage(texture.getImage(), itemX, itemY, ITEM_SIZE, ITEM_SIZE, null);
        if (item.hasEnchantmentGlint()) {
            graphics.setColor(new Color(130, 95, 255, 150));
            graphics.setStroke(new BasicStroke(3f));
            graphics.drawRoundRect(itemX + 1, itemY + 1, ITEM_SIZE - 2, ITEM_SIZE - 2, 8, 8);
        }
        if (item.getAmount() > 1) drawAmount(graphics, slotX, slotY, item.getAmount());
        if (item.getMaxDamage() > 0) drawDurability(graphics, slotX, slotY, item);
    }

    private static void drawAmount(Graphics2D graphics, int slotX, int slotY, int amount) {
        String text = Integer.toString(amount);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        FontMetrics metrics = graphics.getFontMetrics();
        int x = slotX + 68 - metrics.stringWidth(text);
        int y = slotY + 68;
        graphics.setColor(SHADOW);
        graphics.drawString(text, x + 2, y + 2);
        graphics.setColor(COUNT);
        graphics.drawString(text, x, y);
    }

    private static void drawDurability(Graphics2D graphics, int slotX, int slotY, ItemSnapshot item) {
        double remaining = 1.0d - (double) item.getDamage() / (double) item.getMaxDamage();
        remaining = Math.max(0.0d, Math.min(1.0d, remaining));
        int x = slotX + 8;
        int y = slotY + 64;
        graphics.setColor(new Color(18, 18, 18, 230));
        graphics.fillRect(x, y, 56, 4);
        graphics.setColor(remaining > 0.5d ? new Color(73, 214, 112) : new Color(238, 177, 47));
        graphics.fillRect(x, y, (int) Math.round(56 * remaining), 4);
    }

    private static void drawFreshness(Graphics2D graphics, InventoryRenderMetadata metadata) {
        if (metadata == null || metadata.getFreshness() != InventoryRenderMetadata.Freshness.OFFLINE_SNAPSHOT) return;
        String time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault()).format(metadata.getCapturedAt());
        String label = "Offline Snapshot · " + time;
        Graphics2D layer = (Graphics2D) graphics.create();
        try {
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, 11);
            layer.setFont(font);
            FontMetrics metrics = layer.getFontMetrics(font);
            int width = metrics.stringWidth(label) + 12;
            layer.setColor(new Color(10, 18, 23, 185));
            layer.fillRoundRect(8, 8, width, 18, 8, 8);
            layer.setColor(new Color(223, 237, 240));
            layer.drawString(label, 14, 21);
        } finally {
            layer.dispose();
        }
    }

    private void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            theme.isNearestNeighborTextures()
                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                : RenderingHints.VALUE_INTERPOLATION_BILINEAR
        );
    }

    private static BufferedImage loadBackground(Path path) {
        Objects.requireNonNull(path, "backgroundPath");
        if (Files.isRegularFile(path)) {
            try {
                BufferedImage image = ImageIO.read(path.toFile());
                if (image != null) return image;
            } catch (Exception error) {
                throw new IllegalArgumentException("Could not read Ender Chest background " + path, error);
            }
        }
        return neutralBackground();
    }

    private static BufferedImage neutralBackground() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(194, 194, 194));
            graphics.fillRoundRect(0, 0, WIDTH, HEIGHT, 18, 18);
            for (int index = 0; index < 27; index++) {
                int x = START_X + index % 9 * SLOT_SIZE;
                int y = START_Y + index / 9 * SLOT_SIZE;
                graphics.setColor(new Color(142, 142, 142));
                graphics.fillRect(x, y, SLOT_SIZE, SLOT_SIZE);
                graphics.setColor(new Color(71, 71, 71));
                graphics.drawRect(x, y, SLOT_SIZE - 1, SLOT_SIZE - 1);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static RenderResult encode(BufferedImage image) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(96 * 1024);
            if (!ImageIO.write(image, "png", output)) throw new IllegalStateException("No PNG writer is available");
            return new RenderResult(output.toByteArray(), "image/png", image.getWidth(), image.getHeight());
        } catch (Exception error) {
            throw new IllegalStateException("Could not encode Ender Chest PNG", error);
        }
    }
}
