package cn.huohuas001.huhobot.inventory.renderer;

import cn.huohuas001.huhobot.inventory.datasource.MockInventoryDataSource;
import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnderChestRendererTest {
    private static final Path THEME = Paths.get("src", "main", "resources", "themes", "faithful32x");

    @Test
    void rendersCompactFaithfulThreeRowPngAndOfflineBadge() throws Exception {
        Theme theme = ThemeLoader.load(THEME);
        InventorySnapshot snapshot = new MockInventoryDataSource("ender-render-test")
            .createSnapshot("Steve");
        EnderChestRenderer renderer = new EnderChestRenderer(
            theme, THEME.resolve("ender-chest-background.png")
        );

        RenderResult realtime = renderer.render(
            snapshot, null, InventoryRenderMetadata.realtime(snapshot.getCapturedAt())
        );
        RenderResult offline = renderer.render(
            snapshot, null, InventoryRenderMetadata.offline(snapshot.getCapturedAt())
        );

        assertEquals("image/png", realtime.getMimeType());
        assertEquals(704, realtime.getWidth());
        assertEquals(308, realtime.getHeight());
        assertTrue(realtime.getByteSize() > 8_000);
        assertTrue(realtime.getByteSize() < 4 * 1024 * 1024);
        assertNotEquals(java.util.Arrays.hashCode(realtime.getBytes()), java.util.Arrays.hashCode(offline.getBytes()));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(realtime.getBytes()));
        assertNotNull(image);
        assertEquals(704, image.getWidth());
        assertEquals(308, image.getHeight());

        Path output = Paths.get("build", "rendered-test-output", "ender-chest-1.17.0-reference.png");
        Files.createDirectories(output.getParent());
        Files.write(output, realtime.getBytes());
        Path offlineOutput = Paths.get("build", "rendered-test-output", "ender-chest-1.17.0-offline.png");
        Files.write(offlineOutput, offline.getBytes());
    }
}
