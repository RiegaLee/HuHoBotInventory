package cn.huohuas001.huhobot.inventory.renderer;

import cn.huohuas001.huhobot.inventory.datasource.MockInventoryDataSource;
import cn.huohuas001.huhobot.inventory.asset.VanillaImportedAssetProvider;
import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;
import cn.huohuas001.huhobot.inventory.model.ItemSnapshot;
import cn.huohuas001.huhobot.inventory.skin.DefaultPlayerSkinProvider;
import cn.huohuas001.huhobot.inventory.skin.PlayerModelRenderer;
import cn.huohuas001.huhobot.inventory.skin.PlayerSkin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Java2DInventoryRendererTest {
    private static final Path DEFAULT_THEME = Paths.get("src", "main", "resources", "themes", "default");
    private static final Path FAITHFUL_THEME =
        Paths.get("src", "main", "resources", "themes", "faithful32x");

    @Test
    void rendersValidHeadlessPngAndWritesPreview() throws Exception {
        Theme theme = ThemeLoader.load(DEFAULT_THEME);
        InventorySnapshot snapshot = new MockInventoryDataSource("renderer-test").createSnapshot("MockPlayer");
        RenderResult result = new Java2DInventoryRenderer(theme).render(snapshot);

        assertEquals("image/png", result.getMimeType());
        assertEquals(704, result.getWidth());
        assertEquals(600, result.getHeight());
        assertTrue(result.getByteSize() > 10_000);
        assertTrue(result.getByteSize() < 4 * 1024 * 1024);
        byte[] bytes = result.getBytes();
        assertEquals((byte) 0x89, bytes[0]);
        assertEquals((byte) 'P', bytes[1]);
        assertEquals((byte) 'N', bytes[2]);
        assertEquals((byte) 'G', bytes[3]);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(decoded);
        assertEquals(704, decoded.getWidth());
        assertEquals(600, decoded.getHeight());

        Path preview = Paths.get("build", "rendered-test-output", "mock-inventory-default.png");
        Files.createDirectories(preview.getParent());
        Files.write(preview, bytes);
    }

    @Test
    void rendersFaithfulThemeWithNamespacedTexturesAndWritesPrimaryPreview() throws Exception {
        Theme theme = ThemeLoader.load(FAITHFUL_THEME);
        assertEquals("faithful32x", theme.getId());
        assertTrue(theme.getAssetPackVersion().contains("26.2"));
        assertFalse(theme.isDrawTitle());
        assertFalse(theme.isDrawSlotBackgrounds());
        assertTrue(theme.isNearestNeighborTextures());

        for (String material : new String[] {
            "stone", "diamond", "golden_apple", "bread", "cooked_beef",
            "diamond_sword", "diamond_pickaxe", "firework_rocket",
            "diamond_helmet", "diamond_chestplate", "diamond_leggings",
            "diamond_boots", "shield", "totem_of_undying"
        }) {
            TextureResolver.ResolvedTexture texture =
                theme.getTextures().resolve(ItemSnapshot.basic("minecraft:" + material, 1));
            assertFalse(texture.isFallback(), material + " should resolve from the Faithful namespace");
        }
        assertTrue(
            theme.getTextures().resolve(ItemSnapshot.basic("minecraft:mystery_relic", 1)).isFallback()
        );

        InventorySnapshot snapshot = new MockInventoryDataSource("renderer-test").createSnapshot("MockPlayer");
        RenderResult result = new Java2DInventoryRenderer(theme).render(snapshot);
        assertEquals(704, result.getWidth());
        assertEquals(664, result.getHeight());
        assertTrue(result.getByteSize() > 10_000);
        assertTrue(result.getByteSize() < 4 * 1024 * 1024);

        Path preview = Paths.get("build", "rendered-test-output", "mock-inventory-faithful32x.png");
        Files.createDirectories(preview.getParent());
        Files.write(preview, result.getBytes());
    }

    @Test
    void faithfulBackgroundUsesCreativeStyleBlankCraftingPanel() throws Exception {
        Theme theme = ThemeLoader.load(FAITHFUL_THEME);
        BufferedImage background = theme.getBackground();
        int panel = background.getRGB(380, 60);
        for (int y = 70; y < 210; y++) for (int x = 390; x < 682; x++) {
            assertEquals(panel, background.getRGB(x, y),
                "survival crafting controls must be absent at " + x + ',' + y);
        }
        assertNotEquals(panel, background.getRGB(390, 334),
            "removing crafting controls must not erase the storage grid");
        assertNotEquals(panel, background.getRGB(306, 248),
            "removing crafting controls must not erase the offhand slot");
    }

    @Test
    void rendersReportedBlueEndermanSkinInCreativeStyleInventory() throws Exception {
        VanillaImportedAssetProvider local = VanillaImportedAssetProvider.open(
            Paths.get("data", "imported-assets", "vanilla")
        );
        Theme theme = local.isAvailable() ? ThemeLoader.load(FAITHFUL_THEME, local) : ThemeLoader.load(FAITHFUL_THEME);
        java.awt.Rectangle area = theme.getLayout().getPlayerPreview();
        BufferedImage texture = ImageIO.read(Paths.get(
            "src", "test-fixtures", "skins", "blue-enderman-avatar.png"
        ).toFile());
        BufferedImage preview = new PlayerModelRenderer(area.width, area.height).render(
            new PlayerSkin(texture, "blue-enderman-pv8", "REPORTED_SKIN_FIXTURE", false)
        );
        InventorySnapshot snapshot = new MockInventoryDataSource("blue-enderman-pv8")
            .createSnapshot("BlueEnderman");
        RenderResult result = new Java2DInventoryRenderer(theme).render(snapshot, preview);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.getBytes()));

        assertEquals(704, decoded.getWidth());
        assertEquals(664, decoded.getHeight());
        assertEquals(decoded.getRGB(380, 60), decoded.getRGB(500, 120),
            "the reported-skin proof must retain the blank creative-style crafting panel");

        Path output = Paths.get(
            "data", "visual-audit", "26.1.2-B1B315857266-MB7-PD1337875",
            "player-head-shell", "inventory-pv8-blue-enderman-creative-layout.png"
        );
        Files.createDirectories(output.getParent());
        Files.write(output, result.getBytes());
    }

    @Test
    void compositesPlayerPreviewInsideFaithfulCharacterArea() throws Exception {
        VanillaImportedAssetProvider local = VanillaImportedAssetProvider.open(
            Paths.get("data", "imported-assets", "vanilla")
        );
        Theme theme = local.isAvailable() ? ThemeLoader.load(FAITHFUL_THEME, local) : ThemeLoader.load(FAITHFUL_THEME);
        InventorySnapshot snapshot = new MockInventoryDataSource("renderer-test").createSnapshot("MockPlayer");
        java.awt.Rectangle area = theme.getLayout().getPlayerPreview();
        BufferedImage preview = new PlayerModelRenderer(area.width, area.height)
            .render(new DefaultPlayerSkinProvider().getFallback());
        RenderResult result = new Java2DInventoryRenderer(theme).render(snapshot, preview);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.getBytes()));

        assertNotNull(area);
        assertEquals(area.width, preview.getWidth());
        assertEquals(area.height, preview.getHeight());
        int changed = 0;
        BufferedImage background = theme.getBackground();
        for (int y = area.y; y < area.y + area.height; y++) {
            for (int x = area.x; x < area.x + area.width; x++) {
                if (decoded.getRGB(x, y) != background.getRGB(x, y)) changed++;
            }
        }
        assertTrue(changed > 1000, "player preview must replace the empty black character area");
        int mattePixel = decoded.getRGB(area.x + 12, area.y + 12);
        assertNotEquals(background.getRGB(area.x + 12, area.y + 12), mattePixel);
        assertTrue(luminance(mattePixel) >= 30, "matte panel must separate dark skins from the black theme background");

        Path output = Paths.get(
            "build", "rendered-test-output",
            local.isAvailable() ? "inventory-1.10.0-mb6-pv3-player-preview.png" : "inventory-3d-player-preview-fallback.png"
        );
        Files.createDirectories(output.getParent());
        Files.write(output, result.getBytes());
    }

    @Test
    void offlineSnapshotRendersTimestampBadgeWithoutChangingSnapshotModel() throws Exception {
        Theme theme = ThemeLoader.load(FAITHFUL_THEME);
        InventorySnapshot snapshot = new MockInventoryDataSource("renderer-test").createSnapshot("Steve");
        java.awt.Rectangle area = theme.getLayout().getPlayerPreview();
        BufferedImage preview = new PlayerModelRenderer(area.width, area.height)
            .render(new DefaultPlayerSkinProvider().getFallback());
        Java2DInventoryRenderer renderer = new Java2DInventoryRenderer(theme);
        BufferedImage realtime = ImageIO.read(new ByteArrayInputStream(
            renderer.render(snapshot, preview, InventoryRenderMetadata.realtime(snapshot.getCapturedAt())).getBytes()
        ));
        RenderResult offlineResult = renderer.render(
            snapshot, preview, InventoryRenderMetadata.offline(snapshot.getCapturedAt())
        );
        BufferedImage offline = ImageIO.read(new ByteArrayInputStream(offlineResult.getBytes()));
        int changed = 0;
        for (int y = area.y; y < area.y + 28; y++) for (int x = area.x; x < area.x + area.width; x++)
            if (realtime.getRGB(x, y) != offline.getRGB(x, y)) changed++;
        assertTrue(changed > 100, "offline timestamp badge must be visible inside the preview panel");
        Path output = Paths.get("build", "rendered-test-output", "inventory-1.10.0-offline-snapshot.png");
        Files.createDirectories(output.getParent());
        Files.write(output, offlineResult.getBytes());
    }

    @Test
    void offlineSnapshotLabelIsPortableAsciiText() {
        InventoryRenderMetadata metadata = InventoryRenderMetadata.offline(
            java.time.Instant.parse("2026-08-24T12:41:00Z")
        );
        String label = Java2DInventoryRenderer.offlineSnapshotLabel(metadata);
        assertTrue(label.startsWith("Offline Snapshot · "));
        assertFalse(label.contains("离线快照"));
    }

    @Test
    void unknownMaterialUsesFallbackWithoutAbortingRender() {
        Theme theme = ThemeLoader.load(DEFAULT_THEME);
        Theme faithful = ThemeLoader.load(FAITHFUL_THEME);
        AtomicInteger reports = new AtomicInteger();
        theme.getTextures().setMissingReporter(material -> reports.incrementAndGet());
        TextureResolver.ResolvedTexture known = theme.getTextures().resolve(ItemSnapshot.basic("minecraft:stone", 1));
        TextureResolver.ResolvedTexture missing = theme.getTextures().resolve(ItemSnapshot.basic("minecraft:not_present", 1));
        TextureResolver.ResolvedTexture chest = faithful.getTextures().resolve(ItemSnapshot.basic("minecraft:chest", 1));
        theme.getTextures().resolve(ItemSnapshot.basic("minecraft:not_present", 1));
        assertFalse(known.isFallback());
        assertTrue(missing.isFallback());
        assertNotNull(missing.getImage());
        assertEquals(TextureResolver.Source.EXPLICIT_OVERRIDE, chest.getSource());
        assertFalse(chest.isFallback());
        assertEquals(1, reports.get());
    }

    @Test
    void unsafeNamespacedTexturePathUsesFallback() {
        Theme theme = ThemeLoader.load(FAITHFUL_THEME);
        TextureResolver.ResolvedTexture unsafe =
            theme.getTextures().resolve(ItemSnapshot.basic("minecraft:../diamond", 1));
        assertTrue(unsafe.isFallback());
    }

    @Test
    void missingThemeAndInvalidLayoutAreRejected(@TempDir Path temp) throws Exception {
        assertThrows(IllegalArgumentException.class, () -> ThemeLoader.load(temp.resolve("missing")));

        Path broken = temp.resolve("broken");
        copyTheme(DEFAULT_THEME, broken);
        Path layout = broken.resolve("layout.yml");
        String yaml = new String(Files.readAllBytes(layout), StandardCharsets.UTF_8)
            .replace("width: 704", "width: 100");
        Files.write(layout, yaml.getBytes(StandardCharsets.UTF_8));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ThemeLoader.load(broken));
        assertTrue(error.getMessage().contains("outside") || error.getMessage().contains("overlap"));
    }

    @Test
    void deployedFaithfulLayoutWithoutPreviewKeyGetsCompatibleCharacterArea(@TempDir Path temp) throws Exception {
        Path legacy = temp.resolve("legacy-faithful");
        copyTheme(FAITHFUL_THEME, legacy);
        org.bukkit.configuration.file.YamlConfiguration yaml =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(legacy.resolve("layout.yml").toFile());
        yaml.set("player-preview", null);
        yaml.save(legacy.resolve("layout.yml").toFile());

        Theme theme = ThemeLoader.load(legacy);
        java.awt.Rectangle preview = theme.getLayout().getPlayerPreview();
        assertNotNull(preview);
        assertEquals(new java.awt.Rectangle(102, 30, 198, 283), preview);
    }

    private static void copyTheme(Path source, Path target) throws Exception {
        Files.walk(source).forEach(path -> {
            try {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
    }

    private static int luminance(int argb) {
        int red = (argb >>> 16) & 0xff;
        int green = (argb >>> 8) & 0xff;
        int blue = argb & 0xff;
        return (red * 299 + green * 587 + blue * 114) / 1000;
    }
}
