package cn.huohuas001.huhobot.inventory.renderer;

import cn.huohuas001.huhobot.inventory.asset.VanillaImportedAssetProvider;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Loads the same generated item icons that are bundled for production previews. */
final class RendererTestAssets {
    static final Path FAITHFUL_THEME = Paths.get(
        "src", "main", "resources", "themes", "faithful32x"
    );

    private RendererTestAssets() {}

    static Theme loadProductionFaithfulTheme() {
        VanillaImportedAssetProvider imported = VanillaImportedAssetProvider.open(
            Paths.get("data", "imported-assets", "vanilla")
        );
        if (!imported.isAvailable()) {
            throw new IllegalStateException(
                "Bundled generated item icons are required for production-style render previews"
            );
        }
        return ThemeLoader.load(FAITHFUL_THEME, imported);
    }
}
