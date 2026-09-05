package cn.huohuas001.huhobot.inventory.renderer;

import java.awt.image.BufferedImage;
import java.util.Objects;

/** Loaded, validated visual resources for one renderer theme. */
public final class Theme {
    private final String id;
    private final String name;
    private final String version;
    private final String minecraftVersion;
    private final String assetPackVersion;
    private final BufferedImage background;
    private final Layout layout;
    private final TextureResolver textures;
    private final boolean drawTitle;
    private final boolean drawSlotBackgrounds;
    private final boolean nearestNeighborTextures;

    Theme(
        String id,
        String name,
        String version,
        String minecraftVersion,
        String assetPackVersion,
        BufferedImage background,
        Layout layout,
        TextureResolver textures,
        boolean drawTitle,
        boolean drawSlotBackgrounds,
        boolean nearestNeighborTextures
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.version = Objects.requireNonNull(version, "version");
        this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        this.assetPackVersion = Objects.requireNonNull(assetPackVersion, "assetPackVersion");
        this.background = Objects.requireNonNull(background, "background");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.textures = Objects.requireNonNull(textures, "textures");
        this.drawTitle = drawTitle;
        this.drawSlotBackgrounds = drawSlotBackgrounds;
        this.nearestNeighborTextures = nearestNeighborTextures;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getMinecraftVersion() { return minecraftVersion; }
    public String getAssetPackVersion() { return assetPackVersion; }
    BufferedImage getBackground() { return background; }
    public Layout getLayout() { return layout; }
    public TextureResolver getTextures() { return textures; }
    public boolean isDrawTitle() { return drawTitle; }
    public boolean isDrawSlotBackgrounds() { return drawSlotBackgrounds; }
    public boolean isNearestNeighborTextures() { return nearestNeighborTextures; }
}
