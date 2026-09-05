package cn.huohuas001.huhobot.inventory.snapshot;

import cn.huohuas001.huhobot.inventory.armor.ArmorVisualDescriptor;
import cn.huohuas001.huhobot.inventory.model.InventorySlot;
import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;
import cn.huohuas001.huhobot.inventory.model.ItemSnapshot;
import cn.huohuas001.huhobot.inventory.model.SlotType;
import cn.huohuas001.huhobot.inventory.potion.PotionVisualDescriptor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Versioned, atomic, Bukkit-object-free persistence for last known inventory snapshots. */
public final class OfflineInventorySnapshotStore implements AutoCloseable {
    public static final int SCHEMA_VERSION = 3;

    private final Path root;
    private final Logger logger;
    private final ExecutorService writer;

    public OfflineInventorySnapshotStore(Path root, Logger logger) {
        this.root = root.toAbsolutePath().normalize();
        this.logger = logger;
        this.writer = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "HuHoBotInventory-SnapshotWriter");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public CompletableFuture<Void> saveAsync(final InventorySnapshot snapshot) {
        final CompletableFuture<Void> result = new CompletableFuture<Void>();
        writer.execute(new Runnable() {
            @Override public void run() {
                try {
                    write(snapshot);
                    result.complete(null);
                } catch (Throwable error) {
                    logger.log(Level.WARNING, "Could not save offline inventory snapshot for " +
                        snapshot.getPlayerName(), error);
                    result.completeExceptionally(error);
                }
            }
        });
        return result;
    }

    public Optional<InventorySnapshot> load(UUID playerUuid) {
        Path file = fileFor(playerUuid);
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file.toFile());
            int schema = yaml.getInt("schema-version", 0);
            if (schema < 1 || schema > SCHEMA_VERSION) {
                throw new IllegalArgumentException("unsupported schema " + schema);
            }
            UUID storedUuid = UUID.fromString(required(yaml.getString("player.uuid"), "player.uuid"));
            if (!storedUuid.equals(playerUuid)) throw new IllegalArgumentException("snapshot UUID does not match file name");
            InventorySnapshot snapshot = new InventorySnapshot(
                InventorySnapshot.CURRENT_SCHEMA_VERSION,
                storedUuid,
                required(yaml.getString("player.last-known-name"), "player.last-known-name"),
                Instant.parse(required(yaml.getString("captured-at"), "captured-at")),
                required(yaml.getString("source-server"), "source-server"),
                required(yaml.getString("content-revision"), "content-revision"),
                readGrid(yaml, "storage", SlotType.STORAGE, 27, schema),
                readGrid(yaml, "hotbar", SlotType.HOTBAR, 9, schema),
                Arrays.asList(
                    readSlot(yaml, "armor.head", SlotType.ARMOR_HEAD, 0, schema),
                    readSlot(yaml, "armor.chest", SlotType.ARMOR_CHEST, 0, schema),
                    readSlot(yaml, "armor.legs", SlotType.ARMOR_LEGS, 0, schema),
                    readSlot(yaml, "armor.feet", SlotType.ARMOR_FEET, 0, schema)
                ),
                readSlot(yaml, "offhand", SlotType.OFFHAND, 0, schema)
            );
            logger.info(
                "[OfflineSnapshotStore] disk load snapshot found uuid=" + playerUuid +
                    " capturedAt=" + snapshot.getCapturedAt() + " file=" + file
            );
            return Optional.of(snapshot);
        } catch (Throwable error) {
            logger.log(Level.WARNING, "Ignoring corrupt offline inventory snapshot " + file, error);
            return Optional.empty();
        }
    }

    private void write(InventorySnapshot snapshot) throws Exception {
        if (snapshot.getPlayerUuid() == null) throw new IllegalArgumentException("snapshot UUID is required");
        Files.createDirectories(root);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", SCHEMA_VERSION);
        yaml.set("player.uuid", snapshot.getPlayerUuid().toString());
        yaml.set("player.last-known-name", snapshot.getPlayerName());
        yaml.set("captured-at", snapshot.getCapturedAt().toString());
        yaml.set("source-server", snapshot.getSourceServer());
        yaml.set("content-revision", snapshot.getContentRevision());
        writeSlots(yaml, "storage", snapshot.getStorage());
        writeSlots(yaml, "hotbar", snapshot.getHotbar());
        for (InventorySlot slot : snapshot.getArmor()) {
            String name;
            switch (slot.getSlotType()) {
                case ARMOR_HEAD: name = "head"; break;
                case ARMOR_CHEST: name = "chest"; break;
                case ARMOR_LEGS: name = "legs"; break;
                case ARMOR_FEET: name = "feet"; break;
                default: throw new IllegalArgumentException("unexpected armor slot");
            }
            writeSlot(yaml, "armor." + name, slot);
        }
        writeSlot(yaml, "offhand", snapshot.getOffhand());

        byte[] bytes = yaml.saveToString().getBytes(StandardCharsets.UTF_8);
        Path destination = fileFor(snapshot.getPlayerUuid());
        Path temporary = Files.createTempFile(root, snapshot.getPlayerUuid().toString() + "-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(
                temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path fileFor(UUID uuid) {
        Path file = root.resolve(uuid.toString().toLowerCase(Locale.ROOT) + ".yml").normalize();
        if (!file.startsWith(root)) throw new IllegalArgumentException("snapshot path escaped its root");
        return file;
    }

    private static void writeSlots(YamlConfiguration yaml, String path, List<InventorySlot> slots) {
        for (InventorySlot slot : slots) writeSlot(yaml, path + "." + slot.getIndex(), slot);
    }

    private static void writeSlot(YamlConfiguration yaml, String path, InventorySlot slot) {
        ItemSnapshot item = slot.getItem();
        yaml.set(path + ".empty", item == null);
        if (item == null) return;
        yaml.set(path + ".material", item.getMaterialKey());
        yaml.set(path + ".amount", item.getAmount());
        yaml.set(path + ".damage", item.getDamage());
        yaml.set(path + ".max-damage", item.getMaxDamage());
        yaml.set(path + ".display-name", item.getDisplayName());
        yaml.set(path + ".custom-model-data", item.getCustomModelData());
        yaml.set(path + ".enchantment-glint", item.hasEnchantmentGlint());
        yaml.set(path + ".texture-hint", item.getTextureHint());
        ArmorVisualDescriptor armor = item.getArmorVisual();
        yaml.set(path + ".armor-visual.present", armor != null);
        if (armor != null) {
            yaml.set(path + ".armor-visual.slot", armor.getSlot().name());
            yaml.set(path + ".armor-visual.base-material", armor.getBaseMaterialKey());
            yaml.set(path + ".armor-visual.equipment-model", armor.getEquipmentModelKey());
            yaml.set(path + ".armor-visual.trim-pattern", armor.getTrimPatternKey());
            yaml.set(path + ".armor-visual.trim-material", armor.getTrimMaterialKey());
            yaml.set(path + ".armor-visual.leather-color", armor.getLeatherColor());
            yaml.set(path + ".armor-visual.glint", armor.hasGlint());
        }
        PotionVisualDescriptor potion = item.getPotionVisual();
        yaml.set(path + ".potion-visual.present", potion != null);
        if (potion != null) {
            yaml.set(path + ".potion-visual.item-type", potion.getItemTypeKey());
            yaml.set(path + ".potion-visual.base-potion", potion.getBasePotionKey());
            yaml.set(path + ".potion-visual.resolved-tint-rgb", potion.getResolvedTintRgb());
            yaml.set(path + ".potion-visual.custom-color", potion.hasCustomColor());
            yaml.set(path + ".potion-visual.glint", potion.hasGlint());
        }
    }

    private static List<InventorySlot> readGrid(
        YamlConfiguration yaml, String path, SlotType type, int size, int schema
    ) {
        List<InventorySlot> slots = new ArrayList<InventorySlot>(size);
        for (int index = 0; index < size; index++) {
            slots.add(readSlot(yaml, path + "." + index, type, index, schema));
        }
        return slots;
    }

    private static InventorySlot readSlot(
        YamlConfiguration yaml, String path, SlotType type, int index, int schema
    ) {
        if (!yaml.contains(path + ".empty")) throw new IllegalArgumentException("missing slot " + path);
        if (yaml.getBoolean(path + ".empty")) return InventorySlot.empty(type, index);
        Integer customModelData = yaml.contains(path + ".custom-model-data")
            ? Integer.valueOf(yaml.getInt(path + ".custom-model-data")) : null;
        ArmorVisualDescriptor armor = schema >= 2 ? readArmorVisual(yaml, path) : null;
        PotionVisualDescriptor potion = schema >= 3 ? readPotionVisual(yaml, path) : null;
        ItemSnapshot item = new ItemSnapshot(
            required(yaml.getString(path + ".material"), path + ".material"),
            yaml.getInt(path + ".amount"),
            yaml.getInt(path + ".damage"),
            yaml.getInt(path + ".max-damage"),
            yaml.getString(path + ".display-name"),
            customModelData,
            yaml.getBoolean(path + ".enchantment-glint"),
            yaml.getString(path + ".texture-hint"),
            armor,
            potion
        );
        return InventorySlot.of(type, index, item);
    }

    private static ArmorVisualDescriptor readArmorVisual(YamlConfiguration yaml, String path) {
        String base = path + ".armor-visual";
        if (!yaml.contains(base + ".present")) {
            throw new IllegalArgumentException("missing armor visual marker " + base);
        }
        if (!yaml.getBoolean(base + ".present")) return null;
        ArmorVisualDescriptor.Slot slot;
        try {
            slot = ArmorVisualDescriptor.Slot.valueOf(required(yaml.getString(base + ".slot"), base + ".slot"));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("invalid armor slot " + base, error);
        }
        Integer leatherColor = yaml.contains(base + ".leather-color")
            ? Integer.valueOf(yaml.getInt(base + ".leather-color")) : null;
        return new ArmorVisualDescriptor(
            slot,
            required(yaml.getString(base + ".base-material"), base + ".base-material"),
            required(yaml.getString(base + ".equipment-model"), base + ".equipment-model"),
            yaml.getString(base + ".trim-pattern"),
            yaml.getString(base + ".trim-material"),
            leatherColor,
            yaml.getBoolean(base + ".glint")
        );
    }

    private static PotionVisualDescriptor readPotionVisual(YamlConfiguration yaml, String path) {
        String base = path + ".potion-visual";
        if (!yaml.contains(base + ".present")) {
            throw new IllegalArgumentException("missing potion visual marker " + base);
        }
        if (!yaml.getBoolean(base + ".present")) return null;
        return new PotionVisualDescriptor(
            required(yaml.getString(base + ".item-type"), base + ".item-type"),
            yaml.getString(base + ".base-potion"),
            yaml.getInt(base + ".resolved-tint-rgb"),
            yaml.getBoolean(base + ".custom-color"),
            yaml.getBoolean(base + ".glint")
        );
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("missing " + field);
        return value.trim();
    }

    @Override public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(15, TimeUnit.SECONDS)) {
                logger.warning("Timed out while flushing offline inventory snapshots");
                writer.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }
}
