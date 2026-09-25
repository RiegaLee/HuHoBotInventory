package cn.huohuas001.huhobot.inventory.renderer;

import cn.huohuas001.huhobot.inventory.datasource.MockInventoryDataSource;
import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;
import cn.huohuas001.huhobot.inventory.model.InventorySlot;
import cn.huohuas001.huhobot.inventory.model.SlotType;
import cn.huohuas001.huhobot.inventory.skin.DefaultPlayerSkinProvider;
import cn.huohuas001.huhobot.inventory.skin.PlayerModelRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LayeredBackgroundTest {
    private static final Path THEMES = Paths.get("src", "main", "resources", "themes");

    @Test
    void rendersBundledMistBlueDefaultWallpaper() throws Exception {
        Path themeDirectory = THEMES.resolve("faithful32x");
        Theme theme = ThemeLoader.load(themeDirectory);
        Path wallpaper = themeDirectory.resolve("default-wallpaper.png");
        BufferedImage inventoryBackground = LayeredBackground.forInventory(
            theme,
            wallpaper,
            "stretch"
        );
        Rectangle playerArea = theme.getLayout().getPlayerPreview();
        BufferedImage player = new PlayerModelRenderer(playerArea.width, playerArea.height)
            .render(new DefaultPlayerSkinProvider().getFallback());
        InventorySnapshot snapshot = new MockInventoryDataSource("bundled-wallpaper-test")
            .createSnapshot("WallpaperUser");
        RenderResult inventory = new Java2DInventoryRenderer(theme, inventoryBackground)
            .render(snapshot, player);
        BufferedImage enderBackground = LayeredBackground.forEnderChest(
            theme,
            wallpaper,
            "cover"
        );
        RenderResult ender = new EnderChestRenderer(theme, enderBackground).render(snapshot);

        Path output = Paths.get("build", "rendered-test-output");
        Files.createDirectories(output);
        Files.write(output.resolve("default-wallpaper-inventory.png"), inventory.getBytes());
        Files.write(output.resolve("default-wallpaper-ender-chest.png"), ender.getBytes());

        assertEquals(1359, inventory.getWidth());
        assertEquals(1017, inventory.getHeight());
        assertEquals(1620, ender.getWidth());
        assertEquals(694, ender.getHeight());
    }

    @Test
    void roundsWallpaperAndMasksOnlyInteractiveCards(@TempDir Path directory) throws Exception {
        Path wallpaperFile = directory.resolve("solid.png");
        BufferedImage solid = new BufferedImage(1600, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D source = solid.createGraphics();
        try {
            source.setColor(new Color(198, 38, 142));
            source.fillRect(0, 0, solid.getWidth(), solid.getHeight());
        } finally {
            source.dispose();
        }
        ImageIO.write(solid, "png", wallpaperFile.toFile());

        Theme theme = ThemeLoader.load(THEMES.resolve("faithful32x"));
        BufferedImage result = LayeredBackground.forInventory(theme, wallpaperFile, "cover");
        Rectangle slot = theme.getLayout().slotBounds(InventorySlot.empty(SlotType.STORAGE, 0));

        assertEquals(0, result.getRGB(0, 0) >>> 24, "wallpaper corners must be transparent");
        assertEquals(
            new Color(198, 38, 142).getRGB(),
            result.getRGB(1120, 360),
            "wallpaper outside the icon cards must keep its original color"
        );
        assertNotEquals(
            new Color(198, 38, 142).getRGB(),
            result.getRGB(slot.x + slot.width / 2, slot.y + slot.height / 2),
            "each item position must receive a local selection-card mask"
        );
    }

    @Test
    void rejectsUnknownFitMode(@TempDir Path directory) throws Exception {
        Path wallpaperFile = directory.resolve("wallpaper.png");
        ImageIO.write(wallpaper(20, 20), "png", wallpaperFile.toFile());
        Theme theme = ThemeLoader.load(THEMES.resolve("faithful32x"));
        assertThrows(
            IllegalArgumentException.class,
            () -> LayeredBackground.forInventory(theme, wallpaperFile, "contain")
        );
    }

    @Test
    void rendersFaithfulItemsWithDesktopStyleWallpaper(@TempDir Path directory) throws Exception {
        Path wallpaperFile = directory.resolve("wallpaper.png");
        ImageIO.write(wallpaper(1600, 900), "png", wallpaperFile.toFile());
        Path output = Paths.get("build", "rendered-test-output");
        Files.createDirectories(output);

        for (String id : new String[] {"faithful32x"}) {
            Path themeDirectory = THEMES.resolve(id);
            Theme theme = ThemeLoader.load(themeDirectory);
            BufferedImage inventoryBackground = LayeredBackground.forInventory(
                theme, wallpaperFile, "cover"
            );
            Rectangle playerArea = theme.getLayout().getPlayerPreview();
            BufferedImage player = new PlayerModelRenderer(playerArea.width, playerArea.height)
                .render(new DefaultPlayerSkinProvider().getFallback());
            InventorySnapshot snapshot = new MockInventoryDataSource("custom-background-test")
                .createSnapshot("WallpaperUser");
            RenderResult inventory = new Java2DInventoryRenderer(theme, inventoryBackground)
                .render(snapshot, player);
            Files.write(output.resolve("custom-background-" + id + "-inventory.png"), inventory.getBytes());

            BufferedImage enderBackground = LayeredBackground.forEnderChest(
                theme,
                wallpaperFile,
                "cover"
            );
            RenderResult ender = new EnderChestRenderer(theme, enderBackground).render(snapshot);
            Files.write(output.resolve("custom-background-" + id + "-ender-chest.png"), ender.getBytes());

            assertEquals(theme.getLayout().getWidth(), inventory.getWidth());
            assertEquals(theme.getEnderChestLayout().getWidth(), ender.getWidth());
        }
    }

    private static BufferedImage wallpaper(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setPaint(new GradientPaint(
                0, 0, new Color(22, 61, 115),
                width, height, new Color(202, 79, 132)
            ));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(new Color(255, 223, 128, 190));
            graphics.fillOval(width / 8, height / 6, width / 3, height / 2);
            graphics.setColor(new Color(93, 230, 206, 180));
            graphics.fillOval(width * 2 / 3, height / 4, width / 5, width / 5);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
