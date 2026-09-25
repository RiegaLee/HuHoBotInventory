package cn.huohuas001.huhobot.inventory.datasource;

import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

/** Selects a version-specific offline playerdata implementation without eagerly linking its NMS classes. */
public final class OfflineInventoryDataSourceFactory {
    private static final String PAPER_MARKER = "io.papermc.paper.ServerBuildInfo";
    private static final String PAPER_1_21_11_IMPLEMENTATION =
        "cn.huohuas001.huhobot.inventory.datasource.PaperOfflineInventoryDataSource";

    private OfflineInventoryDataSourceFactory() {}

    public static Optional<OfflineInventoryDataSource> create(
        JavaPlugin plugin,
        String sourceServer,
        OfflineInventoryDataSource.Kind kind
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(kind, "kind");
        Server server = plugin.getServer();
        ClassLoader loader = plugin.getClass().getClassLoader();
        String implementation = implementationClassName(
            server.getMinecraftVersion(), classExists(PAPER_MARKER, loader)
        );
        if (implementation == null) return Optional.empty();

        try {
            Class<?> rawType = Class.forName(implementation, true, loader);
            Class<? extends OfflineInventoryDataSource> type =
                rawType.asSubclass(OfflineInventoryDataSource.class);
            Constructor<? extends OfflineInventoryDataSource> constructor = type.getConstructor(
                JavaPlugin.class, String.class, OfflineInventoryDataSource.Kind.class
            );
            return Optional.of(constructor.newInstance(plugin, sourceServer, kind));
        } catch (Throwable error) {
            plugin.getLogger().log(
                Level.WARNING,
                "Matching offline playerdata adapter could not be loaded; YAML snapshots remain available",
                error
            );
            return Optional.empty();
        }
    }

    static String implementationClassName(String minecraftVersion, boolean paper) {
        if (paper && "1.21.11".equals(minecraftVersion)) return PAPER_1_21_11_IMPLEMENTATION;
        return null;
    }

    private static boolean classExists(String className, ClassLoader loader) {
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
