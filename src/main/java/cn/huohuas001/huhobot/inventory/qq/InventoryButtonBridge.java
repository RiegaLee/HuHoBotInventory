package cn.huohuas001.huhobot.inventory.qq;

import cn.huohuas001.huhobot.api.MessageReference;
import cn.huohuas001.huhobot.api.Registration;
import cn.huohuas001.huhobot.api.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Inventory-owned optional bridge to the QQ SDK already hosted by HuHoBot. */
public interface InventoryButtonBridge extends AutoCloseable {
    InventoryButtonBridge UNAVAILABLE = new InventoryButtonBridge() {
        @Override public boolean isAvailable() { return false; }
        @Override public CompletionStage<SendResult> replySelection(
            MessageReference reference, String markdown, List<InventoryButton> buttons
        ) {
            return CompletableFuture.completedFuture(
                SendResult.of(SendResult.Status.FAILED, "Inventory QQ button bridge is unavailable")
            );
        }
        @Override public Registration register(String dataPrefix, InventoryButtonHandler handler) {
            throw new IllegalStateException("Inventory QQ button bridge is unavailable");
        }
        @Override public void close() { }
    };

    boolean isAvailable();

    CompletionStage<SendResult> replySelection(
        MessageReference reference,
        String markdown,
        List<InventoryButton> buttons
    );

    Registration register(String dataPrefix, InventoryButtonHandler handler);

    @Override
    void close();
}
