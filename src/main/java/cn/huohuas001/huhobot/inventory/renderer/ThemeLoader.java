package cn.huohuas001.huhobot.inventory.renderer;

import cn.huohuas001.huhobot.inventory.asset.VanillaImportedAssetProvider;
import cn.huohuas001.huhobot.inventory.model.SlotType;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.imageio.ImageIO;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Loads YAML descriptors and assets while enforcing theme-directory path boundaries. */
public final class ThemeLoader {
    private ThemeLoader() {}

    public static Theme load(Path themeDirectory) {
        return load(
            themeDirectory,
            VanillaImportedAssetProvider.disabled(themeDirectory, "Vanilla provider not configured")
        );
    }

    public static Theme load(Path themeDirectory, VanillaImportedAssetProvider vanilla) {
        return load(themeDirectory, vanilla, null);
    }

    public static Theme load(
        Path themeDirectory,
        VanillaImportedAssetProvider vanilla,
        Path customOverrideDirectory
    ) {
        Path root = Objects.requireNonNull(themeDirectory, "themeDirectory").toAbsolutePath().normalize();
        Path descriptorPath = safeResolve(root, "theme.yml");
        if (!Files.isRegularFile(descriptorPath)) {
            throw new IllegalArgumentException("Missing theme descriptor: " + descriptorPath);
        }
        YamlConfiguration descriptor = YamlConfiguration.loadConfiguration(descriptorPath.toFile());
        requireVersion(descriptor.getInt("format-version", 0), "theme");
        String id = requireText(descriptor.getString("id"), "theme.id");
        String name = requireText(descriptor.getString("name"), "theme.name");
        String version = requireText(descriptor.getString("version"), "theme.version");
        String minecraftVersion = requireText(
            descriptor.getString("minecraft-version"),
            "theme.minecraft-version"
        );
        String assetPackVersion = requireText(
            descriptor.getString("asset-pack-version"),
            "theme.asset-pack-version"
        );
        String interpolation = requireText(
            descriptor.getString("render.texture-interpolation", "bilinear"),
            "theme.render.texture-interpolation"
        );
        if (!"bilinear".equals(interpolation) && !"nearest".equals(interpolation)) {
            throw new IllegalArgumentException(
                "theme.render.texture-interpolation must be bilinear or nearest"
            );
        }

        Path backgroundPath = safeResolve(root, requireText(descriptor.getString("background"), "theme.background"));
        Path layoutPath = safeResolve(root, requireText(descriptor.getString("layout"), "theme.layout"));
        Path textureDirectory = safeResolve(
            root,
            requireText(descriptor.getString("textures-directory"), "theme.textures-directory")
        );
        Path overrideDirectory = safeResolve(
            root,
            requireText(descriptor.getString("overrides-directory", "overrides/items"), "theme.overrides-directory")
        );
        Path specialVariantDirectory = safeResolve(
            root,
            requireText(
                descriptor.getString("special-variants-directory", "special-variants"),
                "theme.special-variants-directory"
            )
        );
        Path runtimeCompositeDirectory = safeResolve(
            root,
            requireText(
                descriptor.getString("runtime-composites-directory", "runtime-composites/items/minecraft"),
                "theme.runtime-composites-directory"
            )
        );
        Path fallbackPath = safeResolve(
            root,
            requireText(descriptor.getString("fallback-texture"), "theme.fallback-texture")
        );

        Layout layout = loadLayout(layoutPath);
        BufferedImage background = readImage(backgroundPath, "background");
        if (background.getWidth() != layout.getWidth() || background.getHeight() != layout.getHeight()) {
            throw new IllegalArgumentException(
                "Background dimensions " + background.getWidth() + "x" + background.getHeight() +
                    " do not match layout canvas " + layout.getWidth() + "x" + layout.getHeight()
            );
        }
        TextureResolver textures = new TextureResolver(
            customOverrideDirectory,
            overrideDirectory,
            specialVariantDirectory,
            textureDirectory,
            fallbackPath,
            Objects.requireNonNull(vanilla, "vanilla"),
            Clock.systemDefaultZone()
        );
        if (Files.isDirectory(runtimeCompositeDirectory)) {
            textures.setRuntimeItemRenderer(new RuntimeItemIconRenderer(runtimeCompositeDirectory));
        }
        return new Theme(
            id,
            name,
            version,
            minecraftVersion,
            assetPackVersion,
            background,
            layout,
            textures,
            descriptor.getBoolean("render.draw-title", true),
            descriptor.getBoolean("render.draw-slot-backgrounds", true),
            "nearest".equals(interpolation)
        );
    }

    static Layout loadLayout(Path layoutPath) {
        if (!Files.isRegularFile(layoutPath)) throw new IllegalArgumentException("Missing layout: " + layoutPath);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(layoutPath.toFile());
        requireVersion(yaml.getInt("format-version", 0), "layout");

        Layout.Grid storage = grid(yaml, "storage");
        Layout.Grid hotbar = grid(yaml, "hotbar");
        Map<SlotType, Point> equipment = new EnumMap<SlotType, Point>(SlotType.class);
        equipment.put(SlotType.ARMOR_HEAD, point(yaml, "armor.head"));
        equipment.put(SlotType.ARMOR_CHEST, point(yaml, "armor.chest"));
        equipment.put(SlotType.ARMOR_LEGS, point(yaml, "armor.legs"));
        equipment.put(SlotType.ARMOR_FEET, point(yaml, "armor.feet"));
        equipment.put(SlotType.OFFHAND, point(yaml, "offhand"));
        int slotSize = positive(yaml, "slot.size");
        Rectangle playerPreview = yaml.isConfigurationSection("player-preview")
            ? rectangle(yaml, "player-preview")
            : inferLegacyPlayerPreview(equipment, slotSize);

        return new Layout(
            positive(yaml, "canvas.width"),
            positive(yaml, "canvas.height"),
            slotSize,
            positive(yaml, "slot.item-size"),
            point(yaml, "title"),
            storage,
            hotbar,
            equipment,
            playerPreview,
            new Point(nonNegative(yaml, "quantity.offset-x"), nonNegative(yaml, "quantity.offset-y")),
            new Rectangle(
                nonNegative(yaml, "durability.offset-x"),
                nonNegative(yaml, "durability.offset-y"),
                positive(yaml, "durability.width"),
                positive(yaml, "durability.height")
            )
        );
    }

    private static Rectangle rectangle(YamlConfiguration yaml, String path) {
        return new Rectangle(
            nonNegative(yaml, path + ".x"),
            nonNegative(yaml, path + ".y"),
            positive(yaml, path + ".width"),
            positive(yaml, path + ".height")
        );
    }

    /** Keeps already-deployed Faithful layouts working without overwriting user-owned YAML. */
    private static Rectangle inferLegacyPlayerPreview(Map<SlotType, Point> equipment, int slotSize) {
        Point head = equipment.get(SlotType.ARMOR_HEAD);
        Point chest = equipment.get(SlotType.ARMOR_CHEST);
        Point legs = equipment.get(SlotType.ARMOR_LEGS);
        Point feet = equipment.get(SlotType.ARMOR_FEET);
        Point offhand = equipment.get(SlotType.OFFHAND);
        if (head.x != chest.x || head.x != legs.x || head.x != feet.x) return null;
        int x = head.x + slotSize + 2;
        int y = head.y + 2;
        int right = offhand.x - 4;
        int bottom = feet.y + slotSize - 3;
        return right > x && bottom > y ? new Rectangle(x, y, right - x, bottom - y) : null;
    }

    private static Layout.Grid grid(YamlConfiguration yaml, String path) {
        return new Layout.Grid(
            nonNegative(yaml, path + ".start-x"),
            nonNegative(yaml, path + ".start-y"),
            positive(yaml, path + ".columns"),
            positive(yaml, path + ".rows"),
            positive(yaml, path + ".step-x"),
            positive(yaml, path + ".step-y")
        );
    }

    private static Point point(YamlConfiguration yaml, String path) {
        return new Point(nonNegative(yaml, path + ".x"), nonNegative(yaml, path + ".y"));
    }

    private static int positive(YamlConfiguration yaml, String path) {
        int value = yaml.getInt(path, Integer.MIN_VALUE);
        if (value < 1) throw new IllegalArgumentException(path + " must be positive");
        return value;
    }

    private static int nonNegative(YamlConfiguration yaml, String path) {
        int value = yaml.getInt(path, Integer.MIN_VALUE);
        if (value < 0) throw new IllegalArgumentException(path + " must not be negative");
        return value;
    }

    private static void requireVersion(int version, String type) {
        if (version != 1) throw new IllegalArgumentException("Unsupported " + type + " format-version " + version);
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }

    private static Path safeResolve(Path root, String relative) {
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("Theme path escapes its directory: " + relative);
        return resolved;
    }

    private static BufferedImage readImage(Path path, String label) {
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Missing " + label + ": " + path);
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) throw new IllegalArgumentException("Unreadable " + label + ": " + path);
            return image;
        } catch (IOException error) {
            throw new IllegalArgumentException("Could not read " + label + ": " + path, error);
        }
    }
}
