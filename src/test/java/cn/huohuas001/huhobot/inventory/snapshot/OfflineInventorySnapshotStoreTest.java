package cn.huohuas001.huhobot.inventory.snapshot;

import cn.huohuas001.huhobot.inventory.armor.ArmorVisualDescriptor;
import cn.huohuas001.huhobot.inventory.datasource.MockInventoryDataSource;
import cn.huohuas001.huhobot.inventory.model.InventorySlot;
import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;
import cn.huohuas001.huhobot.inventory.model.ItemSnapshot;
import cn.huohuas001.huhobot.inventory.model.SlotType;
import cn.huohuas001.huhobot.inventory.potion.PotionVisualDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineInventorySnapshotStoreTest {
    @Test
    void snapshotLoadsStayQuietUnlessDebugIsEnabled(@TempDir Path temp) throws Exception {
        InventorySnapshot snapshot = new MockInventoryDataSource("paper-test").createSnapshot("QuietUser");
        AtomicInteger infoLogs = new AtomicInteger();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) {
                if (Level.INFO.equals(record.getLevel())) infoLogs.incrementAndGet();
            }
            @Override public void flush() { }
            @Override public void close() { }
        });

        OfflineInventorySnapshotStore quiet = new OfflineInventorySnapshotStore(temp, logger);
        quiet.saveAsync(snapshot).get();
        assertTrue(quiet.load(snapshot.getPlayerUuid()).isPresent());
        quiet.close();
        assertEquals(0, infoLogs.get());

        OfflineInventorySnapshotStore debug = new OfflineInventorySnapshotStore(temp, logger, true);
        try {
            assertTrue(debug.load(snapshot.getPlayerUuid()).isPresent());
            assertEquals(1, infoLogs.get());
        } finally {
            debug.close();
        }
    }

    @Test
    void atomicallyRoundTripsEveryRenderedFieldAndCapturedAt(@TempDir Path temp) throws Exception {
        InventorySnapshot expected = new MockInventoryDataSource("paper-test").createSnapshot("Steve");
        OfflineInventorySnapshotStore store = new OfflineInventorySnapshotStore(temp, Logger.getAnonymousLogger());
        try {
            store.saveAsync(expected).get();
            InventorySnapshot actual = store.load(expected.getPlayerUuid()).get();

            assertEquals(expected.getPlayerUuid(), actual.getPlayerUuid());
            assertEquals("Steve", actual.getPlayerName());
            assertEquals(expected.getCapturedAt(), actual.getCapturedAt());
            assertEquals(expected.getContentRevision(), actual.getContentRevision());
            assertEquals(27, actual.getStorage().size());
            assertEquals("minecraft:stone", actual.getStorage().get(0).getItem().getMaterialKey());
            assertEquals(64, actual.getStorage().get(0).getItem().getAmount());
            assertEquals(212, actual.getHotbar().get(0).getItem().getDamage());
            assertEquals(1561, actual.getHotbar().get(0).getItem().getMaxDamage());
            assertTrue(actual.getOffhand().getItem().hasEnchantmentGlint());
            try (java.util.stream.Stream<Path> files = Files.list(temp)) {
                assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
            }
        } finally {
            store.close();
        }
    }

    @Test
    void corruptOrUnsupportedSnapshotIsIsolated(@TempDir Path temp) throws Exception {
        UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        Files.write(temp.resolve(uuid + ".yml"), "schema-version: 999\ninvalid: [".getBytes(StandardCharsets.UTF_8));
        OfflineInventorySnapshotStore store = new OfflineInventorySnapshotStore(temp, Logger.getAnonymousLogger());
        try {
            Optional<InventorySnapshot> result = store.load(uuid);
            assertFalse(result.isPresent());
            assertFalse(store.load(UUID.randomUUID()).isPresent());
        } finally {
            store.close();
        }
    }

    @Test
    void persistsArmorTrimLeatherColorAndReadsLegacySchemaOne(@TempDir Path temp) throws Exception {
        InventorySnapshot base = new MockInventoryDataSource("paper-test").createSnapshot("Alex");
        ArmorVisualDescriptor visual = new ArmorVisualDescriptor(
            ArmorVisualDescriptor.Slot.HEAD,
            "minecraft:leather_helmet",
            "minecraft:leather",
            "minecraft:spire",
            "minecraft:redstone",
            Integer.valueOf(0x5a31c8),
            true
        );
        ItemSnapshot helmet = new ItemSnapshot(
            "minecraft:leather_helmet", 1, 7, 55, null, null, true, null, visual
        );
        List<InventorySlot> armor = new ArrayList<InventorySlot>(base.getArmor());
        for (int index = 0; index < armor.size(); index++) {
            if (armor.get(index).getSlotType() == SlotType.ARMOR_HEAD) {
                armor.set(index, InventorySlot.of(SlotType.ARMOR_HEAD, 0, helmet));
            }
        }
        InventorySnapshot expected = new InventorySnapshot(
            InventorySnapshot.CURRENT_SCHEMA_VERSION,
            base.getPlayerUuid(), base.getPlayerName(), base.getCapturedAt(), base.getSourceServer(),
            "trim-revision", base.getStorage(), base.getHotbar(), armor, base.getOffhand()
        );

        OfflineInventorySnapshotStore store = new OfflineInventorySnapshotStore(temp, Logger.getAnonymousLogger());
        try {
            store.saveAsync(expected).get();
            ArmorVisualDescriptor actual = store.load(expected.getPlayerUuid()).get().getArmor().stream()
                .filter(slot -> slot.getSlotType() == SlotType.ARMOR_HEAD)
                .findFirst().get().getItem().getArmorVisual();
            assertEquals(visual.visualKey(), actual.visualKey());
            assertEquals("minecraft:spire", actual.getTrimPatternKey());
            assertEquals("minecraft:redstone", actual.getTrimMaterialKey());
            assertEquals(Integer.valueOf(0x5a31c8), actual.getLeatherColor());
            assertTrue(actual.hasGlint());

            Path file = temp.resolve(expected.getPlayerUuid().toString() + ".yml");
            String yaml = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replaceFirst("schema-version: 3", "schema-version: 1");
            Files.write(file, yaml.getBytes(StandardCharsets.UTF_8));
            assertTrue(store.load(expected.getPlayerUuid()).isPresent(), "schema 1 snapshots must remain readable");
        } finally {
            store.close();
        }
    }

    @Test
    void persistsArmorGlintAcrossStoreRestart(@TempDir Path temp) throws Exception {
        InventorySnapshot base = new MockInventoryDataSource("paper-test").createSnapshot("GlintUser");
        ArmorVisualDescriptor visual = new ArmorVisualDescriptor(
            ArmorVisualDescriptor.Slot.CHEST,
            "minecraft:netherite_chestplate",
            "minecraft:netherite",
            "minecraft:coast",
            "minecraft:gold",
            null,
            true
        );
        ItemSnapshot chestplate = new ItemSnapshot(
            visual.getBaseMaterialKey(), 1, 0, 592, null, null, true, null, visual
        );
        List<InventorySlot> armor = new ArrayList<InventorySlot>(base.getArmor());
        for (int index = 0; index < armor.size(); index++) {
            if (armor.get(index).getSlotType() == SlotType.ARMOR_CHEST) {
                armor.set(index, InventorySlot.of(SlotType.ARMOR_CHEST, 0, chestplate));
            }
        }
        InventorySnapshot expected = new InventorySnapshot(
            InventorySnapshot.CURRENT_SCHEMA_VERSION,
            base.getPlayerUuid(), base.getPlayerName(), base.getCapturedAt(), base.getSourceServer(),
            visual.visualKey(), base.getStorage(), base.getHotbar(), armor, base.getOffhand()
        );

        OfflineInventorySnapshotStore writer = new OfflineInventorySnapshotStore(temp, Logger.getAnonymousLogger());
        writer.saveAsync(expected).get();
        writer.close();

        OfflineInventorySnapshotStore afterRestart = new OfflineInventorySnapshotStore(temp, Logger.getAnonymousLogger());
        try {
            ArmorVisualDescriptor actual = afterRestart.load(expected.getPlayerUuid()).get().getArmor().stream()
                .filter(slot -> slot.getSlotType() == SlotType.ARMOR_CHEST)
                .findFirst().get().getItem().getArmorVisual();
            assertTrue(actual.hasGlint());
            assertEquals(visual.visualKey(), actual.visualKey());
        } finally {
            afterRestart.close();
        }
    }

    @Test
    void persistsPotionTintForOfflineAndCrossRestartReads(@TempDir Path temp) throws Exception {
        InventorySnapshot base = new MockInventoryDataSource("paper-test").createSnapshot("PotionUser");
        PotionVisualDescriptor visual = new PotionVisualDescriptor(
            "minecraft:splash_potion", "minecraft:healing", 0xf82423, true, false
        );
        ItemSnapshot potion = new ItemSnapshot(
            "minecraft:splash_potion", 1, 0, 0, null, null, false, null, null, visual
        );
        List<InventorySlot> storage = new ArrayList<InventorySlot>(base.getStorage());
        storage.set(0, InventorySlot.of(SlotType.STORAGE, 0, potion));
        List<InventorySlot> hotbar = new ArrayList<InventorySlot>(base.getHotbar());
        hotbar.set(0, InventorySlot.of(SlotType.HOTBAR, 0, ItemSnapshot.basic("minecraft:trident", 1)));
        InventorySnapshot expected = new InventorySnapshot(
            InventorySnapshot.CURRENT_SCHEMA_VERSION,
            base.getPlayerUuid(), base.getPlayerName(), base.getCapturedAt(), base.getSourceServer(),
            "potion-revision", storage, hotbar, base.getArmor(), base.getOffhand()
        );

        OfflineInventorySnapshotStore writer = new OfflineInventorySnapshotStore(temp, Logger.getAnonymousLogger());
        writer.saveAsync(expected).get();
        writer.close();

        OfflineInventorySnapshotStore afterRestart = new OfflineInventorySnapshotStore(temp, Logger.getAnonymousLogger());
        try {
            PotionVisualDescriptor actual = afterRestart.load(expected.getPlayerUuid()).get()
                .getStorage().get(0).getItem().getPotionVisual();
            assertEquals(visual.visualKey(), actual.visualKey());
            assertEquals(0xf82423, actual.getResolvedTintRgb());
            assertTrue(actual.hasCustomColor());
            assertEquals(
                "minecraft:trident",
                afterRestart.load(expected.getPlayerUuid()).get().getHotbar().get(0).getItem().getMaterialKey()
            );

            String yaml = new String(
                Files.readAllBytes(temp.resolve(expected.getPlayerUuid().toString() + ".yml")), StandardCharsets.UTF_8
            );
            assertTrue(yaml.contains("schema-version: 3"));
            assertTrue(yaml.contains("resolved-tint-rgb: 16262179"));
        } finally {
            afterRestart.close();
        }
    }
}
