package cn.huohuas001.huhobot.inventory.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Validated configuration owned exclusively by the Inventory addon. */
public final class InventoryPluginConfig {
    public static final int CURRENT_VERSION = 8;

    private final String commandName;
    private final List<String> commandAliases;
    private final boolean publishToMenu;
    private final String onlineCommandName;
    private final List<String> onlineCommandAliases;
    private final boolean onlinePublishToMenu;
    private final boolean enderChestEnabled;
    private final String enderChestCommandName;
    private final List<String> enderChestCommandAliases;
    private final boolean enderChestPublishToMenu;
    private final String mockPlayerName;
    private final String mockSourceServer;
    private final String onlineSourceServer;
    private final int onlineCooldownSeconds;
    private final String enderChestSourceServer;
    private final int enderChestCooldownSeconds;
    private final boolean allowLegacyUnverifiedBindings;
    private final boolean offlineInventoryEnabled;
    private final boolean allowLegacyOfflineSnapshots;
    private final String offlineSnapshotDirectory;
    private final int offlinePeriodicSaveSeconds;
    private final boolean offlineEnderChestEnabled;
    private final boolean allowLegacyOfflineEnderChestSnapshots;
    private final String offlineEnderChestSnapshotDirectory;
    private final int offlineEnderChestPeriodicSaveSeconds;
    private final String themeId;
    private final boolean vanillaAssetsEnabled;
    private final String vanillaCacheDirectory;
    private final String vanillaMinecraftJar;
    private final boolean vanillaAutoImport;
    private final boolean playerPreviewEnabled;
    private final String playerPreviewProvider;
    private final String playerPreviewMode;
    private final String skinCacheDirectory;
    private final boolean allowSkinTextureDownloads;
    private final int skinConnectTimeoutMillis;
    private final int skinReadTimeoutMillis;
    private final int maxOutputBytes;
    private final String imageFileName;
    private final String optionalCaption;
    private final String onlineImageFileName;
    private final String onlineOptionalCaption;
    private final String enderChestImageFileName;
    private final String enderChestOptionalCaption;
    private final String failureMessage;
    private final String usageMessage;
    private final String playerOfflineMessage;
    private final String playerStateChangedMessage;
    private final String notAuthorizedMessage;
    private final String cooldownMessage;
    private final String bindingRequiredMessage;
    private final String bindingVerificationRequiredMessage;
    private final String offlineSnapshotMissingMessage;
    private final String offlineLegacyDeniedMessage;
    private final String enderChestFailureMessage;
    private final String enderChestUsageMessage;
    private final String enderChestPlayerOfflineMessage;
    private final String enderChestPlayerStateChangedMessage;
    private final String enderChestNotAuthorizedMessage;
    private final String enderChestOfflineSnapshotMissingMessage;
    private final String enderChestOfflineLegacyDeniedMessage;

    private InventoryPluginConfig(
        String commandName,
        List<String> commandAliases,
        boolean publishToMenu,
        String onlineCommandName,
        List<String> onlineCommandAliases,
        boolean onlinePublishToMenu,
        boolean enderChestEnabled,
        String enderChestCommandName,
        List<String> enderChestCommandAliases,
        boolean enderChestPublishToMenu,
        String mockPlayerName,
        String mockSourceServer,
        String onlineSourceServer,
        int onlineCooldownSeconds,
        String enderChestSourceServer,
        int enderChestCooldownSeconds,
        boolean allowLegacyUnverifiedBindings,
        boolean offlineInventoryEnabled,
        boolean allowLegacyOfflineSnapshots,
        String offlineSnapshotDirectory,
        int offlinePeriodicSaveSeconds,
        boolean offlineEnderChestEnabled,
        boolean allowLegacyOfflineEnderChestSnapshots,
        String offlineEnderChestSnapshotDirectory,
        int offlineEnderChestPeriodicSaveSeconds,
        String themeId,
        boolean vanillaAssetsEnabled,
        String vanillaCacheDirectory,
        String vanillaMinecraftJar,
        boolean vanillaAutoImport,
        boolean playerPreviewEnabled,
        String playerPreviewProvider,
        String playerPreviewMode,
        String skinCacheDirectory,
        boolean allowSkinTextureDownloads,
        int skinConnectTimeoutMillis,
        int skinReadTimeoutMillis,
        int maxOutputBytes,
        String imageFileName,
        String optionalCaption,
        String onlineImageFileName,
        String onlineOptionalCaption,
        String enderChestImageFileName,
        String enderChestOptionalCaption,
        String failureMessage,
        String usageMessage,
        String playerOfflineMessage,
        String playerStateChangedMessage,
        String notAuthorizedMessage,
        String cooldownMessage,
        String bindingRequiredMessage,
        String bindingVerificationRequiredMessage,
        String offlineSnapshotMissingMessage,
        String offlineLegacyDeniedMessage,
        String enderChestFailureMessage,
        String enderChestUsageMessage,
        String enderChestPlayerOfflineMessage,
        String enderChestPlayerStateChangedMessage,
        String enderChestNotAuthorizedMessage,
        String enderChestOfflineSnapshotMissingMessage,
        String enderChestOfflineLegacyDeniedMessage
    ) {
        this.commandName = normalizeCommand(commandName, "command.name");
        this.commandAliases = normalizedAliases(commandAliases, "command.aliases", this.commandName);
        this.publishToMenu = publishToMenu;
        this.onlineCommandName = normalizeCommand(onlineCommandName, "online-command.name");
        this.onlineCommandAliases = normalizedAliases(
            onlineCommandAliases,
            "online-command.aliases",
            this.onlineCommandName
        );
        this.onlinePublishToMenu = onlinePublishToMenu;
        this.enderChestEnabled = enderChestEnabled;
        this.enderChestCommandName = normalizeCommand(enderChestCommandName, "ender-chest-command.name");
        this.enderChestCommandAliases = normalizedAliases(
            enderChestCommandAliases,
            "ender-chest-command.aliases",
            this.enderChestCommandName
        );
        this.enderChestPublishToMenu = enderChestPublishToMenu;
        ensureDistinctCommands();

        this.mockPlayerName = requireText(mockPlayerName, "mock.player-name");
        this.mockSourceServer = requireText(mockSourceServer, "mock.source-server");
        this.onlineSourceServer = requireText(onlineSourceServer, "online.source-server");
        if (onlineCooldownSeconds < 0 || onlineCooldownSeconds > 60) {
            throw new IllegalArgumentException("online.cooldown-seconds must be between 0 and 60");
        }
        this.onlineCooldownSeconds = onlineCooldownSeconds;
        this.enderChestSourceServer = requireText(enderChestSourceServer, "ender-chest.source-server");
        if (enderChestCooldownSeconds < 0 || enderChestCooldownSeconds > 60) {
            throw new IllegalArgumentException("ender-chest.cooldown-seconds must be between 0 and 60");
        }
        this.enderChestCooldownSeconds = enderChestCooldownSeconds;
        this.allowLegacyUnverifiedBindings = allowLegacyUnverifiedBindings;
        this.offlineInventoryEnabled = offlineInventoryEnabled;
        this.allowLegacyOfflineSnapshots = allowLegacyOfflineSnapshots;
        this.offlineSnapshotDirectory = requirePathText(offlineSnapshotDirectory, "offline-inventory.directory");
        if (offlinePeriodicSaveSeconds != 0 && (offlinePeriodicSaveSeconds < 60 || offlinePeriodicSaveSeconds > 86400)) {
            throw new IllegalArgumentException("offline-inventory.periodic-save-seconds must be 0 or between 60 and 86400");
        }
        this.offlinePeriodicSaveSeconds = offlinePeriodicSaveSeconds;
        this.offlineEnderChestEnabled = offlineEnderChestEnabled;
        this.allowLegacyOfflineEnderChestSnapshots = allowLegacyOfflineEnderChestSnapshots;
        this.offlineEnderChestSnapshotDirectory = requirePathText(
            offlineEnderChestSnapshotDirectory, "offline-ender-chest.directory"
        );
        if (offlineEnderChestPeriodicSaveSeconds != 0 &&
            (offlineEnderChestPeriodicSaveSeconds < 60 || offlineEnderChestPeriodicSaveSeconds > 86400)) {
            throw new IllegalArgumentException(
                "offline-ender-chest.periodic-save-seconds must be 0 or between 60 and 86400"
            );
        }
        this.offlineEnderChestPeriodicSaveSeconds = offlineEnderChestPeriodicSaveSeconds;

        this.themeId = requireSafeName(themeId, "render.theme");
        this.vanillaAssetsEnabled = vanillaAssetsEnabled;
        this.vanillaCacheDirectory = requirePathText(vanillaCacheDirectory, "assets.vanilla.cache-directory");
        this.vanillaMinecraftJar = normalizeOptional(vanillaMinecraftJar);
        this.vanillaAutoImport = vanillaAutoImport;
        if (vanillaAutoImport) {
            throw new IllegalArgumentException(
                "assets.vanilla.auto-import is not supported in Paper; use the offline VanillaAssetImporter"
            );
        }
        this.playerPreviewEnabled = playerPreviewEnabled;
        this.playerPreviewProvider = requireText(playerPreviewProvider, "player-preview.provider")
            .toLowerCase(Locale.ROOT);
        if (!"auto".equals(this.playerPreviewProvider) && !"default".equals(this.playerPreviewProvider)) {
            throw new IllegalArgumentException("player-preview.provider must be auto or default");
        }
        this.playerPreviewMode = requireText(playerPreviewMode, "player-preview.mode").toLowerCase(Locale.ROOT);
        if (!"2d".equals(this.playerPreviewMode) && !"3d".equals(this.playerPreviewMode)) {
            throw new IllegalArgumentException("player-preview.mode must be 2d or 3d");
        }
        this.skinCacheDirectory = requirePathText(skinCacheDirectory, "player-preview.cache-directory");
        this.allowSkinTextureDownloads = allowSkinTextureDownloads;
        this.skinConnectTimeoutMillis = timeout(skinConnectTimeoutMillis, "player-preview.connect-timeout-ms");
        this.skinReadTimeoutMillis = timeout(skinReadTimeoutMillis, "player-preview.read-timeout-ms");
        if (maxOutputBytes < 1024 || maxOutputBytes > 8 * 1024 * 1024) {
            throw new IllegalArgumentException("render.max-output-bytes must be between 1024 and 8388608");
        }
        this.maxOutputBytes = maxOutputBytes;
        this.imageFileName = requireSafeFileName(imageFileName, "render.image-file-name");
        this.optionalCaption = normalizeOptional(optionalCaption);
        this.onlineImageFileName = requireSafeFileName(onlineImageFileName, "online.image-file-name");
        this.onlineOptionalCaption = normalizeOptional(onlineOptionalCaption);
        this.enderChestImageFileName = requireSafeFileName(
            enderChestImageFileName, "ender-chest.image-file-name"
        );
        this.enderChestOptionalCaption = normalizeOptional(enderChestOptionalCaption);

        this.failureMessage = requireText(failureMessage, "messages.failure");
        this.usageMessage = requireText(usageMessage, "messages.usage");
        this.playerOfflineMessage = requireText(playerOfflineMessage, "messages.player-offline");
        this.playerStateChangedMessage = requireText(
            playerStateChangedMessage,
            "messages.player-state-changed"
        );
        this.notAuthorizedMessage = requireText(notAuthorizedMessage, "messages.not-authorized");
        this.cooldownMessage = requireText(cooldownMessage, "messages.cooldown");
        this.bindingRequiredMessage = requireText(bindingRequiredMessage, "messages.binding-required");
        this.bindingVerificationRequiredMessage = requireText(
            bindingVerificationRequiredMessage,
            "messages.binding-verification-required"
        );
        this.offlineSnapshotMissingMessage = requireText(
            offlineSnapshotMissingMessage, "messages.offline-snapshot-missing"
        );
        this.offlineLegacyDeniedMessage = requireText(
            offlineLegacyDeniedMessage, "messages.offline-legacy-denied"
        );
        this.enderChestFailureMessage = requireText(
            enderChestFailureMessage, "messages.ender-chest-failure"
        );
        this.enderChestUsageMessage = requireText(enderChestUsageMessage, "messages.ender-chest-usage");
        this.enderChestPlayerOfflineMessage = requireText(
            enderChestPlayerOfflineMessage, "messages.ender-chest-player-offline"
        );
        this.enderChestPlayerStateChangedMessage = requireText(
            enderChestPlayerStateChangedMessage, "messages.ender-chest-player-state-changed"
        );
        this.enderChestNotAuthorizedMessage = requireText(
            enderChestNotAuthorizedMessage, "messages.ender-chest-not-authorized"
        );
        this.enderChestOfflineSnapshotMissingMessage = requireText(
            enderChestOfflineSnapshotMissingMessage, "messages.ender-chest-offline-snapshot-missing"
        );
        this.enderChestOfflineLegacyDeniedMessage = requireText(
            enderChestOfflineLegacyDeniedMessage, "messages.ender-chest-offline-legacy-denied"
        );
    }

    /** Adds missing Phase 2/asset-pipeline keys without overwriting existing values. */
    public static boolean migrateToCurrent(FileConfiguration config) {
        Objects.requireNonNull(config, "config");
        int version = config.getInt("config-version", 0);
        if (version == CURRENT_VERSION) return false;
        if (version < 1 || version > CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported config-version " + version);
        }
        if (version == 1) {
            setIfMissing(config, "online-command.name", "inventory");
            setIfMissing(config, "online-command.aliases", java.util.Arrays.asList("inv", "背包"));
            setIfMissing(config, "online-command.publish-to-menu", false);
            setIfMissing(config, "online.source-server", "local-paper");
            setIfMissing(config, "online.cooldown-seconds", 3);
            setIfMissing(config, "online.image-file-name", "inventory.png");
            setIfMissing(config, "online.optional-caption", "Inventory: %player%");
            setIfMissing(config, "messages.usage", "用法：/背包，管理员可使用 /背包 <在线玩家名>");
            setIfMissing(config, "messages.player-offline", "玩家当前不在线，暂时无法查询背包。");
            setIfMissing(config, "messages.player-state-changed", "玩家状态发生变化，请重新查询。");
            setIfMissing(config, "messages.not-authorized", "权限不足，无法查询在线玩家背包。");
            setIfMissing(config, "messages.cooldown", "查询过于频繁，请稍后再试。");
        }
        setIfMissing(config, "binding.allow-legacy-unverified", true);
        setIfMissing(config, "messages.binding-required", "你还没有绑定 Minecraft 账号，请先使用 /绑定 <游戏ID>。");
        setIfMissing(
            config,
            "messages.binding-verification-required",
            "当前绑定尚未完成 Minecraft 账号验证，暂时不能查询背包。"
        );
        setIfMissing(config, "assets.vanilla.enabled", false);
        setIfMissing(config, "assets.vanilla.prefer-external", false);
        setIfMissing(config, "assets.vanilla.cache-directory", "data/imported-assets/vanilla");
        setIfMissing(config, "assets.vanilla.minecraft-jar", "");
        setIfMissing(config, "assets.vanilla.auto-import", false);
        setIfMissing(config, "player-preview.enabled", true);
        setIfMissing(config, "player-preview.provider", "auto");
        setIfMissing(config, "player-preview.mode", "3d");
        setIfMissing(config, "player-preview.cache-directory", "cache/skins");
        setIfMissing(config, "player-preview.allow-texture-downloads", true);
        setIfMissing(config, "player-preview.connect-timeout-ms", 4000);
        setIfMissing(config, "player-preview.read-timeout-ms", 8000);
        setIfMissing(config, "offline-inventory.enabled", true);
        setIfMissing(config, "offline-inventory.allow-legacy-unverified", false);
        setIfMissing(config, "offline-inventory.directory", "data/offline-snapshots");
        setIfMissing(config, "offline-inventory.periodic-save-seconds", 300);
        setIfMissing(
            config,
            "messages.offline-snapshot-missing",
            "暂时没有该玩家的离线背包快照，请等待玩家至少登录服务器一次。"
        );
        setIfMissing(
            config,
            "messages.offline-legacy-denied",
            "当前旧版绑定未完成游戏内验证，不能读取持久化离线背包快照。"
        );
        setIfMissing(config, "ender-chest-command.name", "enderchest");
        setIfMissing(config, "ender-chest-command.aliases", java.util.Arrays.asList("ec", "末影箱"));
        setIfMissing(config, "ender-chest-command.publish-to-menu", false);
        setIfMissing(config, "ender-chest.enabled", true);
        setIfMissing(config, "ender-chest.source-server", "local-paper");
        setIfMissing(config, "ender-chest.cooldown-seconds", 3);
        setIfMissing(config, "ender-chest.image-file-name", "ender-chest.png");
        setIfMissing(config, "ender-chest.optional-caption", "Ender Chest: %player%");
        setIfMissing(config, "offline-ender-chest.enabled", true);
        setIfMissing(config, "offline-ender-chest.allow-legacy-unverified", false);
        setIfMissing(config, "offline-ender-chest.directory", "data/offline-ender-chest-snapshots");
        setIfMissing(config, "offline-ender-chest.periodic-save-seconds", 300);
        setIfMissing(config, "messages.ender-chest-failure", "末影箱图片生成或发送失败，请稍后再试。");
        setIfMissing(config, "messages.ender-chest-usage", "用法：/末影箱，管理员可使用 /末影箱 <在线玩家名>");
        setIfMissing(config, "messages.ender-chest-player-offline", "玩家当前不在线，且暂时没有可用的末影箱快照。");
        setIfMissing(config, "messages.ender-chest-player-state-changed", "玩家状态发生变化，请重新查询末影箱。");
        setIfMissing(config, "messages.ender-chest-not-authorized", "权限不足，无法查询其他玩家的末影箱。");
        setIfMissing(
            config,
            "messages.ender-chest-offline-snapshot-missing",
            "暂时没有该玩家的离线末影箱快照，请等待玩家至少登录服务器一次。"
        );
        setIfMissing(
            config,
            "messages.ender-chest-offline-legacy-denied",
            "当前旧版绑定未完成游戏内验证，不能读取持久化离线末影箱快照。"
        );
        config.set("config-version", CURRENT_VERSION);
        return true;
    }

    /** Compatibility entry point retained for older callers. */
    public static boolean migrateFromVersion1(FileConfiguration config) {
        return migrateToCurrent(config);
    }

    public static InventoryPluginConfig load(FileConfiguration config) {
        Objects.requireNonNull(config, "config");
        int version = config.getInt("config-version", 0);
        if (version != CURRENT_VERSION) throw new IllegalArgumentException("Unsupported config-version " + version);
        return new InventoryPluginConfig(
            config.getString("command.name", "inventorytest"),
            config.getStringList("command.aliases"),
            config.getBoolean("command.publish-to-menu", false),
            config.getString("online-command.name", "inventory"),
            config.getStringList("online-command.aliases"),
            config.getBoolean("online-command.publish-to-menu", false),
            config.getBoolean("ender-chest.enabled", true),
            config.getString("ender-chest-command.name", "enderchest"),
            config.getStringList("ender-chest-command.aliases"),
            config.getBoolean("ender-chest-command.publish-to-menu", false),
            config.getString("mock.player-name", "MockPlayer"),
            config.getString("mock.source-server", "mock-paper"),
            config.getString("online.source-server", "local-paper"),
            config.getInt("online.cooldown-seconds", 3),
            config.getString("ender-chest.source-server", "local-paper"),
            config.getInt("ender-chest.cooldown-seconds", 3),
            config.getBoolean("binding.allow-legacy-unverified", true),
            config.getBoolean("offline-inventory.enabled", true),
            config.getBoolean("offline-inventory.allow-legacy-unverified", false),
            config.getString("offline-inventory.directory", "data/offline-snapshots"),
            config.getInt("offline-inventory.periodic-save-seconds", 300),
            config.getBoolean("offline-ender-chest.enabled", true),
            config.getBoolean("offline-ender-chest.allow-legacy-unverified", false),
            config.getString("offline-ender-chest.directory", "data/offline-ender-chest-snapshots"),
            config.getInt("offline-ender-chest.periodic-save-seconds", 300),
            config.getString("render.theme", "faithful32x"),
            config.getBoolean("assets.vanilla.prefer-external", false),
            config.getString("assets.vanilla.cache-directory", "data/imported-assets/vanilla"),
            config.getString("assets.vanilla.minecraft-jar", ""),
            config.getBoolean("assets.vanilla.auto-import", false),
            config.getBoolean("player-preview.enabled", true),
            config.getString("player-preview.provider", "auto"),
            config.getString("player-preview.mode", "3d"),
            config.getString("player-preview.cache-directory", "cache/skins"),
            config.getBoolean("player-preview.allow-texture-downloads", true),
            config.getInt("player-preview.connect-timeout-ms", 4000),
            config.getInt("player-preview.read-timeout-ms", 8000),
            config.getInt("render.max-output-bytes", 4 * 1024 * 1024),
            config.getString("render.image-file-name", "mock-inventory.png"),
            config.getString("render.optional-caption", ""),
            config.getString("online.image-file-name", "inventory.png"),
            config.getString("online.optional-caption", "Inventory: %player%"),
            config.getString("ender-chest.image-file-name", "ender-chest.png"),
            config.getString("ender-chest.optional-caption", "Ender Chest: %player%"),
            config.getString("messages.failure", "背包图片生成或发送失败，请稍后再试"),
            config.getString("messages.usage", "用法：/背包，管理员可使用 /背包 <在线玩家名>"),
            config.getString("messages.player-offline", "玩家当前不在线，暂时无法查询背包。"),
            config.getString("messages.player-state-changed", "玩家状态发生变化，请重新查询。"),
            config.getString("messages.not-authorized", "权限不足，无法查询在线玩家背包。"),
            config.getString("messages.cooldown", "查询过于频繁，请稍后再试。"),
            config.getString(
                "messages.binding-required",
                "你还没有绑定 Minecraft 账号，请先使用 /绑定 <游戏ID>。"
            ),
            config.getString(
                "messages.binding-verification-required",
                "当前绑定尚未完成 Minecraft 账号验证，暂时不能查询背包。"
            ),
            config.getString(
                "messages.offline-snapshot-missing",
                "暂时没有该玩家的离线背包快照，请等待玩家至少登录服务器一次。"
            ),
            config.getString(
                "messages.offline-legacy-denied",
                "当前旧版绑定未完成游戏内验证，不能读取持久化离线背包快照。"
            ),
            config.getString("messages.ender-chest-failure", "末影箱图片生成或发送失败，请稍后再试。"),
            config.getString("messages.ender-chest-usage", "用法：/末影箱，管理员可使用 /末影箱 <在线玩家名>"),
            config.getString(
                "messages.ender-chest-player-offline",
                "玩家当前不在线，且暂时没有可用的末影箱快照。"
            ),
            config.getString(
                "messages.ender-chest-player-state-changed",
                "玩家状态发生变化，请重新查询末影箱。"
            ),
            config.getString("messages.ender-chest-not-authorized", "权限不足，无法查询其他玩家的末影箱。"),
            config.getString(
                "messages.ender-chest-offline-snapshot-missing",
                "暂时没有该玩家的离线末影箱快照，请等待玩家至少登录服务器一次。"
            ),
            config.getString(
                "messages.ender-chest-offline-legacy-denied",
                "当前旧版绑定未完成游戏内验证，不能读取持久化离线末影箱快照。"
            )
        );
    }

    private void ensureDistinctCommands() {
        Set<String> tokens = new HashSet<String>();
        tokens.add(commandName);
        tokens.addAll(commandAliases);
        if (!tokens.add(onlineCommandName)) {
            throw new IllegalArgumentException("online command conflicts with mock command");
        }
        for (String alias : onlineCommandAliases) {
            if (!tokens.add(alias)) throw new IllegalArgumentException("online command alias conflicts with mock command");
        }
        if (!tokens.add(enderChestCommandName)) {
            throw new IllegalArgumentException("ender chest command conflicts with another command");
        }
        for (String alias : enderChestCommandAliases) {
            if (!tokens.add(alias)) throw new IllegalArgumentException("ender chest command alias conflicts with another command");
        }
    }

    private static List<String> normalizedAliases(List<String> values, String field, String commandName) {
        Objects.requireNonNull(values, field);
        List<String> aliases = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        for (String value : values) {
            String alias = normalizeCommand(value, field);
            if (alias.equals(commandName) || !seen.add(alias)) {
                throw new IllegalArgumentException(field + " repeats a command token");
            }
            aliases.add(alias);
        }
        return Collections.unmodifiableList(aliases);
    }

    private static void setIfMissing(FileConfiguration config, String path, Object value) {
        if (!config.contains(path, true)) config.set(path, value);
    }

    private static String normalizeCommand(String value, String field) {
        String normalized = requireText(value, field).toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/") || normalized.matches(".*\\s+.*")) {
            throw new IllegalArgumentException(field + " must be one command token without slash or whitespace");
        }
        return normalized;
    }

    private static String requireSafeName(String value, String field) {
        String normalized = requireText(value, field);
        if (!normalized.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException(field + " contains unsafe characters");
        }
        return normalized;
    }

    private static String requireSafeFileName(String value, String field) {
        String normalized = requireSafeName(value, field);
        if (!normalized.toLowerCase(Locale.ROOT).endsWith(".png")) {
            throw new IllegalArgumentException(field + " must end in .png");
        }
        return normalized;
    }

    private static String requirePathText(String value, String field) {
        String normalized = requireText(value, field);
        if (normalized.indexOf('\0') >= 0) throw new IllegalArgumentException(field + " contains a NUL character");
        return normalized;
    }

    private static int timeout(int value, String field) {
        if (value < 250 || value > 30000) {
            throw new IllegalArgumentException(field + " must be between 250 and 30000");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String caption(String template, String playerName) {
        return template == null ? null : template.replace("%player%", playerName);
    }

    public String captionFor(String playerName) { return captionForMock(playerName); }
    public String captionForMock(String playerName) { return caption(optionalCaption, playerName); }
    public String captionForOnline(String playerName) { return caption(onlineOptionalCaption, playerName); }
    public String captionForEnderChest(String playerName) { return caption(enderChestOptionalCaption, playerName); }

    public String getCommandName() { return commandName; }
    public List<String> getCommandAliases() { return commandAliases; }
    public boolean isPublishToMenu() { return publishToMenu; }
    public String getOnlineCommandName() { return onlineCommandName; }
    public List<String> getOnlineCommandAliases() { return onlineCommandAliases; }
    public boolean isOnlinePublishToMenu() { return onlinePublishToMenu; }
    public boolean isEnderChestEnabled() { return enderChestEnabled; }
    public String getEnderChestCommandName() { return enderChestCommandName; }
    public List<String> getEnderChestCommandAliases() { return enderChestCommandAliases; }
    public boolean isEnderChestPublishToMenu() { return enderChestPublishToMenu; }
    public String getMockPlayerName() { return mockPlayerName; }
    public String getMockSourceServer() { return mockSourceServer; }
    public String getOnlineSourceServer() { return onlineSourceServer; }
    public int getOnlineCooldownSeconds() { return onlineCooldownSeconds; }
    public String getEnderChestSourceServer() { return enderChestSourceServer; }
    public int getEnderChestCooldownSeconds() { return enderChestCooldownSeconds; }
    public boolean isAllowLegacyUnverifiedBindings() { return allowLegacyUnverifiedBindings; }
    public boolean isOfflineInventoryEnabled() { return offlineInventoryEnabled; }
    public boolean isAllowLegacyOfflineSnapshots() { return allowLegacyOfflineSnapshots; }
    public String getOfflineSnapshotDirectory() { return offlineSnapshotDirectory; }
    public int getOfflinePeriodicSaveSeconds() { return offlinePeriodicSaveSeconds; }
    public boolean isOfflineEnderChestEnabled() { return offlineEnderChestEnabled; }
    public boolean isAllowLegacyOfflineEnderChestSnapshots() { return allowLegacyOfflineEnderChestSnapshots; }
    public String getOfflineEnderChestSnapshotDirectory() { return offlineEnderChestSnapshotDirectory; }
    public int getOfflineEnderChestPeriodicSaveSeconds() { return offlineEnderChestPeriodicSaveSeconds; }
    public String getThemeId() { return themeId; }
    public boolean isVanillaAssetsEnabled() { return vanillaAssetsEnabled; }
    public boolean isExternalVanillaPreferred() { return vanillaAssetsEnabled; }
    public String getVanillaCacheDirectory() { return vanillaCacheDirectory; }
    public String getVanillaMinecraftJar() { return vanillaMinecraftJar; }
    public boolean isVanillaAutoImport() { return vanillaAutoImport; }
    public boolean isPlayerPreviewEnabled() { return playerPreviewEnabled; }
    public String getPlayerPreviewProvider() { return playerPreviewProvider; }
    public String getPlayerPreviewMode() { return playerPreviewMode; }
    public String getSkinCacheDirectory() { return skinCacheDirectory; }
    public boolean isAllowSkinTextureDownloads() { return allowSkinTextureDownloads; }
    public int getSkinConnectTimeoutMillis() { return skinConnectTimeoutMillis; }
    public int getSkinReadTimeoutMillis() { return skinReadTimeoutMillis; }
    public int getMaxOutputBytes() { return maxOutputBytes; }
    public String getImageFileName() { return imageFileName; }
    public String getOnlineImageFileName() { return onlineImageFileName; }
    public String getEnderChestImageFileName() { return enderChestImageFileName; }
    public String getFailureMessage() { return failureMessage; }
    public String getUsageMessage() { return usageMessage; }
    public String getPlayerOfflineMessage() { return playerOfflineMessage; }
    public String getPlayerStateChangedMessage() { return playerStateChangedMessage; }
    public String getNotAuthorizedMessage() { return notAuthorizedMessage; }
    public String getCooldownMessage() { return cooldownMessage; }
    public String getBindingRequiredMessage() { return bindingRequiredMessage; }
    public String getBindingVerificationRequiredMessage() { return bindingVerificationRequiredMessage; }
    public String getOfflineSnapshotMissingMessage() { return offlineSnapshotMissingMessage; }
    public String getOfflineLegacyDeniedMessage() { return offlineLegacyDeniedMessage; }
    public String getEnderChestFailureMessage() { return enderChestFailureMessage; }
    public String getEnderChestUsageMessage() { return enderChestUsageMessage; }
    public String getEnderChestPlayerOfflineMessage() { return enderChestPlayerOfflineMessage; }
    public String getEnderChestPlayerStateChangedMessage() { return enderChestPlayerStateChangedMessage; }
    public String getEnderChestNotAuthorizedMessage() { return enderChestNotAuthorizedMessage; }
    public String getEnderChestOfflineSnapshotMissingMessage() { return enderChestOfflineSnapshotMissingMessage; }
    public String getEnderChestOfflineLegacyDeniedMessage() { return enderChestOfflineLegacyDeniedMessage; }
}
