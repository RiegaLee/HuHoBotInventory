package cn.huohuas001.huhobot.inventory.skin;

import cn.huohuas001.huhobot.inventory.armor.ArmorEquipmentSet;
import cn.huohuas001.huhobot.inventory.armor.ArmorVisualDescriptor;
import cn.huohuas001.huhobot.inventory.armor.EquipmentAssetResolver;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerModelRendererTest {
    @Test
    void usesCanonicalMinecraftGeometryAndPv8GlintConstants() {
        assertEquals("pv8", PlayerModelRenderer.CACHE_VERSION);
        assertEquals(8, PlayerModelRenderer.HEAD_SIZE);
        assertEquals(8, PlayerModelRenderer.BODY_WIDTH);
        assertEquals(12, PlayerModelRenderer.BODY_HEIGHT);
        assertEquals(4, PlayerModelRenderer.BODY_DEPTH);
        assertEquals(4, PlayerModelRenderer.CLASSIC_ARM_WIDTH);
        assertEquals(3, PlayerModelRenderer.SLIM_ARM_WIDTH);
        assertEquals(12, PlayerModelRenderer.ARM_HEIGHT);
        assertEquals(4, PlayerModelRenderer.LEG_WIDTH);
        assertEquals(12, PlayerModelRenderer.LEG_HEIGHT);
        assertEquals(32, PlayerModelRenderer.MODEL_HEIGHT);
        assertEquals(16, PlayerModelRenderer.BODY_WIDTH + 2 * PlayerModelRenderer.CLASSIC_ARM_WIDTH);
        assertEquals(14, PlayerModelRenderer.BODY_WIDTH + 2 * PlayerModelRenderer.SLIM_ARM_WIDTH);
        assertEquals(0.5, PlayerModelRenderer.HEAD_OUTER_EXPANSION);
        assertEquals(0.25, PlayerModelRenderer.BODY_OUTER_EXPANSION);
        assertEquals(1.0, PlayerModelRenderer.OUTER_ARMOR_EXPANSION);
        assertEquals(0.5, PlayerModelRenderer.INNER_ARMOR_EXPANSION);
        assertEquals(0.16, PlayerModelRenderer.ARMOR_GLINT_UV_SCALE);
        assertEquals(Math.toRadians(10.0), PlayerModelRenderer.ARMOR_GLINT_ROTATION);
        assertEquals(0.75, PlayerModelRenderer.ARMOR_GLINT_STRENGTH);
        assertEquals(0xffffff, PlayerModelRenderer.ARMOR_GLINT_MODULATOR,
            "leather dye must not attenuate or recolor the independent armor glint pass");
        assertEquals(0.6, PlayerModelRenderer.FACE_EDGE_BLEED,
            "shared projected face edges need subpixel overlap to avoid transparent cracks");
    }

    @Test
    void rendersDirectlyAtFaithfulFinalPreviewSizeWithoutIntermediateResample() throws Exception {
        int targetWidth = 198;
        int targetHeight = 283;
        PlayerSkin skin = skin("pv8-direct", false, false, true, new Color(42, 58, 72));
        EquipmentAssetResolver assets = new EquipmentAssetResolver(Paths.get("src", "armor-assets"));
        ArmorEquipmentSet equipment = fullSet("netherite", "coast", "gold", null, true);

        BufferedImage legacy = new PlayerModelRenderer().render(skin, equipment, assets);
        BufferedImage resampled = fitPreview(legacy, targetWidth, targetHeight);
        PlayerModelRenderer directRenderer = new PlayerModelRenderer(targetWidth, targetHeight);
        BufferedImage direct = directRenderer.render(skin, equipment, assets);

        assertEquals(targetWidth, directRenderer.getWidth());
        assertEquals(targetHeight, directRenderer.getHeight());
        assertEquals(targetWidth, direct.getWidth());
        assertEquals(targetHeight, direct.getHeight());
        assertNotEquals(hash(resampled), hash(direct),
            "PV8 must rasterize at the final theme size instead of scaling the old 128x256 preview");
        Bounds directBounds = bounds(direct);
        assertTrue(directBounds.top >= 9, "direct head must not be clipped");
        assertTrue(directBounds.bottom < targetHeight - 5, "direct feet and shadow must not be clipped");
        assertTrue(directBounds.centerX() >= 94 && directBounds.centerX() <= 104,
            "direct model must remain centered");

        BufferedImage comparison = new BufferedImage(targetWidth * 2, targetHeight + 28, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = comparison.createGraphics();
        try {
            graphics.setColor(new Color(43, 54, 62));
            graphics.fillRect(0, 0, comparison.getWidth(), comparison.getHeight());
            graphics.drawImage(resampled, 0, 28, null);
            graphics.drawImage(direct, targetWidth, 28, null);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            graphics.drawString("Before: 128x256 -> 198x283", 6, 19);
            graphics.drawString("After: direct 198x283", targetWidth + 6, 19);
        } finally {
            graphics.dispose();
        }
        Path output = Paths.get(
            "data", "visual-audit", "26.1.2-B1B315857266-MB7-PD1337875",
            "player-preview-hd", "player-preview-pv6-resampled-vs-pv8-direct.png"
        );
        Files.createDirectories(output.getParent());
        ImageIO.write(comparison, "png", output.toFile());
    }

    @Test
    void rendersClientSizedSecondLayerForEveryModernSkinBodyPartAndWritesProof() throws Exception {
        int width = 198;
        int height = 283;
        PlayerModelRenderer renderer = new PlayerModelRenderer(width, height);
        BufferedImage base = renderer.render(
            skin("outer-proof-base", false, false, false, new Color(42, 136, 150))
        );
        BufferedImage outer = renderer.render(
            skin("outer-proof-enabled", false, false, true, new Color(42, 136, 150))
        );
        BufferedImage difference = difference(base, outer);

        assertNotEquals(hash(base), hash(outer));
        assertTrue(visible(difference) > 2500,
            "hat, jacket, both sleeves and both trouser legs must change the final projection");
        assertTrue(moreOpaquePixels(base, outer) > 50,
            "expanded second layer must add pixels beyond the base skin faces");

        BufferedImage proof = new BufferedImage(width * 3, height + 28, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = proof.createGraphics();
        try {
            graphics.setColor(new Color(43, 54, 62));
            graphics.fillRect(0, 0, proof.getWidth(), proof.getHeight());
            graphics.drawImage(base, 0, 28, null);
            graphics.drawImage(outer, width, 28, null);
            graphics.drawImage(difference, width * 2, 28, null);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            graphics.drawString("Base skin", 6, 19);
            graphics.drawString("Second layer: 0.5 / 0.25", width + 6, 19);
            graphics.drawString("Changed projection pixels", width * 2 + 6, 19);
        } finally {
            graphics.dispose();
        }
        Path output = Paths.get(
            "data", "visual-audit", "26.1.2-B1B315857266-MB7-PD1337875",
            "player-outer-layer", "player-skin-second-layer-proof.png"
        );
        Files.createDirectories(output.getParent());
        ImageIO.write(proof, "png", output.toFile());
    }

    @Test
    void preservesSparseHeadOuterLayerFromReportedBlueEndermanSkin() throws Exception {
        BufferedImage texture = ImageIO.read(Paths.get(
            "src", "test-fixtures", "skins", "blue-enderman-avatar.png"
        ).toFile());
        assertEquals(64, texture.getWidth());
        assertEquals(64, texture.getHeight());

        PlayerModelRenderer renderer = new PlayerModelRenderer(198, 283);
        BufferedImage rendered = renderer.render(new PlayerSkin(
            texture, "blue-enderman-head-shell", "REPORTED_SKIN_FIXTURE", false
        ));

        int exactBluePixels = pixelsWithRgb(rendered, 0x378ce3);
        assertTrue(exactBluePixels >= 300,
            "the one-pixel blue band on the hat top must keep its projected face and voxel side walls");

        Path output = Paths.get(
            "data", "visual-audit", "26.1.2-B1B315857266-MB7-PD1337875",
            "player-head-shell", "blue-enderman-skin-pv8.png"
        );
        Files.createDirectories(output.getParent());
        ImageIO.write(rendered, "png", output.toFile());
    }

    @Test
    void rendersClassicSlimLegacyOuterDarkLightAndGoldenReference() throws Exception {
        PlayerModelRenderer renderer = new PlayerModelRenderer();
        PlayerSkin classic = skin("classic", false, false, false, new Color(42, 136, 150));
        PlayerSkin slim = skin("slim", true, false, false, new Color(150, 82, 135));
        PlayerSkin legacy = skin("legacy", false, true, false, new Color(80, 130, 62));
        PlayerSkin outer = skin("outer", false, false, true, new Color(42, 136, 150));
        PlayerSkin dark = skin("dark", false, false, true, new Color(15, 17, 21));
        PlayerSkin light = skin("light", false, false, true, new Color(225, 225, 218));
        PlayerSkin fallback = new DefaultPlayerSkinProvider().getFallback();

        BufferedImage classicImage = renderer.render(classic);
        BufferedImage slimImage = renderer.render(slim);
        BufferedImage legacyImage = renderer.render(legacy);
        BufferedImage outerImage = renderer.render(outer);
        BufferedImage darkImage = renderer.render(dark);
        BufferedImage lightImage = renderer.render(light);
        BufferedImage fallbackImage = renderer.render(fallback);

        for (BufferedImage image : new BufferedImage[] {
            classicImage, slimImage, legacyImage, outerImage, darkImage, lightImage, fallbackImage
        }) {
            assertEquals(128, image.getWidth());
            assertEquals(256, image.getHeight());
            Bounds bounds = bounds(image);
            assertTrue(bounds.left >= 0 && bounds.right < 128);
            assertTrue(bounds.top >= 8, "head must not be clipped");
            assertTrue(bounds.bottom < 250, "feet and shadow must not be clipped");
            assertTrue(bounds.centerX() >= 59 && bounds.centerX() <= 69,
                "model must remain centered; bounds=" + bounds.left + "," + bounds.right);
        }
        assertNotEquals(hash(classicImage), hash(slimImage), "3px slim arms must differ from classic arms");
        assertNotEquals(hash(classicImage), hash(outerImage), "outer skin layers must be visible");
        assertTrue(visible(legacyImage) > 2000, "legacy 64x32 skin must remain renderable");
        assertTrue(visible(darkImage) > 2000 && visible(lightImage) > 2000);

        BufferedImage golden = new BufferedImage(3 * 128, 3 * 282, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = golden.createGraphics();
        try {
            graphics.setColor(new Color(60, 72, 80));
            graphics.fillRect(0, 0, golden.getWidth(), golden.getHeight());
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            BufferedImage[] images = {classicImage, slimImage, darkImage, lightImage, fallbackImage, legacyImage, outerImage};
            String[] labels = {"Classic", "Slim", "Dark", "Light", "Default", "Legacy", "Outer"};
            for (int index = 0; index < images.length; index++) {
                int column = index % 3;
                int row = index / 3;
                int x = column * 128;
                int y = row * 282;
                graphics.setColor(new Color(43, 54, 62));
                graphics.fillRect(x + 4, y + 24, 120, 256);
                graphics.drawImage(images[index], x, y + 24, null);
                graphics.setColor(Color.WHITE);
                graphics.drawString(labels[index], x + 8, y + 18);
            }
        } finally {
            graphics.dispose();
        }
        Path output = Paths.get("build", "rendered-test-output", "player-model-proportion-reference.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(golden, "png", output.toFile());
    }

    @Test
    void rendersNoArmorFullSetsLeatherTrimClassicSlimAndReference() throws Exception {
        PlayerModelRenderer renderer = new PlayerModelRenderer();
        EquipmentAssetResolver assets = new EquipmentAssetResolver(Paths.get("src", "armor-assets"));
        PlayerSkin classic = skin("armor-classic", false, false, true, new Color(42, 58, 72));
        PlayerSkin slim = skin("armor-slim", true, false, true, new Color(42, 58, 72));
        ArmorEquipmentSet[] equipment = {
            ArmorEquipmentSet.empty(),
            fullSet("iron", null, null, null),
            fullSet("diamond", null, null, null),
            fullSet("netherite", null, null, null),
            fullSet("leather", null, null, Integer.valueOf(0x5a31c8)),
            fullSet("diamond", "spire", "redstone", null),
            fullSet("netherite", "coast", "gold", null),
            fullSet("diamond", "ward", "amethyst", null)
        };
        PlayerSkin[] skins = {classic, classic, classic, classic, classic, classic, classic, slim};
        BufferedImage[] images = new BufferedImage[equipment.length];
        for (int index = 0; index < images.length; index++) {
            images[index] = renderer.render(skins[index], equipment[index], assets);
            assertEquals(128, images[index].getWidth());
            assertEquals(256, images[index].getHeight());
            Bounds bounds = bounds(images[index]);
            assertTrue(bounds.top >= 8 && bounds.bottom < 250);
        }
        assertNotEquals(hash(images[0]), hash(images[1]));
        assertNotEquals(hash(images[2]), hash(images[5]));
        assertNotEquals(hash(images[5]), hash(images[6]));
        assertNotEquals(hash(images[5]), hash(images[7]));

        BufferedImage reference = new BufferedImage(4 * 128, 2 * 282, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = reference.createGraphics();
        try {
            graphics.setColor(new Color(60, 72, 80));
            graphics.fillRect(0, 0, reference.getWidth(), reference.getHeight());
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            String[] labels = {
                "No Armor", "Iron", "Diamond", "Netherite", "Dyed Leather",
                "Diamond Spire/Red", "Netherite Coast/Gold", "Slim Ward/Amethyst"
            };
            for (int index = 0; index < images.length; index++) {
                int x = index % 4 * 128;
                int y = index / 4 * 282;
                graphics.setColor(new Color(43, 54, 62));
                graphics.fillRect(x + 4, y + 24, 120, 256);
                graphics.drawImage(images[index], x, y + 24, null);
                graphics.setColor(Color.WHITE);
                graphics.drawString(labels[index], x + 5, y + 17);
            }
        } finally { graphics.dispose(); }
        Path output = Paths.get("build", "rendered-test-output", "armor-preview-reference.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(reference, "png", output.toFile());
    }

    @Test
    void rendersDeterministicPerPieceArmorGlintWithTrimLeatherClassicAndSlim() throws Exception {
        PlayerModelRenderer renderer = new PlayerModelRenderer();
        EquipmentAssetResolver assets = new EquipmentAssetResolver(Paths.get("src", "armor-assets"));
        PlayerSkin classic = skin("glint-classic", false, false, true, new Color(42, 58, 72));
        PlayerSkin slim = skin("glint-slim", true, false, true, new Color(42, 58, 72));

        ArmorEquipmentSet diamondPlain = fullSet("diamond", null, null, null, false);
        ArmorEquipmentSet diamondGlint = fullSet("diamond", null, null, null, true);
        ArmorEquipmentSet netheriteTrim = fullSet("netherite", "coast", "gold", null, false);
        ArmorEquipmentSet netheriteTrimGlint = fullSet("netherite", "coast", "gold", null, true);
        ArmorEquipmentSet leatherPlain = fullSet("leather", null, null, 0xa06540, false);
        ArmorEquipmentSet leatherGlint = fullSet("leather", null, null, 0xa06540, true);
        ArmorEquipmentSet leatherTrimGlint = fullSet("leather", "ward", "amethyst", 0x5a31c8, true);
        ArmorEquipmentSet mixed = ArmorEquipmentSet.of(
            armor(ArmorVisualDescriptor.Slot.HEAD, "diamond", null, null, null, false),
            armor(ArmorVisualDescriptor.Slot.CHEST, "diamond", null, null, null, true),
            armor(ArmorVisualDescriptor.Slot.LEGS, "diamond", null, null, null, false),
            armor(ArmorVisualDescriptor.Slot.FEET, "diamond", null, null, null, true)
        );
        ArmorEquipmentSet[] sets = {
            diamondPlain, diamondGlint, netheriteTrim, netheriteTrimGlint,
            leatherPlain, leatherGlint, leatherTrimGlint, mixed
        };
        PlayerSkin[] skins = {classic, classic, classic, classic, classic, classic, classic, slim};
        BufferedImage[] images = new BufferedImage[sets.length];
        for (int index = 0; index < images.length; index++) {
            images[index] = renderer.render(skins[index], sets[index], assets);
            assertEquals(128, images[index].getWidth());
            assertEquals(256, images[index].getHeight());
        }

        assertNotEquals(hash(images[0]), hash(images[1]), "full-set glint must be visible");
        assertNotEquals(hash(images[2]), hash(images[3]), "trim and trim+glint must differ");
        assertNotEquals(hash(images[4]), hash(images[5]),
            "default leather glint must remain visibly distinct from leather without enchantment");
        assertNotEquals(hash(images[1]), hash(images[7]), "per-piece glint mask must differ from full-set glint");
        assertEquals(hash(images[3]), hash(renderer.render(classic, netheriteTrimGlint, assets)),
            "deterministic glint phase must render the same pixels repeatedly");
        assertNotEquals(hash(images[6]), hash(renderer.render(slim, leatherTrimGlint, assets)),
            "classic and slim armor previews must both render and preserve their model difference");
        BufferedImage noArmor = renderer.render(classic, ArmorEquipmentSet.empty(), assets);
        for (int y = 0; y < images[0].getHeight(); y++) for (int x = 0; x < images[0].getWidth(); x++) {
            if (images[0].getRGB(x, y) == noArmor.getRGB(x, y)) {
                assertEquals(images[0].getRGB(x, y), images[1].getRGB(x, y),
                    "glint must not alter pixels outside visible armor surfaces at " + x + ',' + y);
            }
        }

        String[] labels = {
            "Diamond / No Enchantment", "Diamond / All Enchanted", "Netherite / Trim Only",
            "Netherite / Trim + Glint", "Leather / No Enchantment", "Leather / Glint",
            "Dyed Leather / Trim + Glint", "Slim / Chest + Boots Glint"
        };
        BufferedImage reference = new BufferedImage(256, 8 * 276, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = reference.createGraphics();
        try {
            graphics.setColor(new Color(60, 72, 80));
            graphics.fillRect(0, 0, reference.getWidth(), reference.getHeight());
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            for (int index = 0; index < images.length; index++) {
                int y = index * 276;
                graphics.setColor(new Color(43, 54, 62));
                graphics.fillRect(64, y + 18, 128, 256);
                graphics.drawImage(images[index], 64, y + 18, null);
                graphics.setColor(Color.WHITE);
                graphics.drawString(labels[index], 8, y + 15);
            }
        } finally { graphics.dispose(); }
        Path root = Paths.get(
            "data", "visual-audit", "26.1.2-B1B315857266-MB7-PD1337875", "player-armor-glint"
        );
        Files.createDirectories(root);
        ImageIO.write(reference, "png", root.resolve("armor-glint-preview-reference.png").toFile());

        BufferedImage comparison = new BufferedImage(256, 2 * 280, BufferedImage.TYPE_INT_ARGB);
        graphics = comparison.createGraphics();
        try {
            graphics.setColor(new Color(60, 72, 80));
            graphics.fillRect(0, 0, comparison.getWidth(), comparison.getHeight());
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            BufferedImage[] values = {images[0], images[1], images[2], images[3]};
            String[] captions = {"Before: Diamond", "After: Diamond Glint", "Before: Trim", "After: Trim + Glint"};
            for (int index = 0; index < values.length; index++) {
                int x = index % 2 * 128;
                int y = index / 2 * 280;
                graphics.drawImage(values[index], x, y + 22, null);
                graphics.setColor(Color.WHITE);
                graphics.drawString(captions[index], x + 4, y + 16);
            }
        } finally { graphics.dispose(); }
        ImageIO.write(comparison, "png", root.resolve("armor-glint-before-vs-after.png").toFile());

        BufferedImage leatherComparison = new BufferedImage(256, 280, BufferedImage.TYPE_INT_ARGB);
        graphics = leatherComparison.createGraphics();
        try {
            graphics.setColor(new Color(60, 72, 80));
            graphics.fillRect(0, 0, leatherComparison.getWidth(), leatherComparison.getHeight());
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            graphics.drawImage(images[4], 0, 22, null);
            graphics.drawImage(images[5], 128, 22, null);
            graphics.setColor(Color.WHITE);
            graphics.drawString("Leather / No Glint", 4, 16);
            graphics.drawString("Leather / Corrected Glint", 132, 16);
        } finally { graphics.dispose(); }
        ImageIO.write(leatherComparison, "png", root.resolve("armor-glint-leather-before-vs-after.png").toFile());
    }

    private static ArmorEquipmentSet fullSet(
        String family, String pattern, String trimMaterial, Integer leatherColor
    ) {
        return fullSet(family, pattern, trimMaterial, leatherColor, false);
    }

    private static ArmorEquipmentSet fullSet(
        String family, String pattern, String trimMaterial, Integer leatherColor, boolean glint
    ) {
        return ArmorEquipmentSet.of(
            armor(ArmorVisualDescriptor.Slot.HEAD, family, pattern, trimMaterial, leatherColor, glint),
            armor(ArmorVisualDescriptor.Slot.CHEST, family, pattern, trimMaterial, leatherColor, glint),
            armor(ArmorVisualDescriptor.Slot.LEGS, family, pattern, trimMaterial, leatherColor, glint),
            armor(ArmorVisualDescriptor.Slot.FEET, family, pattern, trimMaterial, leatherColor, glint)
        );
    }

    private static ArmorVisualDescriptor armor(
        ArmorVisualDescriptor.Slot slot,
        String family,
        String pattern,
        String trimMaterial,
        Integer leatherColor
    ) {
        return armor(slot, family, pattern, trimMaterial, leatherColor, false);
    }

    private static ArmorVisualDescriptor armor(
        ArmorVisualDescriptor.Slot slot,
        String family,
        String pattern,
        String trimMaterial,
        Integer leatherColor,
        boolean glint
    ) {
        String suffix;
        switch (slot) {
            case HEAD: suffix = "helmet"; break;
            case CHEST: suffix = "chestplate"; break;
            case LEGS: suffix = "leggings"; break;
            case FEET: suffix = "boots"; break;
            default: throw new IllegalArgumentException();
        }
        return new ArmorVisualDescriptor(
            slot,
            "minecraft:" + family + '_' + suffix,
            "minecraft:" + family,
            pattern == null ? null : "minecraft:" + pattern,
            trimMaterial == null ? null : "minecraft:" + trimMaterial,
            leatherColor,
            glint
        );
    }

    private static PlayerSkin skin(
        String key, boolean slim, boolean legacy, boolean outer, Color clothing
    ) {
        BufferedImage image = new BufferedImage(64, legacy ? 32 : 64, BufferedImage.TYPE_INT_ARGB);
        Color face = new Color(211, 151, 113);
        fillCuboid(image, 0, 0, 8, 8, 8, face);
        fillCuboid(image, 16, 16, 8, 12, 4, clothing);
        fillCuboid(image, 0, 16, 4, 12, 4, new Color(49, 54, 79));
        fillCuboid(image, 40, 16, slim ? 3 : 4, 12, 4, clothing);
        if (!legacy) {
            fillCuboid(image, 16, 48, 4, 12, 4, new Color(49, 54, 79));
            fillCuboid(image, 32, 48, slim ? 3 : 4, 12, 4, clothing);
        }
        fill(image, 11, 11, 1, 1, new Color(30, 25, 24, 255));
        fill(image, 14, 11, 1, 1, new Color(30, 25, 24, 255));
        if (outer) {
            fillCuboid(image, 32, 0, 8, 8, 8, new Color(230, 75, 160, 90));
            if (!legacy) {
                fillCuboid(image, 16, 32, 8, 12, 4, new Color(70, 170, 210, 72));
                fillCuboid(image, 0, 32, 4, 12, 4, new Color(80, 150, 230, 70));
                fillCuboid(image, 0, 48, 4, 12, 4, new Color(80, 150, 230, 70));
                fillCuboid(image, 40, 32, slim ? 3 : 4, 12, 4, new Color(70, 170, 210, 72));
                fillCuboid(image, 48, 48, slim ? 3 : 4, 12, 4, new Color(70, 170, 210, 72));
            }
        }
        return new PlayerSkin(image, key, "TEST", slim);
    }

    private static void fillCuboid(BufferedImage image, int u, int v, int width, int height, int depth, Color color) {
        int right = Math.min(image.getWidth(), u + depth * 2 + width * 2);
        int bottom = Math.min(image.getHeight(), v + depth + height);
        fill(image, u, v, right - u, bottom - v, color);
    }

    private static void fill(BufferedImage image, int x, int y, int width, int height, Color color) {
        for (int py = y; py < y + height && py < image.getHeight(); py++) {
            for (int px = x; px < x + width && px < image.getWidth(); px++) image.setRGB(px, py, color.getRGB());
        }
    }

    private static int visible(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if ((image.getRGB(x, y) >>> 24) != 0) count++;
        return count;
    }

    private static int pixelsWithRgb(BufferedImage image, int rgb) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            if ((image.getRGB(x, y) & 0xffffff) == rgb && (image.getRGB(x, y) >>> 24) != 0) count++;
        }
        return count;
    }

    private static BufferedImage fitPreview(BufferedImage preview, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        double scale = Math.min((double) width / preview.getWidth(), (double) height / preview.getHeight());
        int scaledWidth = Math.max(1, (int) Math.round(preview.getWidth() * scale));
        int scaledHeight = Math.max(1, (int) Math.round(preview.getHeight() * scale));
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            );
            graphics.drawImage(
                preview, (width - scaledWidth) / 2, (height - scaledHeight) / 2,
                scaledWidth, scaledHeight, null
            );
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static long hash(BufferedImage image) {
        long value = 1125899906842597L;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            value = value * 31 + image.getRGB(x, y);
        return value;
    }

    private static BufferedImage difference(BufferedImage first, BufferedImage second) {
        assertEquals(first.getWidth(), second.getWidth());
        assertEquals(first.getHeight(), second.getHeight());
        BufferedImage output = new BufferedImage(first.getWidth(), first.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < first.getHeight(); y++) for (int x = 0; x < first.getWidth(); x++) {
            if (first.getRGB(x, y) != second.getRGB(x, y)) output.setRGB(x, y, 0xffff35ba);
        }
        return output;
    }

    private static int moreOpaquePixels(BufferedImage first, BufferedImage second) {
        int count = 0;
        for (int y = 0; y < first.getHeight(); y++) for (int x = 0; x < first.getWidth(); x++) {
            if ((second.getRGB(x, y) >>> 24) > (first.getRGB(x, y) >>> 24)) count++;
        }
        return count;
    }

    private static Bounds bounds(BufferedImage image) {
        int left=image.getWidth(),top=image.getHeight(),right=-1,bottom=-1;
        for (int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
            if ((image.getRGB(x,y)>>>24)!=0) { left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y); }
        }
        return new Bounds(left,top,right,bottom);
    }
    private static final class Bounds {
        private final int left,top,right,bottom;
        private Bounds(int left,int top,int right,int bottom){this.left=left;this.top=top;this.right=right;this.bottom=bottom;}
        private int centerX(){return (left+right)/2;}
    }
}
