package cn.huohuas001.huhobot.inventory.renderer;

import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;

import java.awt.image.BufferedImage;

/** Converts a Bukkit-neutral snapshot directly to an in-memory PNG result. */
public interface InventoryRenderer {
    RenderResult render(InventorySnapshot snapshot);

    default RenderResult render(InventorySnapshot snapshot, BufferedImage playerPreview) {
        return render(snapshot);
    }

    default RenderResult render(
        InventorySnapshot snapshot,
        BufferedImage playerPreview,
        InventoryRenderMetadata metadata
    ) {
        return render(snapshot, playerPreview);
    }
}
