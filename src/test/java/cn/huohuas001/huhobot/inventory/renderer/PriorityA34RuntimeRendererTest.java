package cn.huohuas001.huhobot.inventory.renderer;

import cn.huohuas001.huhobot.inventory.asset.VanillaImportedAssetProvider;
import cn.huohuas001.huhobot.inventory.model.ItemSnapshot;
import cn.huohuas001.huhobot.inventory.potion.PotionVisualDescriptor;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PriorityA34RuntimeRendererTest {
    @Test
    void routesTridentToGuiModelAndPotionFamilyToTintCompositor() throws Exception {
        Theme theme = ThemeLoader.load(
            Paths.get("src/main/resources/themes/faithful32x"),
            VanillaImportedAssetProvider.open(Paths.get("data/imported-assets/vanilla"))
        );
        TextureResolver resolver = theme.getTextures();
        List<TextureResolver.ResolutionTrace> traces = new ArrayList<TextureResolver.ResolutionTrace>();
        resolver.setResolutionReporter(traces::add);

        TextureResolver.ResolvedTexture trident = resolver.resolve(ItemSnapshot.basic("minecraft:trident", 1));
        assertEquals(TextureResolver.Source.GUI_MODEL, trident.getSource());
        assertFalse(trident.isFallback());

        TextureResolver.ResolvedTexture red = resolver.resolve(potion("minecraft:potion", 0xff2020));
        TextureResolver.ResolvedTexture blue = resolver.resolve(potion("minecraft:potion", 0x2040ff));
        assertEquals(TextureResolver.Source.RUNTIME_COMPOSITE, red.getSource());
        assertEquals(TextureResolver.Source.RUNTIME_COMPOSITE, blue.getSource());
        assertNotEquals(red.getImage().getRGB(15,20), blue.getImage().getRGB(15,20));

        for (String material : Arrays.asList(
            "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:tipped_arrow"
        )) {
            TextureResolver.ResolvedTexture resolved = resolver.resolve(potion(material, 0x4e9331));
            assertEquals(TextureResolver.Source.RUNTIME_COMPOSITE, resolved.getSource(), material);
            assertFalse(resolved.isFallback(), material);
        }

        TextureResolver.Coverage coverage = resolver.coverage(Arrays.asList(
            "minecraft:trident", "minecraft:potion", "minecraft:splash_potion",
            "minecraft:lingering_potion", "minecraft:tipped_arrow"
        ));
        assertEquals(5, coverage.getRuntimeComposite());
        assertEquals(0, coverage.getSpecialUnsupported());
        assertEquals(0, coverage.getUnknownCount());
        assertEquals("GENERATED_2D_GUI_MODEL", trace(traces, "minecraft:trident").getRenderPath());
        assertEquals("POTION_TINT", trace(traces, "minecraft:potion").getRenderPath());
        assertEquals(TextureResolver.Source.GUI_MODEL, trace(traces, "minecraft:trident").getSource());
        assertEquals(TextureResolver.Source.RUNTIME_COMPOSITE, trace(traces, "minecraft:potion").getSource());
    }

    private static TextureResolver.ResolutionTrace trace(
        List<TextureResolver.ResolutionTrace> traces, String material
    ) {
        for (TextureResolver.ResolutionTrace trace : traces) {
            if (material.equals(trace.getMaterialKey())) return trace;
        }
        throw new AssertionError("missing resolution trace for " + material);
    }

    private static ItemSnapshot potion(String material, int tint) {
        return new ItemSnapshot(
            material, 1, 0, 0, null, null, false, null, null,
            new PotionVisualDescriptor(material, "minecraft:test", tint, false, false)
        );
    }
}
