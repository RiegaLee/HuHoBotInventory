package cn.huohuas001.huhobot.inventory.skin;

import cn.huohuas001.huhobot.inventory.armor.ArmorVisualDescriptor;
import cn.huohuas001.huhobot.inventory.armor.EquipmentAssetResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import cn.huohuas001.huhobot.inventory.datasource.MockInventoryDataSource;
import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;
import cn.huohuas001.huhobot.inventory.model.InventorySlot;
import cn.huohuas001.huhobot.inventory.model.ItemSnapshot;
import cn.huohuas001.huhobot.inventory.model.SlotType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSkinRendererTest {
    @Test
    void rendersStandardSkinAndOuterLayerToStablePreview() throws Exception {
        BufferedImage base = testSkin(false);
        BufferedImage outer = testSkin(true);
        PlayerSkinRenderer renderer = new PlayerSkinRenderer();
        BufferedImage basePreview = renderer.render(new PlayerSkin(base, "base", "TEST", false));
        BufferedImage outerPreview = renderer.render(new PlayerSkin(outer, "outer", "TEST", false));

        assertEquals(128, outerPreview.getWidth());
        assertEquals(256, outerPreview.getHeight());
        assertTrue(visiblePixels(outerPreview) > 5000);
        assertTrue((basePreview.getRGB(36, 24) >>> 24) != 0, "head top-left must remain axis-aligned");
        assertTrue((basePreview.getRGB(91, 24) >>> 24) != 0, "head top-right must remain axis-aligned");
        assertEquals(0, basePreview.getRGB(35, 24) >>> 24, "head must not lean outside its straight bounds");
        assertNotEquals(pixelHash(basePreview), pixelHash(outerPreview), "hat/jacket/sleeves/pants must be rendered");

        Path output = java.nio.file.Paths.get("build", "rendered-test-output");
        Files.createDirectories(output);
        javax.imageio.ImageIO.write(basePreview, "png", output.resolve("skin-standard-64x64.png").toFile());
        javax.imageio.ImageIO.write(outerPreview, "png", output.resolve("skin-outer-layer-64x64.png").toFile());
        javax.imageio.ImageIO.write(
            renderer.render(new DefaultPlayerSkinProvider().getFallback()),
            "png",
            output.resolve("skin-provider-unavailable-fallback.png").toFile()
        );
    }

    @Test
    void providerFailureUsesLocalFallbackAndCachesPreview(@TempDir Path temp) throws Exception {
        PlayerSkinProvider failing = player -> { throw new IllegalStateException("provider unavailable"); };
        PlayerPreviewService service = new PlayerPreviewService(true, failing, temp, Logger.getAnonymousLogger());
        InventorySnapshot snapshot = new MockInventoryDataSource("test").createSnapshot("MockPlayer");

        BufferedImage first = service.preview(snapshot);
        BufferedImage second = service.preview(snapshot);

        assertEquals(pixelHash(first), pixelHash(second));
        assertTrue(Files.isDirectory(temp.resolve("previews")));
        try (java.util.stream.Stream<Path> files = Files.list(temp.resolve("previews"))) {
            assertEquals(1L, files.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void pv8SizedCacheInvalidatesImmediatelyWhenEquipmentTrimOrGlintChanges(@TempDir Path temp) throws Exception {
        PlayerSkin fixedSkin = new PlayerSkin(testSkin(true), "fixed-skin", "TEST", false);
        PlayerSkinProvider provider = player -> Optional.of(fixedSkin);
        EquipmentAssetResolver assets = new EquipmentAssetResolver(
            java.nio.file.Paths.get("src", "armor-assets")
        );
        PlayerPreviewService service = new PlayerPreviewService(
            true, provider, temp, Logger.getAnonymousLogger(), "3d", assets, 198, 283
        );
        InventorySnapshot base = new MockInventoryDataSource("test").createSnapshot("MockPlayer");
        ArmorVisualDescriptor plain = new ArmorVisualDescriptor(
            ArmorVisualDescriptor.Slot.HEAD, "minecraft:diamond_helmet", "minecraft:diamond",
            null, null, null, false
        );
        ArmorVisualDescriptor trimmed = new ArmorVisualDescriptor(
            ArmorVisualDescriptor.Slot.HEAD, "minecraft:diamond_helmet", "minecraft:diamond",
            "minecraft:spire", "minecraft:redstone", null, false
        );
        ArmorVisualDescriptor enchanted = new ArmorVisualDescriptor(
            ArmorVisualDescriptor.Slot.HEAD, "minecraft:diamond_helmet", "minecraft:diamond",
            "minecraft:spire", "minecraft:redstone", null, true
        );

        BufferedImage noArmor = service.preview(base);
        BufferedImage plainArmor = service.preview(withHelmet(base, plain));
        BufferedImage trimArmor = service.preview(withHelmet(base, trimmed));
        BufferedImage glintArmor = service.preview(withHelmet(base, enchanted));
        assertEquals(198, glintArmor.getWidth());
        assertEquals(283, glintArmor.getHeight());
        assertNotEquals(pixelHash(noArmor), pixelHash(plainArmor));
        assertNotEquals(pixelHash(plainArmor), pixelHash(trimArmor));
        assertNotEquals(pixelHash(trimArmor), pixelHash(glintArmor));
        assertEquals(pixelHash(glintArmor), pixelHash(service.preview(withHelmet(base, enchanted))));
        try (java.util.stream.Stream<Path> files = Files.list(temp.resolve("previews"))) {
            assertEquals(4L, files.filter(Files::isRegularFile).count());
        }
        try (java.util.stream.Stream<Path> files = Files.list(temp.resolve("previews"))) {
            assertTrue(files.allMatch(path -> path.getFileName().toString().startsWith("pv8-198x283-")));
        }
    }

    private static InventorySnapshot withHelmet(
        InventorySnapshot base, ArmorVisualDescriptor descriptor
    ) {
        List<InventorySlot> armor = new ArrayList<InventorySlot>(base.getArmor());
        ItemSnapshot item = new ItemSnapshot(
            descriptor.getBaseMaterialKey(), 1, 0, 0, null, null, descriptor.hasGlint(), null, descriptor
        );
        for (int index = 0; index < armor.size(); index++) {
            if (armor.get(index).getSlotType() == SlotType.ARMOR_HEAD) {
                armor.set(index, InventorySlot.of(SlotType.ARMOR_HEAD, 0, item));
            }
        }
        return new InventorySnapshot(
            InventorySnapshot.CURRENT_SCHEMA_VERSION,
            base.getPlayerUuid(), base.getPlayerName(), base.getCapturedAt(), base.getSourceServer(),
            descriptor.visualKey(), base.getStorage(), base.getHotbar(), armor, base.getOffhand()
        );
    }

    static BufferedImage testSkin(boolean outerLayer) {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        fill(image, 8, 8, 8, 8, new Color(210, 150, 110));
        fill(image, 20, 20, 8, 12, new Color(30, 120, 180));
        fill(image, 44, 20, 4, 12, new Color(210, 150, 110));
        fill(image, 36, 52, 4, 12, new Color(210, 150, 110));
        fill(image, 4, 20, 4, 12, new Color(45, 55, 100));
        fill(image, 20, 52, 4, 12, new Color(45, 55, 100));
        if (outerLayer) {
            fill(image, 40, 8, 8, 8, new Color(240, 40, 160, 160));
            fill(image, 20, 36, 8, 12, new Color(40, 230, 90, 140));
            fill(image, 44, 36, 4, 12, new Color(250, 210, 30, 150));
            fill(image, 52, 52, 4, 12, new Color(250, 210, 30, 150));
            fill(image, 4, 36, 4, 12, new Color(150, 70, 240, 150));
            fill(image, 4, 52, 4, 12, new Color(150, 70, 240, 150));
        }
        return image;
    }

    private static void fill(BufferedImage image, int x, int y, int width, int height, Color color) {
        for (int py = y; py < y + height; py++) {
            for (int px = x; px < x + width; px++) image.setRGB(px, py, color.getRGB());
        }
    }

    private static int visiblePixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) if ((image.getRGB(x, y) >>> 24) != 0) count++;
        }
        return count;
    }

    private static long pixelHash(BufferedImage image) {
        long value = 1125899906842597L;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) value = value * 31 + image.getRGB(x, y);
        }
        return value;
    }
}
