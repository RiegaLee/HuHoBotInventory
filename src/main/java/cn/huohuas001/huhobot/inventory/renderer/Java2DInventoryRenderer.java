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
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Headless-safe Java2D renderer producing PNG bytes without temporary files. */
public final class Java2DInventoryRenderer implements InventoryRenderer {
    private static final Color SLOT_FILL = new Color(12, 22, 29, 190);
    private static final Color SLOT_EDGE = new Color(102, 158, 177, 210);
    private static final Color SLOT_INNER = new Color(33, 54, 64, 220);
    private static final Color TITLE = new Color(226, 242, 245);
    private static final Color COUNT = new Color(250, 250, 250);
    private static final Color SHADOW = new Color(0, 0, 0, 210);

    private final Theme theme;

    public Java2DInventoryRenderer(Theme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
    }

    @Override
    public RenderResult render(InventorySnapshot snapshot) {
        return render(snapshot, null);
    }

    @Override
    public RenderResult render(InventorySnapshot snapshot, BufferedImage playerPreview) {
        return render(snapshot, playerPreview, InventoryRenderMetadata.realtime(snapshot.getCapturedAt()));
    }

    @Override
    public RenderResult render(
        InventorySnapshot snapshot,
        BufferedImage playerPreview,
        InventoryRenderMetadata metadata
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Layout layout = theme.getLayout();
        BufferedImage canvas = new BufferedImage(layout.getWidth(), layout.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            configure(graphics, theme);
            graphics.drawImage(theme.getBackground(), 0, 0, null);
            drawPlayerPreview(graphics, layout, playerPreview);
            drawFreshness(graphics, layout, metadata);
            if (theme.isDrawTitle()) drawTitle(graphics, layout, snapshot);
            for (InventorySlot slot : snapshot.getAllSlots()) drawSlot(graphics, layout, slot);
        } finally {
            graphics.dispose();
        }
        return encode(canvas);
    }

    private static void drawFreshness(Graphics2D graphics, Layout layout, InventoryRenderMetadata metadata) {
        if (metadata == null || metadata.getFreshness() != InventoryRenderMetadata.Freshness.OFFLINE_SNAPSHOT) return;
        Rectangle area = layout.getPlayerPreview();
        if (area == null) return;
        String label = offlineSnapshotLabel(metadata);
        Graphics2D layer = (Graphics2D) graphics.create();
        try {
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, 11);
            FontMetrics metrics = layer.getFontMetrics(font);
            while (font.getSize() > 8 && metrics.stringWidth(label) > area.width - 24) {
                font = font.deriveFont((float) font.getSize() - 1.0f);
                metrics = layer.getFontMetrics(font);
            }
            layer.setFont(font);
            int width = Math.min(area.width - 12, metrics.stringWidth(label) + 12);
            int x = area.x + 6;
            int y = area.y + 6;
            layer.setColor(new Color(10, 18, 23, 185));
            layer.fillRoundRect(x, y, width, 18, 8, 8);
            layer.setColor(new Color(223, 237, 240));
            layer.drawString(label, x + 6, y + 13);
        } finally {
            layer.dispose();
        }
    }

    static String offlineSnapshotLabel(InventoryRenderMetadata metadata) {
        String time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault()).format(metadata.getCapturedAt());
        return "Offline Snapshot · " + time;
    }

    private static void drawPlayerPreview(Graphics2D graphics, Layout layout, BufferedImage preview) {
        Rectangle area = layout.getPlayerPreview();
        if (area == null || preview == null) return;
        double scale = Math.min((double) area.width / preview.getWidth(), (double) area.height / preview.getHeight());
        int width = Math.max(1, (int) Math.round(preview.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(preview.getHeight() * scale));
        int x = area.x + (area.width - width) / 2;
        int y = area.y + (area.height - height) / 2;
        Graphics2D layer = (Graphics2D) graphics.create();
        try {
            layer.setClip(area);
            layer.setComposite(AlphaComposite.SrcOver);
            layer.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int inset = 3;
            int panelX = area.x + inset;
            int panelY = area.y + inset;
            int panelWidth = area.width - inset * 2;
            int panelHeight = area.height - inset * 2;
            int arc = 14;
            layer.setPaint(new GradientPaint(
                panelX, panelY, new Color(112, 124, 132, 175),
                panelX, panelY + panelHeight, new Color(42, 51, 58, 205)
            ));
            layer.fillRoundRect(panelX, panelY, panelWidth, panelHeight, arc, arc);

            float glowX = area.x + area.width / 2.0f;
            float glowY = area.y + area.height * 0.43f;
            float glowRadius = Math.max(area.width, area.height) * 0.58f;
            layer.setPaint(new RadialGradientPaint(
                glowX,
                glowY,
                glowRadius,
                new float[] {0.0f, 1.0f},
                new Color[] {new Color(225, 232, 235, 62), new Color(120, 132, 140, 0)}
            ));
            layer.fillRoundRect(panelX, panelY, panelWidth, panelHeight, arc, arc);

            layer.setStroke(new BasicStroke(1.5f));
            layer.setColor(new Color(222, 231, 235, 78));
            layer.drawRoundRect(panelX, panelY, panelWidth - 1, panelHeight - 1, arc, arc);

            layer.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            layer.drawImage(preview, x, y, width, height, null);
        } finally {
            layer.dispose();
        }
    }

    private void drawTitle(Graphics2D graphics, Layout layout, InventorySnapshot snapshot) {
        Point title = layout.getTitle();
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        graphics.setColor(SHADOW);
        graphics.drawString(snapshot.getPlayerName() + " — Inventory", title.x + 2, title.y + 2);
        graphics.setColor(TITLE);
        graphics.drawString(snapshot.getPlayerName() + " — Inventory", title.x, title.y);
    }

    private void drawSlot(Graphics2D graphics, Layout layout, InventorySlot slot) {
        Rectangle bounds = layout.slotBounds(slot);
        if (theme.isDrawSlotBackgrounds()) {
            graphics.setColor(SLOT_FILL);
            graphics.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 10, 10);
            graphics.setStroke(new BasicStroke(2f));
            graphics.setColor(SLOT_EDGE);
            graphics.drawRoundRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1, 10, 10);
            graphics.setColor(SLOT_INNER);
            graphics.drawRoundRect(bounds.x + 4, bounds.y + 4, bounds.width - 9, bounds.height - 9, 7, 7);
        }

        ItemSnapshot item = slot.getItem();
        if (item == null) return;
        TextureResolver.ResolvedTexture texture = theme.getTextures().resolve(item);
        int itemSize = layout.getItemSize();
        int itemX = bounds.x + (bounds.width - itemSize) / 2;
        int itemY = bounds.y + (bounds.height - itemSize) / 2;
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.drawImage(texture.getImage(), itemX, itemY, itemSize, itemSize, null);
        if (item.hasEnchantmentGlint()) {
            graphics.setColor(new Color(130, 95, 255, 150));
            graphics.setStroke(new BasicStroke(3f));
            graphics.drawRoundRect(itemX + 1, itemY + 1, itemSize - 2, itemSize - 2, 8, 8);
        }
        if (item.getAmount() > 1) drawAmount(graphics, layout, bounds, item.getAmount());
        if (item.getMaxDamage() > 0) drawDurability(graphics, layout, bounds, item);
    }

    private void drawAmount(Graphics2D graphics, Layout layout, Rectangle bounds, int amount) {
        String text = Integer.toString(amount);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        FontMetrics metrics = graphics.getFontMetrics();
        Point offset = layout.getQuantityOffset();
        int x = bounds.x + offset.x - metrics.stringWidth(text);
        int y = bounds.y + offset.y;
        graphics.setColor(SHADOW);
        graphics.drawString(text, x + 2, y + 2);
        graphics.setColor(COUNT);
        graphics.drawString(text, x, y);
    }

    private void drawDurability(Graphics2D graphics, Layout layout, Rectangle bounds, ItemSnapshot item) {
        Rectangle bar = layout.getDurability();
        double remaining = 1.0d - (double) item.getDamage() / (double) item.getMaxDamage();
        remaining = Math.max(0.0d, Math.min(1.0d, remaining));
        int x = bounds.x + bar.x;
        int y = bounds.y + bar.y;
        graphics.setColor(new Color(18, 18, 18, 230));
        graphics.fillRect(x, y, bar.width, bar.height);
        int filled = (int) Math.round(bar.width * remaining);
        graphics.setColor(remaining > 0.5d ? new Color(73, 214, 112) : new Color(238, 177, 47));
        graphics.fillRect(x, y, filled, bar.height);
    }

    private static void configure(Graphics2D graphics, Theme theme) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            theme.isNearestNeighborTextures()
                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                : RenderingHints.VALUE_INTERPOLATION_BILINEAR
        );
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static RenderResult encode(BufferedImage image) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(128 * 1024);
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("No ImageIO PNG writer is available");
            }
            return new RenderResult(output.toByteArray(), "image/png", image.getWidth(), image.getHeight());
        } catch (IOException error) {
            throw new IllegalStateException("Could not encode inventory PNG", error);
        }
    }
}
