package cn.huohuas001.huhobot.inventory.skin;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Optional;

/** Original programmatic fallback skin; no Mojang or third-party image is bundled. */
public final class DefaultPlayerSkinProvider implements PlayerSkinProvider {
    private final PlayerSkin fallback = new PlayerSkin(createSkin(), "huhobot-default-v1", "LOCAL_DEFAULT", false);

    @Override
    public Optional<PlayerSkin> findSkin(PlayerIdentity player) {
        return Optional.of(fallback);
    }

    public PlayerSkin getFallback() { return fallback; }

    private static BufferedImage createSkin() {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = skin.createGraphics();
        try {
            Color face = new Color(207, 151, 112);
            Color hair = new Color(58, 36, 31);
            Color shirt = new Color(43, 145, 151);
            Color trousers = new Color(48, 55, 92);
            fillCuboid(graphics, 0, 0, 8, 8, 8, face);
            fillCuboid(graphics, 16, 16, 8, 12, 4, shirt);
            fillCuboid(graphics, 40, 16, 4, 12, 4, face);
            fillCuboid(graphics, 32, 48, 4, 12, 4, face);
            fillCuboid(graphics, 0, 16, 4, 12, 4, trousers);
            fillCuboid(graphics, 16, 48, 4, 12, 4, trousers);
            graphics.setColor(hair);
            graphics.fillRect(8, 8, 8, 3);
            graphics.setColor(new Color(45, 30, 27));
            graphics.fillRect(9, 11, 2, 1);
            graphics.fillRect(13, 11, 2, 1);
            fillCuboid(graphics, 16, 32, 8, 12, 4, new Color(78, 198, 197, 130));
            fillCuboid(graphics, 40, 32, 4, 12, 4, new Color(78, 198, 197, 130));
            fillCuboid(graphics, 48, 48, 4, 12, 4, new Color(78, 198, 197, 130));
        } finally {
            graphics.dispose();
        }
        return skin;
    }

    private static void fillCuboid(
        Graphics2D graphics, int u, int v, int width, int height, int depth, Color color
    ) {
        graphics.setColor(color);
        graphics.fillRect(u, v, depth * 2 + width * 2, depth + height);
    }
}
