package cn.huohuas001.huhobot.inventory.renderer;

import cn.huohuas001.huhobot.inventory.datasource.MockInventoryDataSource;
import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;
import cn.huohuas001.huhobot.inventory.skin.DefaultPlayerSkinProvider;
import cn.huohuas001.huhobot.inventory.skin.PlayerModelRenderer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CozyUiThemeTest {
    private static final Path THEME =
        Paths.get("src", "main", "resources", "themes", "cozyui-plus");

    @Test
    void loadsAndRendersTheUnofficialCompatibilityTheme() throws Exception {
        Theme theme = ThemeLoader.load(THEME);
        assertEquals("cozyui-plus", theme.getId());
        assertFalse(theme.isDrawTitle());
        assertFalse(theme.isDrawSlotBackgrounds());
        assertFalse(theme.isDrawPlayerPreviewMatte());
        assertTrue(theme.isNearestNeighborTextures());
        assertEquals(780, theme.getLayout().getWidth());
        assertEquals(544, theme.getLayout().getHeight());

        EnderChestLayout ender = theme.getEnderChestLayout();
        assertEquals(712, ender.getWidth());
        assertEquals(276, ender.getHeight());
        assertEquals(32, ender.getStartX());
        assertEquals(32, ender.getStartY());
        assertEquals(72, ender.getStepX());
        assertEquals(72, ender.getStepY());

        InventorySnapshot snapshot = new MockInventoryDataSource("cozyui-test")
            .createSnapshot("CozyUI");
        Rectangle playerArea = theme.getLayout().getPlayerPreview();
        BufferedImage player = new PlayerModelRenderer(playerArea.width, playerArea.height)
            .render(new DefaultPlayerSkinProvider().getFallback());
        RenderResult inventoryResult = new Java2DInventoryRenderer(theme).render(snapshot, player);
        BufferedImage inventory = ImageIO.read(new ByteArrayInputStream(inventoryResult.getBytes()));
        assertEquals(780, inventory.getWidth());
        assertEquals(544, inventory.getHeight());
        assertEquals(
            theme.getBackground().getRGB(playerArea.x + 5, playerArea.y + 5),
            inventory.getRGB(playerArea.x + 5, playerArea.y + 5),
            "transparent player pixels must preserve CozyUI+'s own panel instead of a second matte"
        );

        EnderChestRenderer renderer = new EnderChestRenderer(
            theme, THEME.resolve("ender-chest-background.png")
        );
        RenderResult realtime = renderer.render(
            snapshot, null, InventoryRenderMetadata.realtime(snapshot.getCapturedAt())
        );
        RenderResult offline = renderer.render(
            snapshot, null, InventoryRenderMetadata.offline(snapshot.getCapturedAt())
        );
        assertEquals(712, realtime.getWidth());
        assertEquals(276, realtime.getHeight());
        assertNotEquals(
            java.util.Arrays.hashCode(realtime.getBytes()),
            java.util.Arrays.hashCode(offline.getBytes())
        );
        BufferedImage realtimeImage = ImageIO.read(new ByteArrayInputStream(realtime.getBytes()));
        BufferedImage offlineImage = ImageIO.read(new ByteArrayInputStream(offline.getBytes()));
        assertEquals(
            realtimeImage.getRGB(200, 150),
            offlineImage.getRGB(200, 150),
            "offline freshness badge must not clear or replace the CozyUI+ background"
        );

        BufferedImage background = ImageIO.read(THEME.resolve("ender-chest-background.png").toFile());
        int differences = 0;
        for (int y = 0; y < 92; y++) for (int x = 0; x < 696; x++) {
            if (background.getRGB(x + 8, y + 8) != background.getRGB(x + 8, 8 + 259 - y)) {
                differences++;
            }
        }
        assertEquals(0, differences, "top and mirrored bottom regions must remain pixel-identical");

        Path output = Paths.get("build", "rendered-test-output");
        Files.createDirectories(output);
        Files.write(output.resolve("cozyui-plus-inventory.png"), inventoryResult.getBytes());
        Files.write(output.resolve("cozyui-plus-ender-chest.png"), realtime.getBytes());
        Files.write(output.resolve("cozyui-plus-ender-chest-offline.png"), offline.getBytes());
    }
}
