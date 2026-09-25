package cn.huohuas001.huhobot.inventory.renderer;

import cn.huohuas001.huhobot.inventory.head.PlayerHeadIconCache;
import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;

import java.awt.image.BufferedImage;
import java.util.Objects;

/** Waits for the bounded set of custom head icons needed by one render, then delegates normally. */
public final class PlayerHeadPreparingRenderer implements InventoryRenderer {
    private final InventoryRenderer delegate;
    private final PlayerHeadIconCache playerHeads;
    private final int maxNewPerRequest;

    public PlayerHeadPreparingRenderer(
        InventoryRenderer delegate,
        PlayerHeadIconCache playerHeads,
        int maxNewPerRequest
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.playerHeads = Objects.requireNonNull(playerHeads, "playerHeads");
        this.maxNewPerRequest = Math.max(1, maxNewPerRequest);
    }

    @Override
    public RenderResult render(InventorySnapshot snapshot) {
        prepare(snapshot);
        return delegate.render(snapshot);
    }

    @Override
    public RenderResult render(InventorySnapshot snapshot, BufferedImage playerPreview) {
        prepare(snapshot);
        return delegate.render(snapshot, playerPreview);
    }

    @Override
    public RenderResult render(
        InventorySnapshot snapshot,
        BufferedImage playerPreview,
        InventoryRenderMetadata metadata
    ) {
        prepare(snapshot);
        return delegate.render(snapshot, playerPreview, metadata);
    }

    private void prepare(InventorySnapshot snapshot) {
        playerHeads.prepare(snapshot, maxNewPerRequest).join();
    }
}
