package cn.huohuas001.huhobot.inventory.snapshot;

import cn.huohuas001.huhobot.inventory.datasource.BukkitOnlineInventoryDataSource;
import cn.huohuas001.huhobot.inventory.datasource.BukkitOnlineEnderChestDataSource;
import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.logging.Logger;
import java.util.logging.Level;

/** Main-thread capture boundary for quit, periodic and normal shutdown snapshots. */
public final class OfflineInventorySnapshotManager implements Listener, AutoCloseable {
    private final Environment environment;
    private final PlayerSnapshotCapturer capturer;
    private final OfflineInventorySnapshotStore store;
    private Cancellable periodicTask;

    public OfflineInventorySnapshotManager(
        JavaPlugin plugin,
        BukkitOnlineInventoryDataSource source,
        OfflineInventorySnapshotStore store
    ) {
        this(new PluginEnvironment(plugin), new PlayerSnapshotCapturer() {
            @Override public InventorySnapshot capture(Player player) { return source.capturePlayer(player); }
        }, store);
    }

    public OfflineInventorySnapshotManager(
        JavaPlugin plugin,
        BukkitOnlineEnderChestDataSource source,
        OfflineInventorySnapshotStore store
    ) {
        this(new PluginEnvironment(plugin), new PlayerSnapshotCapturer() {
            @Override public InventorySnapshot capture(Player player) { return source.capturePlayer(player); }
        }, store);
    }

    OfflineInventorySnapshotManager(
        Environment environment,
        PlayerSnapshotCapturer capturer,
        OfflineInventorySnapshotStore store
    ) {
        this.environment = environment;
        this.capturer = capturer;
        this.store = store;
    }

    public void start(int periodicSaveSeconds) {
        environment.register(this);
        if (periodicSaveSeconds > 0) {
            long ticks = periodicSaveSeconds * 20L;
            periodicTask = environment.schedule(new Runnable() {
                    @Override public void run() { captureAllOnline(); }
                }, ticks);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        capture(event.getPlayer());
    }

    public void captureAllOnline() {
        for (Player player : environment.onlinePlayers()) capture(player);
    }

    private void capture(Player player) {
        try {
            InventorySnapshot snapshot = capturer.capture(player);
            store.saveAsync(snapshot);
        } catch (Throwable error) {
            environment.logger().log(Level.WARNING, "Could not capture offline snapshot for " + player.getName(), error);
        }
    }

    @Override public void close() {
        if (periodicTask != null) {
            periodicTask.cancel();
            periodicTask = null;
        }
        captureAllOnline();
        store.close();
    }

    interface PlayerSnapshotCapturer { InventorySnapshot capture(Player player); }
    interface Cancellable { void cancel(); }
    interface Environment {
        void register(Listener listener);
        Cancellable schedule(Runnable task, long periodTicks);
        Collection<? extends Player> onlinePlayers();
        Logger logger();
    }

    private static final class PluginEnvironment implements Environment {
        private final JavaPlugin plugin;
        private PluginEnvironment(JavaPlugin plugin) { this.plugin = plugin; }
        @Override public void register(Listener listener) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
        @Override public Cancellable schedule(Runnable task, long periodTicks) {
            final BukkitTask value = plugin.getServer().getScheduler().runTaskTimer(
                plugin, task, periodTicks, periodTicks
            );
            return new Cancellable() { @Override public void cancel() { value.cancel(); } };
        }
        @Override public Collection<? extends Player> onlinePlayers() { return plugin.getServer().getOnlinePlayers(); }
        @Override public Logger logger() { return plugin.getLogger(); }
    }
}
