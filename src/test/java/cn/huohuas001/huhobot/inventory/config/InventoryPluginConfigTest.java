package cn.huohuas001.huhobot.inventory.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryPluginConfigTest {
    @Test
    void migratesPhaseOneConfigThroughCurrentVersionWithoutOverwritingExistingValues() {
        YamlConfiguration yaml = phaseOneConfig();
        yaml.set("online.cooldown-seconds", 5);

        assertTrue(InventoryPluginConfig.migrateFromVersion1(yaml));
        assertEquals(InventoryPluginConfig.CURRENT_VERSION, yaml.getInt("config-version"));
        assertEquals(5, yaml.getInt("online.cooldown-seconds"));
        assertEquals("inventory", yaml.getString("online-command.name"));
        assertEquals("inventory.png", yaml.getString("online.image-file-name"));

        InventoryPluginConfig config = InventoryPluginConfig.load(yaml);
        assertEquals("inventorytest", config.getCommandName());
        assertEquals("inventory", config.getOnlineCommandName());
        assertEquals(5, config.getOnlineCooldownSeconds());
        assertEquals("Inventory: Steve", config.captionForOnline("Steve"));
        assertFalse(config.isVanillaAssetsEnabled());
        assertFalse(config.isExternalVanillaPreferred());
        assertEquals("data/imported-assets/vanilla", config.getVanillaCacheDirectory());
        assertTrue(config.isAllowLegacyUnverifiedBindings());
        assertTrue(config.isPlayerPreviewEnabled());
        assertEquals("auto", config.getPlayerPreviewProvider());
        assertEquals("cache/skins", config.getSkinCacheDirectory());
        assertEquals("3d", config.getPlayerPreviewMode());
        assertTrue(config.isOfflineInventoryEnabled());
        assertFalse(config.isAllowLegacyOfflineSnapshots());
        assertEquals(300, config.getOfflinePeriodicSaveSeconds());
        assertTrue(config.isEnderChestEnabled());
        assertEquals("enderchest", config.getEnderChestCommandName());
        assertTrue(config.getEnderChestCommandAliases().contains("末影箱"));
        assertEquals("local-paper", config.getEnderChestSourceServer());
        assertEquals("ender-chest.png", config.getEnderChestImageFileName());
        assertTrue(config.isOfflineEnderChestEnabled());
        assertFalse(config.isDebugEnabled());
        assertEquals("data/offline-ender-chest-snapshots", config.getOfflineEnderChestSnapshotDirectory());
        assertEquals(
            "你还没有绑定 Minecraft 账号，请先使用 /绑定 <游戏ID>。",
            config.getBindingRequiredMessage()
        );
        assertFalse(InventoryPluginConfig.migrateFromVersion1(yaml));
    }

    @Test
    void migratesVersionTwoAssetKeysAndRejectsRuntimeAutoImport() {
        YamlConfiguration yaml = phaseOneConfig();
        InventoryPluginConfig.migrateToCurrent(yaml);
        yaml.set("config-version", 2);
        yaml.set("assets", null);

        assertTrue(InventoryPluginConfig.migrateToCurrent(yaml));
        assertEquals(InventoryPluginConfig.CURRENT_VERSION, yaml.getInt("config-version"));
        assertFalse(yaml.getBoolean("assets.vanilla.enabled"));
        assertFalse(yaml.getBoolean("assets.vanilla.prefer-external"));
        assertFalse(yaml.getBoolean("assets.vanilla.auto-import"));

        yaml.set("assets.vanilla.auto-import", true);
        assertThrows(IllegalArgumentException.class, () -> InventoryPluginConfig.load(yaml));
    }

    @Test
    void migratesVersionThreeToBindingAwareConfigWithoutChangingAssetSettings() {
        YamlConfiguration yaml = phaseOneConfig();
        InventoryPluginConfig.migrateToCurrent(yaml);
        yaml.set("config-version", 3);
        yaml.set("binding", null);
        yaml.set("messages.binding-required", null);
        yaml.set("messages.binding-verification-required", null);

        assertTrue(InventoryPluginConfig.migrateToCurrent(yaml));
        assertEquals(InventoryPluginConfig.CURRENT_VERSION, yaml.getInt("config-version"));
        assertTrue(yaml.getBoolean("binding.allow-legacy-unverified"));
        assertFalse(yaml.getBoolean("assets.vanilla.enabled"));
    }

    @Test
    void rejectsCommandConflictsAndUnsafeCooldown() {
        YamlConfiguration conflict = phaseOneConfig();
        InventoryPluginConfig.migrateFromVersion1(conflict);
        conflict.set("online-command.name", "invtest");
        assertThrows(IllegalArgumentException.class, () -> InventoryPluginConfig.load(conflict));

        YamlConfiguration cooldown = phaseOneConfig();
        InventoryPluginConfig.migrateFromVersion1(cooldown);
        cooldown.set("online.cooldown-seconds", 61);
        assertThrows(IllegalArgumentException.class, () -> InventoryPluginConfig.load(cooldown));
    }

    @Test
    void migratesVersionFourPlayerPreviewSettingsAndValidatesTimeouts() {
        YamlConfiguration yaml = phaseOneConfig();
        InventoryPluginConfig.migrateToCurrent(yaml);
        yaml.set("config-version", 4);
        yaml.set("player-preview", null);

        assertTrue(InventoryPluginConfig.migrateToCurrent(yaml));
        assertEquals(InventoryPluginConfig.CURRENT_VERSION, yaml.getInt("config-version"));
        assertTrue(yaml.getBoolean("player-preview.enabled"));
        assertEquals("auto", yaml.getString("player-preview.provider"));
        assertEquals("3d", yaml.getString("player-preview.mode"));

        yaml.set("player-preview.read-timeout-ms", 50);
        assertThrows(IllegalArgumentException.class, () -> InventoryPluginConfig.load(yaml));
    }

    @Test
    void migratesVersionFiveWithoutOverwritingOfflineTexturePolicy() {
        YamlConfiguration yaml = phaseOneConfig();
        InventoryPluginConfig.migrateToCurrent(yaml);
        yaml.set("config-version", 5);
        yaml.set("player-preview.allow-texture-downloads", false);
        yaml.set("player-preview.mode", null);
        yaml.set("offline-inventory", null);

        assertTrue(InventoryPluginConfig.migrateToCurrent(yaml));
        assertEquals(InventoryPluginConfig.CURRENT_VERSION, yaml.getInt("config-version"));
        assertFalse(yaml.getBoolean("player-preview.allow-texture-downloads"));
        assertEquals("3d", yaml.getString("player-preview.mode"));
        assertTrue(yaml.getBoolean("offline-inventory.enabled"));
        assertFalse(yaml.getBoolean("offline-inventory.allow-legacy-unverified"));
    }

    @Test
    void migratesDeployedVersionSevenToIndependentEnderChestSettings() {
        YamlConfiguration yaml = phaseOneConfig();
        InventoryPluginConfig.migrateToCurrent(yaml);
        yaml.set("config-version", 7);
        yaml.set("ender-chest-command", null);
        yaml.set("ender-chest", null);
        yaml.set("offline-ender-chest", null);
        yaml.set("messages.ender-chest-failure", null);

        assertTrue(InventoryPluginConfig.migrateToCurrent(yaml));
        assertEquals(InventoryPluginConfig.CURRENT_VERSION, yaml.getInt("config-version"));
        assertEquals("enderchest", yaml.getString("ender-chest-command.name"));
        assertEquals(java.util.Arrays.asList("ec", "末影箱"), yaml.getStringList("ender-chest-command.aliases"));
        assertEquals("data/offline-ender-chest-snapshots", yaml.getString("offline-ender-chest.directory"));
        assertEquals("末影箱图片生成或发送失败，请稍后再试。", yaml.getString("messages.ender-chest-failure"));
        assertTrue(InventoryPluginConfig.load(yaml).isEnderChestEnabled());
    }

    @Test
    void debugLoggingIsOffByDefaultAndCanBeEnabledExplicitly() {
        YamlConfiguration yaml = phaseOneConfig();
        InventoryPluginConfig.migrateToCurrent(yaml);

        assertFalse(yaml.getBoolean("debug"));
        assertFalse(InventoryPluginConfig.load(yaml).isDebugEnabled());

        yaml.set("debug", true);
        assertTrue(InventoryPluginConfig.load(yaml).isDebugEnabled());
    }

    private static YamlConfiguration phaseOneConfig() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("config-version", 1);
        yaml.set("command.name", "inventorytest");
        yaml.set("command.aliases", Collections.singletonList("invtest"));
        yaml.set("command.publish-to-menu", false);
        yaml.set("mock.player-name", "MockPlayer");
        yaml.set("mock.source-server", "mock-paper");
        yaml.set("render.theme", "faithful32x");
        yaml.set("render.max-output-bytes", 4 * 1024 * 1024);
        yaml.set("render.image-file-name", "mock-inventory.png");
        yaml.set("render.optional-caption", "Mock inventory: %player%");
        yaml.set("messages.failure", "背包图片生成或发送失败，请稍后再试");
        return yaml;
    }
}
