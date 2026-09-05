package cn.huohuas001.huhobot.inventory.asset;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Offline importer for user-owned Minecraft client assets. Does not use the network. */
public final class VanillaAssetImporter {
    private static final String ITEM_PREFIX = "assets/minecraft/items/";
    private static final String VERSION_ENTRY = "version.json";
    private static final String CURRENT_POINTER = "current-version.txt";
    private static final String GENERATED_BY = "HuHoBot Minecraft Inventory VanillaAssetImporter";
    private static final int MODEL_BAKER_VERSION = 7;
    private static final int MAX_VERSION_JSON_BYTES = 1024 * 1024;

    public ImportResult importClientJar(Path clientJar, Path cacheRoot, String expectedVersion) throws IOException {
        return importClientJar(clientJar, cacheRoot, expectedVersion, null);
    }

    public ImportResult importClientJar(
        Path clientJar,
        Path cacheRoot,
        String expectedVersion,
        Path texturePack
    ) throws IOException {
        Path jar = requireFile(clientJar, "Minecraft client JAR");
        Path pack = texturePack == null ? null : requireFile(texturePack, "texture resource pack");
        Path root = cacheRoot.toAbsolutePath().normalize();
        if (Files.exists(root) && !Files.isDirectory(root)) {
            throw new IllegalArgumentException("Vanilla cache root is not a directory: " + root);
        }
        Files.createDirectories(root);

        String jarHash = sha256(jar);
        String packHash = pack == null ? null : sha256(pack);
        try (ZipFile zip = new ZipFile(jar.toFile());
             ZipFile textureZip = pack == null ? null : new ZipFile(pack.toFile())) {
            VersionInfo version = readVersion(zip);
            if (expectedVersion != null && !expectedVersion.trim().isEmpty() &&
                !version.id.equals(expectedVersion.trim())) {
                throw new IllegalArgumentException(
                    "Minecraft version mismatch: expected " + expectedVersion.trim() + " but JAR is " + version.id
                );
            }
            String cacheKey = safeToken(version.id, "Minecraft version") + "-" + jarHash.substring(0, 12) +
                "-MB" + MODEL_BAKER_VERSION + (packHash == null ? "" : "-P" + packHash.substring(0, 8));
            Path target = safeResolve(root, cacheKey);
            if (isReusable(target, jarHash, packHash)) {
                writeCurrentPointer(root, cacheKey);
                Map<String, Object> metadata = readJson(target.resolve("metadata.json"));
                return new ImportResult(
                    ImportStatus.REUSED,
                    target,
                    version.id,
                    MiniJson.integer(metadata, "generatedIcons", 0),
                    MiniJson.integer(metadata, "totalDefinitions", 0)
                );
            }
            if (Files.exists(target)) {
                throw new IllegalStateException("Existing cache key is incomplete or does not match source JAR: " + target);
            }

            List<? extends ZipEntry> definitions = itemDefinitions(zip);
            if (definitions.isEmpty()) {
                throw new IllegalArgumentException("Client JAR has no " + ITEM_PREFIX + " definitions");
            }
            Path staging = safeResolve(root, ".staging-" + UUID.randomUUID().toString());
            Files.createDirectories(staging);
            try {
                ImportResult result = generate(
                    zip, textureZip, jar, jarHash, pack, packHash, version, definitions, staging, target, cacheKey
                );
                writeCurrentPointer(root, cacheKey);
                return result;
            } catch (Throwable error) {
                deleteTree(staging, root);
                if (error instanceof IOException) throw (IOException) error;
                if (error instanceof RuntimeException) throw (RuntimeException) error;
                throw new IOException("Could not import Vanilla assets", error);
            }
        } catch (ZipException error) {
            throw new IllegalArgumentException("Not a readable Minecraft client JAR: " + jar, error);
        }
    }

    private ImportResult generate(
        ZipFile zip,
        ZipFile texturePack,
        Path jar,
        String jarHash,
        Path texturePackPath,
        String texturePackHash,
        VersionInfo version,
        List<? extends ZipEntry> definitions,
        Path staging,
        Path target,
        String cacheKey
    ) throws IOException {
        ItemModelResolver resolver = new ItemModelResolver(zip, texturePack);
        int generated = 0;
        Map<String, Integer> renderCounts = new LinkedHashMap<String, Integer>();
        Map<String, String> renderPaths = new LinkedHashMap<String, String>();
        Map<String, Integer> reasonCounts = new LinkedHashMap<String, Integer>();
        List<Object> unresolved = new ArrayList<Object>();

        for (ZipEntry definition : definitions) {
            String itemPath = definition.getName().substring(ITEM_PREFIX.length());
            itemPath = itemPath.substring(0, itemPath.length() - ".json".length());
            ItemModelResolver.ResourceLocation material;
            try {
                material = ItemModelResolver.ResourceLocation.parse("minecraft:" + itemPath, "minecraft");
            } catch (IllegalArgumentException unsafe) {
                addUnresolved(unresolved, reasonCounts, "minecraft:" + itemPath, "UNSAFE_ITEM_ID");
                continue;
            }
            ItemModelResolver.Resolution resolution = resolver.resolve(definition.getName());
            if (!resolution.isResolved()) {
                addUnresolved(unresolved, reasonCounts, material.toString(), resolution.getReason());
                continue;
            }
            Path icon = safeResolve(
                staging.resolve("generated-icons"),
                material.toString().replace(':', '/') + ".png"
            );
            Files.createDirectories(icon.getParent());
            if (!ImageIO.write(resolution.getImage(), "png", icon.toFile())) {
                addUnresolved(unresolved, reasonCounts, material.toString(), "PNG_WRITE_ERROR");
                continue;
            }
            generated++;
            String kind = resolution.getKind().name();
            renderPaths.put(material.toString(), kind);
            Integer previous = renderCounts.get(kind);
            renderCounts.put(kind, previous == null ? 1 : previous + 1);
        }

        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("source", "Minecraft Java Edition");
        metadata.put("minecraftVersion", version.id);
        metadata.put("resourcePackMajor", version.resourcePackMajor);
        metadata.put("importedAt", Instant.now().toString());
        metadata.put("sourceJar", jar.getFileName().toString());
        metadata.put("sourceJarSha256", jarHash);
        metadata.put("generatedBy", GENERATED_BY);
        metadata.put("modelBakerVersion", MODEL_BAKER_VERSION);
        metadata.put("cacheKey", cacheKey);
        if (texturePackPath != null) {
            metadata.put("textureResourcePack", texturePackPath.getFileName().toString());
            metadata.put("textureResourcePackSha256", texturePackHash);
        }
        metadata.put("generatedIcons", generated);
        metadata.put("totalDefinitions", definitions.size());
        metadata.put("unresolved", unresolved.size());
        metadata.put("renderCounts", new LinkedHashMap<String, Object>(renderCounts));
        writeJson(staging.resolve("metadata.json"), metadata);

        Map<String, Object> coverage = new LinkedHashMap<String, Object>();
        coverage.put("minecraftVersion", version.id);
        coverage.put("totalDefinitions", definitions.size());
        coverage.put("generatedIcons", generated);
        coverage.put("renderCounts", new LinkedHashMap<String, Object>(renderCounts));
        coverage.put("unresolvedCount", unresolved.size());
        coverage.put("reasonCounts", new LinkedHashMap<String, Object>(reasonCounts));
        coverage.put("unresolved", unresolved);
        writeJson(staging.resolve("coverage.json"), coverage);
        writeUnresolved(staging.resolve("unresolved.txt"), unresolved);
        writeRenderPaths(staging.resolve("render-paths.tsv"), renderPaths);

        moveDirectory(staging, target);
        return new ImportResult(ImportStatus.IMPORTED, target, version.id, generated, definitions.size());
    }

    private static void addUnresolved(
        List<Object> unresolved,
        Map<String, Integer> reasonCounts,
        String material,
        String reason
    ) {
        Integer old = reasonCounts.get(reason);
        reasonCounts.put(reason, old == null ? 1 : old + 1);
        Map<String, Object> entry = new LinkedHashMap<String, Object>();
        entry.put("material", material);
        entry.put("reason", reason);
        unresolved.add(entry);
    }

    private static void writeUnresolved(Path path, List<Object> unresolved) throws IOException {
        List<String> lines = new ArrayList<String>();
        lines.add("# Material\tReason");
        for (Object raw : unresolved) {
            Map<String, Object> entry = MiniJson.object(raw, "unresolved entry");
            lines.add(entry.get("material") + "\t" + entry.get("reason"));
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private static void writeRenderPaths(Path path, Map<String, String> renderPaths) throws IOException {
        List<String> lines = new ArrayList<String>();
        lines.add("# Material\tRenderPath");
        for (Map.Entry<String, String> entry : renderPaths.entrySet()) {
            lines.add(entry.getKey() + "\t" + entry.getValue());
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private static List<? extends ZipEntry> itemDefinitions(ZipFile zip) {
        List<ZipEntry> definitions = new ArrayList<ZipEntry>();
        java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory() && entry.getName().startsWith(ITEM_PREFIX) && entry.getName().endsWith(".json")) {
                definitions.add(entry);
            }
        }
        Collections.sort(definitions, (left, right) -> left.getName().compareTo(right.getName()));
        return definitions;
    }

    private static VersionInfo readVersion(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry(VERSION_ENTRY);
        if (entry == null || entry.isDirectory()) {
            throw new IllegalArgumentException("JAR is missing Minecraft client " + VERSION_ENTRY);
        }
        try (InputStream input = zip.getInputStream(entry)) {
            Map<String, Object> root = MiniJson.object(
                MiniJson.parse(new String(readLimited(input, MAX_VERSION_JSON_BYTES), StandardCharsets.UTF_8)),
                VERSION_ENTRY
            );
            String id = safeToken(MiniJson.string(root, "id", true), "Minecraft version");
            int resourcePackMajor = 0;
            Object rawPack = root.get("pack_version");
            if (rawPack instanceof Map) {
                resourcePackMajor = MiniJson.integer(MiniJson.object(rawPack, "pack_version"), "resource_major", 0);
            }
            return new VersionInfo(id, resourcePackMajor);
        }
    }

    private static boolean isReusable(Path target, String jarHash, String texturePackHash) {
        Path metadata = target.resolve("metadata.json");
        Path icons = target.resolve("generated-icons");
        if (!Files.isRegularFile(metadata) || !Files.isDirectory(icons)) return false;
        try {
            Map<String, Object> values = readJson(metadata);
            if (!jarHash.equals(MiniJson.string(values, "sourceJarSha256", true)) ||
                MiniJson.integer(values, "modelBakerVersion", 0) != MODEL_BAKER_VERSION) return false;
            String recordedPackHash = MiniJson.string(values, "textureResourcePackSha256", false);
            return texturePackHash == null ? recordedPackHash == null : texturePackHash.equals(recordedPackHash);
        } catch (RuntimeException | IOException error) {
            return false;
        }
    }

    private static void writeCurrentPointer(Path root, String cacheKey) throws IOException {
        Path temporary = safeResolve(root, CURRENT_POINTER + ".tmp");
        Path target = safeResolve(root, CURRENT_POINTER);
        Files.write(temporary, Collections.singletonList(cacheKey), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target);
        }
    }

    private static Map<String, Object> readJson(Path path) throws IOException {
        return MiniJson.object(MiniJson.parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)), path.toString());
    }

    private static void writeJson(Path path, Map<String, Object> value) throws IOException {
        Files.write(path, MiniJson.stringify(value).getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > limit) throw new IOException("ZIP entry exceeds safety limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static Path requireFile(Path path, String label) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) throw new IllegalArgumentException("Missing " + label + ": " + normalized);
        return normalized;
    }

    private static Path safeResolve(Path root, String relative) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(relative).normalize();
        if (!resolved.startsWith(normalizedRoot)) throw new IllegalArgumentException("Cache path escapes root: " + relative);
        return resolved;
    }

    private static String safeToken(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException(label + " contains unsafe characters");
        return normalized;
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file); DigestInputStream hashing = new DigestInputStream(input, digest)) {
                byte[] buffer = new byte[64 * 1024];
                while (hashing.read(buffer) >= 0) { }
            }
            StringBuilder result = new StringBuilder();
            for (byte part : digest.digest()) result.append(String.format("%02X", part & 0xFF));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void deleteTree(Path target, Path allowedRoot) throws IOException {
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path normalizedRoot = allowedRoot.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedRoot) ||
            !normalizedTarget.getFileName().toString().startsWith(".staging-")) {
            throw new IllegalArgumentException("Refusing to remove unsafe staging path " + normalizedTarget);
        }
        if (!Files.exists(normalizedTarget)) return;
        Files.walkFileTree(normalizedTarget, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public Path diagnoseStone(Path clientJar, Path outputDirectory, Path texturePack) throws IOException {
        Path jar = requireFile(clientJar, "Minecraft client JAR");
        Path pack = requireFile(texturePack, "texture resource pack");
        Path output = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(output);
        try (ZipFile zip = new ZipFile(jar.toFile()); ZipFile textureZip = new ZipFile(pack.toFile())) {
            ItemModelResolver.BlockDiagnostic diagnostic = new ItemModelResolver(zip, textureZip)
                .diagnoseBlock("assets/minecraft/items/stone.json");
            BufferedImage finalInventory = inventoryStage(diagnostic.getShaded());
            List<ImageStage> stages = new ArrayList<ImageStage>();
            stages.add(new ImageStage("01-source-stone.png", diagnostic.getSource()));
            stages.add(new ImageStage("02-baked-stone-unshaded.png", diagnostic.getUnshaded()));
            stages.add(new ImageStage("03-baked-stone-shaded.png", diagnostic.getShaded()));
            stages.add(new ImageStage("04-final-inventory-stone.png", finalInventory));
            List<String> report = new ArrayList<String>();
            report.add("HuHoBot Inventory stone color diagnostic");
            report.add("Texture: " + diagnostic.getTexture());
            report.add("Texture provider: " + pack.getFileName());
            report.add("Model shading: top=1.00, sideA=0.80, sideB=0.62; no face exceeds source RGB");
            report.add("Final stage: shaded 64x64 block-model icon drawn 1:1 in the Faithful 64px item area");
            report.add("");
            for (ImageStage stage : stages) {
                Path file = safeResolve(output, stage.fileName);
                byte[] png = png(stage.image);
                Files.write(file, png);
                report.add(stage.fileName + "\t" + stage.image.getWidth() + "x" + stage.image.getHeight() +
                    "\tSHA-256=" + sha256(png) + "\tAverageRGB=" + averageRgb(stage.image));
            }
            Path reportPath = safeResolve(output, "stone-diagnostics.txt");
            Files.write(reportPath, report, StandardCharsets.UTF_8);
            return reportPath;
        }
    }

    private static BufferedImage inventoryStage(BufferedImage icon) {
        BufferedImage result = new BufferedImage(72, 72, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setColor(new Color(153, 153, 153));
            graphics.fillRect(0, 0, 72, 72);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.drawImage(icon, 4, 4, 64, 64, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) throw new IllegalStateException("No PNG writer is available");
        return output.toByteArray();
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder();
            for (byte part : hash) value.append(String.format(java.util.Locale.ROOT, "%02X", part & 0xff));
            return value.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String averageRgb(BufferedImage image) {
        long red = 0;
        long green = 0;
        long blue = 0;
        long alphaWeight = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getRGB(x, y);
                int alpha = color >>> 24;
                if (alpha == 0) continue;
                red += ((color >>> 16) & 0xff) * (long) alpha;
                green += ((color >>> 8) & 0xff) * (long) alpha;
                blue += (color & 0xff) * (long) alpha;
                alphaWeight += alpha;
            }
        }
        if (alphaWeight == 0) return "0,0,0";
        return Math.round((double) red / alphaWeight) + "," +
            Math.round((double) green / alphaWeight) + "," +
            Math.round((double) blue / alphaWeight);
    }

    public static void main(String[] args) throws Exception {
        if (args.length >= 1 && "audit".equals(args[0])) {
            if (args.length < 4 || args.length > 5) throw new IllegalArgumentException(
                "Usage: VanillaAssetImporter audit <cache-root-or-directory> <output-directory> " +
                    "<explicit-overrides-directory> [minecraft-client.jar]"
            );
            InventoryAssetVisualAudit.AuditResult result = new InventoryAssetVisualAudit().audit(
                Paths.get(args[1]), Paths.get(args[2]), Paths.get(args[3]),
                args.length == 5 ? Paths.get(args[4]) : null
            );
            System.out.println(
                "Visual audit: total=" + result.getTotal() + " generated=" + result.getGenerated() +
                    " explicit=" + result.getExplicit() + " autoSuspects=" + result.getSuspects() +
                    " at " + result.getOutput()
            );
            return;
        }
        if (args.length == 4 && "diagnose-stone".equals(args[0])) {
            Path report = new VanillaAssetImporter().diagnoseStone(
                Paths.get(args[1]), Paths.get(args[2]), Paths.get(args[3])
            );
            System.out.println("Stone diagnostic written to " + report);
            return;
        }
        if (args.length < 3 || args.length > 5 || !"import".equals(args[0])) {
            throw new IllegalArgumentException(
                "Usage: VanillaAssetImporter import <minecraft-client.jar> <cache-root> " +
                    "[expected-version] [texture-resource-pack.zip], or diagnose-stone " +
                    "<minecraft-client.jar> <output-directory> <texture-resource-pack.zip>, or audit " +
                    "<cache-root-or-directory> <output-directory> <explicit-overrides-directory> " +
                    "[minecraft-client.jar]"
            );
        }
        String expectedVersion = args.length == 4 ? args[3] : null;
        if (args.length == 5) expectedVersion = args[3];
        Path texturePack = args.length == 5 ? Paths.get(args[4]) : null;
        ImportResult result = new VanillaAssetImporter().importClientJar(
            Paths.get(args[1]), Paths.get(args[2]), expectedVersion, texturePack
        );
        System.out.println(
            result.getStatus() + " Minecraft " + result.getMinecraftVersion() + ": " +
                result.getGeneratedIcons() + "/" + result.getTotalDefinitions() + " icons at " +
                result.getCacheDirectory()
        );
    }

    private static final class ImageStage {
        private final String fileName;
        private final BufferedImage image;
        private ImageStage(String fileName, BufferedImage image) {
            this.fileName = fileName;
            this.image = image;
        }
    }

    public enum ImportStatus { IMPORTED, REUSED }

    public static final class ImportResult {
        private final ImportStatus status;
        private final Path cacheDirectory;
        private final String minecraftVersion;
        private final int generatedIcons;
        private final int totalDefinitions;

        private ImportResult(
            ImportStatus status,
            Path cacheDirectory,
            String minecraftVersion,
            int generatedIcons,
            int totalDefinitions
        ) {
            this.status = status;
            this.cacheDirectory = cacheDirectory;
            this.minecraftVersion = minecraftVersion;
            this.generatedIcons = generatedIcons;
            this.totalDefinitions = totalDefinitions;
        }

        public ImportStatus getStatus() { return status; }
        public Path getCacheDirectory() { return cacheDirectory; }
        public String getMinecraftVersion() { return minecraftVersion; }
        public int getGeneratedIcons() { return generatedIcons; }
        public int getTotalDefinitions() { return totalDefinitions; }
    }

    private static final class VersionInfo {
        private final String id;
        private final int resourcePackMajor;
        private VersionInfo(String id, int resourcePackMajor) {
            this.id = id;
            this.resourcePackMajor = resourcePackMajor;
        }
    }
}
