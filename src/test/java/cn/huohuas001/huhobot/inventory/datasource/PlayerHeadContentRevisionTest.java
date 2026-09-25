package cn.huohuas001.huhobot.inventory.datasource;

import cn.huohuas001.huhobot.inventory.head.PlayerHeadVisualDescriptor;
import cn.huohuas001.huhobot.inventory.model.InventorySlot;
import cn.huohuas001.huhobot.inventory.model.ItemSnapshot;
import cn.huohuas001.huhobot.inventory.model.SlotType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PlayerHeadContentRevisionTest {
    @Test
    void inventoryAndEnderChestRevisionsIncludeHeadTextureIdentity() {
        List<InventorySlot> first = grid(head(hash(1)));
        List<InventorySlot> second = grid(head(hash(2)));
        List<InventorySlot> hotbar = empty(SlotType.HOTBAR, 9);
        List<InventorySlot> armor = Arrays.asList(
            InventorySlot.empty(SlotType.ARMOR_HEAD, 0),
            InventorySlot.empty(SlotType.ARMOR_CHEST, 0),
            InventorySlot.empty(SlotType.ARMOR_LEGS, 0),
            InventorySlot.empty(SlotType.ARMOR_FEET, 0)
        );
        InventorySlot offhand = InventorySlot.empty(SlotType.OFFHAND, 0);

        assertNotEquals(
            BukkitOnlineEnderChestDataSource.contentRevision(first),
            BukkitOnlineEnderChestDataSource.contentRevision(second)
        );
        assertNotEquals(
            BukkitOnlineInventoryDataSource.contentRevision(first, hotbar, armor, offhand),
            BukkitOnlineInventoryDataSource.contentRevision(second, hotbar, armor, offhand)
        );
    }

    private static List<InventorySlot> grid(ItemSnapshot first) {
        List<InventorySlot> slots = empty(SlotType.STORAGE, 27);
        slots.set(0, InventorySlot.of(SlotType.STORAGE, 0, first));
        return slots;
    }

    private static List<InventorySlot> empty(SlotType type, int size) {
        List<InventorySlot> slots = new ArrayList<InventorySlot>();
        for (int index = 0; index < size; index++) slots.add(InventorySlot.empty(type, index));
        return slots;
    }

    private static ItemSnapshot head(String hash) {
        return new ItemSnapshot(
            "minecraft:player_head", 1, 0, 0, null, null, false, null,
            null, null, new PlayerHeadVisualDescriptor(hash)
        );
    }

    private static String hash(int value) { return String.format("%064x", value); }
}
