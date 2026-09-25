package cn.huohuas001.huhobot.inventory.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomBackgroundConfigTest {
    @Test
    void loadsSafeLocalPngSettingsAndReusesInventoryForEnderChest() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("render.custom-background.enabled", true);
        yaml.set("render.custom-background.inventory-file", "my-wallpaper.png");
        yaml.set("render.custom-background.ender-chest-file", "");
        yaml.set("render.custom-background.fit", "cover");

        CustomBackgroundConfig config = CustomBackgroundConfig.load(yaml);
        Path root = Paths.get("build", "custom-backgrounds").toAbsolutePath().normalize();
        assertTrue(config.isEnabled());
        assertEquals("cover", config.getFit());
        assertEquals(root.resolve("my-wallpaper.png"), config.inventoryPath(root));
        assertEquals(config.inventoryPath(root), config.enderChestPath(root));
    }

    @Test
    void rejectsTraversalAndUnknownFitMode() {
        YamlConfiguration traversal = new YamlConfiguration();
        traversal.set("render.custom-background.inventory-file", "../outside.png");
        assertThrows(IllegalArgumentException.class, () -> CustomBackgroundConfig.load(traversal));

        YamlConfiguration fit = new YamlConfiguration();
        fit.set("render.custom-background.fit", "contain");
        assertThrows(IllegalArgumentException.class, () -> CustomBackgroundConfig.load(fit));
    }
}
