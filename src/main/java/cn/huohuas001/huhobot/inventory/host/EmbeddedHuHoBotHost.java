package cn.huohuas001.huhobot.inventory.host;

import cn.huohuas001.huhobot.api.HuHoBotService;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicBoolean;

/** Lifecycle owner for the compatibility host packaged inside the Inventory JAR. */
public final class EmbeddedHuHoBotHost implements AutoCloseable {
    private final JavaPlugin plugin;
    private final HuHoBotService service;
    private final EmbeddedHuHoBotService ownedService;
    private final OfficialQqCommandBridge commandBridge;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private EmbeddedHuHoBotHost(
        JavaPlugin plugin,
        HuHoBotService service,
        EmbeddedHuHoBotService ownedService,
        OfficialQqCommandBridge commandBridge
    ) {
        this.plugin = plugin;
        this.service = service;
        this.ownedService = ownedService;
        this.commandBridge = commandBridge;
    }

    public static EmbeddedHuHoBotHost start(JavaPlugin plugin) {
        ServicesManager services = plugin.getServer().getServicesManager();
        RegisteredServiceProvider<HuHoBotService> existing =
            services.getRegistration(HuHoBotService.class);
        if (existing != null) {
            plugin.getLogger().info(
                "检测到现有 HuHoBot API 宿主，将直接复用 " + existing.getProvider().getApiVersion()
            );
            return new EmbeddedHuHoBotHost(plugin, existing.getProvider(), null, null);
        }

        DynamicBindingServices bindings = new DynamicBindingServices(services, plugin.getLogger());
        EmbeddedHuHoBotService service = new EmbeddedHuHoBotService(
            plugin, new OfficialQqMessageGateway(), bindings, bindings
        );
        OfficialQqCommandBridge bridge = null;
        try {
            services.register(HuHoBotService.class, service, plugin, ServicePriority.Normal);
            bridge = OfficialQqCommandBridge.start(plugin, service);
            plugin.getLogger().info(
                "已从 Inventory JAR 启用 HuHoBot API 1.3 兼容宿主；官方 Core 无需修改"
            );
            return new EmbeddedHuHoBotHost(plugin, service, service, bridge);
        } catch (Throwable error) {
            if (bridge != null) bridge.close();
            services.unregister(HuHoBotService.class, service);
            service.close();
            throw error;
        }
    }

    public HuHoBotService getService() { return service; }
    public boolean isEmbedded() { return ownedService != null; }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true) || ownedService == null) return;
        if (commandBridge != null) commandBridge.close();
        plugin.getServer().getServicesManager().unregister(HuHoBotService.class, ownedService);
        ownedService.close();
    }
}
