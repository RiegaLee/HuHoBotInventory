package cn.huohuas001.huhobot.inventory.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaAssetImporterTest {
    @Test
    void importsStaticItemsWritesMetadataAndReusesMatchingCache(@TempDir Path temp) throws Exception {
        Path jar = createClientJar(temp.resolve("test-version.jar"), true);
        Path cache = temp.resolve("cache");
        VanillaAssetImporter importer = new VanillaAssetImporter();

        VanillaAssetImporter.ImportResult first = importer.importClientJar(jar, cache, "test-version");
        assertEquals(VanillaAssetImporter.ImportStatus.IMPORTED, first.getStatus());
        assertEquals(4, first.getGeneratedIcons());
        assertEquals(5, first.getTotalDefinitions());

        Path diamond = first.getCacheDirectory().resolve("generated-icons/minecraft/diamond.png");
        Path block = first.getCacheDirectory().resolve("generated-icons/minecraft/test_block.png");
        assertTrue(Files.isRegularFile(diamond));
        assertTrue(Files.isRegularFile(block));
        assertFalse(Files.exists(first.getCacheDirectory().resolve("generated-icons/minecraft/shield.png")));
        BufferedImage icon = ImageIO.read(diamond.toFile());
        assertEquals(32, icon.getWidth());
        assertEquals(32, icon.getHeight());
        assertEquals(0, icon.getRGB(0, 0) >>> 24);
        BufferedImage blockIcon = ImageIO.read(block.toFile());
        assertEquals(64, blockIcon.getWidth());
        assertEquals(64, blockIcon.getHeight());
        assertEquals(0, blockIcon.getRGB(0, 0) >>> 24);
        Set<Integer> opaqueBlockColors = new HashSet<Integer>();
        for (int y = 0; y < blockIcon.getHeight(); y++) {
            for (int x = 0; x < blockIcon.getWidth(); x++) {
                int color = blockIcon.getRGB(x, y);
                if ((color >>> 24) != 0) opaqueBlockColors.add(color);
            }
        }
        assertTrue(opaqueBlockColors.size() >= 2, "block icon must contain separately lit model faces");

        String metadata = new String(
            Files.readAllBytes(first.getCacheDirectory().resolve("metadata.json")),
            StandardCharsets.UTF_8
        );
        assertTrue(metadata.contains("\"sourceJar\": \"test-version.jar\""));
        assertTrue(metadata.contains("\"modelBakerVersion\": 7"));
        assertTrue(metadata.contains("\"BLOCK_MODEL\": 2"));
        assertTrue(metadata.contains("\"GENERATED_2D\": 2"));
        assertFalse(metadata.contains(temp.toAbsolutePath().toString()));
        String unresolved = new String(
            Files.readAllBytes(first.getCacheDirectory().resolve("unresolved.txt")),
            StandardCharsets.UTF_8
        );
        assertTrue(unresolved.contains("minecraft:shield\tSPECIAL_RENDERER"));
        String paths = new String(
            Files.readAllBytes(first.getCacheDirectory().resolve("render-paths.tsv")), StandardCharsets.UTF_8
        );
        assertTrue(paths.contains("minecraft:diamond\tGENERATED_2D"));
        assertTrue(paths.contains("minecraft:test_block\tBLOCK_MODEL"));
        assertTrue(paths.contains("minecraft:trident\tGENERATED_2D"));

        VanillaAssetImporter.ImportResult second = importer.importClientJar(jar, cache, null);
        assertEquals(VanillaAssetImporter.ImportStatus.REUSED, second.getStatus());
        assertEquals(first.getCacheDirectory(), second.getCacheDirectory());

        VanillaImportedAssetProvider provider = VanillaImportedAssetProvider.open(cache);
        assertTrue(provider.isAvailable());
        assertTrue(provider.resolve("minecraft:diamond").isPresent());
        assertTrue(provider.resolve("minecraft:test_block").isPresent());
        assertTrue(provider.resolve("minecraft:grass_block").isPresent());
        assertEquals("GENERATED_2D", provider.getRenderPath("minecraft:diamond").orElse(null));
        assertEquals("BLOCK_MODEL", provider.getRenderPath("minecraft:test_block").orElse(null));
        assertEquals("GENERATED_2D", provider.getRenderPath("minecraft:trident").orElse(null));
        assertFalse(provider.resolve("minecraft:shield").isPresent());
        assertEquals("test-version", provider.getMinecraftVersion());
    }

    @Test
    void optionalLocalTexturePackOverridesVanillaTextures(@TempDir Path temp) throws Exception {
        Path jar = createClientJar(temp.resolve("test-version.jar"), true);
        Path pack = temp.resolve("faithful.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(pack))) {
            image(output, "assets/minecraft/textures/block/test_block.png", new Color(220, 40, 30, 255), false);
        }
        VanillaAssetImporter.ImportResult result = new VanillaAssetImporter().importClientJar(
            jar, temp.resolve("cache"), "test-version", pack
        );
        assertTrue(result.getCacheDirectory().getFileName().toString().contains("-MB7-P"));
        BufferedImage block = ImageIO.read(
            result.getCacheDirectory().resolve("generated-icons/minecraft/test_block.png").toFile()
        );
        boolean foundRed = false;
        for (int y = 0; y < block.getHeight(); y++) {
            for (int x = 0; x < block.getWidth(); x++) {
                int color = block.getRGB(x, y);
                if (((color >>> 16) & 0xff) > ((color >>> 8) & 0xff) * 2) foundRed = true;
            }
        }
        assertTrue(foundRed, "resource-pack texture must be used by the block model baker");
    }

    @Test
    void rejectsCorruptWrongVersionAndJarWithoutItemAssets(@TempDir Path temp) throws Exception {
        Path corrupt = temp.resolve("corrupt.jar");
        Files.write(corrupt, "not a zip".getBytes(StandardCharsets.UTF_8));
        VanillaAssetImporter importer = new VanillaAssetImporter();
        assertThrows(
            IllegalArgumentException.class,
            () -> importer.importClientJar(corrupt, temp.resolve("corrupt-cache"), null)
        );

        Path valid = createClientJar(temp.resolve("valid.jar"), true);
        assertThrows(
            IllegalArgumentException.class,
            () -> importer.importClientJar(valid, temp.resolve("wrong-version-cache"), "another-version")
        );

        Path empty = createClientJar(temp.resolve("empty.jar"), false);
        assertThrows(
            IllegalArgumentException.class,
            () -> importer.importClientJar(empty, temp.resolve("empty-cache"), null)
        );
    }

    @Test
    void writesFourStageStoneColorDiagnostic(@TempDir Path temp) throws Exception {
        Path jar = createStoneClientJar(temp.resolve("stone.jar"));
        Path pack = temp.resolve("faithful.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(pack))) {
            image(output, "assets/minecraft/textures/block/stone.png", new Color(96, 92, 88), false);
        }
        Path report = new VanillaAssetImporter().diagnoseStone(jar, temp.resolve("diagnostic"), pack);

        assertTrue(Files.isRegularFile(report));
        for (String file : new String[] {
            "01-source-stone.png", "02-baked-stone-unshaded.png",
            "03-baked-stone-shaded.png", "04-final-inventory-stone.png"
        }) assertTrue(Files.isRegularFile(report.getParent().resolve(file)), file);
        String text = new String(Files.readAllBytes(report), StandardCharsets.UTF_8);
        assertTrue(text.contains("AverageRGB=96,92,88"));
        assertTrue(text.contains("top=1.00, sideA=0.80, sideB=0.62"));
    }

    private static Path createStoneClientJar(Path path) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            entry(output, "version.json", "{\"id\":\"test-version\",\"pack_version\":{\"resource_major\":99}}");
            entry(output, "assets/minecraft/items/stone.json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:block/stone\"}}");
            entry(output, "assets/minecraft/models/block/stone.json",
                "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"minecraft:block/stone\"}}");
            entry(output, "assets/minecraft/models/block/cube_all.json",
                "{\"parent\":\"minecraft:block/cube\",\"textures\":{" +
                    "\"down\":\"#all\",\"up\":\"#all\",\"north\":\"#all\"," +
                    "\"south\":\"#all\",\"west\":\"#all\",\"east\":\"#all\"}}");
            entry(output, "assets/minecraft/models/block/cube.json",
                "{\"parent\":\"minecraft:block/block\",\"elements\":[{" +
                    "\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{" +
                    "\"down\":{\"texture\":\"#down\"},\"up\":{\"texture\":\"#up\"}," +
                    "\"north\":{\"texture\":\"#north\"},\"south\":{\"texture\":\"#south\"}," +
                    "\"west\":{\"texture\":\"#west\"},\"east\":{\"texture\":\"#east\"}}}]}");
            entry(output, "assets/minecraft/models/block/block.json",
                "{\"display\":{\"gui\":{\"rotation\":[30,225,0]," +
                    "\"translation\":[0,0,0],\"scale\":[0.625,0.625,0.625]}}}");
        }
        return path;
    }

    private static Path createClientJar(Path path, boolean includeItems) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            entry(output, "version.json", "{\"id\":\"test-version\",\"pack_version\":{\"resource_major\":99}}");
            if (includeItems) {
                entry(output, "assets/minecraft/items/diamond.json",
                    "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/diamond\"}}");
                entry(output, "assets/minecraft/models/item/diamond.json",
                    "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/diamond\"}}");
                image(output, "assets/minecraft/textures/item/diamond.png", new Color(40, 220, 240, 255), true);

                entry(output, "assets/minecraft/items/test_block.json",
                    "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:block/test_block\"}}");
                entry(output, "assets/minecraft/models/block/test_block.json",
                    "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"minecraft:block/test_block\"}}");
                entry(output, "assets/minecraft/models/block/cube_all.json",
                    "{\"parent\":\"minecraft:block/cube\",\"textures\":{" +
                        "\"down\":\"#all\",\"up\":\"#all\",\"north\":\"#all\"," +
                        "\"south\":\"#all\",\"west\":\"#all\",\"east\":\"#all\"}}");
                entry(output, "assets/minecraft/models/block/cube.json",
                    "{\"parent\":\"minecraft:block/block\",\"elements\":[{" +
                        "\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{" +
                        "\"down\":{\"texture\":\"#down\"},\"up\":{\"texture\":\"#up\"}," +
                        "\"north\":{\"texture\":\"#north\"},\"south\":{\"texture\":\"#south\"}," +
                        "\"west\":{\"texture\":\"#west\"},\"east\":{\"texture\":\"#east\"}}}]}");
                entry(output, "assets/minecraft/models/block/block.json",
                    "{\"display\":{\"gui\":{\"rotation\":[30,225,0]," +
                        "\"translation\":[0,0,0],\"scale\":[0.625,0.625,0.625]}}}");
                image(output, "assets/minecraft/textures/block/test_block.png", new Color(90, 180, 70, 255), false);

                entry(output, "assets/minecraft/items/grass_block.json",
                    "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:block/grass_block\"," +
                        "\"tints\":[{\"type\":\"minecraft:grass\",\"temperature\":0.5,\"downfall\":1.0}]}}");
                entry(output, "assets/minecraft/models/block/grass_block.json",
                    "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"minecraft:block/grass_block\"}}");
                image(output, "assets/minecraft/textures/block/grass_block.png", new Color(180, 180, 180, 255), false);
                image(output, "assets/minecraft/textures/colormap/grass.png", new Color(70, 180, 50, 255), false);

                entry(output, "assets/minecraft/items/shield.json",
                    "{\"model\":{\"type\":\"minecraft:special\",\"base\":\"minecraft:item/shield\",\"model\":{\"type\":\"minecraft:shield\"}}}");

                entry(output, "assets/minecraft/items/trident.json",
                    "{\"model\":{\"type\":\"minecraft:select\",\"property\":\"minecraft:display_context\"," +
                        "\"cases\":[{\"when\":[\"gui\",\"ground\"],\"model\":{\"type\":\"minecraft:model\"," +
                        "\"model\":\"minecraft:item/trident\"}}],\"fallback\":{\"type\":\"minecraft:special\"," +
                        "\"base\":\"minecraft:item/trident_in_hand\",\"model\":{\"type\":\"minecraft:trident\"}}}}");
                entry(output, "assets/minecraft/models/item/trident.json",
                    "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/trident\"}}");
                image(output, "assets/minecraft/textures/item/trident.png", new Color(70, 170, 150, 255), true);
            }
        }
        return path;
    }

    private static void entry(ZipOutputStream output, String name, String value) throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private static void image(
        ZipOutputStream output,
        String name,
        Color color,
        boolean transparentCorner
    ) throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, color.getRGB());
        }
        if (transparentCorner) image.setRGB(0, 0, 0);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        output.putNextEntry(new ZipEntry(name));
        output.write(bytes.toByteArray());
        output.closeEntry();
    }
}
