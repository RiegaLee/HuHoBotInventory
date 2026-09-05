package cn.huohuas001.huhobot.inventory.asset;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockModelRendererTest {
    private static final BlockModelRenderer.Transform GUI = new BlockModelRenderer.Transform(
        new BlockModelRenderer.Vec3(30, 225, 0),
        new BlockModelRenderer.Vec3(0, 0, 0),
        new BlockModelRenderer.Vec3(0.625, 0.625, 0.625)
    );

    @Test
    void fullCubeUsesGuiTransformThreeFaceShadingAndTransparentMargin() {
        BufferedImage icon = render(
            Collections.singletonList(cube(null, allFaces("#all", -1))),
            Collections.singletonMap("all", "minecraft:block/stone"),
            Collections.<Integer>emptyList(),
            Collections.singletonMap("minecraft:block/stone", solid(new Color(150, 150, 150, 255)))
        );
        assertEquals(64, icon.getWidth());
        assertEquals(64, icon.getHeight());
        Bounds bounds = bounds(icon);
        assertTrue(bounds.width <= 56 && bounds.height <= 56, "icon must preserve a transparent GUI margin");
        assertTrue(bounds.width >= 44 && bounds.height >= 40, "full cube must remain clearly visible");
        assertEquals(0, icon.getRGB(0, 0) >>> 24);
        assertTrue(uniqueOpaqueColors(icon) >= 3, "top/left/right faces must have distinct GUI lighting");
    }

    @Test
    void mapsDifferentFaceTexturesAndAppliesStaticTint() {
        Map<String, BlockModelRenderer.Face> faces = new LinkedHashMap<String, BlockModelRenderer.Face>();
        faces.put("up", new BlockModelRenderer.Face("#top", null, 0, 0));
        faces.put("north", new BlockModelRenderer.Face("#north", null, 0));
        faces.put("east", new BlockModelRenderer.Face("#east", null, 0));
        Map<String, String> references = new LinkedHashMap<String, String>();
        references.put("top", "minecraft:block/top");
        references.put("north", "minecraft:block/north");
        references.put("east", "minecraft:block/east");
        Map<String, BufferedImage> textures = new LinkedHashMap<String, BufferedImage>();
        textures.put("minecraft:block/top", solid(Color.WHITE));
        textures.put("minecraft:block/north", solid(Color.RED));
        textures.put("minecraft:block/east", solid(Color.BLUE));

        BufferedImage icon = render(
            Collections.singletonList(cube(null, faces)), references,
            Collections.singletonList(0x55cc44), textures
        );
        int green = 0;
        int red = 0;
        int blue = 0;
        for (int y = 0; y < icon.getHeight(); y++) {
            for (int x = 0; x < icon.getWidth(); x++) {
                int color = icon.getRGB(x, y);
                int r = (color >>> 16) & 0xff;
                int g = (color >>> 8) & 0xff;
                int b = color & 0xff;
                if (g > r * 1.5 && g > b * 1.5) green++;
                if (r > g * 1.5 && r > b * 1.5) red++;
                if (b > r * 1.5 && b > g * 1.5) blue++;
            }
        }
        assertTrue(green > 10 && red > 10 && blue > 10, "three visible faces must retain their own texture/tint");
    }

    @Test
    void supportsMultipleElementsRotationTransparencyAndMissingTexture() {
        Map<String, BlockModelRenderer.Face> firstFaces = allFaces("#visible", -1);
        firstFaces.put("south", new BlockModelRenderer.Face("#missing", null, 0));
        BlockModelRenderer.Element first = new BlockModelRenderer.Element(
            new BlockModelRenderer.Vec3(2, 0, 6), new BlockModelRenderer.Vec3(7, 16, 10), firstFaces,
            new BlockModelRenderer.Rotation(new BlockModelRenderer.Vec3(8, 8, 8), "y", 22.5), true
        );
        BlockModelRenderer.Element second = new BlockModelRenderer.Element(
            new BlockModelRenderer.Vec3(9, 0, 6), new BlockModelRenderer.Vec3(14, 12, 10),
            allFaces("#visible", -1), null, true
        );
        Map<String, String> references = new LinkedHashMap<String, String>();
        references.put("visible", "minecraft:block/alpha");
        references.put("missing", "minecraft:block/missing");
        BufferedImage transparent = solid(new Color(240, 190, 40, 180));
        transparent.setRGB(0, 0, 0);
        Map<String, BufferedImage> textures = Collections.singletonMap("minecraft:block/alpha", transparent);
        BufferedImage rotated = render(Arrays.asList(first, second), references, Collections.<Integer>emptyList(), textures);

        BlockModelRenderer.Element unrotatedFirst = new BlockModelRenderer.Element(
            first.from, first.to, firstFaces, null, true
        );
        BufferedImage unrotated = render(
            Arrays.asList(unrotatedFirst, second), references, Collections.<Integer>emptyList(), textures
        );
        assertTrue(bounds(rotated).width > 5 && bounds(rotated).height > 10);
        assertNotEquals(pixelHash(unrotated), pixelHash(rotated), "element rotation must alter projected geometry");
    }

    @Test
    void faceLightingNeverRaisesSourceRgbChannels() {
        int sourceChannel = 150;
        BufferedImage icon = render(
            Collections.singletonList(cube(null, allFaces("#all", -1))),
            Collections.singletonMap("all", "minecraft:block/stone"),
            Collections.<Integer>emptyList(),
            Collections.singletonMap("minecraft:block/stone", solid(new Color(sourceChannel, sourceChannel, sourceChannel)))
        );
        for (int y = 0; y < icon.getHeight(); y++) {
            for (int x = 0; x < icon.getWidth(); x++) {
                int color = icon.getRGB(x, y);
                if ((color >>> 24) == 0) continue;
                assertTrue(((color >>> 16) & 0xff) <= sourceChannel);
                assertTrue(((color >>> 8) & 0xff) <= sourceChannel);
                assertTrue((color & 0xff) <= sourceChannel);
            }
        }
    }

    private static BufferedImage render(
        List<BlockModelRenderer.Element> elements,
        Map<String, String> references,
        List<Integer> tints,
        Map<String, BufferedImage> textures
    ) {
        return new BlockModelRenderer().render(
            new BlockModelRenderer.Model(references, elements, GUI, tints), textures::get
        );
    }

    private static BlockModelRenderer.Element cube(
        BlockModelRenderer.Rotation rotation,
        Map<String, BlockModelRenderer.Face> faces
    ) {
        return new BlockModelRenderer.Element(
            new BlockModelRenderer.Vec3(0, 0, 0), new BlockModelRenderer.Vec3(16, 16, 16),
            faces, rotation, true
        );
    }

    private static Map<String, BlockModelRenderer.Face> allFaces(String texture, int tintIndex) {
        Map<String, BlockModelRenderer.Face> result = new LinkedHashMap<String, BlockModelRenderer.Face>();
        for (String direction : Arrays.asList("down", "up", "north", "south", "west", "east")) {
            result.put(direction, new BlockModelRenderer.Face(texture, null, 0, tintIndex));
        }
        return result;
    }

    private static BufferedImage solid(Color color) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, color.getRGB());
        }
        return image;
    }

    private static int uniqueOpaqueColors(BufferedImage image) {
        java.util.HashSet<Integer> colors = new java.util.HashSet<Integer>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getRGB(x, y);
                if ((color >>> 24) != 0) colors.add(color);
            }
        }
        return colors.size();
    }

    private static long pixelHash(BufferedImage image) {
        long value = 1125899906842597L;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) value = value * 31 + image.getRGB(x, y);
        }
        return value;
    }

    private static Bounds bounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) continue;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        return new Bounds(maxX < minX ? 0 : maxX - minX + 1, maxY < minY ? 0 : maxY - minY + 1);
    }

    private static final class Bounds {
        final int width;
        final int height;
        Bounds(int width, int height) { this.width = width; this.height = height; }
    }
}
