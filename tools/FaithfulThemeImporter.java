import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Reproducible, bounded importer for the HuHoBot Faithful 32x development theme. */
public final class FaithfulThemeImporter {
    private static final Asset[] ASSETS = {
        item("diamond"), item("emerald"), item("iron_ingot"), item("gold_ingot"),
        item("coal"), item("stick"), item("apple"), item("golden_apple"),
        item("bread"), item("cooked_beef"), item("diamond_sword"), item("diamond_pickaxe"),
        item("diamond_axe"), item("diamond_shovel"), item("bow"), item("arrow"),
        item("firework_rocket"), item("totem_of_undying"), item("diamond_helmet"),
        item("diamond_chestplate"), item("diamond_leggings"), item("diamond_boots"),
        block("stone"), block("cobblestone"), block("dirt"), block("oak_planks")
    };

    private FaithfulThemeImporter() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                "Usage: FaithfulThemeImporter <Faithful-pack-root> <plugin-unknown.png> <theme-output-dir>"
            );
        }
        Path packRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path fallbackSource = Paths.get(args[1]).toAbsolutePath().normalize();
        Path output = Paths.get(args[2]).toAbsolutePath().normalize();
        requireFile(packRoot.resolve("pack.mcmeta"), "Faithful pack.mcmeta");
        requireFile(packRoot.resolve("LICENSE.txt"), "Faithful LICENSE.txt");
        requireFile(fallbackSource, "plugin fallback texture");
        Files.createDirectories(output.resolve("assets").resolve("minecraft"));
        Files.createDirectories(output.resolve("fallback"));

        List<String> manifest = new ArrayList<String>();
        manifest.add("target\tmaterial-key\tsource-path\tsource-sha256\toutput-sha256\tprocessing");
        importBackground(packRoot, output, manifest);
        for (Asset asset : ASSETS) importAsset(packRoot, output, "assets", asset, manifest);
        importShield(packRoot, output, manifest);

        Path fallbackTarget = output.resolve("fallback").resolve("unknown.png");
        Files.copy(fallbackSource, fallbackTarget, StandardCopyOption.REPLACE_EXISTING);
        manifest.add(
            "fallback/unknown.png\t*\tHuHoBot original default/items/unknown.png\t" +
                sha256(fallbackSource) + "\t" + sha256(fallbackTarget) + "\tcopied; first-party fallback"
        );
        Files.write(output.resolve("ASSET_MANIFEST.tsv"), manifest, StandardCharsets.UTF_8);
    }

    private static void importShield(Path packRoot, Path output, List<String> manifest) throws Exception {
        String relative = "assets/minecraft/textures/entity/shield/shield_base_nopattern.png";
        Path sourcePath = requireFile(packRoot.resolve(relative), "Faithful plain shield entity texture");
        BufferedImage generated = ClientEquivalentShieldOverrideGenerator.generate(read(sourcePath));
        String sourceHash = sha256(sourcePath);
        String processing = "generated 64x plain shield from unmodified Faithful entity texture and " +
            "Minecraft 26.1.2 ShieldModel; banner patterns deferred";
        for (String target : new String[] {
            "assets/minecraft/shield.png", "overrides/items/minecraft/shield.png"
        }) {
            Path targetPath = output.resolve(target);
            Files.createDirectories(targetPath.getParent());
            write(generated, targetPath);
            manifest.add(target + "\tminecraft:shield\t" + relative + "\t" + sourceHash + "\t" +
                sha256(targetPath) + "\t" + processing);
        }
    }

    private static void importBackground(Path packRoot, Path output, List<String> manifest) throws Exception {
        String relative = "assets/minecraft/textures/gui/container/inventory.png";
        Path sourcePath = requireFile(packRoot.resolve(relative), "Faithful inventory GUI");
        BufferedImage source = read(sourcePath);
        Rectangle bounds = opaqueBounds(source);
        if (bounds.isEmpty()) throw new IllegalArgumentException("Faithful inventory GUI has no visible pixels");

        BufferedImage target = new BufferedImage(bounds.width * 2, bounds.height * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            );
            graphics.drawImage(
                source,
                0,
                0,
                target.getWidth(),
                target.getHeight(),
                bounds.x,
                bounds.y,
                bounds.x + bounds.width,
                bounds.y + bounds.height,
                null
            );
        } finally {
            graphics.dispose();
        }
        removeCraftingControls(target);
        Path targetPath = output.resolve("background.png");
        write(target, targetPath);
        manifest.add(
            "background.png\t-\t" + relative + "\t" + sha256(sourcePath) + "\t" +
                sha256(targetPath) + "\talpha-cropped " + bounds.width + "x" + bounds.height +
                "; nearest-neighbor 2x; creative-style crafting controls removed"
        );
    }

    private static void removeCraftingControls(BufferedImage target) {
        // Faithful's survival inventory contributes only these pixels in the otherwise empty
        // upper-right panel. Sampling the adjacent panel color keeps this derivation tied to the
        // source pack instead of baking a second, nearly-identical gray into the importer.
        Color panel = new Color(target.getRGB(380, 60), true);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setColor(panel);
            graphics.fillRect(390, 70, 292, 140);
        } finally {
            graphics.dispose();
        }
    }

    private static void importAsset(
        Path packRoot,
        Path output,
        String targetRoot,
        Asset asset,
        List<String> manifest
    )
        throws Exception {
        Path sourcePath = requireFile(
            asset.fromPack ? packRoot.resolve(asset.sourcePath) : Paths.get(asset.sourcePath),
            asset.materialKey
        );
        Path targetDirectory = output.resolve(targetRoot).toAbsolutePath().normalize();
        Path targetPath = targetDirectory.resolve(asset.targetPath).normalize();
        if (!targetPath.toAbsolutePath().normalize().startsWith(targetDirectory)) {
            throw new IllegalArgumentException("Asset target escapes theme directory: " + asset.targetPath);
        }
        Files.createDirectories(targetPath.getParent());
        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        manifest.add(
            targetRoot + "/" + asset.targetPath + "\t" + asset.materialKey + "\t" + asset.sourcePath + "\t" +
                sha256(sourcePath) + "\t" + sha256(targetPath) + "\t" + asset.processing
        );
    }

    private static Asset item(String name) {
        return new Asset(
            "minecraft/" + name + ".png",
            "minecraft:" + name,
            "assets/minecraft/textures/item/" + name + ".png",
            "copied unmodified",
            true
        );
    }

    private static Asset block(String name) {
        return new Asset(
            "minecraft/" + name + ".png",
            "minecraft:" + name,
            "assets/minecraft/textures/block/" + name + ".png",
            "copied unmodified; block texture used by the vanilla item model",
            true
        );
    }

    private static Rectangle opaqueBounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) continue;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        return maxX < minX
            ? new Rectangle()
            : new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static Path requireFile(Path path, String label) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Missing " + label + ": " + normalized);
        }
        return normalized;
    }

    private static BufferedImage read(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new IOException("Unreadable PNG: " + path);
        return image;
    }

    private static void write(BufferedImage image, Path path) throws IOException {
        if (!ImageIO.write(image, "png", path.toFile())) throw new IOException("No PNG writer for " + path);
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = Files.readAllBytes(path);
        byte[] hash = digest.digest(bytes);
        StringBuilder value = new StringBuilder(hash.length * 2);
        for (byte part : hash) value.append(String.format("%02X", part & 0xFF));
        return value.toString();
    }

    private static final class Asset {
        private final String targetPath;
        private final String materialKey;
        private final String sourcePath;
        private final String processing;
        private final boolean fromPack;

        private Asset(
            String targetPath,
            String materialKey,
            String sourcePath,
            String processing,
            boolean fromPack
        ) {
            this.targetPath = targetPath;
            this.materialKey = materialKey;
            this.sourcePath = sourcePath;
            this.processing = processing;
            this.fromPack = fromPack;
        }
    }
}
