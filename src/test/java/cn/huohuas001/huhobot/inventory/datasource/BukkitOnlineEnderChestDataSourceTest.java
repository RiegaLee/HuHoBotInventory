package cn.huohuas001.huhobot.inventory.datasource;

import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitOnlineEnderChestDataSourceTest {
    private static final UUID PLAYER_UUID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-05T01:00:00Z"), ZoneOffset.UTC);

    @Test
    void schedulesMainThreadAndMapsExactlyTwentySevenEnderChestSlots() {
        ItemStack[] contents = new ItemStack[27];
        contents[0] = item(Material.ENDER_CHEST, 1);
        contents[8] = item(Material.ENDER_PEARL, 16);
        contents[26] = item(Material.DIAMOND, 12);
        FakeAccess access = new FakeAccess(player("Steve", inventory(contents), () -> true), false);

        InventorySnapshot snapshot = source(access).getInventory("steve").toCompletableFuture().join();

        assertEquals(1, access.syncRuns.get());
        assertEquals(PLAYER_UUID, snapshot.getPlayerUuid());
        assertEquals("Steve", snapshot.getPlayerName());
        assertEquals("paper-test", snapshot.getSourceServer());
        assertTrue(snapshot.getContentRevision().startsWith("bukkit-ender-chest-"));
        assertEquals(27, snapshot.getStorage().size());
        assertEquals("minecraft:ender_chest", snapshot.getStorage().get(0).getItem().getMaterialKey());
        assertEquals(16, snapshot.getStorage().get(8).getItem().getAmount());
        assertEquals("minecraft:diamond", snapshot.getStorage().get(26).getItem().getMaterialKey());
        assertEquals(9, snapshot.getHotbar().size());
        assertNull(snapshot.getHotbar().get(0).getItem());
        assertNull(snapshot.getArmor().get(0).getItem());
        assertNull(snapshot.getOffhand().getItem());
    }

    @Test
    void contentRevisionChangesWithEnderChestContents() {
        ItemStack[] contents = new ItemStack[27];
        contents[0] = item(Material.ENDER_PEARL, 8);
        BukkitOnlineEnderChestDataSource source = source(
            new FakeAccess(player("Steve", inventory(contents), () -> true), true)
        );

        InventorySnapshot first = source.getInventory("Steve").toCompletableFuture().join();
        contents[0] = item(Material.ENDER_PEARL, 16);
        InventorySnapshot second = source.getInventory("Steve").toCompletableFuture().join();

        assertEquals(8, first.getStorage().get(0).getItem().getAmount());
        assertEquals(16, second.getStorage().get(0).getItem().getAmount());
        assertNotEquals(first.getContentRevision(), second.getContentRevision());
    }

    @Test
    void rejectsOfflineMalformedAndChangingPlayers() {
        assertEquals(
            InventoryDataSourceException.Reason.PLAYER_OFFLINE,
            failure(source(new FakeAccess(null, true)), "Nobody").getReason()
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> source(new FakeAccess(null, true)).getInventory("Steve extra")
        );

        AtomicInteger checks = new AtomicInteger();
        Player changing = player("Steve", inventory(new ItemStack[27]), () -> checks.incrementAndGet() < 3);
        assertEquals(
            InventoryDataSourceException.Reason.PLAYER_STATE_CHANGED,
            failure(source(new FakeAccess(changing, true)), "Steve").getReason()
        );
    }

    private static BukkitOnlineEnderChestDataSource source(FakeAccess access) {
        return new BukkitOnlineEnderChestDataSource(
            access, new BukkitItemSnapshotMapper(), "paper-test", CLOCK
        );
    }

    private static InventoryDataSourceException failure(
        BukkitOnlineEnderChestDataSource source,
        String playerName
    ) {
        CompletionException wrapper = assertThrows(
            CompletionException.class,
            () -> source.getInventory(playerName).toCompletableFuture().join()
        );
        assertTrue(wrapper.getCause() instanceof InventoryDataSourceException);
        return (InventoryDataSourceException) wrapper.getCause();
    }

    private static ItemStack item(Material material, int amount) {
        return BukkitItemSnapshotMapperTest.TestItemStack.create(material, amount, null);
    }

    private static Inventory inventory(ItemStack[] contents) {
        return (Inventory) Proxy.newProxyInstance(
            BukkitOnlineEnderChestDataSourceTest.class.getClassLoader(),
            new Class<?>[] {Inventory.class},
            (proxy, method, arguments) -> {
                if ("getContents".equals(method.getName())) return contents.clone();
                if ("toString".equals(method.getName())) return "TestEnderChest";
                if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                if ("equals".equals(method.getName())) return proxy == arguments[0];
                if (method.getReturnType() == boolean.class) return false;
                if (method.getReturnType() == int.class) return 0;
                return null;
            }
        );
    }

    private static Player player(String name, Inventory inventory, BooleanSupplier online) {
        return (Player) Proxy.newProxyInstance(
            BukkitOnlineEnderChestDataSourceTest.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, arguments) -> {
                if ("getName".equals(method.getName())) return name;
                if ("getUniqueId".equals(method.getName())) return PLAYER_UUID;
                if ("getEnderChest".equals(method.getName())) return inventory;
                if ("isOnline".equals(method.getName())) return online.getAsBoolean();
                if ("toString".equals(method.getName())) return "TestPlayer(" + name + ")";
                if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                if ("equals".equals(method.getName())) return proxy == arguments[0];
                Class<?> type = method.getReturnType();
                if (type == boolean.class) return false;
                if (type == int.class) return 0;
                if (type == double.class) return 0.0d;
                if (type == float.class) return 0.0f;
                if (type == long.class) return 0L;
                return null;
            }
        );
    }

    private static final class FakeAccess implements BukkitOnlineEnderChestDataSource.BukkitAccess {
        private final Player player;
        private final boolean primaryThread;
        private final AtomicInteger syncRuns = new AtomicInteger();
        private boolean executingSync;

        private FakeAccess(Player player, boolean primaryThread) {
            this.player = player;
            this.primaryThread = primaryThread;
        }

        @Override public boolean isPrimaryThread() { return primaryThread || executingSync; }
        @Override public void executeSync(Runnable task) {
            syncRuns.incrementAndGet();
            executingSync = true;
            try { task.run(); }
            finally { executingSync = false; }
        }
        @Override public Player findExactPlayer(String playerName) { return player; }
    }
}
