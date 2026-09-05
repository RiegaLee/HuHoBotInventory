package cn.huohuas001.huhobot.inventory.qq;

@FunctionalInterface
public interface InventoryButtonHandler {
    InventoryButtonResult handle(InventoryButtonInteraction interaction);
}
