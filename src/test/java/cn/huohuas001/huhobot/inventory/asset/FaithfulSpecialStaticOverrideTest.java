package cn.huohuas001.huhobot.inventory.asset;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaithfulSpecialStaticOverrideTest {
    private static final Path ROOT = Paths.get(
        "src", "main", "resources", "themes", "faithful32x", "overrides", "items", "minecraft"
    );
    private static final List<String> CHESTS = Arrays.asList(
        "ender_chest", "trapped_chest", "copper_chest", "exposed_copper_chest",
        "weathered_copper_chest", "oxidized_copper_chest", "waxed_copper_chest",
        "waxed_exposed_copper_chest", "waxed_weathered_copper_chest", "waxed_oxidized_copper_chest"
    );
    private static final List<String> COLORS = Arrays.asList(
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    );

    @Test
    void plainShieldIsAClientModelBakeFromTheFaithfulEntityTexture() throws Exception {
        assertClientIcon("shield", 22, 54, 1, 61, 1500);
        Path source = Paths.get("tools", "assets", "faithful32x", "shield_base_nopattern.png");
        BufferedImage texture = ImageIO.read(source.toFile());
        assertEquals(128, texture.getWidth());
        assertEquals(128, texture.getHeight());
        assertFalse(Arrays.equals(
            Files.readAllBytes(Paths.get("tools", "assets", "faithful32x", "shield.png")),
            bytes("shield")
        ), "the former handmade 32x shield must not remain bundled as the override");
    }

    @Test
    void chestAndShulkerFamiliesContainTwentySevenClientSlotIcons() throws Exception {
        for (String chest : CHESTS) assertClientIcon(chest, 7, 56, 7, 60, 2000);
        assertClientIcon("shulker_box", 4, 59, 1, 62, 2600);
        for (String color : COLORS) assertClientIcon(color + "_shulker_box", 4, 59, 1, 62, 2600);
        assertEquals(27, CHESTS.size() + COLORS.size() + 1);
    }

    @Test
    void waxedCopperVariantsReuseTheirOxidationTextureOutput() throws Exception {
        assertArrayEquals(bytes("copper_chest"), bytes("waxed_copper_chest"));
        assertArrayEquals(bytes("exposed_copper_chest"), bytes("waxed_exposed_copper_chest"));
        assertArrayEquals(bytes("weathered_copper_chest"), bytes("waxed_weathered_copper_chest"));
        assertArrayEquals(bytes("oxidized_copper_chest"), bytes("waxed_oxidized_copper_chest"));
    }

    @Test
    void representativeShulkerColorsRemainDistinctWithoutSecondTint() throws Exception {
        int[] white = average("white_shulker_box");
        int[] red = average("red_shulker_box");
        int[] blue = average("blue_shulker_box");
        int[] black = average("black_shulker_box");
        assertTrue(white[0] > 125 && white[1] > 125 && white[2] > 125);
        assertTrue(red[0] > red[1] * 3 && red[0] > red[2] * 3);
        assertTrue(blue[2] > blue[0] * 2 && blue[2] > blue[1] * 2);
        assertTrue(black[0] < 30 && black[1] < 30 && black[2] < 30);
        assertFalse(Arrays.equals(white, red));
        assertFalse(Arrays.equals(red, blue));
        assertFalse(Arrays.equals(blue, black));
    }

    @Test
    void christmasTrappedChestIsASeparateManagedVariant() throws Exception {
        Path christmas = Paths.get(
            "src", "main", "resources", "themes", "faithful32x", "special-variants",
            "minecraft", "trapped_chest_christmas.png"
        );
        BufferedImage image = ImageIO.read(christmas.toFile());
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
        assertFalse(Arrays.equals(Files.readAllBytes(christmas), bytes("trapped_chest")));
    }

    private static void assertClientIcon(
        String item, int minX, int maxX, int minY, int maxY, int minimumPixels
    ) throws Exception {
        BufferedImage image = ImageIO.read(ROOT.resolve(item + ".png").toFile());
        assertEquals(64, image.getWidth(), item);
        assertEquals(64, image.getHeight(), item);
        int actualMinX=64, actualMaxX=-1, actualMinY=64, actualMaxY=-1, pixels=0;
        for (int y=0; y<64; y++) for (int x=0; x<64; x++) {
            if ((image.getRGB(x,y) >>> 24) == 0) continue;
            actualMinX=Math.min(actualMinX,x); actualMaxX=Math.max(actualMaxX,x);
            actualMinY=Math.min(actualMinY,y); actualMaxY=Math.max(actualMaxY,y); pixels++;
        }
        assertEquals(minX, actualMinX, item);
        assertEquals(maxX, actualMaxX, item);
        assertEquals(minY, actualMinY, item);
        assertEquals(maxY, actualMaxY, item);
        assertTrue(pixels >= minimumPixels, item + " alpha coverage");
    }

    private static byte[] bytes(String item) throws Exception {
        return Files.readAllBytes(ROOT.resolve(item + ".png"));
    }

    private static int[] average(String item) throws Exception {
        BufferedImage image = ImageIO.read(ROOT.resolve(item + ".png").toFile());
        long red=0,green=0,blue=0,count=0;
        for (int y=0; y<image.getHeight(); y++) for (int x=0; x<image.getWidth(); x++) {
            int argb=image.getRGB(x,y);
            if ((argb >>> 24) == 0) continue;
            red+=(argb >>> 16)&255; green+=(argb >>> 8)&255; blue+=argb&255; count++;
        }
        return new int[] {(int)(red/count),(int)(green/count),(int)(blue/count)};
    }
}
