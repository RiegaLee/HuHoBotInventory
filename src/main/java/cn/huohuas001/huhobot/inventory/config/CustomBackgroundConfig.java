package cn.huohuas001.huhobot.inventory.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Validated, local-only user wallpaper settings. */
public final class CustomBackgroundConfig {
    private final boolean enabled;
    private final String inventoryFile;
    private final String enderChestFile;
    private final String fit;

    private CustomBackgroundConfig(
        boolean enabled,
        String inventoryFile,
        String enderChestFile,
        String fit
    ) {
        this.enabled = enabled;
        this.inventoryFile = safePng(inventoryFile, "render.custom-background.inventory-file", false);
        this.enderChestFile = safePng(
            enderChestFile,
            "render.custom-background.ender-chest-file",
            true
        );
        this.fit = Objects.requireNonNull(fit, "render.custom-background.fit")
            .trim().toLowerCase(Locale.ROOT);
        if (!"cover".equals(this.fit) && !"stretch".equals(this.fit)) {
            throw new IllegalArgumentException("render.custom-background.fit must be cover or stretch");
        }
    }

    public static CustomBackgroundConfig load(FileConfiguration config) {
        Objects.requireNonNull(config, "config");
        return new CustomBackgroundConfig(
            config.getBoolean("render.custom-background.enabled", false),
            config.getString("render.custom-background.inventory-file", "inventory.png"),
            config.getString("render.custom-background.ender-chest-file", ""),
            config.getString("render.custom-background.fit", "cover")
        );
    }

    public boolean isEnabled() { return enabled; }
    public String getFit() { return fit; }

    public Path inventoryPath(Path backgroundsDirectory) {
        return resolve(backgroundsDirectory, inventoryFile);
    }

    public Path enderChestPath(Path backgroundsDirectory) {
        return enderChestFile == null
            ? inventoryPath(backgroundsDirectory)
            : resolve(backgroundsDirectory, enderChestFile);
    }

    private static Path resolve(Path root, String fileName) {
        Path normalizedRoot = Objects.requireNonNull(root, "backgroundsDirectory")
            .toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(fileName).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Custom background escapes its directory: " + fileName);
        }
        return resolved;
    }

    private static String safePng(String value, String field, boolean optional) {
        String normalized = value == null ? "" : value.trim();
        if (optional && normalized.isEmpty()) return null;
        if (!normalized.matches("[A-Za-z0-9._-]+\\.png")) {
            throw new IllegalArgumentException(
                field + " must be a PNG file name without directories"
            );
        }
        return normalized;
    }
}
