package cn.huohuas001.huhobot.inventory.datasource;

import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Read-only source for the last player state persisted by the Minecraft server. */
public interface OfflineInventoryDataSource {
    CompletionStage<Optional<InventorySnapshot>> getInventory(UUID playerUuid, String lastKnownName);
}
