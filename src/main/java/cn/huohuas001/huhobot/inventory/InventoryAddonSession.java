package cn.huohuas001.huhobot.inventory;

import cn.huohuas001.huhobot.api.ApiVersion;
import cn.huohuas001.huhobot.api.Capability;
import cn.huohuas001.huhobot.api.CommandPermission;
import cn.huohuas001.huhobot.api.CommandSpec;
import cn.huohuas001.huhobot.api.HuHoBotService;
import cn.huohuas001.huhobot.api.PluginContext;
import cn.huohuas001.huhobot.api.PluginDescriptor;
import cn.huohuas001.huhobot.api.Registration;
import cn.huohuas001.huhobot.inventory.command.InventoryCommand;
import cn.huohuas001.huhobot.inventory.config.InventoryPluginConfig;
import cn.huohuas001.huhobot.inventory.datasource.InventoryDataSource;
import cn.huohuas001.huhobot.inventory.renderer.InventoryRenderer;
import cn.huohuas001.huhobot.inventory.qq.InventoryButtonBridge;
import cn.huohuas001.huhobot.inventory.skin.PlayerPreviewService;
import cn.huohuas001.huhobot.inventory.snapshot.OfflineInventorySnapshotStore;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owner-scoped API-only Inventory addon session, independent from Bukkit startup. */
final class InventoryAddonSession implements AutoCloseable {
    private final PluginContext context;
    private final List<Registration> commands;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private InventoryAddonSession(PluginContext context, List<Registration> commands) {
        this.context = context;
        this.commands = Collections.unmodifiableList(new ArrayList<Registration>(commands));
    }

    static InventoryAddonSession start(
        HuHoBotService service,
        PluginDescriptor descriptor,
        InventoryPluginConfig config,
        InventoryDataSource mockDataSource,
        InventoryDataSource onlineDataSource,
        InventoryRenderer renderer
    ) {
        return start(service, descriptor, config, mockDataSource, onlineDataSource, renderer, null);
    }

    static InventoryAddonSession start(
        HuHoBotService service,
        PluginDescriptor descriptor,
        InventoryPluginConfig config,
        InventoryDataSource mockDataSource,
        InventoryDataSource onlineDataSource,
        InventoryRenderer renderer,
        PlayerPreviewService previewService
    ) {
        return start(service, descriptor, config, mockDataSource, onlineDataSource, renderer, previewService, null);
    }

    static InventoryAddonSession start(
        HuHoBotService service,
        PluginDescriptor descriptor,
        InventoryPluginConfig config,
        InventoryDataSource mockDataSource,
        InventoryDataSource onlineDataSource,
        InventoryRenderer renderer,
        PlayerPreviewService previewService,
        OfflineInventorySnapshotStore offlineStore
    ) {
        return start(
            service, descriptor, config, mockDataSource, onlineDataSource, renderer,
            previewService, offlineStore, null, null, null, InventoryButtonBridge.UNAVAILABLE
        );
    }

    static InventoryAddonSession start(
        HuHoBotService service,
        PluginDescriptor descriptor,
        InventoryPluginConfig config,
        InventoryDataSource mockDataSource,
        InventoryDataSource onlineDataSource,
        InventoryRenderer renderer,
        PlayerPreviewService previewService,
        OfflineInventorySnapshotStore offlineStore,
        InventoryDataSource enderChestDataSource,
        InventoryRenderer enderChestRenderer,
        OfflineInventorySnapshotStore offlineEnderChestStore
    ) {
        return start(
            service, descriptor, config, mockDataSource, onlineDataSource, renderer,
            previewService, offlineStore, enderChestDataSource, enderChestRenderer,
            offlineEnderChestStore, InventoryButtonBridge.UNAVAILABLE
        );
    }

    static InventoryAddonSession start(
        HuHoBotService service,
        PluginDescriptor descriptor,
        InventoryPluginConfig config,
        InventoryDataSource mockDataSource,
        InventoryDataSource onlineDataSource,
        InventoryRenderer renderer,
        PlayerPreviewService previewService,
        OfflineInventorySnapshotStore offlineStore,
        InventoryDataSource enderChestDataSource,
        InventoryRenderer enderChestRenderer,
        OfflineInventorySnapshotStore offlineEnderChestStore,
        InventoryButtonBridge buttonBridge
    ) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(mockDataSource, "mockDataSource");
        Objects.requireNonNull(onlineDataSource, "onlineDataSource");
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(buttonBridge, "buttonBridge");
        if ((enderChestDataSource == null) != (enderChestRenderer == null)) {
            throw new IllegalArgumentException("Ender Chest data source and renderer must be supplied together");
        }
        boolean enderChestAvailable = config.isEnderChestEnabled() && enderChestDataSource != null;

        if (!service.getApiVersion().supports(descriptor.getRequiredApiVersion())) {
            throw new IllegalStateException(
                "HuHoBot API " + service.getApiVersion() +
                    " cannot satisfy required " + descriptor.getRequiredApiVersion()
            );
        }
        Set<Capability> required = EnumSet.of(
            Capability.COMMANDS,
            Capability.TEXT_MESSAGES,
            Capability.BYTE_ARRAY_IMAGES,
            Capability.SCHEDULER,
            Capability.BINDING_LOOKUP
        );
        if (!service.getCapabilities().containsAll(required)) {
            throw new IllegalStateException("HuHoBot host is missing required Inventory capabilities");
        }

        PluginContext context = service.openPlugin(descriptor);
        List<Registration> registrations = new ArrayList<Registration>(5);
        try {
            CommandSpec mockSpec = new CommandSpec(
                config.getCommandName(),
                config.getCommandAliases(),
                "生成稳定的 Minecraft 模拟背包 PNG",
                CommandPermission.ANY,
                config.isPublishToMenu()
            );
            InventoryCommand mockHandler = new InventoryCommand(
                mockDataSource,
                renderer,
                config,
                context.getLogger()
            );
            registrations.add(context.getCommands().register(mockSpec, mockHandler));

            CommandSpec onlineSpec = new CommandSpec(
                config.getOnlineCommandName(),
                config.getOnlineCommandAliases(),
                "查询 Minecraft 玩家当前或最近保存的背包图片",
                CommandPermission.ANY,
                config.isOnlinePublishToMenu()
            );
            InventoryCommand onlineHandler = InventoryCommand.online(
                onlineDataSource,
                renderer,
                config,
                context.getBindings(),
                context.getLogger(),
                previewService,
                offlineStore,
                buttonBridge
            );
            registrations.add(context.getCommands().register(onlineSpec, onlineHandler));
            if (buttonBridge.isAvailable()) {
                registrations.add(buttonBridge.register(
                    onlineHandler.getButtonDataPrefix(),
                    interaction -> onlineHandler.handleButton(
                        interaction, context.getMessages(), context.getScheduler()
                    )
                ));
            }

            if (enderChestAvailable) {
                CommandSpec enderChestSpec = new CommandSpec(
                    config.getEnderChestCommandName(),
                    config.getEnderChestCommandAliases(),
                    "查询 Minecraft 玩家当前或最近保存的末影箱图片",
                    CommandPermission.ANY,
                    config.isEnderChestPublishToMenu()
                );
                InventoryCommand enderChestHandler = InventoryCommand.enderChest(
                    enderChestDataSource,
                    enderChestRenderer,
                    config,
                    context.getBindings(),
                    context.getLogger(),
                    offlineEnderChestStore,
                    buttonBridge
                );
                registrations.add(context.getCommands().register(enderChestSpec, enderChestHandler));
                if (buttonBridge.isAvailable()) {
                    registrations.add(buttonBridge.register(
                        enderChestHandler.getButtonDataPrefix(),
                        interaction -> enderChestHandler.handleButton(
                            interaction, context.getMessages(), context.getScheduler()
                        )
                    ));
                }
            }
            return new InventoryAddonSession(context, registrations);
        } catch (RuntimeException | Error error) {
            for (int index = registrations.size() - 1; index >= 0; index--) {
                try {
                    registrations.get(index).close();
                } catch (Throwable ignored) {
                    // The context close below is still authoritative for owner-scoped cleanup.
                }
            }
            context.close();
            throw error;
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            for (int index = commands.size() - 1; index >= 0; index--) commands.get(index).close();
        } finally {
            context.close();
        }
    }
}
