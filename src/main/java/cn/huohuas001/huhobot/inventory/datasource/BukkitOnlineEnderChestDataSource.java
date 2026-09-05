package cn.huohuas001.huhobot.inventory.datasource;

import cn.huohuas001.huhobot.inventory.armor.ArmorVisualDescriptor;
import cn.huohuas001.huhobot.inventory.model.InventorySlot;
import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;
import cn.huohuas001.huhobot.inventory.model.ItemSnapshot;
import cn.huohuas001.huhobot.inventory.model.SlotType;
import cn.huohuas001.huhobot.inventory.potion.PotionVisualDescriptor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Main-thread source for one online player's private 27-slot Ender Chest. */
public final class BukkitOnlineEnderChestDataSource implements InventoryDataSource {
    private final BukkitAccess access;
    private final BukkitItemSnapshotMapper mapper;
    private final String sourceServer;
    private final Clock clock;

    public BukkitOnlineEnderChestDataSource(JavaPlugin plugin, String sourceServer) {
        this(new PluginBukkitAccess(plugin), new BukkitItemSnapshotMapper(), sourceServer, Clock.systemUTC());
    }

    BukkitOnlineEnderChestDataSource(
        BukkitAccess access,
        BukkitItemSnapshotMapper mapper,
        String sourceServer,
        Clock clock
    ) {
        this.access = Objects.requireNonNull(access, "access");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.sourceServer = requireText(sourceServer, "sourceServer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletionStage<InventorySnapshot> getInventory(String playerName) {
        String requestedName = requirePlayerName(playerName);
        CompletableFuture<InventorySnapshot> result = new CompletableFuture<InventorySnapshot>();
        Runnable capture = () -> captureOnMainThread(requestedName, result);
        try {
            if (access.isPrimaryThread()) capture.run();
            else access.executeSync(capture);
        } catch (Throwable error) {
            result.completeExceptionally(new InventoryDataSourceException(
                InventoryDataSourceException.Reason.SOURCE_UNAVAILABLE,
                "Could not schedule Bukkit Ender Chest capture",
                error
            ));
        }
        return result;
    }

    private void captureOnMainThread(String requestedName, CompletableFuture<InventorySnapshot> result) {
        if (result.isDone()) return;
        try {
            Player player = access.findExactPlayer(requestedName);
            if (player == null || !player.isOnline() || !player.getName().equalsIgnoreCase(requestedName)) {
                throw new InventoryDataSourceException(
                    InventoryDataSourceException.Reason.PLAYER_OFFLINE,
                    "Player is not online: " + requestedName
                );
            }
            result.complete(capturePlayer(player));
        } catch (Throwable error) {
            result.completeExceptionally(error);
        }
    }

    /** Captures one live Player immediately; callers must be on the Bukkit primary thread. */
    public InventorySnapshot capturePlayer(Player player) {
        Objects.requireNonNull(player, "player");
        if (!access.isPrimaryThread()) throw new IllegalStateException("Ender Chest capture must run on the primary thread");
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        Inventory enderChest = player.getEnderChest();
        if (uuid == null || enderChest == null || !player.isOnline()) throw playerStateChanged(name);
        ItemStack[] contents = enderChest.getContents();
        if (contents == null || contents.length < 27) {
            throw new InventoryDataSourceException(
                InventoryDataSourceException.Reason.SOURCE_UNAVAILABLE,
                "Bukkit Ender Chest contains fewer than 27 slots"
            );
        }

        List<InventorySlot> storage = new ArrayList<InventorySlot>(27);
        for (int index = 0; index < 27; index++) storage.add(slot(SlotType.STORAGE, index, contents[index]));
        List<InventorySlot> hotbar = emptyGrid(SlotType.HOTBAR, 9);
        List<InventorySlot> armor = Arrays.asList(
            InventorySlot.empty(SlotType.ARMOR_HEAD, 0),
            InventorySlot.empty(SlotType.ARMOR_CHEST, 0),
            InventorySlot.empty(SlotType.ARMOR_LEGS, 0),
            InventorySlot.empty(SlotType.ARMOR_FEET, 0)
        );
        InventorySlot offhand = InventorySlot.empty(SlotType.OFFHAND, 0);
        if (!player.isOnline()) throw playerStateChanged(name);
        Instant capturedAt = clock.instant();
        return new InventorySnapshot(
            InventorySnapshot.CURRENT_SCHEMA_VERSION,
            uuid,
            name,
            capturedAt,
            sourceServer,
            contentRevision(storage),
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

    private static String contentRevision(List<InventorySlot> slots) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (InventorySlot slot : slots) {
                update(digest, Integer.toString(slot.getIndex()));
                ItemSnapshot item = slot.getItem();
                if (item == null) {
                    update(digest, "empty");
                } else {
                    update(digest, item.getMaterialKey());
                    update(digest, Integer.toString(item.getAmount()));
                    update(digest, Integer.toString(item.getDamage()));
                    update(digest, Integer.toString(item.getMaxDamage()));
                    update(digest, item.getDisplayName() == null ? "" : item.getDisplayName());
                    update(digest, item.getCustomModelData() == null ? "" : item.getCustomModelData().toString());
                    update(digest, Boolean.toString(item.hasEnchantmentGlint()));
                    ArmorVisualDescriptor armor = item.getArmorVisual();
                    update(digest, armor == null ? "" : armor.visualKey());
                    PotionVisualDescriptor potion = item.getPotionVisual();
                    update(digest, potion == null ? "" : potion.visualKey());
                }
            }
            byte[] hash = digest.digest();
            StringBuilder value = new StringBuilder("bukkit-ender-chest-");
            for (int index = 0; index < 12; index++) {
                value.append(String.format(Locale.ROOT, "%02x", hash[index] & 0xff));
            }
            return value.toString();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static InventoryDataSourceException playerStateChanged(String name) {
        return new InventoryDataSourceException(
            InventoryDataSourceException.Reason.PLAYER_STATE_CHANGED,
            "Player state changed while capturing Ender Chest: " + name
        );
    }

    private static String requirePlayerName(String value) {
        String normalized = requireText(value, "playerName");
        if (!normalized.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IllegalArgumentException("playerName must be one exact Minecraft player name");
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }

    interface BukkitAccess {
        boolean isPrimaryThread();
        void executeSync(Runnable task);
        Player findExactPlayer(String playerName);
    }

    private static final class PluginBukkitAccess implements BukkitAccess {
        private final JavaPlugin plugin;
        private final Server server;
        private PluginBukkitAccess(JavaPlugin plugin) {
            this.plugin = Objects.requireNonNull(plugin, "plugin");
            this.server = plugin.getServer();
        }
        @Override public boolean isPrimaryThread() { return server.isPrimaryThread(); }
        @Override public void executeSync(Runnable task) { server.getScheduler().runTask(plugin, task); }
        @Override public Player findExactPlayer(String playerName) { return server.getPlayerExact(playerName); }
    }
}
