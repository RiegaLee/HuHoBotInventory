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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnderChestRendererTest {
    @Test
    void rendersCompactFaithfulThreeRowPngWithoutFreshnessBadge() throws Exception {
        Theme theme = RendererTestAssets.loadProductionFaithfulTheme();
        InventorySnapshot snapshot = new MockInventoryDataSource("ender-render-test")
            .createSnapshot("Steve");
        EnderChestRenderer renderer = new EnderChestRenderer(
            theme,
            LayeredBackground.forEnderChest(
                theme,
                RendererTestAssets.FAITHFUL_THEME.resolve("default-wallpaper.png"),
                "cover"
            )
        );

        RenderResult realtime = renderer.render(
            snapshot, null, InventoryRenderMetadata.realtime(snapshot.getCapturedAt())
        );
        RenderResult offline = renderer.render(
            snapshot, null, InventoryRenderMetadata.offline(snapshot.getCapturedAt())
        );

        assertEquals("image/png", realtime.getMimeType());
        assertEquals(1620, realtime.getWidth());
        assertEquals(694, realtime.getHeight());
        assertTrue(realtime.getByteSize() > 8_000);
        assertTrue(realtime.getByteSize() < 4 * 1024 * 1024);
        assertArrayEquals(realtime.getBytes(), offline.getBytes());
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(realtime.getBytes()));
        assertNotNull(image);
        assertEquals(1620, image.getWidth());
        assertEquals(694, image.getHeight());

        Path output = Paths.get("build", "rendered-test-output", "ender-chest-1.17.0-reference.png");
        Files.createDirectories(output.getParent());
        Files.write(output, realtime.getBytes());
        Path offlineOutput = Paths.get("build", "rendered-test-output", "ender-chest-1.17.0-offline.png");
        Files.write(offlineOutput, offline.getBytes());
    }
}
