package cn.huohuas001.huhobot.inventory.datasource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OfflineInventoryDataSourceFactoryTest {
    @Test
    void selectsOnlyTheExactPaperImplementationCurrentlyBundled() {
        assertEquals(
            "cn.huohuas001.huhobot.inventory.datasource.PaperOfflineInventoryDataSource",
            OfflineInventoryDataSourceFactory.implementationClassName("1.21.11", true)
        );
        assertNull(OfflineInventoryDataSourceFactory.implementationClassName("1.21.11", false));
        assertNull(OfflineInventoryDataSourceFactory.implementationClassName("1.21.10", true));
        assertNull(OfflineInventoryDataSourceFactory.implementationClassName("26.1.1", true));
    }
}
