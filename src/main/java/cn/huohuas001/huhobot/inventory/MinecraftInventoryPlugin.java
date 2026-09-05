package cn.huohuas001.huhobot.inventory;

import cn.huohuas001.huhobot.api.ApiVersion;
import cn.huohuas001.huhobot.api.HuHoBotService;
import cn.huohuas001.huhobot.api.PluginDescriptor;
import cn.huohuas001.huhobot.inventory.asset.BundledAssetBootstrap;
import cn.huohuas001.huhobot.inventory.asset.VanillaImportedAssetProvider;
import cn.huohuas001.huhobot.inventory.armor.ArmorItemIconRenderer;
import cn.huohuas001.huhobot.inventory.armor.EquipmentAssetResolver;
import cn.huohuas001.huhobot.inventory.config.InventoryPluginConfig;
import cn.huohuas001.huhobot.inventory.host.EmbeddedHuHoBotHost;
import cn.huohuas001.huhobot.inventory.datasource.BukkitOnlineInventoryDataSource;
import cn.huohuas001.huhobot.inventory.datasource.BukkitOnlineEnderChestDataSource;
import cn.huohuas001.huhobot.inventory.datasource.MockInventoryDataSource;
import cn.huohuas001.huhobot.inventory.renderer.Java2DInventoryRenderer;
import cn.huohuas001.huhobot.inventory.renderer.EnderChestRenderer;
import cn.huohuas001.huhobot.inventory.renderer.Theme;
import cn.huohuas001.huhobot.inventory.renderer.ThemeLoader;
import cn.huohuas001.huhobot.inventory.renderer.TextureResolver;
import cn.huohuas001.huhobot.inventory.qq.InventoryButtonBridge;
import cn.huohuas001.huhobot.inventory.qq.QqInventoryButtonBridge;
import cn.huohuas001.huhobot.inventory.skin.PlayerPreviewService;
import cn.huohuas001.huhobot.inventory.skin.PlayerModelRenderer;
import cn.huohuas001.huhobot.inventory.skin.PlayerSkinProvider;
import cn.huohuas001.huhobot.inventory.skin.SkinsRestorerSkinProvider;
import cn.huohuas001.huhobot.inventory.snapshot.OfflineInventorySnapshotManager;
import cn.huohuas001.huhobot.inventory.snapshot.OfflineInventorySnapshotStore;
import org.bukkit.Material;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/** Independent Bukkit addon for Mock and exact online-player inventory PNG delivery. */
public final class MinecraftInventoryPlugin extends JavaPlugin {
    private InventoryAddonSession session;
    private OfflineInventorySnapshotManager snapshotManager;
    private OfflineInventorySnapshotManager enderChestSnapshotManager;
    private InventoryButtonBridge buttonBridge = InventoryButtonBridge.UNAVAILABLE;
    private EmbeddedHuHoBotHost embeddedHost;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            embeddedHost = EmbeddedHuHoBotHost.start(this);
            migrateConfigIfRequired();
            BundledAssetBootstrap.Installation bundledAssets = BundledAssetBootstrap.install(
                getDataFolder().toPath(), this::getResource
            );
            getLogger().info(
                "Bundled Inventory asset pack " + bundledAssets.getPackId() + " ready: " +
                    bundledAssets.getInstalledFiles() + " installed/repaired, " +
                    bundledAssets.getReusedFiles() + " reused"
            );
            InventoryPluginConfig config = InventoryPluginConfig.load(getConfig());
            Path themeDirectory = selectThemeDirectory(config.getThemeId(), bundledAssets);
            VanillaImportedAssetProvider bundledVanilla = VanillaImportedAssetProvider.open(
                bundledAssets.getVanillaRoot()
            );
            if (!bundledVanilla.isAvailable()) {
                throw new IllegalStateException(
                    "Bundled Vanilla asset pack is unavailable: " + bundledVanilla.getDiagnostic()
                );
            }
            Path externalCache = configuredPath(config.getVanillaCacheDirectory());
            VanillaImportedAssetProvider externalVanilla = config.isExternalVanillaPreferred()
                ? VanillaImportedAssetProvider.open(externalCache)
                : VanillaImportedAssetProvider.disabled(externalCache, "advanced external provider disabled");
            VanillaImportedAssetProvider vanilla = externalVanilla.isAvailable() ? externalVanilla : bundledVanilla;
            getLogger().info(
                "Inventory Vanilla icon provider: " +
                    (externalVanilla.isAvailable() ? "advanced external cache " : "bundled MB7 pack ") +
                    vanilla.getCacheRoot()
            );
            if (config.isExternalVanillaPreferred() && !externalVanilla.isAvailable()) {
                getLogger().warning(
                    "Advanced external Vanilla cache unavailable; using bundled MB7: " +
                        externalVanilla.getDiagnostic()
                );
            }
            Theme theme = ThemeLoader.load(
                themeDirectory,
                vanilla,
                bundledAssets.getCustomRoot().resolve("overrides/items")
            );
            EquipmentAssetResolver equipmentAssets = new EquipmentAssetResolver(
                bundledAssets.getPackRoot().resolve("armor")
            );
            theme.getTextures().setArmorItemRenderer(new ArmorItemIconRenderer(
                equipmentAssets,
                getDataFolder().toPath().resolve("cache").resolve("armor-items")
            ));
            if (!config.getThemeId().equals(theme.getId())) {
                throw new IllegalArgumentException(
                    "Configured theme " + config.getThemeId() + " contains descriptor id " + theme.getId()
                );
            }
            if (config.isDebugEnabled()) {
                theme.getTextures().setResolutionReporter(trace -> {
                    String message = "[InventoryAssets] " + trace.toLogMessage();
                    if (trace.getSource() == TextureResolver.Source.UNKNOWN) getLogger().warning(message);
                    else getLogger().info(message);
                });
                getLogger().info("Inventory debug logging enabled");
            }
            getLogger().info(
                "Loaded Vanilla assets for Minecraft " + vanilla.getMinecraftVersion() +
                    " (" + vanilla.getGeneratedIcons() + "/" + vanilla.getTotalDefinitions() + " static icons)"
            );
            writeCoverageReport(theme);

            Path skinCache = configuredPath(config.getSkinCacheDirectory());
            PlayerSkinProvider skinProvider = createSkinProvider(config, skinCache);
            Rectangle previewArea = theme.getLayout().getPlayerPreview();
            PlayerPreviewService previewService = new PlayerPreviewService(
                config.isPlayerPreviewEnabled(), skinProvider, skinCache, getLogger(),
                config.getPlayerPreviewMode(), equipmentAssets,
                previewArea == null ? PlayerModelRenderer.WIDTH : previewArea.width,
                previewArea == null ? PlayerModelRenderer.HEIGHT : previewArea.height
            );

            BukkitOnlineInventoryDataSource onlineSource = new BukkitOnlineInventoryDataSource(
                this, config.getOnlineSourceServer()
            );
            BukkitOnlineEnderChestDataSource enderChestSource = config.isEnderChestEnabled()
                ? new BukkitOnlineEnderChestDataSource(this, config.getEnderChestSourceServer())
                : null;
            OfflineInventorySnapshotStore snapshotStore = null;
            if (config.isOfflineInventoryEnabled()) {
                Path snapshotDirectory = configuredPath(config.getOfflineSnapshotDirectory());
                snapshotStore = new OfflineInventorySnapshotStore(
                    snapshotDirectory, getLogger(), config.isDebugEnabled()
                );
                snapshotManager = new OfflineInventorySnapshotManager(this, onlineSource, snapshotStore);
                snapshotManager.start(config.getOfflinePeriodicSaveSeconds());
                getLogger().info(
                    "Offline inventory snapshots enabled at " + snapshotDirectory +
                        " (periodic save " + config.getOfflinePeriodicSaveSeconds() + "s)"
                );
            }

            OfflineInventorySnapshotStore enderChestSnapshotStore = null;
            if (config.isEnderChestEnabled() && config.isOfflineEnderChestEnabled()) {
                Path snapshotDirectory = configuredPath(config.getOfflineEnderChestSnapshotDirectory());
                enderChestSnapshotStore = new OfflineInventorySnapshotStore(
                    snapshotDirectory, getLogger(), config.isDebugEnabled()
                );
                enderChestSnapshotManager = new OfflineInventorySnapshotManager(
                    this, enderChestSource, enderChestSnapshotStore
                );
                enderChestSnapshotManager.start(config.getOfflineEnderChestPeriodicSaveSeconds());
                getLogger().info(
                    "Offline Ender Chest snapshots enabled at " + snapshotDirectory +
                        " (periodic save " + config.getOfflineEnderChestPeriodicSaveSeconds() + "s)"
                );
            }

            HuHoBotService hostService = embeddedHost.getService();

            PluginDescriptor descriptor = new PluginDescriptor(
                "minecraft-inventory",
                getDescription().getName(),
                getDescription().getVersion(),
                ApiVersion.V1_3_0
            );
            buttonBridge = createButtonBridge();
            session = InventoryAddonSession.start(
                hostService,
                descriptor,
                config,
                new MockInventoryDataSource(config.getMockSourceServer()),
                onlineSource,
                new Java2DInventoryRenderer(theme),
                previewService,
                snapshotStore,
                enderChestSource,
                config.isEnderChestEnabled()
                    ? new EnderChestRenderer(theme, themeDirectory.resolve("ender-chest-background.png"))
                    : null,
                enderChestSnapshotStore,
                buttonBridge
            );
            getLogger().info(
                "Registered /" + config.getCommandName() + " mock command and /" +
                    config.getOnlineCommandName() + " [player] binding-aware online command through HuHoBot API " +
                    hostService.getApiVersion()
            );
            if (config.isEnderChestEnabled()) {
                getLogger().info(
                    "Registered /" + config.getEnderChestCommandName() +
                        " [player] binding-aware Ender Chest command"
                );
            }
            getLogger().info(
                "Using inventory theme " + theme.getId() + " " + theme.getVersion() +
                    " (" + theme.getAssetPackVersion() + ")"
            );
        } catch (Throwable error) {
            disable("Could not initialize the HuHoBot Minecraft Inventory addon", error);
        }
    }

    private PlayerSkinProvider createSkinProvider(InventoryPluginConfig config, Path skinCache) {
        if (!config.isPlayerPreviewEnabled() || "default".equals(config.getPlayerPreviewProvider())) {
            getLogger().info("Player preview provider: local default");
            return null;
        }
        if (!getServer().getPluginManager().isPluginEnabled("SkinsRestorer")) {
            getLogger().info("SkinsRestorer is not enabled; player preview will use the local default skin");
            return null;
        }
        try {
            PlayerSkinProvider provider = new SkinsRestorerSkinProvider(
                skinCache,
                config.isAllowSkinTextureDownloads(),
                config.getSkinConnectTimeoutMillis(),
                config.getSkinReadTimeoutMillis()
            );
            getLogger().info(
                "Player preview provider: SkinsRestorer v15; texture downloads " +
                    (config.isAllowSkinTextureDownloads() ? "enabled with local content cache" : "disabled")
            );
            return provider;
        } catch (Throwable error) {
            getLogger().log(Level.WARNING, "SkinsRestorer API is unavailable; using the local default skin", error);
            return null;
        }
    }

    private InventoryButtonBridge createButtonBridge() {
        try {
            return QqInventoryButtonBridge.connect(getLogger());
        } catch (Throwable error) {
            getLogger().log(
                Level.WARNING,
                "当前 HuHoBot 分支不支持 Inventory 的 QQ 按钮适配，将保留文字账号选择",
                error
            );
            return InventoryButtonBridge.UNAVAILABLE;
        }
    }

    private void migrateConfigIfRequired() throws Exception {
        int version = getConfig().getInt("config-version", 0);
        if (version > 0 && version < InventoryPluginConfig.CURRENT_VERSION) {
            Path configPath = getDataFolder().toPath().resolve("config.yml").toAbsolutePath().normalize();
            Path backupPath = getDataFolder().toPath().resolve("config-v" + version + "-backup.yml")
                .toAbsolutePath().normalize();
            if (Files.isRegularFile(configPath) && Files.notExists(backupPath)) {
                Files.copy(configPath, backupPath, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
        if (InventoryPluginConfig.migrateToCurrent(getConfig())) {
            saveConfig();
            reloadConfig();
            getLogger().info(
                "Migrated Inventory configuration from version " + version + " to " +
                    InventoryPluginConfig.CURRENT_VERSION
            );
        }
    }

    private Path configuredPath(String value) {
        Path configured = Paths.get(value);
        return (configured.isAbsolute() ? configured : getDataFolder().toPath().resolve(configured))
            .toAbsolutePath().normalize();
    }

    private Path selectThemeDirectory(
        String themeId,
        BundledAssetBootstrap.Installation bundledAssets
    ) {
        Path custom = bundledAssets.getCustomRoot().resolve("themes").resolve(themeId).normalize();
        if (Files.isRegularFile(custom.resolve("theme.yml"))) {
            getLogger().info("Using user-managed Inventory theme " + custom);
            return custom;
        }
        Path bundled = bundledAssets.getThemesRoot().resolve(themeId).normalize();
        if (Files.isRegularFile(bundled.resolve("theme.yml"))) return bundled;

        // Compatibility for older installations with a uniquely named custom theme.
        Path legacy = getDataFolder().toPath().resolve("themes").resolve(themeId).toAbsolutePath().normalize();
        if (Files.isRegularFile(legacy.resolve("theme.yml"))) {
            getLogger().warning(
                "Using legacy custom theme directory " + legacy +
                    "; move it to assets/custom/themes/" + themeId
            );
            return legacy;
        }
        throw new IllegalArgumentException("Inventory theme not found: " + themeId);
    }

    private void writeCoverageReport(Theme theme) throws Exception {
        List<String> materials = new ArrayList<String>();
        for (Material material : Material.values()) {
            if (material.isItem()) materials.add(material.getKey().toString());
        }
        TextureResolver.Coverage coverage = theme.getTextures().coverage(materials);
        List<String> lines = new ArrayList<String>();
        lines.add("HuHoBot Inventory asset coverage");
        lines.add("Theme: " + theme.getId());
        lines.add("Total server item materials: " + coverage.getTotal());
        lines.add("Explicit item overrides: " + coverage.getExplicitOverrides());
        lines.add("Model-baked/imported icons: " + coverage.getVanilla());
        lines.add("Runtime GUI/tint composites: " + coverage.getRuntimeComposite());
        lines.add("Legacy static fallback: " + coverage.getLegacyStatic());
        lines.add("Known special renderer unsupported: " + coverage.getSpecialUnsupported());
        lines.add("Unknown: " + coverage.getUnknownCount());
        lines.add("");
        lines.add("Unresolved materials:");
        lines.addAll(coverage.getUnknown());
        lines.add("");
        lines.add("Resolution paths (material<TAB>path):");
        lines.addAll(coverage.getResolutionPaths());
        Path report = getDataFolder().toPath().resolve("asset-coverage.txt").toAbsolutePath().normalize();
        Files.write(report, lines, StandardCharsets.UTF_8);
        getLogger().info(
            "Inventory asset coverage: Explicit=" + coverage.getExplicitOverrides() + "/" + coverage.getTotal() +
                " Baked=" + coverage.getVanilla() + " Runtime=" + coverage.getRuntimeComposite() +
                " Legacy=" + coverage.getLegacyStatic() +
                " SpecialUnsupported=" + coverage.getSpecialUnsupported() +
                " Unknown=" + coverage.getUnknownCount() +
                " (" + report + ")"
        );
    }

    @Override
    public void onDisable() {
        if (session != null) {
            session.close();
            session = null;
        }
        buttonBridge.close();
        buttonBridge = InventoryButtonBridge.UNAVAILABLE;
        if (snapshotManager != null) {
            snapshotManager.close();
            snapshotManager = null;
        }
        if (enderChestSnapshotManager != null) {
            enderChestSnapshotManager.close();
            enderChestSnapshotManager = null;
        }
        if (embeddedHost != null) {
            embeddedHost.close();
            embeddedHost = null;
        }
    }

    private void disable(String message, Throwable error) {
        if (error == null) getLogger().severe(message);
        else getLogger().log(Level.SEVERE, message, error);
        getServer().getPluginManager().disablePlugin(this);
    }
}
