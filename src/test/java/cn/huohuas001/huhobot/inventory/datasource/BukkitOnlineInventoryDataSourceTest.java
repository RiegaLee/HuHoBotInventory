package cn.huohuas001.huhobot.inventory.datasource;

import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;
import cn.huohuas001.huhobot.inventory.model.SlotType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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

class BukkitOnlineInventoryDataSourceTest {
    private static final UUID PLAYER_UUID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-24T05:00:00Z"), ZoneOffset.UTC);

    @Test
    void schedulesMainThreadAndMapsHotbarStorageNamedArmorAndOffhand() {
        ItemStack[] contents = new ItemStack[36];
        contents[0] = item(Material.DIAMOND_SWORD, 1);
        contents[8] = item(Material.FIREWORK_ROCKET, 32);
        contents[9] = item(Material.STONE, 64);
        contents[10] = item(Material.DIAMOND, 12);
        PlayerInventory inventory = inventory(
            contents,
            item(Material.DIAMOND_HELMET, 1),
            item(Material.DIAMOND_CHESTPLATE, 1),
            item(Material.DIAMOND_LEGGINGS, 1),
            item(Material.DIAMOND_BOOTS, 1),
            item(Material.SHIELD, 1)
        );
        FakeAccess access = new FakeAccess(player("Steve", inventory, () -> true), false);
        BukkitOnlineInventoryDataSource source = source(access);

        InventorySnapshot snapshot = source.getInventory("steve").toCompletableFuture().join();

        assertEquals(1, access.syncRuns.get());
        assertEquals("Steve", snapshot.getPlayerName());
        assertEquals(PLAYER_UUID, snapshot.getPlayerUuid());
        assertEquals(Instant.parse("2026-08-24T05:00:00Z"), snapshot.getCapturedAt());
        assertEquals("paper-test", snapshot.getSourceServer());
        assertTrue(snapshot.getContentRevision().startsWith("bukkit-online-"));
        assertEquals("minecraft:diamond_sword", snapshot.getHotbar().get(0).getItem().getMaterialKey());
        assertEquals(32, snapshot.getHotbar().get(8).getItem().getAmount());
        assertEquals("minecraft:stone", snapshot.getStorage().get(0).getItem().getMaterialKey());
        assertEquals(64, snapshot.getStorage().get(0).getItem().getAmount());
        assertEquals("minecraft:diamond", snapshot.getStorage().get(1).getItem().getMaterialKey());
        assertNull(snapshot.getStorage().get(2).getItem());
        assertEquals(SlotType.ARMOR_HEAD, snapshot.getArmor().get(0).getSlotType());
        assertEquals("minecraft:diamond_helmet", snapshot.getArmor().get(0).getItem().getMaterialKey());
        assertEquals(SlotType.ARMOR_FEET, snapshot.getArmor().get(3).getSlotType());
        assertEquals("minecraft:diamond_boots", snapshot.getArmor().get(3).getItem().getMaterialKey());
        assertEquals("minecraft:shield", snapshot.getOffhand().getItem().getMaterialKey());
    }

    @Test
    void returnsFreshSnapshotAfterInventoryChanges() {
        ItemStack[] contents = new ItemStack[36];
        contents[9] = item(Material.STONE, 64);
        PlayerInventory inventory = inventory(contents, null, null, null, null, null);
        BukkitOnlineInventoryDataSource source = source(new FakeAccess(player("Steve", inventory, () -> true), true));

        InventorySnapshot first = source.getInventory("Steve").toCompletableFuture().join();
        contents[9] = item(Material.STONE, 32);
        InventorySnapshot second = source.getInventory("Steve").toCompletableFuture().join();

        assertEquals(64, first.getStorage().get(0).getItem().getAmount());
        assertEquals(32, second.getStorage().get(0).getItem().getAmount());
        assertNotEquals(first.getContentRevision(), second.getContentRevision());
    }

    @Test
    void offlineOrNonExactPlayerCompletesWithExpectedReason() {
        FakeAccess offline = new FakeAccess(null, true);
        InventoryDataSourceException missing = sourceFailure(source(offline), "Nobody");
        assertEquals(InventoryDataSourceException.Reason.PLAYER_OFFLINE, missing.getReason());

        Player wrong = player("Alex", inventory(new ItemStack[36], null, null, null, null, null), () -> true);
        InventoryDataSourceException mismatch = sourceFailure(source(new FakeAccess(wrong, true)), "Steve");
        assertEquals(InventoryDataSourceException.Reason.PLAYER_OFFLINE, mismatch.getReason());
    }

    @Test
    void detectsPlayerDisconnectDuringCapture() {
        AtomicInteger onlineChecks = new AtomicInteger();
        BooleanSupplier online = () -> onlineChecks.incrementAndGet() < 3;
        PlayerInventory inventory = inventory(new ItemStack[36], null, null, null, null, null);
        InventoryDataSourceException error = sourceFailure(
            source(new FakeAccess(player("Steve", inventory, online), true)),
            "Steve"
        );
        assertEquals(InventoryDataSourceException.Reason.PLAYER_STATE_CHANGED, error.getReason());
    }

    @Test
    void rejectsFuzzyOrMalformedPlayerNamesBeforeLookup() {
        FakeAccess access = new FakeAccess(null, true);
        assertThrows(IllegalArgumentException.class, () -> source(access).getInventory("Steve extra"));
        assertThrows(IllegalArgumentException.class, () -> source(access).getInventory("name-that-is-far-too-long"));
        assertEquals(0, access.lookupCalls.get());
    }

    private static BukkitOnlineInventoryDataSource source(FakeAccess access) {
        return new BukkitOnlineInventoryDataSource(access, new BukkitItemSnapshotMapper(), "paper-test", CLOCK);
    }

    private static InventoryDataSourceException sourceFailure(
        BukkitOnlineInventoryDataSource source,
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

    private static PlayerInventory inventory(
        ItemStack[] storage,
        ItemStack helmet,
        ItemStack chestplate,
        ItemStack leggings,
        ItemStack boots,
        ItemStack offhand
    ) {
        return (PlayerInventory) Proxy.newProxyInstance(
            BukkitOnlineInventoryDataSourceTest.class.getClassLoader(),
            new Class<?>[] {PlayerInventory.class},
            (proxy, method, arguments) -> {
                String name = method.getName();
                if ("getStorageContents".equals(name)) return storage.clone();
                if ("getHelmet".equals(name)) return helmet;
                if ("getChestplate".equals(name)) return chestplate;
                if ("getLeggings".equals(name)) return leggings;
                if ("getBoots".equals(name)) return boots;
                if ("getItemInOffHand".equals(name)) return offhand;
                if ("toString".equals(name)) return "TestPlayerInventory";
                if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                if ("equals".equals(name)) return proxy == arguments[0];
                Class<?> type = method.getReturnType();
                if (type == boolean.class) return false;
                if (type == int.class) return 0;
                return null;
            }
        );
    }

    private static Player player(String name, PlayerInventory inventory, BooleanSupplier online) {
        return (Player) Proxy.newProxyInstance(
            BukkitOnlineInventoryDataSourceTest.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, arguments) -> {
                String methodName = method.getName();
                if ("getName".equals(methodName)) return name;
                if ("getUniqueId".equals(methodName)) return PLAYER_UUID;
                if ("getInventory".equals(methodName)) return inventory;
                if ("isOnline".equals(methodName)) return online.getAsBoolean();
                if ("toString".equals(methodName)) return "TestPlayer(" + name + ")";
                if ("hashCode".equals(methodName)) return System.identityHashCode(proxy);
                if ("equals".equals(methodName)) return proxy == arguments[0];
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

    private static final class FakeAccess implements BukkitOnlineInventoryDataSource.BukkitAccess {
        private final Player player;
        private final boolean primaryThread;
        private final AtomicInteger syncRuns = new AtomicInteger();
        private final AtomicInteger lookupCalls = new AtomicInteger();

        private FakeAccess(Player player, boolean primaryThread) {
            this.player = player;
            this.primaryThread = primaryThread;
        }

        @Override public boolean isPrimaryThread() { return primaryThread; }
        @Override public void executeSync(Runnable task) { syncRuns.incrementAndGet(); task.run(); }
        @Override public Player findExactPlayer(String playerName) { lookupCalls.incrementAndGet(); return player; }
    }
}
