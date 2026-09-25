package cn.huohuas001.huhobot.inventory.datasource;

import cn.huohuas001.huhobot.inventory.model.InventorySlot;
import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;
import cn.huohuas001.huhobot.inventory.model.ItemSnapshot;
import cn.huohuas001.huhobot.inventory.model.SlotType;
import ca.spottedleaf.dataconverter.minecraft.MCDataConverter;
import ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry;
import ca.spottedleaf.dataconverter.minecraft.util.Version;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import org.bukkit.Server;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Paper 1.21.11 read-only playerdata source.
 *
 * <p>The temporary NMS player is populated from the server's normal player data loader. This
 * class intentionally exposes no save operation and never mutates the source playerdata file.</p>
 */
public final class PaperOfflineInventoryDataSource implements OfflineInventoryDataSource {
    private static final long MAX_COMPRESSED_PLAYERDATA_BYTES = 16L * 1024L * 1024L;
    private static final long MAX_DECOMPRESSED_PLAYERDATA_BYTES = 64L * 1024L * 1024L;
    public enum Kind { INVENTORY, ENDER_CHEST }

    private final Access access;
    private final BukkitItemSnapshotMapper mapper;
    private final String sourceServer;
    private final Kind kind;

    public PaperOfflineInventoryDataSource(JavaPlugin plugin, String sourceServer, Kind kind) {
        this(new PaperAccess(plugin), new BukkitItemSnapshotMapper(), sourceServer, kind);
    }

    PaperOfflineInventoryDataSource(
        Access access,
        BukkitItemSnapshotMapper mapper,
        String sourceServer,
        Kind kind
    ) {
        this.access = Objects.requireNonNull(access, "access");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.sourceServer = requireText(sourceServer, "sourceServer");
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    @Override
    public CompletionStage<Optional<InventorySnapshot>> getInventory(UUID playerUuid, String lastKnownName) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        String playerName = requirePlayerName(lastKnownName);
        CompletableFuture<Optional<InventorySnapshot>> result =
            new CompletableFuture<Optional<InventorySnapshot>>();
        Runnable capture = () -> captureOnMainThread(playerUuid, playerName, result);
        try {
            if (access.isPrimaryThread()) capture.run();
            else access.executeSync(capture);
        } catch (Throwable error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    private void captureOnMainThread(
        UUID playerUuid,
        String playerName,
        CompletableFuture<Optional<InventorySnapshot>> result
    ) {
        if (result.isDone()) return;
        try {
            if (access.isOnline(playerUuid)) throw stateChanged(playerName);
            Optional<LoadedPlayerData> loaded = access.load(playerUuid, playerName);
            if (!loaded.isPresent()) {
                result.complete(Optional.empty());
                return;
            }
            if (access.isOnline(playerUuid)) throw stateChanged(playerName);
            result.complete(Optional.of(toSnapshot(playerUuid, playerName, loaded.get())));
        } catch (Throwable error) {
            result.completeExceptionally(error);
        }
    }

    private InventorySnapshot toSnapshot(UUID playerUuid, String playerName, LoadedPlayerData loaded) {
        if (kind == Kind.ENDER_CHEST) return enderChestSnapshot(playerUuid, playerName, loaded);

        ItemStack[] contents = loaded.inventory;
        if (contents == null || contents.length < 36) {
            throw new IllegalStateException("Persisted player inventory contains fewer than 36 slots");
        }
        List<InventorySlot> hotbar = new ArrayList<InventorySlot>(9);
        for (int index = 0; index < 9; index++) {
            hotbar.add(slot(SlotType.HOTBAR, index, contents[index]));
        }
        List<InventorySlot> storage = new ArrayList<InventorySlot>(27);
        for (int index = 0; index < 27; index++) {
            storage.add(slot(SlotType.STORAGE, index, contents[index + 9]));
        }
        List<InventorySlot> armor = Arrays.asList(
            slot(SlotType.ARMOR_HEAD, 0, loaded.helmet),
            slot(SlotType.ARMOR_CHEST, 0, loaded.chestplate),
            slot(SlotType.ARMOR_LEGS, 0, loaded.leggings),
            slot(SlotType.ARMOR_FEET, 0, loaded.boots)
        );
        InventorySlot offhand = slot(SlotType.OFFHAND, 0, loaded.offhand);
        return new InventorySnapshot(
            InventorySnapshot.CURRENT_SCHEMA_VERSION,
            playerUuid,
            playerName,
            loaded.savedAt,
            sourceServer,
            "paper-playerdata-" + BukkitOnlineInventoryDataSource.contentRevision(
                storage, hotbar, armor, offhand
            ),
            storage,
            hotbar,
            armor,
            offhand
        );
    }

    private InventorySnapshot enderChestSnapshot(UUID playerUuid, String playerName, LoadedPlayerData loaded) {
        ItemStack[] contents = loaded.enderChest;
        if (contents == null || contents.length < 27) {
            throw new IllegalStateException("Persisted Ender Chest contains fewer than 27 slots");
        }
        List<InventorySlot> storage = new ArrayList<InventorySlot>(27);
        for (int index = 0; index < 27; index++) {
            storage.add(slot(SlotType.STORAGE, index, contents[index]));
        }
        List<InventorySlot> hotbar = emptyGrid(SlotType.HOTBAR, 9);
        List<InventorySlot> armor = Arrays.asList(
            InventorySlot.empty(SlotType.ARMOR_HEAD, 0),
            InventorySlot.empty(SlotType.ARMOR_CHEST, 0),
            InventorySlot.empty(SlotType.ARMOR_LEGS, 0),
            InventorySlot.empty(SlotType.ARMOR_FEET, 0)
        );
        InventorySlot offhand = InventorySlot.empty(SlotType.OFFHAND, 0);
        return new InventorySnapshot(
            InventorySnapshot.CURRENT_SCHEMA_VERSION,
            playerUuid,
            playerName,
            loaded.savedAt,
            sourceServer,
            "paper-playerdata-" + BukkitOnlineEnderChestDataSource.contentRevision(storage),
            storage,
            hotbar,
            armor,
            offhand
        );
    }

    private InventorySlot slot(SlotType type, int index, ItemStack stack) {
        ItemSnapshot item = mapper.map(stack);
        return item == null ? InventorySlot.empty(type, index) : InventorySlot.of(type, index, item);
    }

    private static List<InventorySlot> emptyGrid(SlotType type, int size) {
        List<InventorySlot> slots = new ArrayList<InventorySlot>(size);
        for (int index = 0; index < size; index++) slots.add(InventorySlot.empty(type, index));
        return slots;
    }

    private static InventoryDataSourceException stateChanged(String playerName) {
        return new InventoryDataSourceException(
            InventoryDataSourceException.Reason.PLAYER_STATE_CHANGED,
            "Player came online while reading persisted data: " + playerName
        );
    }

    private static String requirePlayerName(String value) {
        String normalized = requireText(value, "lastKnownName");
        if (!normalized.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IllegalArgumentException("lastKnownName must be one exact Minecraft player name");
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }

    interface Access {
        boolean isPrimaryThread();
        void executeSync(Runnable task);
        boolean isOnline(UUID playerUuid);
        Optional<LoadedPlayerData> load(UUID playerUuid, String playerName) throws Exception;
    }

    static final class LoadedPlayerData {
        final Instant savedAt;
        final ItemStack[] inventory;
        final ItemStack helmet;
        final ItemStack chestplate;
        final ItemStack leggings;
        final ItemStack boots;
        final ItemStack offhand;
        final ItemStack[] enderChest;

        LoadedPlayerData(
            Instant savedAt,
            ItemStack[] inventory,
            ItemStack helmet,
            ItemStack chestplate,
            ItemStack leggings,
            ItemStack boots,
            ItemStack offhand,
            ItemStack[] enderChest
        ) {
            this.savedAt = Objects.requireNonNull(savedAt, "savedAt");
            this.inventory = inventory == null ? null : inventory.clone();
            this.helmet = helmet;
            this.chestplate = chestplate;
            this.leggings = leggings;
            this.boots = boots;
            this.offhand = offhand;
            this.enderChest = enderChest == null ? null : enderChest.clone();
        }
    }

    private static final class PaperAccess implements Access {
        private final JavaPlugin plugin;
        private final Server server;

        private PaperAccess(JavaPlugin plugin) {
            this.plugin = Objects.requireNonNull(plugin, "plugin");
            this.server = plugin.getServer();
        }

        @Override public boolean isPrimaryThread() { return server.isPrimaryThread(); }
        @Override public void executeSync(Runnable task) { server.getScheduler().runTask(plugin, task); }
        @Override public boolean isOnline(UUID playerUuid) {
            org.bukkit.entity.Player player = server.getPlayer(playerUuid);
            return player != null && player.isOnline();
        }

        @Override
        public Optional<LoadedPlayerData> load(UUID playerUuid, String playerName) throws Exception {
            CraftServer craftServer = (CraftServer) server;
            if (craftServer.getWorlds().isEmpty()) return Optional.empty();
            PlayerDataStorage storage = craftServer.getHandle().playerIo;
            Path root = storage.getPlayerDir().toPath().toAbsolutePath().normalize();
            Path playerFile = root.resolve(playerUuid.toString().toLowerCase(Locale.ROOT) + ".dat").normalize();
            if (!playerFile.startsWith(root) || Files.isSymbolicLink(playerFile) ||
                !Files.isRegularFile(playerFile)) return Optional.empty();
            long sizeBefore = Files.size(playerFile);
            if (sizeBefore <= 0L || sizeBefore > MAX_COMPRESSED_PLAYERDATA_BYTES) {
                throw new IllegalStateException(
                    "Refusing persisted player data outside the safe compressed size range: " + sizeBefore
                );
            }
            Instant savedAt = Files.getLastModifiedTime(playerFile).toInstant();

            // Do not call PlayerDataStorage.load here. Paper's compatibility loader can create a
            // backup or rename an offline-UUID file. Reading the exact bound UUID ourselves keeps
            // this data source strictly read-only and lets us apply a finite decompression quota.
            CompoundTag tag = readAndUpgradePlayerData(playerFile);

            ReadOnlyPlayer player = new ReadOnlyPlayer(
                craftServer.getServer().overworld(), new GameProfile(playerUuid, playerName)
            );
            ValueInput input = TagValueInput.create(
                new RejectingProblemReporter("playerdata"), player.registryAccess(), tag
            );
            player.readPlayerData(input);
            if (Files.size(playerFile) != sizeBefore ||
                !Files.getLastModifiedTime(playerFile).toInstant().equals(savedAt)) {
                throw new IllegalStateException("Persisted player data changed while it was being read");
            }

            List<net.minecraft.world.item.ItemStack> inventory = player.getInventory().getNonEquipmentItems();
            ItemStack[] bukkitInventory = new ItemStack[inventory.size()];
            for (int index = 0; index < inventory.size(); index++) {
                bukkitInventory[index] = CraftItemStack.asBukkitCopy(inventory.get(index));
            }
            PlayerEnderChestContainer ender = player.getEnderChestInventory();
            ItemStack[] bukkitEnder = new ItemStack[ender.getContainerSize()];
            for (int index = 0; index < bukkitEnder.length; index++) {
                bukkitEnder[index] = CraftItemStack.asBukkitCopy(ender.getItem(index));
            }
            return Optional.of(new LoadedPlayerData(
                savedAt,
                bukkitInventory,
                bukkit(player.getInventory().equipment.get(EquipmentSlot.HEAD)),
                bukkit(player.getInventory().equipment.get(EquipmentSlot.CHEST)),
                bukkit(player.getInventory().equipment.get(EquipmentSlot.LEGS)),
                bukkit(player.getInventory().equipment.get(EquipmentSlot.FEET)),
                bukkit(player.getInventory().equipment.get(EquipmentSlot.OFFHAND)),
                bukkitEnder
            ));
        }

        private static ItemStack bukkit(net.minecraft.world.item.ItemStack stack) {
            return CraftItemStack.asBukkitCopy(stack);
        }
    }

    static CompoundTag readAndUpgradePlayerData(Path playerFile) throws Exception {
        CompoundTag tag = NbtIo.readCompressed(
            playerFile, NbtAccounter.create(MAX_DECOMPRESSED_PLAYERDATA_BYTES)
        );
        int dataVersion = NbtUtils.getDataVersion(tag);
        return MCDataConverter.convertTag(
            MCTypeRegistry.PLAYER, tag, dataVersion, Version.getCurrentVersion()
        );
    }

    private static final class ReadOnlyPlayer extends Player {
        private ReadOnlyPlayer(net.minecraft.world.level.Level level, GameProfile profile) {
            super(level, profile);
        }

        private void readPlayerData(ValueInput input) {
            super.readAdditionalSaveData(input);
        }

        @Override public GameType gameMode() { return null; }
        @Override public boolean isCreative() { return false; }
        @Override public boolean isSpectator() { return false; }
    }

    private static final class RejectingProblemReporter implements ProblemReporter {
        private final String path;

        private RejectingProblemReporter(String path) {
            this.path = path;
        }

        @Override public ProblemReporter forChild(ProblemReporter.PathElement child) {
            return new RejectingProblemReporter(path + "/" + child);
        }

        @Override public void report(ProblemReporter.Problem problem) {
            throw new IllegalArgumentException("Invalid " + path + ": " + problem);
        }
    }
}
