package cn.huohuas001.huhobot.inventory.renderer;

import cn.huohuas001.huhobot.inventory.armor.ArmorEquipmentSet;
import cn.huohuas001.huhobot.inventory.armor.ArmorItemIconRenderer;
import cn.huohuas001.huhobot.inventory.armor.ArmorVisualDescriptor;
import cn.huohuas001.huhobot.inventory.armor.EquipmentAssetResolver;
import cn.huohuas001.huhobot.inventory.asset.VanillaImportedAssetProvider;
import cn.huohuas001.huhobot.inventory.model.InventorySlot;
import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;
import cn.huohuas001.huhobot.inventory.model.ItemSnapshot;
import cn.huohuas001.huhobot.inventory.model.SlotType;
import cn.huohuas001.huhobot.inventory.skin.DefaultPlayerSkinProvider;
import cn.huohuas001.huhobot.inventory.skin.PlayerModelRenderer;
import cn.huohuas001.huhobot.inventory.skin.PlayerSkin;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Audit-only generator that passes every definition through the final Inventory renderer. */
class FullItemRenderAuditTest {
    private static final int SHEET_COLUMNS = 10;
    private static final int SHEET_ROWS = 10;
    private static final int CELL_WIDTH = 124;
    private static final int CELL_HEIGHT = 112;
    private static final int ICON_SIZE = 48;
    private static final Path AUDIT = Paths.get(
        "data", "visual-audit", "26.1.2-B1B315857266-MB7-PD1337875"
    );
    private static final Path OUTPUT = AUDIT.resolve("inventory-final-pages-1.16.0");
    private static final Path THEME = Paths.get("src", "main", "resources", "themes", "faithful32x");
    private static final Path ARMOR = Paths.get("src", "armor-assets");

    @Test
    void rendersEveryDefinitionThroughFinalInventoryComposition() throws Exception {
        VanillaImportedAssetProvider imported = VanillaImportedAssetProvider.open(
            Paths.get("data", "imported-assets", "vanilla")
        );
        assertTrue(imported.isAvailable(), "formal MB7 provider must be available");
        Theme theme = ThemeLoader.load(THEME, imported);
        EquipmentAssetResolver equipmentAssets = new EquipmentAssetResolver(ARMOR);
        theme.getTextures().setArmorItemRenderer(new ArmorItemIconRenderer(
            equipmentAssets, OUTPUT.resolve("runtime-cache/armor-items")
        ));
        Java2DInventoryRenderer renderer = new Java2DInventoryRenderer(theme);
        PlayerSkin skin = new DefaultPlayerSkinProvider().getFallback();
        java.awt.Rectangle previewArea = theme.getLayout().getPlayerPreview();
        PlayerModelRenderer previewRenderer = new PlayerModelRenderer(previewArea.width, previewArea.height);
        BufferedImage plainPreview = previewRenderer.render(skin);

        Map<String, Status> statuses = statuses();
        List<AuditEntry> entries = entries(statuses);
        assertEquals(1506, entries.size());
        Files.createDirectories(OUTPUT.resolve("all-items"));
        Files.createDirectories(OUTPUT.resolve("samples"));

        List<String> index = new ArrayList<String>();
        index.add("category\tpage\tposition\tslot_type\tslot_index\titem_id\tstatus\trender_path\tresolver_source\tfallback\tfile");
        Map<String, List<AuditEntry>> byCategory = new LinkedHashMap<String, List<AuditEntry>>();
        for (AuditEntry entry : entries) {
            byCategory.computeIfAbsent(entry.category, ignored -> new ArrayList<AuditEntry>()).add(entry);
        }
        int renderedDefinitions = 0;
        for (Map.Entry<String, List<AuditEntry>> group : byCategory.entrySet()) {
            List<AuditEntry> values = group.getValue();
            for (int offset = 0, page = 1; offset < values.size(); offset += 36, page++) {
                List<AuditEntry> pageEntries = values.subList(offset, Math.min(offset + 36, values.size()));
                List<ItemSnapshot> items = new ArrayList<ItemSnapshot>();
                for (AuditEntry entry : pageEntries) items.add(itemForAudit(entry));
                InventorySnapshot snapshot = snapshot(group.getKey() + '-' + page, items, emptyArmor());
                Path file = OUTPUT.resolve("all-items").resolve(group.getKey()).resolve(
                    String.format(Locale.ROOT, "page-%03d.png", page)
                );
                write(renderer, snapshot, plainPreview, file);
                for (int position = 0; position < pageEntries.size(); position++) {
                    AuditEntry entry = pageEntries.get(position);
                    ItemSnapshot item = items.get(position);
                    TextureResolver.ResolvedTexture resolved = theme.getTextures().resolve(item);
                    if ("PASS".equals(entry.status)) {
                        assertFalse(resolved.isFallback(), entry.itemId + " is PASS but final resolver used fallback");
                    }
                    String slotType = position < 27 ? "STORAGE" : "HOTBAR";
                    int slotIndex = position < 27 ? position : position - 27;
                    index.add(group.getKey() + '\t' + page + '\t' + position + '\t' + slotType + '\t' +
                        slotIndex + '\t' + entry.itemId + '\t' + entry.status + '\t' + entry.renderPath + '\t' +
                        resolved.getSource().name() + '\t' + resolved.isFallback() + '\t' +
                        OUTPUT.relativize(file).toString().replace('\\', '/'));
                    renderedDefinitions++;
                }
            }
        }
        assertEquals(1506, renderedDefinitions);
        Files.write(OUTPUT.resolve("inventory-page-index.tsv"), index, StandardCharsets.UTF_8);
        writeCurrentContactSheets(theme, entries);

        renderCurated(renderer, plainPreview, "A-block-test.png", Arrays.asList(
            "stone", "grass_block", "furnace", "crafting_table", "bookshelf", "sea_lantern",
            "oak_stairs", "stone_slab", "oak_fence", "cobblestone_wall", "glass", "oak_leaves",
            "ice", "glass_pane", "slime_block", "honey_block", "lantern", "rail", "chest",
            "redstone_lamp", "piston", "hopper", "observer", "anvil", "flower_pot", "iron_bars",
            "chain", "soul_lantern", "scaffolding", "pointed_dripstone", "amethyst_cluster",
            "tinted_glass", "mangrove_roots", "water", "lava", "barrier"
        ));
        renderCurated(renderer, plainPreview, "B-2d-item-test.png", Arrays.asList(
            "diamond_sword", "netherite_sword", "diamond_pickaxe", "iron_axe", "golden_shovel",
            "bow", "crossbow", "fishing_rod", "trident", "mace", "golden_apple", "bread",
            "cooked_beef", "diamond", "iron_ingot", "amethyst_shard", "paper", "book", "brush",
            "spyglass", "firework_rocket", "sentry_armor_trim_smithing_template",
            "spire_armor_trim_smithing_template", "pig_spawn_egg", "creeper_spawn_egg", "bucket",
            "water_bucket", "flint_and_steel", "shears", "compass", "clock", "recovery_compass",
            "totem_of_undying", "experience_bottle", "ender_pearl", "wind_charge"
        ));
        renderCurated(renderer, plainPreview, "C-transparent-complex-test.png", Arrays.asList(
            "glass", "white_stained_glass", "glass_pane", "white_stained_glass_pane", "oak_leaves",
            "azalea_leaves", "ice", "packed_ice", "blue_ice", "slime_block", "honey_block",
            "oak_fence", "oak_fence_gate", "cobblestone_wall", "iron_bars", "chain", "rail",
            "powered_rail", "lantern", "soul_lantern", "campfire", "soul_campfire", "scaffolding",
            "tripwire_hook", "hopper", "cauldron", "brewing_stand", "end_rod", "lightning_rod",
            "pointed_dripstone", "small_amethyst_bud", "large_amethyst_bud", "amethyst_cluster",
            "mangrove_roots", "decorated_pot", "copper_grate"
        ));
        renderCurated(renderer, plainPreview, "D-special-test.png", Arrays.asList(
            "chest", "shield", "potion", "splash_potion", "lingering_potion", "player_head",
            "white_banner", "filled_map", "map", "compass", "clock", "recovery_compass", "bundle",
            "firework_star", "firework_rocket", "crossbow", "bow", "fishing_rod", "goat_horn",
            "knowledge_book", "enchanted_book", "written_book", "ominous_bottle", "suspicious_stew",
            "tipped_arrow", "tropical_fish_bucket", "axolotl_bucket", "wolf_armor", "painting",
            "armor_stand", "elytra", "carved_pumpkin", "dragon_head", "ender_chest", "shulker_box",
            "decorated_pot"
        ));
        renderCurated(renderer, plainPreview, "E-mixed-test.png", Arrays.asList(
            "stone", "diamond_sword", "golden_apple", "chest", "shield", "crafting_table", "glass",
            "oak_leaves", "lantern", "redstone", "repeater", "diamond", "netherite_ingot", "bread",
            "cooked_beef", "ender_pearl", "totem_of_undying", "elytra", "firework_rocket", "bow",
            "arrow", "water_bucket", "potion", "white_banner", "player_head", "compass", "clock",
            "sentry_armor_trim_smithing_template", "diamond_chestplate", "netherite_chestplate",
            "slime_block", "honey_block", "rail", "furnace", "bookshelf", "sea_lantern"
        ));
        renderCurated(renderer, plainPreview, "G-bed-family-test.png", Arrays.asList(
            "white_bed", "orange_bed", "magenta_bed", "light_blue_bed", "yellow_bed", "lime_bed",
            "pink_bed", "gray_bed", "light_gray_bed", "cyan_bed", "purple_bed", "blue_bed",
            "brown_bed", "green_bed", "red_bed", "black_bed"
        ));

        InventorySnapshot armorSnapshot = armorSnapshot();
        BufferedImage armorPreview = previewRenderer.render(
            skin, ArmorEquipmentSet.from(armorSnapshot), equipmentAssets
        );
        write(renderer, armorSnapshot, armorPreview, OUTPUT.resolve("samples/F-armor-runtime-test.png"));
    }

    private static void renderCurated(
        Java2DInventoryRenderer renderer, BufferedImage preview, String fileName, List<String> ids
    ) throws Exception {
        List<ItemSnapshot> items = new ArrayList<ItemSnapshot>();
        for (String id : ids) items.add(ItemSnapshot.basic("minecraft:" + id, 1));
        write(renderer, snapshot(fileName, items, emptyArmor()), preview, OUTPUT.resolve("samples").resolve(fileName));
    }

    private static InventorySnapshot armorSnapshot() {
        ArmorVisualDescriptor head = armor(ArmorVisualDescriptor.Slot.HEAD, "diamond_helmet", "diamond", "spire", "redstone", null);
        ArmorVisualDescriptor chest = armor(ArmorVisualDescriptor.Slot.CHEST, "diamond_chestplate", "diamond", "spire", "redstone", null);
        ArmorVisualDescriptor legs = armor(ArmorVisualDescriptor.Slot.LEGS, "diamond_leggings", "diamond", "spire", "redstone", null);
        ArmorVisualDescriptor feet = armor(ArmorVisualDescriptor.Slot.FEET, "diamond_boots", "diamond", "spire", "redstone", null);
        List<ItemSnapshot> storage = new ArrayList<ItemSnapshot>();
        storage.add(ItemSnapshot.basic("minecraft:diamond_chestplate", 1));
        storage.add(item(armor(ArmorVisualDescriptor.Slot.CHEST, "diamond_chestplate", "diamond", "spire", "redstone", null)));
        storage.add(item(armor(ArmorVisualDescriptor.Slot.CHEST, "diamond_chestplate", "diamond", "spire", "gold", null)));
        storage.add(item(armor(ArmorVisualDescriptor.Slot.CHEST, "netherite_chestplate", "netherite", "coast", "gold", null)));
        storage.add(item(armor(ArmorVisualDescriptor.Slot.CHEST, "leather_chestplate", "leather", null, null, 0x315ac8)));
        storage.add(item(armor(ArmorVisualDescriptor.Slot.CHEST, "leather_chestplate", "leather", "ward", "amethyst", 0x6c35b8)));
        storage.add(ItemSnapshot.basic("minecraft:iron_chestplate", 1));
        storage.add(ItemSnapshot.basic("minecraft:golden_chestplate", 1));
        storage.add(ItemSnapshot.basic("minecraft:chainmail_chestplate", 1));
        storage.add(ItemSnapshot.basic("minecraft:turtle_helmet", 1));
        return snapshot("armor-runtime", storage, Arrays.asList(
            InventorySlot.of(SlotType.ARMOR_HEAD, 0, item(head)),
            InventorySlot.of(SlotType.ARMOR_CHEST, 0, item(chest)),
            InventorySlot.of(SlotType.ARMOR_LEGS, 0, item(legs)),
            InventorySlot.of(SlotType.ARMOR_FEET, 0, item(feet))
        ));
    }

    private static ItemSnapshot item(ArmorVisualDescriptor armor) {
        return new ItemSnapshot(armor.getBaseMaterialKey(), 1, 0, 0, null, null, armor.hasGlint(), null, armor);
    }

    private static ArmorVisualDescriptor armor(
        ArmorVisualDescriptor.Slot slot, String item, String equipment, String pattern, String material, Integer color
    ) {
        return new ArmorVisualDescriptor(
            slot, "minecraft:" + item, "minecraft:" + equipment,
            pattern == null ? null : "minecraft:" + pattern,
            material == null ? null : "minecraft:" + material,
            color, false
        );
    }

    private static ItemSnapshot itemForAudit(AuditEntry entry) {
        ArmorVisualDescriptor.Slot slot = leatherSlot(entry.itemId);
        if (slot == null) return ItemSnapshot.basic(entry.itemId, 1);
        return item(new ArmorVisualDescriptor(
            slot, entry.itemId, "minecraft:leather", null, null, Integer.valueOf(0xa06540), false
        ));
    }

    private static ArmorVisualDescriptor.Slot leatherSlot(String itemId) {
        if ("minecraft:leather_helmet".equals(itemId)) return ArmorVisualDescriptor.Slot.HEAD;
        if ("minecraft:leather_chestplate".equals(itemId)) return ArmorVisualDescriptor.Slot.CHEST;
        if ("minecraft:leather_leggings".equals(itemId)) return ArmorVisualDescriptor.Slot.LEGS;
        if ("minecraft:leather_boots".equals(itemId)) return ArmorVisualDescriptor.Slot.FEET;
        return null;
    }

    private static void writeCurrentContactSheets(Theme theme, List<AuditEntry> entries) throws Exception {
        Path root = OUTPUT.resolve("contact-sheets-1.16.0");
        Map<String, List<AuditEntry>> categories = new LinkedHashMap<String, List<AuditEntry>>();
        Map<String, List<AuditEntry>> paths = new LinkedHashMap<String, List<AuditEntry>>();
        for (AuditEntry entry : entries) {
            categories.computeIfAbsent(entry.category, ignored -> new ArrayList<AuditEntry>()).add(entry);
            paths.computeIfAbsent(entry.renderPath, ignored -> new ArrayList<AuditEntry>()).add(entry);
        }
        writeSheetGroups(theme, root.resolve("category"), categories);
        writeSheetGroups(theme, root.resolve("render-path"), paths);
    }

    private static void writeSheetGroups(
        Theme theme, Path root, Map<String, List<AuditEntry>> groups
    ) throws Exception {
        for (Map.Entry<String, List<AuditEntry>> group : groups.entrySet()) {
            List<AuditEntry> values = group.getValue();
            for (int offset = 0, page = 1; offset < values.size(); offset += SHEET_COLUMNS * SHEET_ROWS, page++) {
                List<AuditEntry> pageEntries = values.subList(
                    offset, Math.min(offset + SHEET_COLUMNS * SHEET_ROWS, values.size())
                );
                BufferedImage sheet = new BufferedImage(
                    SHEET_COLUMNS * CELL_WIDTH, SHEET_ROWS * CELL_HEIGHT, BufferedImage.TYPE_INT_ARGB
                );
                Graphics2D graphics = sheet.createGraphics();
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                graphics.setColor(new Color(34, 40, 47));
                graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
                graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
                for (int position = 0; position < pageEntries.size(); position++) {
                    drawCell(graphics, theme, pageEntries.get(position),
                        (position % SHEET_COLUMNS) * CELL_WIDTH,
                        (position / SHEET_COLUMNS) * CELL_HEIGHT);
                }
                graphics.dispose();
                Path file = root.resolve(slug(group.getKey())).resolve(
                    String.format(Locale.ROOT, "sheet-%03d.png", page)
                );
                Files.createDirectories(file.getParent());
                javax.imageio.ImageIO.write(sheet, "png", file.toFile());
            }
        }
    }

    private static void drawCell(Graphics2D graphics, Theme theme, AuditEntry entry, int x, int y) {
        graphics.setColor(new Color(67, 76, 88));
        graphics.fillRect(x + 1, y + 1, CELL_WIDTH - 2, CELL_HEIGHT - 2);
        TextureResolver.ResolvedTexture resolved = theme.getTextures().resolve(itemForAudit(entry));
        BufferedImage icon = resolved.getImage();
        graphics.drawImage(icon, x + (CELL_WIDTH - ICON_SIZE) / 2, y + 4, ICON_SIZE, ICON_SIZE, null);
        graphics.setColor(Color.WHITE);
        graphics.drawString(clip(shortId(entry.itemId), 19), x + 6, y + 68);
        graphics.setColor(new Color(190, 202, 216));
        graphics.drawString(clip(entry.renderPath, 19), x + 6, y + 94);
        graphics.setColor("PASS".equals(entry.status) ? new Color(139, 190, 142) :
            ("FAIL".equals(entry.status) ? new Color(255, 104, 104) : new Color(255, 189, 89)));
        graphics.drawString(entry.status, x + 6, y + 106);
    }

    private static String clip(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length - 1) + "~";
    }

    private static String shortId(String value) {
        int colon = value.indexOf(':');
        return colon < 0 ? value : value.substring(colon + 1);
    }

    private static String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', '-').replaceAll("[^a-z0-9.-]", "-");
    }

    private static InventorySnapshot snapshot(String label, List<ItemSnapshot> values, List<InventorySlot> armor) {
        List<InventorySlot> storage = new ArrayList<InventorySlot>();
        List<InventorySlot> hotbar = new ArrayList<InventorySlot>();
        for (int index = 0; index < 27; index++) {
            storage.add(index < values.size() ? InventorySlot.of(SlotType.STORAGE, index, values.get(index)) :
                InventorySlot.empty(SlotType.STORAGE, index));
        }
        for (int index = 0; index < 9; index++) {
            int position = 27 + index;
            hotbar.add(position < values.size() ? InventorySlot.of(SlotType.HOTBAR, index, values.get(position)) :
                InventorySlot.empty(SlotType.HOTBAR, index));
        }
        return new InventorySnapshot(
            InventorySnapshot.CURRENT_SCHEMA_VERSION,
            UUID.nameUUIDFromBytes(label.getBytes(StandardCharsets.UTF_8)),
            "Audit-" + label.replaceAll("[^A-Za-z0-9_-]", "-"),
            Instant.parse("2026-08-29T00:00:00Z"), "local-audit", "inventory-1.16.0",
            storage, hotbar, armor, InventorySlot.empty(SlotType.OFFHAND, 0)
        );
    }

    private static List<InventorySlot> emptyArmor() {
        return Arrays.asList(
            InventorySlot.empty(SlotType.ARMOR_HEAD, 0),
            InventorySlot.empty(SlotType.ARMOR_CHEST, 0),
            InventorySlot.empty(SlotType.ARMOR_LEGS, 0),
            InventorySlot.empty(SlotType.ARMOR_FEET, 0)
        );
    }

    private static void write(
        Java2DInventoryRenderer renderer, InventorySnapshot snapshot, BufferedImage preview, Path file
    ) throws Exception {
        RenderResult result = renderer.render(snapshot, preview);
        assertEquals(704, result.getWidth());
        assertEquals(664, result.getHeight());
        assertTrue(result.getByteSize() > 10_000);
        Files.createDirectories(file.getParent());
        Files.write(file, result.getBytes());
    }

    private static List<AuditEntry> entries(Map<String, Status> statuses) throws Exception {
        List<String> lines = Files.readAllLines(AUDIT.resolve("reports/icon-audit.tsv"), StandardCharsets.UTF_8);
        String[] header = lines.get(0).split("\\t", -1);
        Map<String, Integer> columns = columns(header);
        List<AuditEntry> result = new ArrayList<AuditEntry>();
        for (int index = 1; index < lines.size(); index++) {
            String[] row = lines.get(index).split("\\t", -1);
            String id = row[columns.get("item_id")];
            Status status = statuses.get(id);
            assertTrue(status != null, "missing final status for " + id);
            boolean runtimeLeather = leatherSlot(id) != null;
            boolean runtimeA34 = isPriorityA34(id);
            boolean staticBed = isBed(id);
            result.add(new AuditEntry(
                id,
                runtimeLeather ? "armor" : staticBed ? "special" : row[columns.get("category")],
                runtimeLeather ? "RUNTIME_COMPOSITE" : staticBed ? "GENERATED_SPECIAL_STATIC" :
                    runtimeA34 ? a34RenderPath(id) : row[columns.get("render_path")],
                runtimeLeather || staticBed ? "PASS" : status.status
            ));
        }
        Collections.sort(result, Comparator.comparing((AuditEntry entry) -> entry.category).thenComparing(entry -> entry.itemId));
        return result;
    }

    private static boolean isPriorityA34(String id) {
        return "minecraft:trident".equals(id) ||
            "minecraft:potion".equals(id) || "minecraft:splash_potion".equals(id) ||
            "minecraft:lingering_potion".equals(id) || "minecraft:tipped_arrow".equals(id);
    }

    private static boolean isBed(String id) {
        return id.startsWith("minecraft:") && id.endsWith("_bed");
    }

    private static String a34RenderPath(String id) {
        return "minecraft:trident".equals(id) ? "GENERATED_2D_GUI_MODEL" : "POTION_TINT";
    }

    private static Map<String, Status> statuses() throws Exception {
        Map<String, Status> result = new LinkedHashMap<String, Status>();
        for (String line : Files.readAllLines(AUDIT.resolve("reports/audit-status.tsv"), StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            String[] row = line.split("\\t", 3);
            result.put(row[0], new Status(row[1], row.length > 2 ? row[2] : ""));
        }
        return result;
    }

    private static Map<String, Integer> columns(String[] header) {
        Map<String, Integer> result = new LinkedHashMap<String, Integer>();
        for (int index = 0; index < header.length; index++) result.put(header[index], index);
        return result;
    }

    private static final class AuditEntry {
        final String itemId;
        final String category;
        final String renderPath;
        final String status;
        AuditEntry(String itemId, String category, String renderPath, String status) {
            this.itemId = itemId;
            this.category = category;
            this.renderPath = renderPath;
            this.status = status;
        }
    }

    private static final class Status {
        final String status;
        final String note;
        Status(String status, String note) { this.status = status; this.note = note; }
    }
}
