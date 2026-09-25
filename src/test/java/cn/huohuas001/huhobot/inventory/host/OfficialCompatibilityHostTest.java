package cn.huohuas001.huhobot.inventory.host;

import cn.huohuas001.bot.events.commands.RegisteredCommand;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OfficialCompatibilityHostTest {
    @Test
    void officialCommandScannerSeesEveryEmbeddedEntryPoint() {
        assertEquals("为 HuHoBot 提供 Minecraft 背包与末影箱图片查询",
            OfficialQqCommandBridge.ADDON_DESCRIPTION);
        assertEquals("RiegaLee", OfficialQqCommandBridge.ADDON_AUTHOR);
        OfficialQqCommandBridge.InventoryCommands bridge =
            new OfficialQqCommandBridge.InventoryCommands(null, null);
        Set<String> actual = bridge.registeredCommands().stream()
            .map(RegisteredCommand::getCommand)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
            "我的背包", "背包查看", "我的末影箱", "末影箱查看"
        )), actual);
    }
}
