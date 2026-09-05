package cn.huohuas001.huhobot.inventory.snapshot;

import cn.huohuas001.huhobot.inventory.datasource.MockInventoryDataSource;
import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineInventorySnapshotManagerTest {
    @Test
    void quitAndPeriodicCaptureImmutableSnapshotsThenShutdownFlushes(@TempDir Path temp) throws Exception {
        InventorySnapshot snapshot = new MockInventoryDataSource("paper-test").createSnapshot("Steve");
        Player player = player("Steve");
        FakeEnvironment environment = new FakeEnvironment(player);
        AtomicInteger captures = new AtomicInteger();
        OfflineInventorySnapshotStore store = new OfflineInventorySnapshotStore(temp, Logger.getAnonymousLogger());
        OfflineInventorySnapshotManager manager = new OfflineInventorySnapshotManager(
            environment,
            value -> { captures.incrementAndGet(); return snapshot; },
            store
        );

        manager.start(300);
        assertNotNull(environment.listener);
        manager.onQuit(new PlayerQuitEvent(player, "left"));
        environment.periodic.run();
        manager.close();

        assertEquals(3, captures.get(), "quit, periodic and shutdown must each capture on the caller thread");
        assertTrue(environment.cancelled.get());
        OfflineInventorySnapshotStore reopened = new OfflineInventorySnapshotStore(temp, Logger.getAnonymousLogger());
        try {
            assertTrue(reopened.load(snapshot.getPlayerUuid()).isPresent());
        } finally {
            reopened.close();
        }
    }

    private static Player player(String name) {
        return (Player) Proxy.newProxyInstance(
            OfflineInventorySnapshotManagerTest.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, arguments) -> {
                if ("getName".equals(method.getName())) return name;
                if ("toString".equals(method.getName())) return "TestPlayer(" + name + ")";
                if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                if ("equals".equals(method.getName())) return proxy == arguments[0];
                if (method.getReturnType() == boolean.class) return false;
                if (method.getReturnType() == int.class) return 0;
                return null;
            }
        );
    }

    private static final class FakeEnvironment implements OfflineInventorySnapshotManager.Environment {
        private final Player player;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private Listener listener;
        private Runnable periodic;
        private FakeEnvironment(Player player) { this.player = player; }
        @Override public void register(Listener value) { listener = value; }
        @Override public OfflineInventorySnapshotManager.Cancellable schedule(Runnable task, long ticks) {
            assertEquals(6000L, ticks);
            periodic = task;
            return () -> cancelled.set(true);
        }
        @Override public Collection<? extends Player> onlinePlayers() { return Collections.singletonList(player); }
        @Override public Logger logger() { return Logger.getAnonymousLogger(); }
    }
}
