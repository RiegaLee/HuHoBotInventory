package cn.huohuas001.huhobot.inventory.asset;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Offline visual audit for a completed Vanilla icon cache. It never starts Paper, downloads
 * resources, changes the cache, or bundles the generated sheets in the addon.
 */
public final class InventoryAssetVisualAudit {
    private static final int COLUMNS = 10;
    private static final int ROWS = 10;
    private static final int CELL_WIDTH = 124;
    private static final int CELL_HEIGHT = 112;
    private static final int ICON_SIZE = 48;
    private static final String CURRENT_POINTER = "current-version.txt";

    public AuditResult audit(Path cacheRootOrDirectory, Path outputDirectory, Path overrideDirectory)
        throws IOException {
        return audit(cacheRootOrDirectory, outputDirectory, overrideDirectory, null);
    }

    public AuditResult audit(
        Path cacheRootOrDirectory,
        Path outputDirectory,
        Path overrideDirectory,
        Path optionalClientJar
    ) throws IOException {
        Path cache = resolveCache(cacheRootOrDirectory);
        Path output = outputDirectory.toAbsolutePath().normalize();
        Path overrides = overrideDirectory == null ? null : overrideDirectory.toAbsolutePath().normalize();
        Path clientJar = optionalClientJar == null ? null : optionalClientJar.toAbsolutePath().normalize();
        if (overrides != null && !Files.isDirectory(overrides)) {
            throw new IllegalArgumentException("Missing explicit override directory: " + overrides);
        }
        if (clientJar != null && !Files.isRegularFile(clientJar)) {
            throw new IllegalArgumentException("Missing Minecraft client JAR: " + clientJar);
        }
        Files.createDirectories(output);
        Files.createDirectories(output.resolve("sheets/render-path"));
        Files.createDirectories(output.resolve("sheets/category"));
        Files.createDirectories(output.resolve("sheets/priority"));
        Files.createDirectories(output.resolve("reports"));
        Files.createDirectories(output.resolve("metadata"));
        Files.createDirectories(output.resolve("individual"));

        Map<String, Object> metadata = readJson(cache.resolve("metadata.json"));
        Map<String, Object> coverage = readJson(cache.resolve("coverage.json"));
        Map<String, String> paths = readPairs(cache.resolve("render-paths.tsv"));
        Map<String, String> unresolved = readUnresolved(coverage);
        Map<String, ModelInfo> models = clientJar == null ?
            Collections.<String, ModelInfo>emptyMap() : readModels(clientJar);
        String textureSource = stringOr(metadata.get("textureResourcePack"), "VANILLA_CLIENT_JAR");

        Map<String, Entry> entries = new LinkedHashMap<String, Entry>();
        for (Map.Entry<String, String> path : paths.entrySet()) {
            String material = path.getKey();
            Path icon = iconPath(cache, material);
            ModelInfo model = models.get(material);
            Entry entry = Entry.generated(
                material, path.getValue(), icon, textureSource,
                model == null ? ModelInfo.unavailable(material) : model
            );
            inspect(entry);
            entries.put(material, entry);
        }
        for (Map.Entry<String, String> missing : unresolved.entrySet()) {
            if (!entries.containsKey(missing.getKey())) {
                ModelInfo model = models.get(missing.getKey());
                entries.put(missing.getKey(), Entry.unresolved(
                    missing.getKey(), missing.getValue(),
                    model == null ? ModelInfo.unavailable(missing.getKey()) : model
                ));
            }
        }
        if (overrides != null) addOverrides(entries, overrides);

        List<Entry> ordered = new ArrayList<Entry>(entries.values());
        Collections.sort(ordered, Comparator.comparing(Entry::material));
        writeAuditTsv(output.resolve("reports/icon-audit.tsv"), ordered);
        writeSuspects(output.resolve("reports/suspects.tsv"), ordered);
        writeSpecialMatrix(output.resolve("reports/special-items.tsv"), ordered);
        writeStatus(output.resolve("reports/audit-status.tsv"), ordered);
        applyStatuses(output.resolve("reports/audit-status.tsv"), ordered);
        writeIndividualIndex(output.resolve("individual/index.tsv"), ordered);
        writeSheets(output.resolve("sheets/render-path"), groupBy(ordered, true));
        writeSheets(output.resolve("sheets/category"), groupBy(ordered, false));
        Map<String, List<Entry>> priority = new LinkedHashMap<String, List<Entry>>();
        List<Entry> priorityEntries = new ArrayList<Entry>();
        for (Entry entry : ordered) if (isPriority(entry.material)) priorityEntries.add(entry);
        priority.put("representative-items", priorityEntries);
        writeSheets(output.resolve("sheets/priority"), priority);
        writeSummary(output.resolve("reports/audit-summary.json"), cache, ordered, metadata);
        Files.copy(cache.resolve("metadata.json"), output.resolve("metadata/cache-metadata.json"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(cache.resolve("coverage.json"), output.resolve("metadata/generated-coverage.json"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(cache.resolve("render-paths.tsv"), output.resolve("metadata/render-paths.tsv"), StandardCopyOption.REPLACE_EXISTING);

        int generated = 0;
        int explicit = 0;
        int suspects = 0;
        for (Entry entry : ordered) {
            if (entry.generated) generated++;
            if ("EXPLICIT_OVERRIDE".equals(entry.renderPath)) explicit++;
            if (!entry.flags.isEmpty()) suspects++;
        }
        return new AuditResult(output, ordered.size(), generated, explicit, suspects);
    }

    private static Path resolveCache(Path value) throws IOException {
        Path input = value.toAbsolutePath().normalize();
        if (Files.isRegularFile(input.resolve("metadata.json")) && Files.isDirectory(input.resolve("generated-icons"))) {
            return input;
        }
        Path pointer = input.resolve(CURRENT_POINTER);
        if (!Files.isRegularFile(pointer)) throw new IllegalArgumentException("Not a Vanilla cache root or cache directory: " + input);
        String key = new String(Files.readAllBytes(pointer), StandardCharsets.UTF_8).trim();
        if (!key.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("Unsafe current cache key");
        Path cache = input.resolve(key).normalize();
        if (!cache.startsWith(input) || !Files.isRegularFile(cache.resolve("metadata.json"))) {
            throw new IllegalArgumentException("Current Vanilla cache is incomplete: " + cache);
        }
        return cache;
    }

    private static Path iconPath(Path cache, String material) {
        String safe = material.replace(':', '/');
        Path icon = cache.resolve("generated-icons").resolve(safe + ".png").normalize();
        if (!icon.startsWith(cache.resolve("generated-icons").normalize())) {
            throw new IllegalArgumentException("Unsafe material path: " + material);
        }
        return icon;
    }

    private static void addOverrides(Map<String, Entry> entries, Path root) throws IOException {
        List<Path> files = new ArrayList<Path>();
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".png"))
                .forEach(files::add);
        }
        Collections.sort(files);
        for (Path file : files) {
            Path relative = root.relativize(file);
            if (relative.getNameCount() < 2) continue;
            String namespace = relative.getName(0).toString();
            StringBuilder item = new StringBuilder();
            for (int index = 1; index < relative.getNameCount(); index++) {
                if (item.length() > 0) item.append('/');
                item.append(relative.getName(index));
            }
            String path = item.toString();
            if (!path.endsWith(".png")) continue;
            String material = namespace + ":" + path.substring(0, path.length() - 4).replace('\\', '/');
            Entry previous = entries.get(material);
            Entry override = Entry.override(material, file, previous == null ? ModelInfo.unavailable(material) : previous.model);
            inspect(override);
            entries.put(material, override);
        }
    }

    private static void inspect(Entry entry) throws IOException {
        if (entry.icon == null || !Files.isRegularFile(entry.icon)) {
            entry.flags.add("SUSPECT_MISSING_FILE");
            return;
        }
        BufferedImage image = ImageIO.read(entry.icon.toFile());
        if (image == null) {
            entry.flags.add("SUSPECT_UNREADABLE");
            return;
        }
        entry.width = image.getWidth();
        entry.height = image.getHeight();
        int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
        int opaque = 0;
        long red = 0, green = 0, blue = 0, alphaWeight = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int argb = image.getRGB(x, y);
            int alpha = argb >>> 24;
            if (alpha == 0) continue;
            opaque++;
            minX = Math.min(minX, x); minY = Math.min(minY, y);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
            red += ((argb >>> 16) & 255) * (long) alpha;
            green += ((argb >>> 8) & 255) * (long) alpha;
            blue += (argb & 255) * (long) alpha;
            alphaWeight += alpha;
        }
        entry.alphaCoverage = opaque / (double) (image.getWidth() * image.getHeight());
        if (opaque > 0) {
            entry.bbox = minX + "," + minY + "," + (maxX - minX + 1) + "," + (maxY - minY + 1);
            entry.bboxWidth = maxX - minX + 1;
            entry.bboxHeight = maxY - minY + 1;
            entry.averageRgb = Math.round(red / (double) alphaWeight) + "," +
                Math.round(green / (double) alphaWeight) + "," + Math.round(blue / (double) alphaWeight);
        }
        if (entry.alphaCoverage < 0.015) entry.flags.add("SUSPECT_EMPTY");
        if (entry.bboxWidth <= 8 && entry.bboxHeight <= 8) entry.flags.add("SUSPECT_UNDERSIZED");
        if (entry.alphaCoverage > 0.88 || (entry.bboxWidth >= entry.width - 1 && entry.bboxHeight >= entry.height - 1)) {
            entry.flags.add("SUSPECT_OVERSIZED");
        }
        if ("BLOCK_MODEL".equals(entry.renderPath) && entry.bboxWidth >= 28 && entry.bboxHeight >= 28) {
            int corners = opaqueCornerCount(image);
            if (corners >= 3) entry.flags.add("SUSPECT_FLAT");
        }
    }

    private static int opaqueCornerCount(BufferedImage image) {
        int inset = Math.max(0, Math.min(image.getWidth(), image.getHeight()) / 16);
        int[][] points = {{inset,inset},{image.getWidth()-1-inset,inset},{inset,image.getHeight()-1-inset},
            {image.getWidth()-1-inset,image.getHeight()-1-inset}};
        int count = 0;
        for (int[] point : points) if ((image.getRGB(point[0], point[1]) >>> 24) > 32) count++;
        return count;
    }

    private static Map<String, List<Entry>> groupBy(List<Entry> entries, boolean renderPath) {
        Map<String, List<Entry>> groups = new LinkedHashMap<String, List<Entry>>();
        for (Entry entry : entries) {
            String key = renderPath ? entry.renderPath : category(entry);
            groups.computeIfAbsent(slug(key), ignored -> new ArrayList<Entry>()).add(entry);
        }
        return groups;
    }

    private static String category(Entry entry) {
        String id = entry.material.substring(entry.material.indexOf(':') + 1);
        if (!entry.generated && !"EXPLICIT_OVERRIDE".equals(entry.renderPath)) return "special";
        if (contains(id, "helmet","chestplate","leggings","boots","horse_armor")) return "armor";
        if (contains(id, "sword","bow","crossbow","trident","mace","spear")) return "weapons";
        if (contains(id, "pickaxe","_axe","shovel","_hoe","shears","brush","fishing_rod","flint_and_steel")) return "tools";
        if (contains(id, "apple","bread","beef","porkchop","chicken","mutton","rabbit","cod","salmon","stew","soup","carrot","potato","melon","berries","cookie","cake","honey","beetroot")) return "food";
        if (contains(id, "sapling","flower","leaves","grass","fern","vine","seeds","moss","azalea","roots","fungus","lily")) return "plants";
        if (contains(id, "redstone","repeater","comparator","piston","observer","dispenser","dropper","lever","button","pressure_plate","rail","daylight_detector","sculk_sensor")) return "redstone";
        if (contains(id, "chest","barrel","shulker","hopper","furnace","dispenser","dropper")) return "containers";
        if ("BLOCK_MODEL".equals(entry.renderPath)) {
            if (contains(id,"stairs","slab","fence","wall","door","trapdoor","pane","lantern","torch","carpet","candle","chain","rail","sign","skull","head","pot","anvil")) return "blocks-complex";
            return "blocks-full-cube";
        }
        return "decorative";
    }

    private static boolean contains(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static boolean isPriority(String material) {
        String id = shortId(material);
        switch (id) {
            case "stone": case "diamond_block": case "white_wool": case "white_concrete": case "oak_planks":
            case "grass_block": case "furnace": case "crafting_table": case "bookshelf": case "oak_log":
            case "oak_stairs": case "oak_slab": case "oak_fence": case "cobblestone_wall": case "lantern":
            case "oak_leaves": case "glass": case "ice": case "diamond": case "bread":
            case "diamond_pickaxe": case "diamond_sword": case "iron_helmet": case "iron_chestplate":
            case "iron_leggings": case "iron_boots": case "chest": case "trapped_chest":
            case "ender_chest": case "shield": return true;
            default: return false;
        }
    }

    private static void writeSheets(Path root, Map<String, List<Entry>> groups) throws IOException {
        for (Map.Entry<String, List<Entry>> group : groups.entrySet()) {
            Path directory = root.resolve(group.getKey());
            Files.createDirectories(directory);
            List<Entry> entries = group.getValue();
            for (int offset = 0, page = 1; offset < entries.size(); offset += COLUMNS * ROWS, page++) {
                int end = Math.min(entries.size(), offset + COLUMNS * ROWS);
                BufferedImage sheet = new BufferedImage(COLUMNS * CELL_WIDTH, ROWS * CELL_HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = sheet.createGraphics();
                try {
                    graphics.setColor(new Color(36, 40, 46));
                    graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
                    for (int index = offset; index < end; index++) {
                        drawCell(graphics, entries.get(index), index - offset);
                    }
                } finally {
                    graphics.dispose();
                }
                ImageIO.write(sheet, "png", directory.resolve(String.format(Locale.ROOT, "sheet-%03d.png", page)).toFile());
            }
        }
    }

    private static void drawCell(Graphics2D graphics, Entry entry, int slot) throws IOException {
        int x = (slot % COLUMNS) * CELL_WIDTH;
        int y = (slot / COLUMNS) * CELL_HEIGHT;
        graphics.setColor(entry.flags.isEmpty() ? new Color(66, 72, 81) : new Color(101, 68, 48));
        graphics.fillRect(x + 2, y + 2, CELL_WIDTH - 4, CELL_HEIGHT - 4);
        graphics.setColor(new Color(25, 28, 32));
        int iconX = x + (CELL_WIDTH - ICON_SIZE) / 2;
        graphics.fillRect(iconX, y + 6, ICON_SIZE, ICON_SIZE);
        if (entry.icon != null && Files.isRegularFile(entry.icon)) {
            BufferedImage icon = ImageIO.read(entry.icon.toFile());
            if (icon != null) graphics.drawImage(icon, iconX, y + 6, ICON_SIZE, ICON_SIZE, null);
        } else {
            graphics.setColor(new Color(210, 73, 73));
            graphics.drawLine(iconX + 3, y + 9, iconX + 44, y + 50);
            graphics.drawLine(iconX + 44, y + 9, iconX + 3, y + 50);
        }
        graphics.setColor(Color.WHITE);
        String[] itemLines = wrap(shortId(entry.material), 20);
        drawClipped(graphics, itemLines[0], x + 6, y + 68, CELL_WIDTH - 12);
        if (!itemLines[1].isEmpty()) drawClipped(graphics, itemLines[1], x + 6, y + 80, CELL_WIDTH - 12);
        graphics.setColor(new Color(190, 202, 216));
        drawClipped(graphics, entry.renderPath, x + 6, y + 94, CELL_WIDTH - 12);
        String status = entry.auditStatus.isEmpty() ? (entry.flags.isEmpty() ? "PENDING" : "SUSPECT") : entry.auditStatus;
        graphics.setColor("PASS".equals(status) ? new Color(139, 190, 142) :
            ("FAIL".equals(status) ? new Color(255, 104, 104) : new Color(255, 189, 89)));
        drawClipped(graphics, status, x + 6, y + 106, CELL_WIDTH - 12);
    }

    private static String[] wrap(String value, int width) {
        if (value.length() <= width) return new String[] {value, ""};
        int split = value.lastIndexOf('_', width);
        if (split < width / 2) split = width;
        return new String[] {value.substring(0, split + (value.charAt(split) == '_' ? 1 : 0)),
            value.substring(split + (value.charAt(split) == '_' ? 1 : 0))};
    }

    private static void drawClipped(Graphics2D graphics, String text, int x, int y, int width) {
        FontMetrics metrics = graphics.getFontMetrics();
        String value = text;
        while (value.length() > 3 && metrics.stringWidth(value) > width) value = value.substring(0, value.length() - 2);
        if (!value.equals(text)) value += "~";
        graphics.drawString(value, x, y);
    }

    private static String shortId(String material) {
        int colon = material.indexOf(':');
        return colon < 0 ? material : material.substring(colon + 1);
    }

    private static void writeAuditTsv(Path file, List<Entry> entries) throws IOException {
        List<String> lines = new ArrayList<String>();
        lines.add("item_id\tclassification\trender_path\tcategory\tsource_model\tparent_model\ttexture_source\tfinal_source\tcache_file\twidth\theight\topaque_bbox\talpha_coverage\taverage_rgb\tsource_average_rgb\tbrightness_delta\tbrightness_status\texplicit_override\tspecial_reason\tauto_flags");
        for (Entry entry : entries) lines.add(entry.material + "\t" + entry.classification + "\t" +
            entry.renderPath + "\t" + category(entry) + "\t" + entry.model.sourceModel + "\t" +
            entry.model.parentModel + "\t" + entry.textureSource + "\t" + entry.finalSource + "\t" +
            (entry.icon == null ? "" : entry.icon.toAbsolutePath().normalize()) + "\t" + entry.width + "\t" +
            entry.height + "\t" + entry.bbox + "\t" + decimal(entry.alphaCoverage) + "\t" + entry.averageRgb +
            "\tNOT_AVAILABLE\tNOT_AVAILABLE\tRAW_SOURCE_NOT_RECORDED_IN_BAKED_CACHE\t" +
            "EXPLICIT_OVERRIDE".equals(entry.renderPath) + "\t" + entry.specialReason + "\t" + join(entry.flags));
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static void writeSuspects(Path file, List<Entry> entries) throws IOException {
        List<String> lines = new ArrayList<String>();
        lines.add("item_id\trender_path\tauto_flags\tnote");
        for (Entry entry : entries) if (!entry.flags.isEmpty()) {
            lines.add(entry.material + "\t" + entry.renderPath + "\t" + join(entry.flags) + "\tREVIEW_REQUIRED_NO_AUTOMATIC_FIX");
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static void writeStatus(Path file, List<Entry> entries) throws IOException {
        Map<String, String> old = Files.isRegularFile(file) ? readPairs(file) : Collections.<String, String>emptyMap();
        List<String> lines = new ArrayList<String>();
        lines.add("# item_id\tstatus\tnote");
        for (Entry entry : entries) {
            String preserved = old.get(entry.material);
            String[] prior = preserved == null ? new String[0] : preserved.split("\t", 2);
            String status = prior.length == 0 ? (entry.flags.isEmpty() ? "PENDING" : "SUSPECT") : prior[0];
            String note = prior.length < 2 ? "" : prior[1];
            if (!entry.generated && !"EXPLICIT_OVERRIDE".equals(entry.renderPath)) status = "DEFERRED";
            lines.add(entry.material + "\t" + status + "\t" + note);
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static void writeIndividualIndex(Path file, List<Entry> entries) throws IOException {
        List<String> lines = new ArrayList<String>();
        lines.add("# item_id\ticon_file\treport_row");
        int row = 2;
        for (Entry entry : entries) {
            lines.add(entry.material + "\t" + (entry.icon == null ? "" : entry.icon.toAbsolutePath().normalize()) +
                "\treports/icon-audit.tsv:" + row++);
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static void applyStatuses(Path file, List<Entry> entries) throws IOException {
        Map<String, String> values = readPairs(file);
        for (Entry entry : entries) {
            String raw = values.get(entry.material);
            if (raw != null) entry.auditStatus = raw.split("\t", 2)[0];
        }
    }

    private static void writeSpecialMatrix(Path file, List<Entry> entries) throws IOException {
        List<String> lines = new ArrayList<String>();
        lines.add("item_id\treason\ttier\trecommendation");
        for (Entry entry : entries) {
            if (entry.specialReason.isEmpty() && !"EXPLICIT_OVERRIDE".equals(entry.renderPath)) continue;
            String id = shortId(entry.material);
            String tier;
            String action;
            if (contains(id,"chest","shield")) { tier="1"; action="STATIC_EXPLICIT_OVERRIDE"; }
            else if (contains(id,"head","skull","banner","map","potion","compass","clock","bundle","firework_star")) { tier="3"; action="DYNAMIC_DEFERRED"; }
            else if ("SPECIAL_RENDERER".equals(entry.specialReason)) { tier="2"; action="EVALUATE_LIGHTWEIGHT_GENERATOR"; }
            else { tier="4"; action="DEFER_LOW_PRIORITY"; }
            lines.add(entry.material + "\t" + entry.specialReason + "\t" + tier + "\t" + action);
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static void writeSummary(Path file, Path cache, List<Entry> entries, Map<String, Object> metadata) throws IOException {
        int generated=0, explicit=0, unresolved=0, rawUnresolved=0, suspect=0;
        Map<String, Integer> pathCounts = new LinkedHashMap<String, Integer>();
        for (Entry entry : entries) {
            if (entry.generated) generated++;
            if ("EXPLICIT_OVERRIDE".equals(entry.renderPath)) explicit++;
            if (!entry.generated && !"EXPLICIT_OVERRIDE".equals(entry.renderPath)) unresolved++;
            if (!entry.specialReason.isEmpty()) rawUnresolved++;
            if (!entry.flags.isEmpty()) suspect++;
            pathCounts.put(entry.renderPath, pathCounts.getOrDefault(entry.renderPath, 0) + 1);
        }
        int passed=0, failed=0, pending=0, deferred=0;
        Map<String, String> statuses = readPairs(file.getParent().resolve("audit-status.tsv"));
        for (String raw : statuses.values()) {
            String status = raw.split("\t", 2)[0];
            if ("PASS".equals(status)) passed++;
            else if ("FAIL".equals(status)) failed++;
            else if ("DEFERRED".equals(status)) deferred++;
            else pending++;
        }
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("generatedAt", Instant.now().toString());
        summary.put("cacheKey", cache.getFileName().toString());
        summary.put("minecraftVersion", metadata.get("minecraftVersion"));
        summary.put("totalDefinitions", entries.size());
        summary.put("generatedCoverage", generated);
        summary.put("explicitOverrides", explicit);
        summary.put("vanillaUnresolved", rawUnresolved);
        summary.put("effectiveUnresolvedAfterOverrides", unresolved);
        summary.put("autoSuspects", suspect);
        summary.put("visualAudited", passed + failed);
        summary.put("visualPassed", passed);
        summary.put("visualFailed", failed);
        summary.put("pendingHumanReview", pending);
        summary.put("deferredSpecial", deferred);
        summary.put("renderCounts", new LinkedHashMap<String, Object>(pathCounts));
        Files.write(file, MiniJson.stringify(summary).getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> readPairs(Path file) throws IOException {
        Map<String, String> result = new LinkedHashMap<String, String>();
        if (!Files.isRegularFile(file)) return result;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty() || line.startsWith("#") || line.startsWith("item_id\t")) continue;
            String[] parts = line.split("\t", 3);
            if (parts.length >= 2) result.put(parts[0], parts[1] + (parts.length == 3 ? "\t" + parts[2] : ""));
        }
        return result;
    }

    private static Map<String, String> readUnresolved(Map<String, Object> coverage) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        Object raw = coverage.get("unresolved");
        if (!(raw instanceof List)) return result;
        for (Object item : MiniJson.array(raw, "unresolved")) {
            Map<String, Object> entry = MiniJson.object(item, "unresolved entry");
            result.put(String.valueOf(entry.get("material")), String.valueOf(entry.get("reason")));
        }
        return result;
    }

    private static Map<String, ModelInfo> readModels(Path jar) throws IOException {
        Map<String, ModelInfo> result = new LinkedHashMap<String, ModelInfo>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            java.util.Enumeration<? extends ZipEntry> values = zip.entries();
            while (values.hasMoreElements()) {
                ZipEntry entry = values.nextElement();
                String name = entry.getName();
                if (!name.startsWith("assets/minecraft/items/") || !name.endsWith(".json")) continue;
                String id = name.substring("assets/minecraft/items/".length(), name.length() - 5);
                String material = "minecraft:" + id;
                try {
                    Map<String, Object> root = MiniJson.object(MiniJson.parse(new String(readAll(zip, entry), StandardCharsets.UTF_8)), name);
                    Object rawModel = root.get("model");
                    String source = "NOT_STATIC_MODEL";
                    if (rawModel instanceof Map) source = stringOr(MiniJson.object(rawModel, "model").get("model"), "NOT_STATIC_MODEL");
                    String parent = "NOT_AVAILABLE";
                    if (source.contains(":")) {
                        String[] location = source.split(":", 2);
                        ZipEntry modelEntry = zip.getEntry("assets/" + location[0] + "/models/" + location[1] + ".json");
                        if (modelEntry != null) {
                            Map<String, Object> model = MiniJson.object(MiniJson.parse(new String(readAll(zip, modelEntry), StandardCharsets.UTF_8)), modelEntry.getName());
                            parent = stringOr(model.get("parent"), "NONE");
                        }
                    }
                    result.put(material, new ModelInfo(source, parent));
                } catch (RuntimeException ignored) {
                    result.put(material, new ModelInfo("PARSE_ERROR", "PARSE_ERROR"));
                }
            }
        }
        return result;
    }

    private static byte[] readAll(ZipFile zip, ZipEntry entry) throws IOException {
        try (java.io.InputStream input = zip.getInputStream(entry); java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return output.toByteArray();
        }
    }

    private static Map<String, Object> readJson(Path file) throws IOException {
        if (!Files.isRegularFile(file)) throw new IllegalArgumentException("Missing audit input: " + file);
        return MiniJson.object(MiniJson.parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)), file.toString());
    }

    private static String slug(String value) { return value.toLowerCase(Locale.ROOT).replace('_', '-'); }
    private static String stringOr(Object value, String fallback) { return value == null ? fallback : String.valueOf(value); }
    private static String decimal(double value) { return String.format(Locale.ROOT, "%.4f", value); }
    private static String join(Set<String> values) { return String.join(",", values); }

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) throw new IllegalArgumentException(
            "Usage: InventoryAssetVisualAudit <cache-root-or-directory> <output-directory> " +
                "<explicit-overrides-directory> [minecraft-client.jar]"
        );
        AuditResult result = new InventoryAssetVisualAudit().audit(
            Paths.get(args[0]), Paths.get(args[1]), Paths.get(args[2]), args.length == 4 ? Paths.get(args[3]) : null
        );
        System.out.println("Visual audit: total=" + result.total + " generated=" + result.generated +
            " explicit=" + result.explicit + " autoSuspects=" + result.suspects + " at " + result.output);
    }

    public static final class AuditResult {
        private final Path output;
        private final int total;
        private final int generated;
        private final int explicit;
        private final int suspects;
        private AuditResult(Path output, int total, int generated, int explicit, int suspects) {
            this.output=output; this.total=total; this.generated=generated; this.explicit=explicit; this.suspects=suspects;
        }
        public Path getOutput() { return output; }
        public int getTotal() { return total; }
        public int getGenerated() { return generated; }
        public int getExplicit() { return explicit; }
        public int getSuspects() { return suspects; }
    }

    private static final class ModelInfo {
        private final String sourceModel;
        private final String parentModel;
        private ModelInfo(String sourceModel, String parentModel) { this.sourceModel=sourceModel; this.parentModel=parentModel; }
        private static ModelInfo unavailable(String material) {
            return new ModelInfo("assets/minecraft/items/" + shortId(material) + ".json", "NOT_RECORDED_IN_BAKED_CACHE");
        }
    }

    private static final class Entry {
        private final String material;
        private final String classification;
        private final String renderPath;
        private final Path icon;
        private final String textureSource;
        private final String finalSource;
        private final String specialReason;
        private final boolean generated;
        private final ModelInfo model;
        private final Set<String> flags = new LinkedHashSet<String>();
        private int width;
        private int height;
        private int bboxWidth;
        private int bboxHeight;
        private String bbox = "EMPTY";
        private double alphaCoverage;
        private String averageRgb = "0,0,0";
        private String auditStatus = "";
        private Entry(String material,String classification,String renderPath,Path icon,String textureSource,
            String finalSource,String specialReason,boolean generated,ModelInfo model) {
            this.material=material;this.classification=classification;this.renderPath=renderPath;this.icon=icon;
            this.textureSource=textureSource;this.finalSource=finalSource;this.specialReason=specialReason;
            this.generated=generated;this.model=model;
        }
        private static Entry generated(String material,String path,Path icon,String texture,ModelInfo model) {
            return new Entry(material,"BLOCK_MODEL".equals(path)?"BLOCK":"GENERATED",path,icon,texture,"GENERATED_CACHE","",true,model);
        }
        private static Entry unresolved(String material,String reason,ModelInfo model) {
            return new Entry(material,"SPECIAL_RENDERER".equals(reason)?"SPECIAL":"UNRESOLVED",
                "SPECIAL_RENDERER".equals(reason)?"SPECIAL_UNSUPPORTED":"VANILLA_UNRESOLVED",null,
                "NONE","UNKNOWN",reason,false,model);
        }
        private static Entry override(String material,Path icon,ModelInfo model) {
            return new Entry(material,"SPECIAL","EXPLICIT_OVERRIDE",icon,"LOCAL_EXPLICIT_OVERRIDE","EXPLICIT_OVERRIDE","SPECIAL_RENDERER",false,model);
        }
        private String material() { return material; }
    }
}
