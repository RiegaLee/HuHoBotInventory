package cn.huohuas001.huhobot.inventory.datasource;

import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.SharedConstants;

import ca.spottedleaf.dataconverter.minecraft.util.Version;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaperOfflineInventoryDataSourceTest {
    private static final UUID PLAYER = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    @Test
    void exactPlayerdataReadDoesNotChangeTheSourceFile(@TempDir Path temp) throws Exception {
        Path playerdata = temp.resolve(PLAYER.toString() + ".dat");
        SharedConstants.tryDetectVersion();
        CompoundTag original = new CompoundTag();
        original.putInt("DataVersion", Version.getCurrentVersion());
        original.putString("test-marker", "read-only");
        NbtIo.writeCompressed(original, playerdata);
        byte[] bytesBefore = Files.readAllBytes(playerdata);
        long modifiedBefore = Files.getLastModifiedTime(playerdata).toMillis();

        CompoundTag loaded = PaperOfflineInventoryDataSource.readAndUpgradePlayerData(playerdata);

        assertEquals("read-only", loaded.getString("test-marker").orElse(""));
        assertArrayEquals(bytesBefore, Files.readAllBytes(playerdata));
        assertEquals(modifiedBefore, Files.getLastModifiedTime(playerdata).toMillis());
        assertFalse(Files.exists(temp.resolve(PLAYER.toString() + ".dat_old")));
    }
    private static final Instant SAVED_AT = Instant.parse("2026-09-22T01:02:03Z");

    @Test
    void mapsReadOnlyPersistedInventoryIntoRendererSnapshot() {
        ItemStack[] inventory = new ItemStack[36];
        inventory[0] = item(Material.DIAMOND, 2);
        inventory[9] = item(Material.STONE, 3);
        ItemStack[] ender = new ItemStack[27];
        PaperOfflineInventoryDataSource.LoadedPlayerData loaded =
            new PaperOfflineInventoryDataSource.LoadedPlayerData(
                SAVED_AT,
                inventory,
                item(Material.DIAMOND_HELMET, 1),
                item(Material.DIAMOND_CHESTPLATE, 1),
                item(Material.DIAMOND_LEGGINGS, 1),
                item(Material.DIAMOND_BOOTS, 1),
                item(Material.SHIELD, 1),
                ender
            );
        PaperOfflineInventoryDataSource source = source(
            OfflineInventoryDataSource.Kind.INVENTORY, loaded
        );

        InventorySnapshot snapshot = source.getInventory(PLAYER, "Steve")
            .toCompletableFuture().join().get();

        assertEquals(PLAYER, snapshot.getPlayerUuid());
        assertEquals(SAVED_AT, snapshot.getCapturedAt());
        assertEquals("minecraft:diamond", snapshot.getHotbar().get(0).getItem().getMaterialKey());
        assertEquals(2, snapshot.getHotbar().get(0).getItem().getAmount());
        assertEquals("minecraft:stone", snapshot.getStorage().get(0).getItem().getMaterialKey());
        assertEquals("minecraft:diamond_helmet", snapshot.getArmor().get(0).getItem().getMaterialKey());
        assertEquals("minecraft:shield", snapshot.getOffhand().getItem().getMaterialKey());
    }

    @Test
    void mapsPersistedEnderChestAndRejectsOnlineRace() {
        ItemStack[] inventory = new ItemStack[36];
        ItemStack[] ender = new ItemStack[27];
        ender[4] = item(Material.ENDER_PEARL, 7);
        PaperOfflineInventoryDataSource.LoadedPlayerData loaded =
            new PaperOfflineInventoryDataSource.LoadedPlayerData(
                SAVED_AT, inventory, null, null, null, null, null, ender
            );
        PaperOfflineInventoryDataSource source = source(
            OfflineInventoryDataSource.Kind.ENDER_CHEST, loaded
        );

        InventorySnapshot snapshot = source.getInventory(PLAYER, "Steve")
            .toCompletableFuture().join().get();
        assertEquals("minecraft:ender_pearl", snapshot.getStorage().get(4).getItem().getMaterialKey());
        assertEquals(7, snapshot.getStorage().get(4).getItem().getAmount());
        assertNull(snapshot.getHotbar().get(0).getItem());

        PaperOfflineInventoryDataSource online = new PaperOfflineInventoryDataSource(
            new FakeAccess(loaded, true), new BukkitItemSnapshotMapper(), "paper-test",
            OfflineInventoryDataSource.Kind.INVENTORY
        );
        CompletionException failure = assertThrows(
            CompletionException.class,
            () -> online.getInventory(PLAYER, "Steve").toCompletableFuture().join()
        );
        assertEquals(
            InventoryDataSourceException.Reason.PLAYER_STATE_CHANGED,
            ((InventoryDataSourceException) failure.getCause()).getReason()
        );
    }

    private static PaperOfflineInventoryDataSource source(
        OfflineInventoryDataSource.Kind kind,
        PaperOfflineInventoryDataSource.LoadedPlayerData loaded
    ) {
        return new PaperOfflineInventoryDataSource(
            new FakeAccess(loaded, false), new BukkitItemSnapshotMapper(), "paper-test", kind
        );
    }

    private static ItemStack item(Material material, int amount) {
        return BukkitItemSnapshotMapperTest.TestItemStack.create(material, amount, null);
    }

    private static final class FakeAccess implements PaperOfflineInventoryDataSource.Access {
        private final PaperOfflineInventoryDataSource.LoadedPlayerData loaded;
        private final boolean online;

        private FakeAccess(PaperOfflineInventoryDataSource.LoadedPlayerData loaded, boolean online) {
            this.loaded = loaded;
            this.online = online;
        }

        @Override public boolean isPrimaryThread() { return true; }
        @Override public void executeSync(Runnable task) { task.run(); }
        @Override public boolean isOnline(UUID playerUuid) { return online; }
        @Override public Optional<PaperOfflineInventoryDataSource.LoadedPlayerData> load(
            UUID playerUuid, String playerName
        ) {
            return Optional.ofNullable(loaded);
        }
    }
}
