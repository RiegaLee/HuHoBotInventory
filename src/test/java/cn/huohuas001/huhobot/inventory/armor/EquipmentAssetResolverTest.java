package cn.huohuas001.huhobot.inventory.armor;

import cn.huohuas001.huhobot.inventory.model.ItemSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentAssetResolverTest {
    private final EquipmentAssetResolver assets = new EquipmentAssetResolver(
        Paths.get("src", "armor-assets")
    );

    @Test
    void resolvesClientEquipmentLayersTrimPalettesPatternAndLeatherDye() {
        ArmorVisualDescriptor plain = armor(ArmorVisualDescriptor.Slot.CHEST, "diamond", null, null, null);
        ArmorVisualDescriptor redstone = armor(ArmorVisualDescriptor.Slot.CHEST, "diamond", "spire", "redstone", null);
        ArmorVisualDescriptor gold = armor(ArmorVisualDescriptor.Slot.CHEST, "diamond", "spire", "gold", null);
        ArmorVisualDescriptor coast = armor(ArmorVisualDescriptor.Slot.CHEST, "diamond", "coast", "redstone", null);
        BufferedImage plainTexture = assets.resolveArmorTexture(plain, false);
        BufferedImage redstoneTexture = assets.resolveArmorTexture(redstone, false);
        assertEquals(128, redstoneTexture.getWidth());
        assertEquals(64, redstoneTexture.getHeight());
        assertNotEquals(hash(plainTexture), hash(redstoneTexture));
        assertNotEquals(hash(redstoneTexture), hash(assets.resolveArmorTexture(gold, false)));
        assertNotEquals(hash(redstoneTexture), hash(assets.resolveArmorTexture(coast, false)));

        ArmorVisualDescriptor blueLeather = armor(
            ArmorVisualDescriptor.Slot.HEAD, "leather", null, null, Integer.valueOf(0x315ac8)
        );
        ArmorVisualDescriptor redLeather = armor(
            ArmorVisualDescriptor.Slot.HEAD, "leather", null, null, Integer.valueOf(0xc83b31)
        );
        assertNotEquals(
            hash(assets.resolveArmorTexture(blueLeather, false)),
            hash(assets.resolveArmorTexture(redLeather, false))
        );
        assertEquals(2, assets.resolveArmorLayers(blueLeather, false).size(),
            "client leather equipment keeps the dyed layer and untinted overlay separate");
        assertEquals(128, assets.resolveArmorGlintTexture().getWidth());
        assertEquals(128, assets.resolveArmorGlintTexture().getHeight());
        assertTrue(assets.resolveArmorTrimTexture(redstone, false) != null);
    }

    @Test
    void itemIconCacheSeparatesPatternMaterialAndLeatherColor(@TempDir Path cache) throws Exception {
        ArmorItemIconRenderer renderer = new ArmorItemIconRenderer(assets, cache);
        BufferedImage base = ImageIO.read(Paths.get(
            "src", "main", "resources", "themes", "faithful32x", "assets", "minecraft",
            "diamond_chestplate.png"
        ).toFile());

        ArmorVisualDescriptor noTrim = armor(ArmorVisualDescriptor.Slot.CHEST, "diamond", null, null, null);
        ArmorVisualDescriptor spireRedstone = armor(ArmorVisualDescriptor.Slot.CHEST, "diamond", "spire", "redstone", null);
        ArmorVisualDescriptor spireGold = armor(ArmorVisualDescriptor.Slot.CHEST, "diamond", "spire", "gold", null);
        ArmorVisualDescriptor coastRedstone = armor(ArmorVisualDescriptor.Slot.CHEST, "diamond", "coast", "redstone", null);
        ArmorVisualDescriptor dyedLeather = armor(
            ArmorVisualDescriptor.Slot.CHEST, "leather", "spire", "amethyst", Integer.valueOf(0x6c35b8)
        );
        assertNotEquals(spireRedstone.visualKey(), coastRedstone.visualKey());
        assertNotEquals(
            ArmorEquipmentSet.of(spireRedstone).fingerprint(),
            ArmorEquipmentSet.of(coastRedstone).fingerprint()
        );

        BufferedImage[] images = {
            renderer.render(item(noTrim), base),
            renderer.render(item(spireRedstone), base),
            renderer.render(item(spireGold), base),
            renderer.render(item(coastRedstone), base),
            renderer.render(item(dyedLeather), base)
        };
        assertNotEquals(hash(images[0]), hash(images[1]));
        assertNotEquals(hash(images[1]), hash(images[2]));
        try (java.util.stream.Stream<Path> files = Files.list(cache)) {
            assertEquals(4L, files.filter(Files::isRegularFile).count());
        }

        BufferedImage reference = new BufferedImage(5 * 104, 138, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = reference.createGraphics();
        try {
            graphics.setColor(new Color(43, 54, 62));
            graphics.fillRect(0, 0, reference.getWidth(), reference.getHeight());
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            String[] labels = {"No Trim", "Spire/Red", "Spire/Gold", "Coast/Red*", "Dyed+Trim"};
            for (int index = 0; index < images.length; index++) {
                graphics.drawImage(images[index], index * 104 + 20, 12, 64, 64, null);
                graphics.setColor(Color.WHITE);
                graphics.drawString(labels[index], index * 104 + 8, 101);
            }
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            graphics.setColor(new Color(205, 215, 220));
            graphics.drawString("* Item icons use the client slot mask; trim pattern is visible on the 3D model.", 8, 126);
        } finally { graphics.dispose(); }
        Path output = Paths.get("build", "rendered-test-output", "armor-item-icons-reference.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(reference, "png", output.toFile());
    }

    private static ItemSnapshot item(ArmorVisualDescriptor armor) {
        return new ItemSnapshot(
            armor.getBaseMaterialKey(), 1, 0, 0, null, null, false, null, armor
        );
    }

    private static ArmorVisualDescriptor armor(
        ArmorVisualDescriptor.Slot slot,
        String family,
        String pattern,
        String material,
        Integer leatherColor
    ) {
        String item;
        switch (slot) {
            case HEAD: item = family + "_helmet"; break;
            case CHEST: item = family + "_chestplate"; break;
            case LEGS: item = family + "_leggings"; break;
            case FEET: item = family + "_boots"; break;
            default: throw new IllegalArgumentException();
        }
        String equipment = "golden".equals(family) ? "gold" : family;
        return new ArmorVisualDescriptor(
            slot, "minecraft:" + item, "minecraft:" + equipment,
            pattern == null ? null : "minecraft:" + pattern,
            material == null ? null : "minecraft:" + material,
            leatherColor, false
        );
    }

    private static long hash(BufferedImage image) {
        long value = 1125899906842597L;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            value = value * 31 + image.getRGB(x, y);
        }
        return value;
    }
}
