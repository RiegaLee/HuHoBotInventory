package cn.huohuas001.huhobot.inventory.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledAssetBootstrapTest {
    @Test
    void cleanInstallRepairsManagedFilesAndPreservesUserData(@TempDir Path dataRoot) throws Exception {
        Path config = write(dataRoot.resolve("config.yml"), "user-config");
        Path skin = write(dataRoot.resolve("cache/skins/user.png"), "skin-cache");
        Path snapshot = write(dataRoot.resolve("data/offline-snapshots/player.json"), "snapshot");
        Path custom = write(dataRoot.resolve("assets/custom/overrides/items/minecraft/chest.png"), "custom-chest");
        Path oldPack = write(dataRoot.resolve("assets/bundled/old-pack/do-not-delete.txt"), "old-pack");

        BundledAssetBootstrap.ResourceSource classpath = path ->
            BundledAssetBootstrapTest.class.getClassLoader().getResourceAsStream(path);
        BundledAssetBootstrap.Installation first = BundledAssetBootstrap.install(dataRoot, classpath);

        assertEquals("inventory-assets-v11-mb7-pv8-glint-bed-shield-enderchest-hd64-pd1337875", first.getPackId());
        assertEquals(1413, first.getGeneratedIcons());
        assertEquals(1506, first.getTotalDefinitions());
        assertTrue(first.getInstalledFiles() > 1413);
        assertEquals(0, first.getReusedFiles());
        assertTrue(Files.isRegularFile(first.getThemesRoot().resolve("faithful32x/theme.yml")));
        assertTrue(Files.isRegularFile(
            first.getThemesRoot().resolve("faithful32x/overrides/items/minecraft/chest.png")
        ));
        assertTrue(Files.isRegularFile(
            first.getThemesRoot().resolve("faithful32x/overrides/items/minecraft/red_bed.png")
        ));

        VanillaImportedAssetProvider provider = VanillaImportedAssetProvider.open(first.getVanillaRoot());
        assertTrue(provider.isAvailable(), provider.getDiagnostic());
        assertEquals(1413, provider.getGeneratedIcons());
        assertEquals(1506, provider.getTotalDefinitions());
        assertTrue(provider.contains("minecraft:stone"));
        BufferedImage diamond = provider.resolve("minecraft:diamond").orElseThrow(AssertionError::new);
        BufferedImage stone = provider.resolve("minecraft:stone").orElseThrow(AssertionError::new);
        assertEquals(32, diamond.getWidth(), "native Faithful 2D icons stay 32x32");
        assertEquals(32, diamond.getHeight(), "native Faithful 2D icons stay 32x32");
        assertEquals(64, stone.getWidth(), "3D block models must be baked at final slot resolution");
        assertEquals(64, stone.getHeight(), "3D block models must be baked at final slot resolution");
        BufferedImage chest = ImageIO.read(first.getThemesRoot().resolve(
            "faithful32x/overrides/items/minecraft/chest.png"
        ).toFile());
        BufferedImage bed = ImageIO.read(first.getThemesRoot().resolve(
            "faithful32x/overrides/items/minecraft/red_bed.png"
        ).toFile());
        assertEquals(64, chest.getWidth(), "special 3D chest override must render 1:1");
        assertEquals(64, bed.getWidth(), "special 3D bed override must render 1:1");
        try (Stream<Path> icons = Files.walk(first.getVanillaRoot().resolve(
            "26.1.2-B1B315857266-MB7-PD1337875/generated-icons"
        ))) {
            assertEquals(1413, icons.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".png")).count());
        }

        BundledAssetBootstrap.Installation second = BundledAssetBootstrap.install(dataRoot, classpath);
        assertEquals(0, second.getInstalledFiles());
        assertEquals(first.getInstalledFiles() + first.getReusedFiles(), second.getReusedFiles());

        Path managedChest = first.getThemesRoot().resolve(
            "faithful32x/overrides/items/minecraft/chest.png"
        );
        Path managedStone = first.getVanillaRoot().resolve(
            "26.1.2-B1B315857266-MB7-PD1337875/generated-icons/minecraft/stone.png"
        );
        Files.write(managedChest, "corrupt".getBytes(StandardCharsets.UTF_8));
        Files.delete(managedStone);
        BundledAssetBootstrap.Installation repaired = BundledAssetBootstrap.install(dataRoot, classpath);
        assertEquals(2, repaired.getInstalledFiles());
        assertResourceEquals(classpath, "bundled-assets/pack/themes/faithful32x/overrides/items/minecraft/chest.png", managedChest);
        assertResourceEquals(
            classpath,
            "bundled-assets/pack/vanilla/26.1.2-B1B315857266-MB7-PD1337875/generated-icons/minecraft/stone.png",
            managedStone
        );

        assertEquals("user-config", read(config));
        assertEquals("skin-cache", read(skin));
        assertEquals("snapshot", read(snapshot));
        assertEquals("custom-chest", read(custom));
        assertEquals("old-pack", read(oldPack));
        assertTrue(read(dataRoot.resolve("assets-version.json")).contains(first.getPackId()));
    }

    private static void assertResourceEquals(
        BundledAssetBootstrap.ResourceSource resources,
        String resource,
        Path extracted
    ) throws Exception {
        try (InputStream input = resources.open(resource)) {
            assertTrue(input != null, resource);
            assertArrayEquals(readAll(input), Files.readAllBytes(extracted));
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static Path write(Path path, String value) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, value.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
