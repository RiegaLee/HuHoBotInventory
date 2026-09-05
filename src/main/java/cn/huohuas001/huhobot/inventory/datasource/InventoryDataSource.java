package cn.huohuas001.huhobot.inventory.datasource;

import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;

import java.util.concurrent.CompletionStage;

/** Internal asynchronous source boundary; implementations must return Bukkit-neutral snapshots. */
public interface InventoryDataSource {
    CompletionStage<InventorySnapshot> getInventory(String playerName);
}
