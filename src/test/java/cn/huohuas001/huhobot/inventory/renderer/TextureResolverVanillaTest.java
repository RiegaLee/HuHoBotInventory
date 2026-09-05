package cn.huohuas001.huhobot.inventory.renderer;

import cn.huohuas001.huhobot.inventory.asset.VanillaImportedAssetProvider;
import cn.huohuas001.huhobot.inventory.model.ItemSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureResolverVanillaTest {
    @Test
    void selectsStableTrappedChestSeasonalVariantWithoutDailyCacheKeys(@TempDir Path temp) throws Exception {
        Path theme = temp.resolve("theme");
        Path overrides = theme.resolve("overrides/items");
        Path variants = theme.resolve("special-variants");
        Path textures = theme.resolve("assets");
        Path fallback = theme.resolve("unknown.png");
        write(overrides.resolve("minecraft/trapped_chest.png"), new Color(130, 70, 35, 255));
        write(variants.resolve("minecraft/trapped_chest_christmas.png"), new Color(210, 35, 45, 255));
        write(fallback, new Color(180, 30, 220, 255));

        MutableClock clock = new MutableClock(Instant.parse("2026-12-23T12:00:00Z"), ZoneOffset.UTC);
        TextureResolver resolver = new TextureResolver(
            null, overrides, variants, textures, fallback,
            VanillaImportedAssetProvider.disabled(temp, "not needed"), clock
        );
        ItemSnapshot trapped = ItemSnapshot.basic("minecraft:trapped_chest", 1);

        TextureResolver.ResolvedTexture normal = resolver.resolve(trapped);
        assertEquals(TextureResolver.Source.EXPLICIT_OVERRIDE, normal.getSource());
        assertEquals(new Color(130, 70, 35, 255).getRGB(), normal.getImage().getRGB(0, 0));
        assertEquals("NORMAL", TextureResolver.seasonalVariant(clock));

        clock.setInstant(Instant.parse("2026-12-25T12:00:00Z"));
        TextureResolver.ResolvedTexture christmas = resolver.resolve(trapped);
        assertEquals(TextureResolver.Source.GENERATED_SPECIAL_STATIC, christmas.getSource());
        assertEquals(new Color(210, 35, 45, 255).getRGB(), christmas.getImage().getRGB(0, 0));
        assertEquals("CHRISTMAS", TextureResolver.seasonalVariant(clock));

        clock.setInstant(Instant.parse("2026-12-27T12:00:00Z"));
        TextureResolver.ResolvedTexture afterChristmas = resolver.resolve(trapped);
        assertEquals(TextureResolver.Source.EXPLICIT_OVERRIDE, afterChristmas.getSource());
        assertTrue(afterChristmas.getImage() == normal.getImage(), "NORMAL must reuse one stable cached asset");
        assertEquals("NORMAL", TextureResolver.seasonalVariant(clock));
    }

    @Test
    void keepsThemeOverrideThenUsesVanillaThenUnknown(@TempDir Path temp) throws Exception {
        Path theme = temp.resolve("theme");
        Path textures = theme.resolve("assets");
        Path overrides = theme.resolve("overrides/items");
        Path customOverrides = temp.resolve("custom/overrides/items");
        Path fallback = theme.resolve("unknown.png");
        write(overrides.resolve("minecraft/shield.png"), new Color(120, 70, 30, 255));
        write(overrides.resolve("minecraft/chest.png"), new Color(140, 80, 25, 255));
        write(customOverrides.resolve("minecraft/chest.png"), new Color(10, 200, 40, 255));
        write(textures.resolve("minecraft/shield.png"), new Color(120, 70, 30, 255));
        write(textures.resolve("minecraft/diamond.png"), new Color(20, 220, 230, 255));
        write(textures.resolve("minecraft/stone.png"), new Color(90, 90, 90, 255));
        write(fallback, new Color(180, 30, 220, 255));

        Path cache = temp.resolve("vanilla");
        Path current = cache.resolve("test-cache");
        write(current.resolve("generated-icons/minecraft/shield.png"), Color.GRAY);
        write(current.resolve("generated-icons/minecraft/trial_key.png"), Color.ORANGE);
        write(current.resolve("generated-icons/minecraft/stone.png"), new Color(140, 140, 140, 255));
        Files.write(cache.resolve("current-version.txt"), Arrays.asList("test-cache"), StandardCharsets.UTF_8);
        Files.write(
            current.resolve("metadata.json"),
            ("{\"minecraftVersion\":\"test\",\"generatedIcons\":3,\"totalDefinitions\":4," +
                "\"textureResourcePack\":\"Faithful.zip\"}\n")
                .getBytes(StandardCharsets.UTF_8)
        );
        Files.write(
            current.resolve("render-paths.tsv"),
            Arrays.asList(
                "# Material\tRenderPath", "minecraft:trial_key\tBLOCK_MODEL", "minecraft:stone\tBLOCK_MODEL"
            ),
            StandardCharsets.UTF_8
        );
        Files.write(
            current.resolve("unresolved.txt"),
            Arrays.asList("# Material\tReason", "minecraft:bundle\tSPECIAL_RENDERER"),
            StandardCharsets.UTF_8
        );

        VanillaImportedAssetProvider vanilla = VanillaImportedAssetProvider.open(cache);
        assertTrue(vanilla.isAvailable());
        TextureResolver resolver = new TextureResolver(customOverrides, overrides, textures, fallback, vanilla);
        AtomicInteger vanillaReports = new AtomicInteger();
        AtomicInteger unknownReports = new AtomicInteger();
        AtomicInteger specialReports = new AtomicInteger();
        AtomicReference<TextureResolver.ResolutionTrace> stoneTrace = new AtomicReference<TextureResolver.ResolutionTrace>();
        resolver.setResolutionReporter(trace -> {
            if (trace.getSource() == TextureResolver.Source.GENERATED_CACHE) vanillaReports.incrementAndGet();
            if (trace.getSource() == TextureResolver.Source.UNKNOWN) unknownReports.incrementAndGet();
            if (trace.getSource() == TextureResolver.Source.SPECIAL_UNSUPPORTED) specialReports.incrementAndGet();
            if ("minecraft:stone".equals(trace.getMaterialKey())) stoneTrace.set(trace);
        });

        TextureResolver.ResolvedTexture shield = resolver.resolve(ItemSnapshot.basic("minecraft:shield", 1));
        TextureResolver.ResolvedTexture chest = resolver.resolve(ItemSnapshot.basic("minecraft:chest", 1));
        TextureResolver.ResolvedTexture trialKey = resolver.resolve(ItemSnapshot.basic("minecraft:trial_key", 1));
        TextureResolver.ResolvedTexture stone = resolver.resolve(ItemSnapshot.basic("minecraft:stone", 1));
        TextureResolver.ResolvedTexture diamond = resolver.resolve(ItemSnapshot.basic("minecraft:diamond", 1));
        TextureResolver.ResolvedTexture missing = resolver.resolve(ItemSnapshot.basic("minecraft:not_present", 1));
        TextureResolver.ResolvedTexture special = resolver.resolve(ItemSnapshot.basic("minecraft:bundle", 1));
        resolver.resolve(ItemSnapshot.basic("minecraft:trial_key", 1));
        resolver.resolve(ItemSnapshot.basic("minecraft:not_present", 1));

        assertEquals(TextureResolver.Source.EXPLICIT_OVERRIDE, shield.getSource());
        assertEquals(TextureResolver.Source.CUSTOM_OVERRIDE, chest.getSource());
        assertEquals(TextureResolver.Source.GENERATED_CACHE, trialKey.getSource());
        assertEquals(TextureResolver.Source.GENERATED_CACHE, stone.getSource());
        assertEquals(TextureResolver.Source.LEGACY_STATIC, diamond.getSource());
        assertEquals(TextureResolver.Source.UNKNOWN, missing.getSource());
        assertEquals(TextureResolver.Source.SPECIAL_UNSUPPORTED, special.getSource());
        assertFalse(shield.isFallback());
        assertFalse(trialKey.isFallback());
        assertTrue(missing.isFallback());
        assertTrue(special.isFallback());
        assertEquals(2, vanillaReports.get());
        assertEquals(1, unknownReports.get());
        assertEquals(1, specialReports.get());
        assertEquals("BLOCK", stoneTrace.get().getClassification());
        assertEquals("BLOCK_MODEL", stoneTrace.get().getRenderPath());
        assertTrue(stoneTrace.get().getTextureSource().startsWith("RESOURCE_PACK:"));
        assertTrue(stoneTrace.get().getFinalFile().replace('\\', '/').endsWith("generated-icons/minecraft/stone.png"));

        TextureResolver.Coverage coverage = resolver.coverage(Arrays.asList(
            "minecraft:shield", "minecraft:chest", "minecraft:diamond", "minecraft:trial_key",
            "minecraft:stone", "minecraft:bundle", "minecraft:not_present"
        ));
        assertEquals(7, coverage.getTotal());
        assertEquals(2, coverage.getExplicitOverrides());
        assertEquals(2, coverage.getVanilla());
        assertEquals(1, coverage.getLegacyStatic());
        assertEquals(1, coverage.getSpecialUnsupported());
        assertEquals(Arrays.asList("minecraft:not_present"), coverage.getUnknown());
        assertTrue(coverage.getResolutionPaths().contains("minecraft:shield\tEXPLICIT_OVERRIDE"));
        assertTrue(coverage.getResolutionPaths().contains("minecraft:chest\tCUSTOM_OVERRIDE"));
        assertTrue(coverage.getResolutionPaths().contains("minecraft:trial_key\tBLOCK_MODEL"));
        assertTrue(coverage.getResolutionPaths().contains("minecraft:stone\tBLOCK_MODEL"));
        assertTrue(coverage.getResolutionPaths().contains("minecraft:diamond\tLEGACY_STATIC"));
        assertTrue(coverage.getResolutionPaths().contains("minecraft:not_present\tUNKNOWN"));
        assertTrue(coverage.getResolutionPaths().contains("minecraft:bundle\tSPECIAL_UNSUPPORTED"));
    }

    private static void write(Path path, Color color) throws Exception {
        Files.createDirectories(path.getParent());
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, color.getRGB());
        }
        ImageIO.write(image, "png", path.toFile());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void setInstant(Instant instant) { this.instant = instant; }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new MutableClock(instant, zone); }
        @Override public Instant instant() { return instant; }
    }
}
