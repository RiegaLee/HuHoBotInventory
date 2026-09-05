package cn.huohuas001.huhobot.inventory.host;

import cn.huohuas001.bot.events.commands.RegisteredCommand;
import cn.huohuas001.bot.state.CommandRepositories;
import cn.huohuas001.huhobot.api.BindingVerificationState;
import cn.huohuas001.huhobot.api.PlayerBinding;
import org.bukkit.plugin.SimpleServicesManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialCompatibilityHostTest {
    @Test
    void officialCommandScannerSeesEveryEmbeddedEntryPoint() {
        assertEquals("为 HuHoBot 提供 Minecraft 背包与末影箱图片查询",
            OfficialQqCommandBridge.AGENT_ADDON_DESCRIPTION);
        assertEquals("RiegaLee", OfficialQqCommandBridge.AGENT_ADDON_AUTHOR);
        OfficialQqCommandBridge.InventoryCommands bridge =
            new OfficialQqCommandBridge.InventoryCommands(null, null);
        Set<String> actual = bridge.registeredCommands().stream()
            .map(RegisteredCommand::getCommand)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
            "inventorytest", "invtest", "inventory", "inv", "背包",
            "enderchest", "ec", "末影箱", "绑定", "解绑", "绑定列表", "设置主账号"
        )), actual);
    }

    @Test
    void agentSingleAccountBindingIsExposedHonestlyAsLegacy(@TempDir Path directory) {
        CommandRepositories.INSTANCE.initialize(directory.toFile());
        assertTrue(CommandRepositories.INSTANCE.getBindings().setBinding(
            "group-open-id", "user-open-id", "AgentPlayer", "QQ User"
        ));

        DynamicBindingServices bindings = new DynamicBindingServices(
            new SimpleServicesManager(), Logger.getLogger("compat-test")
        );
        Optional<PlayerBinding> result = bindings.findBinding("group-open-id", "user-open-id");
        assertTrue(result.isPresent());
        assertEquals("AgentPlayer", result.get().getPlayerName());
        assertEquals(BindingVerificationState.LEGACY_UNVERIFIED, result.get().getVerificationState());
        assertFalse(result.get().getPlayerUuid().isPresent());
        assertEquals(1, bindings.findBindings("group-open-id", "user-open-id").size());
        assertFalse(bindings.findBinding("other-group", "user-open-id").isPresent());
    }
}
