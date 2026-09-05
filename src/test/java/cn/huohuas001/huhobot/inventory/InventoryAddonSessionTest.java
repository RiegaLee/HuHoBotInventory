package cn.huohuas001.huhobot.inventory;

import cn.huohuas001.huhobot.api.ApiVersion;
import cn.huohuas001.huhobot.api.AttachmentSnapshot;
import cn.huohuas001.huhobot.api.BindingService;
import cn.huohuas001.huhobot.api.BindingVerificationState;
import cn.huohuas001.huhobot.api.BotMessage;
import cn.huohuas001.huhobot.api.Capability;
import cn.huohuas001.huhobot.api.CommandContext;
import cn.huohuas001.huhobot.api.CommandHandler;
import cn.huohuas001.huhobot.api.CommandInvocation;
import cn.huohuas001.huhobot.api.CommandRegistry;
import cn.huohuas001.huhobot.api.CommandResult;
import cn.huohuas001.huhobot.api.CommandSpec;
import cn.huohuas001.huhobot.api.HuHoBotService;
import cn.huohuas001.huhobot.api.MentionSnapshot;
import cn.huohuas001.huhobot.api.MessageGateway;
import cn.huohuas001.huhobot.api.MessageReference;
import cn.huohuas001.huhobot.api.PluginContext;
import cn.huohuas001.huhobot.api.PluginDescriptor;
import cn.huohuas001.huhobot.api.PluginLogger;
import cn.huohuas001.huhobot.api.PlayerBinding;
import cn.huohuas001.huhobot.api.Principal;
import cn.huohuas001.huhobot.api.PrincipalRole;
import cn.huohuas001.huhobot.api.Registration;
import cn.huohuas001.huhobot.api.SendResult;
import cn.huohuas001.huhobot.api.SenderSnapshot;
import cn.huohuas001.huhobot.api.TaskHandle;
import cn.huohuas001.huhobot.api.TaskScheduler;
import cn.huohuas001.huhobot.inventory.config.InventoryPluginConfig;
import cn.huohuas001.huhobot.inventory.datasource.InventoryDataSource;
import cn.huohuas001.huhobot.inventory.datasource.InventoryDataSourceException;
import cn.huohuas001.huhobot.inventory.datasource.MockInventoryDataSource;
import cn.huohuas001.huhobot.inventory.renderer.InventoryRenderer;
import cn.huohuas001.huhobot.inventory.renderer.InventoryRenderMetadata;
import cn.huohuas001.huhobot.inventory.renderer.RenderResult;
import cn.huohuas001.huhobot.inventory.qq.InventoryButton;
import cn.huohuas001.huhobot.inventory.qq.InventoryButtonBridge;
import cn.huohuas001.huhobot.inventory.qq.InventoryButtonHandler;
import cn.huohuas001.huhobot.inventory.qq.InventoryButtonInteraction;
import cn.huohuas001.huhobot.inventory.qq.InventoryButtonResult;
import cn.huohuas001.huhobot.inventory.snapshot.OfflineInventorySnapshotStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryAddonSessionTest {
    @Test
    void registersCommandSendsPngAndClosesEveryOwnedResource() throws Exception {
        FakeService service = new FakeService(ApiVersion.CURRENT, allCapabilities());
        InventoryAddonSession session = InventoryAddonSession.start(
            service,
            descriptor(),
            config(),
            new MockInventoryDataSource("test"),
            new MockInventoryDataSource("online-test"),
            snapshot -> new RenderResult(new byte[] {1, 2, 3, 4}, "image/png", 704, 600)
        );

        CommandResult result = service.context.handler("inventorytest").handle(commandContext(service.context.gateway))
            .toCompletableFuture().get();
        assertEquals(CommandResult.Status.HANDLED, result.getStatus());
        assertArrayEquals(new byte[] {1, 2, 3, 4}, service.context.gateway.lastImage);
        assertEquals("image/png", service.context.gateway.lastMimeType);
        assertEquals("mock-inventory.png", service.context.gateway.lastFileName);
        assertEquals("Mock inventory: MockPlayer", service.context.gateway.lastCaption);
        assertEquals(0, service.context.gateway.textReplies.get());

        session.close();
        session.close();
        assertEquals(2, service.context.closedRegistrations.get());
        assertEquals(1, service.context.closeCount.get());
    }

    @Test
    void imageSendAndDataSourceFailuresReplyWithSimpleText() throws Exception {
        FakeService sendFailure = new FakeService(ApiVersion.CURRENT, allCapabilities());
        sendFailure.context.gateway.imageResult = SendResult.of(SendResult.Status.FAILED, "upload rejected");
        InventoryAddonSession sendSession = InventoryAddonSession.start(
            sendFailure,
            descriptor(),
            config(),
            new MockInventoryDataSource("test"),
            new MockInventoryDataSource("online-test"),
            snapshot -> new RenderResult(new byte[] {1}, "image/png", 1, 1)
        );
        try {
            CommandResult result = sendFailure.context.handler("inventorytest")
                .handle(commandContext(sendFailure.context.gateway))
                .toCompletableFuture().get();
            assertEquals(CommandResult.Status.HANDLED, result.getStatus());
            assertEquals("背包图片生成或发送失败，请稍后再试", sendFailure.context.gateway.lastText);
            assertEquals(1, sendFailure.context.logger.errors.get());
        } finally {
            sendSession.close();
        }

        FakeService sourceFailure = new FakeService(ApiVersion.CURRENT, allCapabilities());
        InventoryDataSource brokenSource = player -> {
            CompletableFuture<cn.huohuas001.huhobot.inventory.model.InventorySnapshot> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("mock source unavailable"));
            return failed;
        };
        InventoryAddonSession sourceSession = InventoryAddonSession.start(
            sourceFailure,
            descriptor(),
            config(),
            brokenSource,
            new MockInventoryDataSource("online-test"),
            snapshot -> new RenderResult(new byte[] {1}, "image/png", 1, 1)
        );
        try {
            CommandResult result = sourceFailure.context.handler("inventorytest")
                .handle(commandContext(sourceFailure.context.gateway))
                .toCompletableFuture().get();
            assertEquals(CommandResult.Status.HANDLED, result.getStatus());
            assertEquals(1, sourceFailure.context.gateway.textReplies.get());
            assertEquals(1, sourceFailure.context.logger.errors.get());
        } finally {
            sourceSession.close();
        }
    }

    @Test
    void onlineCommandUsesExplicitPlayerAndOnlineFileMetadata() throws Exception {
        FakeService service = new FakeService(ApiVersion.CURRENT, allCapabilities());
        AtomicReference<String> requestedPlayer = new AtomicReference<String>();
        InventoryDataSource onlineSource = player -> {
            requestedPlayer.set(player);
            return CompletableFuture.completedFuture(
                new MockInventoryDataSource("online-test").createSnapshot(player)
            );
        };
        InventoryAddonSession session = InventoryAddonSession.start(
            service,
            descriptor(),
            config(),
            new MockInventoryDataSource("mock-test"),
            onlineSource,
            snapshot -> new RenderResult(new byte[] {9, 8, 7}, "image/png", 704, 664)
        );
        try {
            CommandResult result = service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "inventory", "Steve", PrincipalRole.ADMIN))
                .toCompletableFuture()
                .get();
            assertEquals(CommandResult.Status.HANDLED, result.getStatus());
            assertEquals("Steve", requestedPlayer.get());
            assertEquals("inventory.png", service.context.gateway.lastFileName);
            assertEquals("Inventory: Steve", service.context.gateway.lastCaption);
            assertArrayEquals(new byte[] {9, 8, 7}, service.context.gateway.lastImage);
        } finally {
            session.close();
        }
    }

    @Test
    void enderChestCommandRegistersAndUsesIndependentRendererMetadata() throws Exception {
        FakeService service = new FakeService(ApiVersion.CURRENT, allCapabilities());
        AtomicReference<String> requestedPlayer = new AtomicReference<String>();
        InventoryDataSource enderSource = player -> {
            requestedPlayer.set(player);
            return CompletableFuture.completedFuture(
                new MockInventoryDataSource("ender-test").createSnapshot(player)
            );
        };
        InventoryAddonSession session = InventoryAddonSession.start(
            service,
            descriptor(),
            config(),
            new MockInventoryDataSource("mock-test"),
            new MockInventoryDataSource("online-test"),
            fakeRenderer(),
            null,
            null,
            enderSource,
            snapshot -> new RenderResult(new byte[] {4, 2}, "image/png", 704, 308),
            null
        );
        try {
            CommandResult result = service.context.handler("enderchest")
                .handle(commandContext(service.context.gateway, "enderchest", "Steve", PrincipalRole.ADMIN))
                .toCompletableFuture().get();
            assertEquals(CommandResult.Status.HANDLED, result.getStatus());
            assertEquals("Steve", requestedPlayer.get());
            assertEquals("ender-chest.png", service.context.gateway.lastFileName);
            assertEquals("Ender Chest: Steve", service.context.gateway.lastCaption);
            assertArrayEquals(new byte[] {4, 2}, service.context.gateway.lastImage);
        } finally {
            session.close();
        }
        assertEquals(3, service.context.closedRegistrations.get());
    }

    @Test
    void boundMemberUsesCurrentGroupAndOpenIdForSelfQuery() throws Exception {
        FakeService service = new FakeService(ApiVersion.CURRENT, allCapabilities());
        service.context.bind("group-open-id", "openid", "Steve", BindingVerificationState.VERIFIED);
        AtomicReference<String> requestedPlayer = new AtomicReference<String>();
        InventoryDataSource onlineSource = player -> {
            requestedPlayer.set(player);
            return CompletableFuture.completedFuture(new MockInventoryDataSource("online-test").createSnapshot(player));
        };
        InventoryAddonSession session = InventoryAddonSession.start(
            service,
            descriptor(),
            config(),
            new MockInventoryDataSource("mock-test"),
            onlineSource,
            fakeRenderer()
        );
        try {
            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "inventory", "", PrincipalRole.MEMBER))
                .toCompletableFuture().get();
            assertEquals("Steve", requestedPlayer.get());
            assertNotNull(service.context.gateway.lastImage);
        } finally {
            session.close();
        }
    }

    @Test
    void twoBindingsRequireOneTimeSecondaryAccountSelection() throws Exception {
        FakeService service = new FakeService(ApiVersion.CURRENT, allCapabilities());
        service.context.bindTwo("group-open-id", "openid", "Steve", "Alex");
        AtomicReference<String> requestedPlayer = new AtomicReference<String>();
        InventoryDataSource onlineSource = player -> {
            requestedPlayer.set(player);
            return CompletableFuture.completedFuture(new MockInventoryDataSource("online-test").createSnapshot(player));
        };
        InventoryAddonSession session = InventoryAddonSession.start(
            service, descriptor(), config(), new MockInventoryDataSource("mock-test"), onlineSource, fakeRenderer()
        );
        try {
            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "背包", "", PrincipalRole.MEMBER))
                .toCompletableFuture().get();
            assertTrue(service.context.gateway.lastText.contains("/背包 1（Steve）"));
            assertTrue(service.context.gateway.lastText.contains("/背包 2（Alex）"));
            assertEquals(null, requestedPlayer.get());

            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "背包", "2", PrincipalRole.MEMBER))
                .toCompletableFuture().get();
            assertEquals("Alex", requestedPlayer.get());
            assertNotNull(service.context.gateway.lastImage);

            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "背包", "1", PrincipalRole.MEMBER))
                .toCompletableFuture().get();
            assertTrue(service.context.gateway.lastText.contains("账号选择已失效"));
            assertTrue(service.context.gateway.lastText.contains("/背包 获取账号列表"));
        } finally {
            session.close();
        }
    }

    @Test
    void callbackButtonIsOneTimeUserScopedAndRunsSelectedAccount() throws Exception {
        FakeService service = new FakeService(ApiVersion.CURRENT, allCapabilities());
        service.context.bindTwo("group-open-id", "openid", "abcdefghijklmnop", "h1nt0n_X");
        AtomicReference<String> requestedPlayer = new AtomicReference<String>();
        InventoryDataSource onlineSource = player -> {
            requestedPlayer.set(player);
            return CompletableFuture.completedFuture(new MockInventoryDataSource("online-test").createSnapshot(player));
        };
        FakeButtonBridge buttons = new FakeButtonBridge();
        InventoryAddonSession session = InventoryAddonSession.start(
            service, descriptor(), config(), new MockInventoryDataSource("mock-test"), onlineSource,
            fakeRenderer(), null, null, null, null, null, buttons
        );
        try {
            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "背包", "", PrincipalRole.MEMBER))
                .toCompletableFuture().get();
            assertEquals("请选择账号（60 秒内有效）：", buttons.lastMarkdown);
            assertEquals("1 abcdefghijklmnop", buttons.lastButtons.get(0).getLabel());
            assertEquals("2 h1nt0n_X", buttons.lastButtons.get(1).getLabel());
            assertEquals(1, buttons.lastButtons.get(0).getStyle());
            assertEquals(1, buttons.lastButtons.get(1).getStyle());
            assertEquals("openid", buttons.lastButtons.get(1).getAllowedUserOpenId());
            String data = buttons.lastButtons.get(1).getData();
            InventoryButtonInteraction click = new InventoryButtonInteraction(
                "interaction-1", "group-open-id", "openid", data
            );

            assertEquals(InventoryButtonResult.SUCCESS, buttons.handle(data, click));
            assertEquals("h1nt0n_X", requestedPlayer.get());
            assertNotNull(service.context.gateway.lastImage);
            assertEquals(InventoryButtonResult.DUPLICATE, buttons.handle(data, click));

            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "背包", "", PrincipalRole.MEMBER))
                .toCompletableFuture().get();
            String staleData = buttons.lastButtons.get(0).getData();
            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "背包", "", PrincipalRole.MEMBER))
                .toCompletableFuture().get();
            InventoryButtonInteraction staleClick = new InventoryButtonInteraction(
                "interaction-stale", "group-open-id", "openid", staleData
            );
            assertEquals(InventoryButtonResult.EXPIRED_INVENTORY, buttons.handle(staleData, staleClick));
            assertEquals("账号选择已超时，请重新发送 /背包。", service.context.gateway.lastText);

            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "背包", "", PrincipalRole.MEMBER))
                .toCompletableFuture().get();
            String protectedData = buttons.lastButtons.get(0).getData();
            InventoryButtonInteraction foreignClick = new InventoryButtonInteraction(
                "interaction-2", "group-open-id", "another-user", protectedData
            );
            assertEquals(
                InventoryButtonResult.FORBIDDEN,
                buttons.handle(protectedData, foreignClick)
            );
        } finally {
            session.close();
        }
    }

    @Test
    void onlineCommandMapsUsagePermissionAndOfflineToFriendlyText() throws Exception {
        FakeService service = new FakeService(ApiVersion.CURRENT, allCapabilities());
        InventoryDataSource offlineSource = player -> {
            CompletableFuture<cn.huohuas001.huhobot.inventory.model.InventorySnapshot> failed =
                new CompletableFuture<cn.huohuas001.huhobot.inventory.model.InventorySnapshot>();
            failed.completeExceptionally(new InventoryDataSourceException(
                InventoryDataSourceException.Reason.PLAYER_OFFLINE,
                "offline"
            ));
            return failed;
        };
        InventoryAddonSession session = InventoryAddonSession.start(
            service,
            descriptor(),
            config(),
            new MockInventoryDataSource("mock-test"),
            offlineSource,
            fakeRenderer()
        );
        try {
            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "inventory", "", PrincipalRole.MEMBER))
                .toCompletableFuture().get();
            assertEquals(
                "你还没有绑定 Minecraft 账号，请先使用 /绑定 <游戏ID>。",
                service.context.gateway.lastText
            );

            service.context.bind(
                "group-open-id", "openid", "Steve", BindingVerificationState.LEGACY_UNVERIFIED
            );
            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "inventory", "", PrincipalRole.MEMBER))
                .toCompletableFuture().get();
            assertEquals("玩家当前不在线，暂时无法查询背包。", service.context.gateway.lastText);

            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "inventory", "Steve", PrincipalRole.MEMBER))
                .toCompletableFuture().get();
            assertEquals("权限不足，无法查询在线玩家背包。", service.context.gateway.lastText);

            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "inventory", "Steve", PrincipalRole.ADMIN))
                .toCompletableFuture().get();
            assertEquals("玩家当前不在线，暂时无法查询背包。", service.context.gateway.lastText);
            assertEquals(0, service.context.logger.errors.get());
        } finally {
            session.close();
        }
    }

    @Test
    void verifiedOfflineBindingReadsUuidSnapshotAndRealtimeStillWins(@TempDir Path temp) throws Exception {
        FakeService service = new FakeService(ApiVersion.CURRENT, allCapabilities());
        cn.huohuas001.huhobot.inventory.model.InventorySnapshot stored =
            new MockInventoryDataSource("snapshot-test").createSnapshot("Steve");
        service.context.bind(
            "group-open-id", "openid", "Steve", stored.getPlayerUuid(), BindingVerificationState.VERIFIED
        );
        OfflineInventorySnapshotStore store = new OfflineInventorySnapshotStore(temp, Logger.getAnonymousLogger());
        store.saveAsync(stored).get();
        AtomicBoolean onlineNow = new AtomicBoolean(false);
        InventoryDataSource offline = player -> {
            if (onlineNow.get()) {
                return CompletableFuture.completedFuture(
                    new MockInventoryDataSource("realtime-test").createSnapshot(player)
                );
            }
            CompletableFuture<cn.huohuas001.huhobot.inventory.model.InventorySnapshot> failed = new CompletableFuture<>();
            failed.completeExceptionally(new InventoryDataSourceException(
                InventoryDataSourceException.Reason.PLAYER_OFFLINE, "offline"
            ));
            return failed;
        };
        AtomicReference<InventoryRenderMetadata> metadata = new AtomicReference<InventoryRenderMetadata>();
        AtomicReference<cn.huohuas001.huhobot.inventory.model.InventorySnapshot> renderedSnapshot =
            new AtomicReference<cn.huohuas001.huhobot.inventory.model.InventorySnapshot>();
        InventoryRenderer renderer = new InventoryRenderer() {
            @Override public RenderResult render(cn.huohuas001.huhobot.inventory.model.InventorySnapshot snapshot) {
                return new RenderResult(new byte[] {4, 5, 6}, "image/png", 704, 664);
            }
            @Override public RenderResult render(
                cn.huohuas001.huhobot.inventory.model.InventorySnapshot snapshot,
                java.awt.image.BufferedImage preview,
                InventoryRenderMetadata value
            ) {
                metadata.set(value);
                renderedSnapshot.set(snapshot);
                return render(snapshot);
            }
        };
        InventoryAddonSession session = InventoryAddonSession.start(
            service, descriptor(), config(), new MockInventoryDataSource("mock"), offline, renderer, null, store
        );
        try {
            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "inventory", "", PrincipalRole.MEMBER))
                .toCompletableFuture().get();
            assertEquals(InventoryRenderMetadata.Freshness.OFFLINE_SNAPSHOT, metadata.get().getFreshness());
            assertArrayEquals(new byte[] {4, 5, 6}, service.context.gateway.lastImage);

            onlineNow.set(true);
            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "inventory", "", PrincipalRole.MEMBER))
                .toCompletableFuture().get();
            assertEquals(InventoryRenderMetadata.Freshness.REALTIME, metadata.get().getFreshness());
            assertEquals("realtime-test", renderedSnapshot.get().getSourceServer());
        } finally {
            session.close();
            store.close();
        }
    }

    @Test
    void verifiedOfflineEnderChestUsesItsIndependentSnapshotStore(@TempDir Path temp) throws Exception {
        FakeService service = new FakeService(ApiVersion.CURRENT, allCapabilities());
        cn.huohuas001.huhobot.inventory.model.InventorySnapshot stored =
            new MockInventoryDataSource("ender-snapshot-test").createSnapshot("Steve");
        service.context.bind(
            "group-open-id", "openid", "Steve", stored.getPlayerUuid(), BindingVerificationState.VERIFIED
        );
        OfflineInventorySnapshotStore enderStore = new OfflineInventorySnapshotStore(
            temp.resolve("ender"), Logger.getAnonymousLogger()
        );
        enderStore.saveAsync(stored).get();
        InventoryDataSource offlineEnder = player -> {
            CompletableFuture<cn.huohuas001.huhobot.inventory.model.InventorySnapshot> failed =
                new CompletableFuture<>();
            failed.completeExceptionally(new InventoryDataSourceException(
                InventoryDataSourceException.Reason.PLAYER_OFFLINE, "offline"
            ));
            return failed;
        };
        AtomicReference<InventoryRenderMetadata.Freshness> freshness = new AtomicReference<>();
        InventoryRenderer enderRenderer = new InventoryRenderer() {
            @Override public RenderResult render(cn.huohuas001.huhobot.inventory.model.InventorySnapshot snapshot) {
                return new RenderResult(new byte[] {7, 7}, "image/png", 704, 308);
            }
            @Override public RenderResult render(
                cn.huohuas001.huhobot.inventory.model.InventorySnapshot snapshot,
                java.awt.image.BufferedImage preview,
                InventoryRenderMetadata metadata
            ) {
                freshness.set(metadata.getFreshness());
                return render(snapshot);
            }
        };
        InventoryAddonSession session = InventoryAddonSession.start(
            service, descriptor(), config(), new MockInventoryDataSource("mock"),
            new MockInventoryDataSource("online"), fakeRenderer(), null, null,
            offlineEnder, enderRenderer, enderStore
        );
        try {
            service.context.handler("enderchest")
                .handle(commandContext(service.context.gateway, "enderchest", "", PrincipalRole.MEMBER))
                .toCompletableFuture().get();
            assertEquals(InventoryRenderMetadata.Freshness.OFFLINE_SNAPSHOT, freshness.get());
            assertEquals("ender-chest.png", service.context.gateway.lastFileName);
            assertArrayEquals(new byte[] {7, 7}, service.context.gateway.lastImage);
        } finally {
            session.close();
            enderStore.close();
        }
    }

    @Test
    void verifiedOfflineBindingWithoutSnapshotGetsFriendlyMissingText(@TempDir Path temp) throws Exception {
        FakeService service = new FakeService(ApiVersion.CURRENT, allCapabilities());
        java.util.UUID uuid = java.util.UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        service.context.bind("group-open-id", "openid", "Steve", uuid, BindingVerificationState.VERIFIED);
        OfflineInventorySnapshotStore store = new OfflineInventorySnapshotStore(temp, Logger.getAnonymousLogger());
        InventoryDataSource offline = player -> {
            CompletableFuture<cn.huohuas001.huhobot.inventory.model.InventorySnapshot> failed = new CompletableFuture<>();
            failed.completeExceptionally(new InventoryDataSourceException(
                InventoryDataSourceException.Reason.PLAYER_OFFLINE, "offline"
            ));
            return failed;
        };
        InventoryAddonSession session = InventoryAddonSession.start(
            service, descriptor(), config(), new MockInventoryDataSource("mock"), offline, fakeRenderer(), null, store
        );
        try {
            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "inventory", "", PrincipalRole.MEMBER))
                .toCompletableFuture().get();
            assertEquals(
                "暂时没有该玩家的离线背包快照，请等待玩家至少登录服务器一次。",
                service.context.gateway.lastText
            );
        } finally {
            session.close();
            store.close();
        }
    }

    @Test
    void legacyOfflineBindingIsDeniedBeforeSnapshotRead(@TempDir Path temp) throws Exception {
        FakeService service = new FakeService(ApiVersion.CURRENT, allCapabilities());
        cn.huohuas001.huhobot.inventory.model.InventorySnapshot stored =
            new MockInventoryDataSource("snapshot-test").createSnapshot("Steve");
        service.context.bind(
            "group-open-id", "openid", "Steve", stored.getPlayerUuid(), BindingVerificationState.LEGACY_UNVERIFIED
        );
        OfflineInventorySnapshotStore store = new OfflineInventorySnapshotStore(temp, Logger.getAnonymousLogger());
        store.saveAsync(stored).get();
        InventoryDataSource offline = player -> {
            CompletableFuture<cn.huohuas001.huhobot.inventory.model.InventorySnapshot> failed = new CompletableFuture<>();
            failed.completeExceptionally(new InventoryDataSourceException(
                InventoryDataSourceException.Reason.PLAYER_OFFLINE, "offline"
            ));
            return failed;
        };
        InventoryAddonSession session = InventoryAddonSession.start(
            service, descriptor(), config(), new MockInventoryDataSource("mock"), offline, fakeRenderer(), null, store
        );
        try {
            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "inventory", "", PrincipalRole.MEMBER))
                .toCompletableFuture().get();
            assertEquals(
                "当前旧版绑定未完成游戏内验证，不能读取持久化离线背包快照。",
                service.context.gateway.lastText
            );
        } finally {
            session.close();
            store.close();
        }
    }

    @Test
    void allNonVerifiedStatesAndDisallowedLegacyNeverReadInventory() throws Exception {
        FakeService service = new FakeService(ApiVersion.CURRENT, allCapabilities());
        AtomicInteger sourceCalls = new AtomicInteger();
        InventoryDataSource source = player -> {
            sourceCalls.incrementAndGet();
            return CompletableFuture.completedFuture(new MockInventoryDataSource("online-test").createSnapshot(player));
        };
        service.context.bind("group-open-id", "openid", "Steve", BindingVerificationState.PENDING);
        InventoryAddonSession session = InventoryAddonSession.start(
            service,
            descriptor(),
            config(0, false),
            new MockInventoryDataSource("mock-test"),
            source,
            fakeRenderer()
        );
        try {
            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "inventory", "", PrincipalRole.MEMBER))
                .toCompletableFuture().get();
            assertEquals("当前绑定尚未完成 Minecraft 账号验证，暂时不能查询背包。", service.context.gateway.lastText);
            assertEquals(0, sourceCalls.get());

            service.context.bind(
                "group-open-id", "openid", "Steve", BindingVerificationState.IDENTITY_CHANGED
            );
            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "inventory", "", PrincipalRole.MEMBER))
                .toCompletableFuture().get();
            assertEquals("当前绑定尚未完成 Minecraft 账号验证，暂时不能查询背包。", service.context.gateway.lastText);
            assertEquals(0, sourceCalls.get());

            service.context.bind(
                "group-open-id", "openid", "Steve", BindingVerificationState.REVOKED
            );
            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "inventory", "", PrincipalRole.MEMBER))
                .toCompletableFuture().get();
            assertEquals("当前绑定尚未完成 Minecraft 账号验证，暂时不能查询背包。", service.context.gateway.lastText);
            assertEquals(0, sourceCalls.get());

            service.context.bind(
                "group-open-id", "openid", "Steve", BindingVerificationState.LEGACY_UNVERIFIED
            );
            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "inventory", "", PrincipalRole.MEMBER))
                .toCompletableFuture().get();
            assertEquals("当前绑定尚未完成 Minecraft 账号验证，暂时不能查询背包。", service.context.gateway.lastText);
            assertEquals(0, sourceCalls.get());
        } finally {
            session.close();
        }
    }

    @Test
    void onlineCommandAppliesRequesterAndTargetCooldown() throws Exception {
        FakeService service = new FakeService(ApiVersion.CURRENT, allCapabilities());
        AtomicInteger sourceCalls = new AtomicInteger();
        InventoryDataSource onlineSource = player -> {
            sourceCalls.incrementAndGet();
            return CompletableFuture.completedFuture(
                new MockInventoryDataSource("online-test").createSnapshot(player)
            );
        };
        InventoryAddonSession session = InventoryAddonSession.start(
            service,
            descriptor(),
            config(30),
            new MockInventoryDataSource("mock-test"),
            onlineSource,
            fakeRenderer()
        );
        try {
            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "inventory", "Steve", PrincipalRole.ADMIN))
                .toCompletableFuture().get();
            service.context.handler("inventory")
                .handle(commandContext(service.context.gateway, "inventory", "Alex", PrincipalRole.ADMIN))
                .toCompletableFuture().get();

            assertEquals(1, sourceCalls.get());
            assertEquals("查询过于频繁，请稍后再试。", service.context.gateway.lastText);
        } finally {
            session.close();
        }
    }

    @Test
    void incompatibleApiAndMissingImageCapabilityFailBeforeOpeningContext() {
        FakeService future = new FakeService(new ApiVersion(2, 0, 0), allCapabilities());
        assertThrows(IllegalStateException.class, () -> InventoryAddonSession.start(
            future,
            descriptor(),
            config(),
            new MockInventoryDataSource("test"),
            new MockInventoryDataSource("online-test"),
            fakeRenderer()
        ));
        assertEquals(0, future.openCalls.get());

        FakeService incomplete = new FakeService(
            ApiVersion.CURRENT,
            EnumSet.of(Capability.COMMANDS, Capability.TEXT_MESSAGES, Capability.SCHEDULER)
        );
        assertThrows(IllegalStateException.class, () -> InventoryAddonSession.start(
            incomplete,
            descriptor(),
            config(),
            new MockInventoryDataSource("test"),
            new MockInventoryDataSource("online-test"),
            fakeRenderer()
        ));
        assertEquals(0, incomplete.openCalls.get());

        FakeService noBindings = new FakeService(
            ApiVersion.CURRENT,
            EnumSet.of(
                Capability.COMMANDS,
                Capability.TEXT_MESSAGES,
                Capability.BYTE_ARRAY_IMAGES,
                Capability.SCHEDULER
            )
        );
        assertThrows(IllegalStateException.class, () -> InventoryAddonSession.start(
            noBindings,
            descriptor(),
            config(),
            new MockInventoryDataSource("test"),
            new MockInventoryDataSource("online-test"),
            fakeRenderer()
        ));
        assertEquals(0, noBindings.openCalls.get());
    }

    @Test
    void addonClassesDoNotReferenceCoreOrQqSdkTypes() throws Exception {
        assertBoundary(MinecraftInventoryPlugin.class);
        assertBoundary(InventoryAddonSession.class);
        assertBoundary(cn.huohuas001.huhobot.inventory.command.InventoryCommand.class);

        String pluginYaml = new String(readResource("plugin.yml"), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(pluginYaml.contains("depend: [HuHoBotPenguin]"));
        assertTrue(pluginYaml.contains("softdepend: [SkinsRestorer]"));
        assertFalse(pluginYaml.contains("HuHoBotGameAuthCode"));
        assertTrue(pluginYaml.contains("main: cn.huohuas001.huhobot.inventory.MinecraftInventoryPlugin"));
    }

    private static InventoryRenderer fakeRenderer() {
        return snapshot -> new RenderResult(new byte[] {1}, "image/png", 1, 1);
    }

    private static InventoryPluginConfig config() {
        return config(0);
    }

    private static InventoryPluginConfig config(int cooldownSeconds) {
        return config(cooldownSeconds, true);
    }

    private static InventoryPluginConfig config(int cooldownSeconds, boolean allowLegacy) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("config-version", InventoryPluginConfig.CURRENT_VERSION);
        yaml.set("command.name", "inventorytest");
        yaml.set("command.aliases", Collections.singletonList("invtest"));
        yaml.set("command.publish-to-menu", false);
        yaml.set("online-command.name", "inventory");
        yaml.set("online-command.aliases", java.util.Arrays.asList("inv", "背包"));
        yaml.set("online-command.publish-to-menu", false);
        yaml.set("ender-chest-command.name", "enderchest");
        yaml.set("ender-chest-command.aliases", java.util.Arrays.asList("ec", "末影箱"));
        yaml.set("ender-chest-command.publish-to-menu", false);
        yaml.set("mock.player-name", "MockPlayer");
        yaml.set("mock.source-server", "test");
        yaml.set("online.source-server", "online-test");
        yaml.set("online.cooldown-seconds", cooldownSeconds);
        yaml.set("online.image-file-name", "inventory.png");
        yaml.set("online.optional-caption", "Inventory: %player%");
        yaml.set("ender-chest.enabled", true);
        yaml.set("ender-chest.source-server", "online-test");
        yaml.set("ender-chest.cooldown-seconds", cooldownSeconds);
        yaml.set("ender-chest.image-file-name", "ender-chest.png");
        yaml.set("ender-chest.optional-caption", "Ender Chest: %player%");
        yaml.set("binding.allow-legacy-unverified", allowLegacy);
        yaml.set("offline-inventory.enabled", true);
        yaml.set("offline-inventory.allow-legacy-unverified", false);
        yaml.set("offline-inventory.directory", "data/offline-snapshots");
        yaml.set("offline-inventory.periodic-save-seconds", 300);
        yaml.set("offline-ender-chest.enabled", true);
        yaml.set("offline-ender-chest.allow-legacy-unverified", false);
        yaml.set("offline-ender-chest.directory", "data/offline-ender-chest-snapshots");
        yaml.set("offline-ender-chest.periodic-save-seconds", 300);
        yaml.set("player-preview.enabled", true);
        yaml.set("player-preview.provider", "default");
        yaml.set("player-preview.mode", "3d");
        yaml.set("player-preview.cache-directory", "cache/skins");
        yaml.set("player-preview.allow-texture-downloads", false);
        yaml.set("player-preview.connect-timeout-ms", 4000);
        yaml.set("player-preview.read-timeout-ms", 8000);
        yaml.set("render.theme", "default");
        yaml.set("render.max-output-bytes", 4 * 1024 * 1024);
        yaml.set("render.image-file-name", "mock-inventory.png");
        yaml.set("render.optional-caption", "Mock inventory: %player%");
        yaml.set("messages.failure", "背包图片生成或发送失败，请稍后再试");
        yaml.set("messages.usage", "用法：/背包，管理员可使用 /背包 <在线玩家名>");
        yaml.set("messages.player-offline", "玩家当前不在线，暂时无法查询背包。");
        yaml.set("messages.player-state-changed", "玩家状态发生变化，请重新查询。");
        yaml.set("messages.not-authorized", "权限不足，无法查询在线玩家背包。");
        yaml.set("messages.cooldown", "查询过于频繁，请稍后再试。");
        yaml.set("messages.binding-required", "你还没有绑定 Minecraft 账号，请先使用 /绑定 <游戏ID>。");
        yaml.set("messages.binding-verification-required", "当前绑定尚未完成 Minecraft 账号验证，暂时不能查询背包。");
        yaml.set("messages.offline-snapshot-missing", "暂时没有该玩家的离线背包快照，请等待玩家至少登录服务器一次。");
        yaml.set("messages.offline-legacy-denied", "当前旧版绑定未完成游戏内验证，不能读取持久化离线背包快照。");
        yaml.set("messages.ender-chest-failure", "末影箱图片生成或发送失败，请稍后再试。");
        yaml.set("messages.ender-chest-usage", "用法：/末影箱，管理员可使用 /末影箱 <在线玩家名>");
        yaml.set("messages.ender-chest-player-offline", "玩家当前不在线，且暂时没有可用的末影箱快照。");
        yaml.set("messages.ender-chest-player-state-changed", "玩家状态发生变化，请重新查询末影箱。");
        yaml.set("messages.ender-chest-not-authorized", "权限不足，无法查询其他玩家的末影箱。");
        yaml.set("messages.ender-chest-offline-snapshot-missing", "暂时没有该玩家的离线末影箱快照，请等待玩家至少登录服务器一次。");
        yaml.set("messages.ender-chest-offline-legacy-denied", "当前旧版绑定未完成游戏内验证，不能读取持久化离线末影箱快照。");
        return InventoryPluginConfig.load(yaml);
    }

    private static PluginDescriptor descriptor() {
        return new PluginDescriptor("minecraft-inventory", "HuHoBotInventory", "1.20.3", ApiVersion.V1_3_0);
    }

    private static Set<Capability> allCapabilities() {
        return EnumSet.allOf(Capability.class);
    }

    private static CommandContext commandContext(FakeGateway gateway) {
        return commandContext(gateway, "inventorytest", "", PrincipalRole.MEMBER);
    }

    private static CommandContext commandContext(
        FakeGateway gateway,
        String command,
        String arguments,
        PrincipalRole role
    ) {
        BotMessage message = new BotMessage(
            "message-id",
            "group-open-id",
            "group-id",
            new SenderSnapshot("sender", "openid", "Tester", "MEMBER"),
            "/" + command + (arguments.isEmpty() ? "" : " " + arguments),
            "/" + command + (arguments.isEmpty() ? "" : " " + arguments),
            null,
            1,
            Collections.<MentionSnapshot>emptyList(),
            Collections.<AttachmentSnapshot>emptyList()
        );
        return new CommandContext(
            message,
            new CommandInvocation(command, arguments),
            new Principal("sender", "openid", "Tester", role),
            gateway,
            new ImmediateScheduler()
        );
    }

    private static void assertBoundary(Class<?> type) throws Exception {
        String resource = type.getName().replace('.', '/') + ".class";
        String constants = new String(readResource(resource), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(constants.contains("cn/huohuas001/bot/"));
        assertFalse(constants.contains("cn/huohuas001/huhobotPenguin/"));
        assertFalse(constants.contains("io/github/kloping/"));
    }

    private static byte[] readResource(String resource) throws Exception {
        InputStream input = MinecraftInventoryPlugin.class.getClassLoader().getResourceAsStream(resource);
        assertNotNull(input, "Missing test resource " + resource);
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) >= 0) output.write(buffer, 0, read);
            return output.toByteArray();
        }
    }

    private static final class FakeService implements HuHoBotService {
        private final ApiVersion version;
        private final Set<Capability> capabilities;
        private final FakeContext context = new FakeContext();
        private final AtomicInteger openCalls = new AtomicInteger();

        private FakeService(ApiVersion version, Set<Capability> capabilities) {
            this.version = version;
            this.capabilities = capabilities;
        }

        @Override public ApiVersion getApiVersion() { return version; }
        @Override public Set<Capability> getCapabilities() { return capabilities; }
        @Override public PluginContext openPlugin(PluginDescriptor descriptor) {
            openCalls.incrementAndGet();
            return context;
        }
    }

    private static final class FakeContext implements PluginContext {
        private final FakeGateway gateway = new FakeGateway();
        private final CapturingLogger logger = new CapturingLogger();
        private final AtomicInteger closeCount = new AtomicInteger();
        private final AtomicInteger closedRegistrations = new AtomicInteger();
        private final Map<String, CommandHandler> handlers = new LinkedHashMap<String, CommandHandler>();
        private final Map<String, List<PlayerBinding>> bindings =
            new LinkedHashMap<String, List<PlayerBinding>>();
        private final BindingService bindingService = new BindingService() {
            @Override public Optional<PlayerBinding> findBinding(String groupId, String userId) {
                List<PlayerBinding> values = bindings.get(groupId + "\n" + userId);
                return values == null || values.isEmpty()
                    ? Optional.<PlayerBinding>empty()
                    : Optional.of(values.get(0));
            }

            @Override public List<PlayerBinding> findBindings(String groupId, String userId) {
                List<PlayerBinding> values = bindings.get(groupId + "\n" + userId);
                return values == null
                    ? Collections.<PlayerBinding>emptyList()
                    : Collections.unmodifiableList(new ArrayList<PlayerBinding>(values));
            }
        };
        private final CommandRegistry commands = new CommandRegistry() {
            @Override
            public Registration register(CommandSpec spec, CommandHandler value) {
                if ("inventorytest".equals(spec.getId())) {
                    assertEquals(Collections.singletonList("invtest"), spec.getAliases());
                    assertEquals(cn.huohuas001.huhobot.api.CommandPermission.ANY, spec.getPermission());
                } else if ("inventory".equals(spec.getId())) {
                    assertEquals("inventory", spec.getId());
                    assertEquals(java.util.Arrays.asList("inv", "背包"), spec.getAliases());
                    assertEquals(cn.huohuas001.huhobot.api.CommandPermission.ANY, spec.getPermission());
                } else {
                    assertEquals("enderchest", spec.getId());
                    assertEquals(java.util.Arrays.asList("ec", "末影箱"), spec.getAliases());
                    assertEquals(cn.huohuas001.huhobot.api.CommandPermission.ANY, spec.getPermission());
                }
                handlers.put(spec.getId(), value);
                return new FakeRegistration(closedRegistrations);
            }
        };
        private CommandHandler handler(String command) { return handlers.get(command); }

        private void bind(
            String groupId,
            String userId,
            String playerName,
            BindingVerificationState state
        ) {
            bind(groupId, userId, playerName, null, state);
        }

        private void bind(
            String groupId,
            String userId,
            String playerName,
            java.util.UUID uuid,
            BindingVerificationState state
        ) {
            bindings.put(
                groupId + "\n" + userId,
                Collections.singletonList(new PlayerBinding(playerName, uuid, state))
            );
        }

        private void bindTwo(String groupId, String userId, String first, String second) {
            List<PlayerBinding> values = new ArrayList<PlayerBinding>();
            values.add(new PlayerBinding(
                "binding-1", 1, true, first.toLowerCase(java.util.Locale.ROOT), first,
                java.util.UUID.fromString("11111111-2222-3333-4444-555555555555"),
                BindingVerificationState.VERIFIED, null, null, null
            ));
            values.add(new PlayerBinding(
                "binding-2", 2, false, second.toLowerCase(java.util.Locale.ROOT), second,
                java.util.UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                BindingVerificationState.VERIFIED, null, null, null
            ));
            bindings.put(groupId + "\n" + userId, values);
        }

        @Override public PluginDescriptor getDescriptor() { return descriptor(); }
        @Override public Set<Capability> getCapabilities() { return allCapabilities(); }
        @Override public CommandRegistry getCommands() { return commands; }
        @Override public MessageGateway getMessages() { return gateway; }
        @Override public TaskScheduler getScheduler() { return new ImmediateScheduler(); }
        @Override public BindingService getBindings() { return bindingService; }
        @Override public PluginLogger getLogger() { return logger; }
        @Override public boolean isClosed() { return closeCount.get() > 0; }
        @Override public void close() { if (closeCount.getAndIncrement() > 0) closeCount.decrementAndGet(); }
    }

    private static final class FakeRegistration implements Registration {
        private final AtomicInteger closeCount = new AtomicInteger();
        private final AtomicInteger totalCloses;
        private FakeRegistration(AtomicInteger totalCloses) { this.totalCloses = totalCloses; }
        @Override public boolean isClosed() { return closeCount.get() > 0; }
        @Override public void close() {
            if (closeCount.compareAndSet(0, 1)) totalCloses.incrementAndGet();
        }
    }

    private static final class FakeButtonBridge implements InventoryButtonBridge {
        private final Map<String, InventoryButtonHandler> handlers =
            new LinkedHashMap<String, InventoryButtonHandler>();
        private String lastMarkdown;
        private List<InventoryButton> lastButtons;
        private boolean closed;

        @Override public boolean isAvailable() { return !closed; }
        @Override public CompletionStage<SendResult> replySelection(
            MessageReference reference, String markdown, List<InventoryButton> buttons
        ) {
            lastMarkdown = markdown;
            lastButtons = buttons;
            return CompletableFuture.completedFuture(SendResult.success());
        }
        @Override public Registration register(String prefix, InventoryButtonHandler handler) {
            handlers.put(prefix, handler);
            return new Registration() {
                private boolean registrationClosed;
                @Override public boolean isClosed() { return registrationClosed; }
                @Override public void close() {
                    if (!registrationClosed) {
                        registrationClosed = true;
                        handlers.remove(prefix, handler);
                    }
                }
            };
        }
        private InventoryButtonResult handle(String data, InventoryButtonInteraction interaction) {
            for (Map.Entry<String, InventoryButtonHandler> entry : handlers.entrySet()) {
                if (data.startsWith(entry.getKey())) return entry.getValue().handle(interaction);
            }
            return InventoryButtonResult.NOT_HANDLED;
        }
        @Override public void close() { closed = true; handlers.clear(); }
    }

    private static final class FakeGateway implements MessageGateway {
        private final AtomicInteger textReplies = new AtomicInteger();
        private SendResult imageResult = SendResult.success();
        private byte[] lastImage;
        private String lastMimeType;
        private String lastFileName;
        private String lastCaption;
        private String lastText;

        @Override public CompletionStage<SendResult> replyText(MessageReference reference, String text) {
            textReplies.incrementAndGet();
            lastText = text;
            return CompletableFuture.completedFuture(SendResult.success());
        }
        @Override public CompletionStage<SendResult> replyImage(
            MessageReference reference,
            byte[] bytes,
            String mimeType,
            String fileName,
            String optionalText
        ) {
            lastImage = bytes;
            lastMimeType = mimeType;
            lastFileName = fileName;
            lastCaption = optionalText;
            return CompletableFuture.completedFuture(imageResult);
        }
        @Override public CompletionStage<SendResult> sendText(String groupOpenId, String text) {
            lastText = text;
            return CompletableFuture.completedFuture(SendResult.success());
        }
        @Override public CompletionStage<SendResult> sendImage(
            String groupOpenId,
            byte[] bytes,
            String mimeType,
            String fileName,
            String optionalText
        ) {
            lastImage = bytes;
            lastMimeType = mimeType;
            lastFileName = fileName;
            lastCaption = optionalText;
            return CompletableFuture.completedFuture(SendResult.success());
        }
    }

    private static final class ImmediateScheduler implements TaskScheduler {
        @Override public TaskHandle runSync(Runnable task) { task.run(); return new FakeTaskHandle(); }
        @Override public TaskHandle runAsync(Runnable task) { task.run(); return new FakeTaskHandle(); }
        @Override public TaskHandle runLater(Duration delay, Runnable task) { task.run(); return new FakeTaskHandle(); }
        @Override public TaskHandle runTimer(Duration initialDelay, Duration period, Runnable task) {
            task.run();
            return new FakeTaskHandle();
        }
    }

    private static final class FakeTaskHandle implements TaskHandle {
        private final AtomicBoolean closed = new AtomicBoolean();
        @Override public boolean isClosed() { return closed.get(); }
        @Override public boolean cancel() { return closed.compareAndSet(false, true); }
        @Override public void close() { cancel(); }
    }

    private static final class CapturingLogger implements PluginLogger {
        private final AtomicInteger errors = new AtomicInteger();
        @Override public void info(String message) { }
        @Override public void warning(String message) { }
        @Override public void error(String message, Throwable error) { errors.incrementAndGet(); }
    }
}
