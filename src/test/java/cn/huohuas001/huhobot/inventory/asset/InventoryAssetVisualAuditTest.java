package cn.huohuas001.huhobot.inventory.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryAssetVisualAuditTest {
    @Test
    void writesFullReportsSheetsSuspectsAndExplicitOverrides(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("cache");
        Path cache = root.resolve("test-MB6");
        Path icons = cache.resolve("generated-icons/minecraft");
        Files.createDirectories(icons);
        Files.write(root.resolve("current-version.txt"), Arrays.asList("test-MB6"), StandardCharsets.UTF_8);
        Files.write(cache.resolve("metadata.json"), ("{\"minecraftVersion\":\"test\"," +
            "\"textureResourcePack\":\"faithful.zip\",\"generatedIcons\":2,\"totalDefinitions\":3}")
            .getBytes(StandardCharsets.UTF_8));
        Files.write(cache.resolve("coverage.json"), ("{\"unresolved\":[{" +
            "\"material\":\"minecraft:chest\",\"reason\":\"SPECIAL_RENDERER\"}]}")
            .getBytes(StandardCharsets.UTF_8));
        Files.write(cache.resolve("render-paths.tsv"), Arrays.asList(
            "# Material\tRenderPath", "minecraft:stone\tBLOCK_MODEL", "minecraft:diamond\tGENERATED_2D"
        ), StandardCharsets.UTF_8);
        image(icons.resolve("stone.png"), true);
        image(icons.resolve("diamond.png"), false);
        Path overrides = temp.resolve("overrides/minecraft");
        Files.createDirectories(overrides);
        image(overrides.resolve("chest.png"), false);

        InventoryAssetVisualAudit.AuditResult result = new InventoryAssetVisualAudit().audit(
            root, temp.resolve("visual-audit"), temp.resolve("overrides")
        );

        assertEquals(3, result.getTotal());
        assertEquals(2, result.getGenerated());
        assertEquals(1, result.getExplicit());
        Path output = result.getOutput();
        assertTrue(Files.isRegularFile(output.resolve("reports/icon-audit.tsv")));
        assertTrue(Files.isRegularFile(output.resolve("reports/suspects.tsv")));
        assertTrue(Files.isRegularFile(output.resolve("reports/special-items.tsv")));
        assertTrue(Files.isRegularFile(output.resolve("reports/audit-status.tsv")));
        assertTrue(Files.isRegularFile(output.resolve("individual/index.tsv")));
        assertTrue(Files.isRegularFile(output.resolve("sheets/render-path/block-model/sheet-001.png")));
        assertTrue(Files.isRegularFile(output.resolve("sheets/render-path/generated-2d/sheet-001.png")));
        assertTrue(Files.isRegularFile(output.resolve("sheets/render-path/explicit-override/sheet-001.png")));
        String report = new String(Files.readAllBytes(output.resolve("reports/icon-audit.tsv")), StandardCharsets.UTF_8);
        assertTrue(report.contains("minecraft:chest\tSPECIAL\tEXPLICIT_OVERRIDE"));
        assertTrue(report.contains("source_model\tparent_model\ttexture_source"));
        String summary = new String(Files.readAllBytes(output.resolve("reports/audit-summary.json")), StandardCharsets.UTF_8);
        assertTrue(summary.contains("\"visualPassed\": 0"));
        assertTrue(summary.contains("\"generatedCoverage\": 2"));
    }

    private static void image(Path path, boolean flat) throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = flat ? 0 : 8; y < (flat ? 32 : 24); y++) {
            for (int x = flat ? 0 : 8; x < (flat ? 32 : 24); x++) image.setRGB(x, y, new Color(110, 150, 190).getRGB());
        }
        ImageIO.write(image, "png", path.toFile());
    }
}
