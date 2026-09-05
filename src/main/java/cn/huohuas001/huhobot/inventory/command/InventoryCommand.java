package cn.huohuas001.huhobot.inventory.command;

import cn.huohuas001.huhobot.api.BindingService;
import cn.huohuas001.huhobot.api.BindingVerificationState;
import cn.huohuas001.huhobot.api.BotMessage;
import cn.huohuas001.huhobot.api.CommandContext;
import cn.huohuas001.huhobot.api.CommandHandler;
import cn.huohuas001.huhobot.api.CommandInvocation;
import cn.huohuas001.huhobot.api.CommandResult;
import cn.huohuas001.huhobot.api.MessageGateway;
import cn.huohuas001.huhobot.api.MessageReference;
import cn.huohuas001.huhobot.api.PluginLogger;
import cn.huohuas001.huhobot.api.PlayerBinding;
import cn.huohuas001.huhobot.api.Principal;
import cn.huohuas001.huhobot.api.PrincipalRole;
import cn.huohuas001.huhobot.api.SendResult;
import cn.huohuas001.huhobot.api.SenderSnapshot;
import cn.huohuas001.huhobot.api.TaskScheduler;
import cn.huohuas001.huhobot.inventory.config.InventoryPluginConfig;
import cn.huohuas001.huhobot.inventory.datasource.InventoryDataSource;
import cn.huohuas001.huhobot.inventory.datasource.InventoryDataSourceException;
import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;
import cn.huohuas001.huhobot.inventory.qq.InventoryButton;
import cn.huohuas001.huhobot.inventory.qq.InventoryButtonBridge;
import cn.huohuas001.huhobot.inventory.qq.InventoryButtonInteraction;
import cn.huohuas001.huhobot.inventory.qq.InventoryButtonResult;
import cn.huohuas001.huhobot.inventory.renderer.InventoryRenderer;
import cn.huohuas001.huhobot.inventory.renderer.InventoryRenderMetadata;
import cn.huohuas001.huhobot.inventory.renderer.RenderResult;
import cn.huohuas001.huhobot.inventory.skin.PlayerPreviewService;
import cn.huohuas001.huhobot.inventory.snapshot.OfflineInventorySnapshotStore;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

/** Async command pipeline: neutral source -> renderer -> HuHoBot byte-array image reply. */
public final class InventoryCommand implements CommandHandler {
    private enum Mode { MOCK, ONLINE, ENDER_CHEST }

    private final InventoryDataSource dataSource;
    private final InventoryRenderer renderer;
    private final InventoryPluginConfig config;
    private final PluginLogger logger;
    private final BindingService bindings;
    private final Mode mode;
    private final PlayerPreviewService previewService;
    private final OfflineInventorySnapshotStore offlineStore;
    private final InventoryButtonBridge buttonBridge;
    private final Map<String, Long> requesterCooldowns = new HashMap<String, Long>();
    private final Map<String, Long> targetCooldowns = new HashMap<String, Long>();
    private final Map<String, PendingSelection> pendingSelections = new HashMap<String, PendingSelection>();
    private final Map<String, ConsumedButtonSelection> consumedButtonSelections =
        new HashMap<String, ConsumedButtonSelection>();
    private static final long SELECTION_TTL_MILLIS = 60_000L;
    private static final String ONLINE_BUTTON_PREFIX = "hbi:i:";
    private static final String ENDER_CHEST_BUTTON_PREFIX = "hbi:e:";
    private static final int BUTTON_STYLE_BLUE_OUTLINE = 1;

    /** Backward-compatible constructor for the existing Mock command. */
    public InventoryCommand(
        InventoryDataSource dataSource,
        InventoryRenderer renderer,
        InventoryPluginConfig config,
        PluginLogger logger
    ) {
        this(
            dataSource, renderer, config, BindingService.UNAVAILABLE, logger,
            Mode.MOCK, null, null, InventoryButtonBridge.UNAVAILABLE
        );
    }

    public static InventoryCommand online(
        InventoryDataSource dataSource,
        InventoryRenderer renderer,
        InventoryPluginConfig config,
        BindingService bindings,
        PluginLogger logger
    ) {
        return new InventoryCommand(
            dataSource, renderer, config, bindings, logger,
            Mode.ONLINE, null, null, InventoryButtonBridge.UNAVAILABLE
        );
    }

    public static InventoryCommand enderChest(
        InventoryDataSource dataSource,
        InventoryRenderer renderer,
        InventoryPluginConfig config,
        BindingService bindings,
        PluginLogger logger,
        OfflineInventorySnapshotStore offlineStore
    ) {
        return new InventoryCommand(
            dataSource, renderer, config, bindings, logger, Mode.ENDER_CHEST, null, offlineStore,
            InventoryButtonBridge.UNAVAILABLE
        );
    }

    public static InventoryCommand online(
        InventoryDataSource dataSource,
        InventoryRenderer renderer,
        InventoryPluginConfig config,
        BindingService bindings,
        PluginLogger logger,
        PlayerPreviewService previewService
    ) {
        return new InventoryCommand(
            dataSource, renderer, config, bindings, logger,
            Mode.ONLINE, previewService, null, InventoryButtonBridge.UNAVAILABLE
        );
    }

    public static InventoryCommand online(
        InventoryDataSource dataSource,
        InventoryRenderer renderer,
        InventoryPluginConfig config,
        BindingService bindings,
        PluginLogger logger,
        PlayerPreviewService previewService,
        OfflineInventorySnapshotStore offlineStore
    ) {
        return new InventoryCommand(
            dataSource, renderer, config, bindings, logger, Mode.ONLINE, previewService, offlineStore,
            InventoryButtonBridge.UNAVAILABLE
        );
    }

    public static InventoryCommand online(
        InventoryDataSource dataSource,
        InventoryRenderer renderer,
        InventoryPluginConfig config,
        BindingService bindings,
        PluginLogger logger,
        PlayerPreviewService previewService,
        OfflineInventorySnapshotStore offlineStore,
        InventoryButtonBridge buttonBridge
    ) {
        return new InventoryCommand(
            dataSource, renderer, config, bindings, logger, Mode.ONLINE, previewService, offlineStore, buttonBridge
        );
    }

    public static InventoryCommand enderChest(
        InventoryDataSource dataSource,
        InventoryRenderer renderer,
        InventoryPluginConfig config,
        BindingService bindings,
        PluginLogger logger,
        OfflineInventorySnapshotStore offlineStore,
        InventoryButtonBridge buttonBridge
    ) {
        return new InventoryCommand(
            dataSource, renderer, config, bindings, logger, Mode.ENDER_CHEST, null, offlineStore, buttonBridge
        );
    }

    private InventoryCommand(
        InventoryDataSource dataSource,
        InventoryRenderer renderer,
        InventoryPluginConfig config,
        BindingService bindings,
        PluginLogger logger,
        Mode mode,
        PlayerPreviewService previewService,
        OfflineInventorySnapshotStore offlineStore,
        InventoryButtonBridge buttonBridge
    ) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.config = Objects.requireNonNull(config, "config");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.previewService = previewService;
        this.offlineStore = offlineStore;
        this.buttonBridge = Objects.requireNonNull(buttonBridge, "buttonBridge");
    }

    @Override
    public CompletionStage<CommandResult> handle(CommandContext context) {
        Objects.requireNonNull(context, "context");
        TargetResolution resolution = resolveTarget(context);
        if (!resolution.isResolved()) {
            if (resolution.buttons == null) return replyKnownText(context, resolution.message);
            return !buttonBridge.isAvailable()
                ? replyKnownText(context, resolution.fallbackMessage)
                : replySelection(
                    context, resolution.message, resolution.fallbackMessage, resolution.buttons
                );
        }
        String target = resolution.target;
        if (mode != Mode.MOCK && !acquireCooldown(context, target)) {
            return replyKnownText(context, config.getCooldownMessage());
        }

        CompletableFuture<CommandResult> result = new CompletableFuture<CommandResult>();
        try {
            context.getScheduler().runAsync(() -> startPipeline(context, resolution, result));
        } catch (Throwable error) {
            failWithText(context, result, "Could not schedule inventory query", error);
        }
        return result;
    }

    /** Prefix registered with Inventory's Addon-local QQ interaction listener. */
    public String getButtonDataPrefix() {
        if (mode == Mode.ONLINE) return ONLINE_BUTTON_PREFIX;
        if (mode == Mode.ENDER_CHEST) return ENDER_CHEST_BUTTON_PREFIX;
        throw new IllegalStateException("Mock inventory command has no button interactions");
    }

    /** Validates and consumes a one-time selection before scheduling the existing query pipeline. */
    public InventoryButtonResult handleButton(
        InventoryButtonInteraction interaction,
        MessageGateway messages,
        TaskScheduler scheduler
    ) {
        Objects.requireNonNull(interaction, "interaction");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(scheduler, "scheduler");
        if (mode == Mode.MOCK) return InventoryButtonResult.NOT_HANDLED;
        String prefix = getButtonDataPrefix();
        if (!interaction.getData().startsWith(prefix)) return InventoryButtonResult.NOT_HANDLED;
        String payload = interaction.getData().substring(prefix.length());
        int separator = payload.lastIndexOf(':');
        if (separator <= 0 || separator == payload.length() - 1) return InventoryButtonResult.FAILED;
        String nonce = payload.substring(0, separator);
        Integer selection = positiveInteger(payload.substring(separator + 1));
        if (selection == null) return InventoryButtonResult.FAILED;

        ButtonSelectionResult selected = consumeButtonSelection(
            interaction.getGroupOpenId(), interaction.getUserOpenId(), nonce, selection.intValue()
        );
        if (selected.status == ButtonSelectionStatus.FORBIDDEN) return InventoryButtonResult.FORBIDDEN;
        if (selected.status == ButtonSelectionStatus.DUPLICATE) return InventoryButtonResult.DUPLICATE;
        if (selected.status == ButtonSelectionStatus.EXPIRED) {
            InventoryButtonResult expired = mode == Mode.ENDER_CHEST
                ? InventoryButtonResult.EXPIRED_ENDER_CHEST
                : InventoryButtonResult.EXPIRED_INVENTORY;
            sendButtonFeedback(messages, interaction.getGroupOpenId(), expired.getFeedbackMessage());
            return expired;
        }
        if (selected.status == ButtonSelectionStatus.INVALID_OPTION) return InventoryButtonResult.FAILED;

        PlayerBinding current = currentBinding(
            interaction.getGroupOpenId(), interaction.getUserOpenId(), selected.option
        );
        if (current == null) {
            messages.sendText(interaction.getGroupOpenId(), config.getBindingVerificationRequiredMessage());
            return InventoryButtonResult.FAILED;
        }
        if (!acquireCooldown("open:" + interaction.getUserOpenId(), current.getPlayerName())) {
            messages.sendText(interaction.getGroupOpenId(), config.getCooldownMessage());
            return InventoryButtonResult.TOO_FREQUENT;
        }

        CommandContext synthetic = interactionContext(interaction, messages, scheduler);
        TargetResolution resolution = TargetResolution.resolved(current.getPlayerName(), current);
        CompletableFuture<CommandResult> completion = new CompletableFuture<CommandResult>();
        try {
            scheduler.runAsync(() -> startPipeline(synthetic, resolution, completion));
        } catch (Throwable error) {
            logger.error("Could not schedule button inventory query", error);
            return InventoryButtonResult.FAILED;
        }
        return InventoryButtonResult.SUCCESS;
    }

    private void sendButtonFeedback(MessageGateway messages, String groupOpenId, String text) {
        try {
            CompletionStage<SendResult> attempt = messages.sendText(groupOpenId, text);
            if (attempt == null) {
                logger.warning("Inventory 按钮反馈消息未返回发送结果");
                return;
            }
            attempt.whenComplete((result, error) -> {
                if (error != null) {
                    logger.error("Inventory 按钮反馈消息发送失败", error);
                } else if (result == null || !result.isSuccess()) {
                    logger.warning(
                        "Inventory 按钮反馈消息发送失败：" +
                            (result == null ? "missing result" : result.getStatus() +
                                (result.getDiagnostic() == null ? "" : " / " + result.getDiagnostic()))
                    );
                }
            });
        } catch (Throwable error) {
            logger.error("Inventory 按钮反馈消息发送失败", error);
        }
    }

    private TargetResolution resolveTarget(CommandContext context) {
        if (mode == Mode.MOCK) return TargetResolution.resolved(config.getMockPlayerName(), null);
        String arguments = context.getInvocation().getArguments().trim();
        String groupId = firstNonBlank(
            context.getMessage().getGroupOpenId(),
            context.getMessage().getGroupId()
        );
        String userId = firstNonBlank(
            context.getPrincipal().getOpenId(),
            context.getPrincipal().getId()
        );

        if (!arguments.isEmpty()) {
            Integer selection = positiveInteger(arguments);
            if (selection != null && groupId != null && userId != null) {
                SelectionResult selected = consumeSelection(groupId, userId, selection.intValue());
                if (selected.status == SelectionStatus.SELECTED) {
                    PlayerBinding current = currentBinding(groupId, userId, selected.option);
                    return current == null
                        ? TargetResolution.rejected(config.getBindingVerificationRequiredMessage())
                        : TargetResolution.resolved(current.getPlayerName(), current);
                }
                if (!context.getPrincipal().getRole().isAdministrator()) {
                    return TargetResolution.rejected(selectionMessage(
                        selected.status == SelectionStatus.INVALID_OPTION
                            ? "序号无效，请按照刚才的列表选择。"
                            : "账号选择已失效，请先重新发送 /" + queryCommandName(context) + " 获取账号列表。"
                    ));
                }
            }
            if (!context.getPrincipal().getRole().isAdministrator()) {
                return TargetResolution.rejected(notAuthorizedMessage());
            }
            return validPlayerName(arguments)
                ? TargetResolution.resolved(arguments, null)
                : TargetResolution.rejected(usageMessage());
        }

        if (groupId == null || userId == null) {
            return TargetResolution.rejected(config.getBindingRequiredMessage());
        }
        List<PlayerBinding> all = bindings.findBindings(groupId, userId);
        if (all.isEmpty()) {
            return TargetResolution.rejected(config.getBindingRequiredMessage());
        }
        List<PlayerBinding> allowed = new ArrayList<PlayerBinding>();
        for (PlayerBinding value : all) {
            if (isAllowed(value) && validPlayerName(value.getPlayerName())) allowed.add(value);
        }
        if (allowed.isEmpty()) {
            return TargetResolution.rejected(config.getBindingVerificationRequiredMessage());
        }
        if (allowed.size() > 1) {
            PendingSelection pending = rememberSelection(groupId, userId, allowed);
            return TargetResolution.rejected(
                "请选择账号（60 秒内有效）：",
                selectionFallbackPrompt(allowed, queryCommandName(context)),
                selectionButtons(pending, userId)
            );
        }
        PlayerBinding value = allowed.get(0);
        return TargetResolution.resolved(value.getPlayerName(), value);
    }

    private boolean isAllowed(PlayerBinding value) {
        BindingVerificationState state = value.getVerificationState();
        return state == BindingVerificationState.VERIFIED ||
            (state == BindingVerificationState.LEGACY_UNVERIFIED &&
                config.isAllowLegacyUnverifiedBindings());
    }

    private synchronized PendingSelection rememberSelection(
        String groupId,
        String userId,
        List<PlayerBinding> values
    ) {
        long now = System.currentTimeMillis();
        List<SelectionOption> options = new ArrayList<SelectionOption>(values.size());
        for (PlayerBinding value : values) options.add(new SelectionOption(value));
        PendingSelection pending = new PendingSelection(
            now + SELECTION_TTL_MILLIS, UUID.randomUUID().toString().replace("-", ""), options
        );
        pendingSelections.put(selectionKey(groupId, userId), pending);
        if (pendingSelections.size() > 4096) {
            Iterator<Map.Entry<String, PendingSelection>> iterator = pendingSelections.entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getValue().expiresAt <= now) iterator.remove();
            }
        }
        return pending;
    }

    private synchronized SelectionResult consumeSelection(String groupId, String userId, int selection) {
        String key = selectionKey(groupId, userId);
        PendingSelection pending = pendingSelections.get(key);
        if (pending == null) return SelectionResult.expired();
        if (pending.expiresAt <= System.currentTimeMillis()) {
            pendingSelections.remove(key);
            return SelectionResult.expired();
        }
        if (selection < 1 || selection > pending.options.size()) return SelectionResult.invalid();
        pendingSelections.remove(key);
        return SelectionResult.selected(pending.options.get(selection - 1));
    }

    private synchronized ButtonSelectionResult consumeButtonSelection(
        String groupId,
        String userId,
        String nonce,
        int selection
    ) {
        String key = selectionKey(groupId, userId);
        PendingSelection pending = pendingSelections.get(key);
        long now = System.currentTimeMillis();
        if (pending == null || !pending.nonce.equals(nonce)) {
            ConsumedButtonSelection consumed = consumedButtonSelections.get(nonce);
            if (consumed != null) {
                if (consumed.expiresAt <= now) {
                    consumedButtonSelections.remove(nonce);
                    return ButtonSelectionResult.expired();
                }
                return consumed.matches(groupId, userId)
                    ? ButtonSelectionResult.duplicate()
                    : ButtonSelectionResult.forbidden();
            }
            for (PendingSelection candidate : pendingSelections.values()) {
                if (candidate.expiresAt > now && candidate.nonce.equals(nonce)) {
                    return ButtonSelectionResult.forbidden();
                }
            }
            return ButtonSelectionResult.expired();
        }
        if (pending.expiresAt <= now) {
            pendingSelections.remove(key);
            return ButtonSelectionResult.expired();
        }
        if (selection < 1 || selection > pending.options.size()) {
            return ButtonSelectionResult.invalid();
        }
        pendingSelections.remove(key);
        consumedButtonSelections.put(
            nonce,
            new ConsumedButtonSelection(groupId, userId, pending.expiresAt)
        );
        if (consumedButtonSelections.size() > 4096) {
            Iterator<Map.Entry<String, ConsumedButtonSelection>> iterator =
                consumedButtonSelections.entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getValue().expiresAt <= now) iterator.remove();
            }
        }
        return ButtonSelectionResult.selected(pending.options.get(selection - 1));
    }

    private PlayerBinding currentBinding(String groupId, String userId, SelectionOption selected) {
        for (PlayerBinding current : bindings.findBindings(groupId, userId)) {
            if (selected.matches(current) && isAllowed(current) && validPlayerName(current.getPlayerName())) {
                return current;
            }
        }
        return null;
    }

    private String selectionFallbackPrompt(List<PlayerBinding> values, String commandName) {
        StringBuilder message = new StringBuilder("按钮不可用，请在 60 秒内发送：");
        for (int index = 0; index < values.size(); index++) {
            PlayerBinding value = values.get(index);
            message.append("\n/").append(commandName).append(' ').append(index + 1)
                .append("（").append(value.getPlayerName()).append("）");
        }
        return message.toString();
    }

    private List<InventoryButton> selectionButtons(PendingSelection pending, String userId) {
        List<InventoryButton> buttons = new ArrayList<InventoryButton>(pending.options.size());
        for (int index = 0; index < pending.options.size(); index++) {
            SelectionOption option = pending.options.get(index);
            buttons.add(new InventoryButton(
                buttonLabel(index + 1, option.playerName),
                "已选择",
                getButtonDataPrefix() + pending.nonce + ":" + (index + 1),
                userId,
                BUTTON_STYLE_BLUE_OUTLINE
            ));
        }
        return Collections.unmodifiableList(buttons);
    }

    private static String buttonLabel(int index, String playerName) {
        String value = index + " " + playerName;
        int count = value.codePointCount(0, value.length());
        return count <= InventoryButton.MAX_LABEL_CODE_POINTS
            ? value
            : value.substring(0, value.offsetByCodePoints(0, InventoryButton.MAX_LABEL_CODE_POINTS));
    }

    private static String markdownEscape(String value) {
        return value.replace("\\", "\\\\").replace("_", "\\_").replace("*", "\\*");
    }

    private static String selectionMessage(String message) {
        return message;
    }

    private String queryCommandName(CommandContext context) {
        String invoked = context.getInvocation().getCommand();
        if (invoked != null && !invoked.trim().isEmpty()) return invoked.trim();
        return mode == Mode.ENDER_CHEST
            ? config.getEnderChestCommandName()
            : config.getOnlineCommandName();
    }

    private static String selectionKey(String groupId, String userId) {
        return groupId.trim() + "\n" + userId.trim();
    }

    private static Integer positiveInteger(String value) {
        if (!value.matches("[1-9][0-9]*")) return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean validPlayerName(String value) {
        return value != null && value.matches("[A-Za-z0-9_]{1,16}");
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) return first;
        if (second != null && !second.trim().isEmpty()) return second;
        return null;
    }

    private CommandContext interactionContext(
        InventoryButtonInteraction interaction,
        MessageGateway messages,
        TaskScheduler scheduler
    ) {
        MessageGateway proactive = new ProactiveMessageGateway(messages);
        SenderSnapshot sender = new SenderSnapshot(
            interaction.getUserOpenId(), interaction.getUserOpenId(), "button-user", "MEMBER"
        );
        BotMessage message = new BotMessage(
            interaction.getInteractionId(),
            interaction.getGroupOpenId(),
            interaction.getGroupOpenId(),
            sender,
            "",
            "",
            null,
            0,
            Collections.emptyList(),
            Collections.emptyList()
        );
        Principal principal = new Principal(
            interaction.getUserOpenId(),
            interaction.getUserOpenId(),
            "button-user",
            PrincipalRole.MEMBER
        );
        return new CommandContext(
            message,
            new CommandInvocation(mode == Mode.ENDER_CHEST ? "enderchest" : "inventory", ""),
            principal,
            proactive,
            scheduler
        );
    }

    private void startPipeline(
        CommandContext context,
        TargetResolution resolution,
        CompletableFuture<CommandResult> result
    ) {
        CompletionStage<InventorySnapshot> snapshotStage;
        try {
            snapshotStage = dataSource.getInventory(resolution.target);
            if (snapshotStage == null) throw new IllegalStateException("InventoryDataSource returned null");
        } catch (Throwable error) {
            handleSourceFailure(context, resolution, result, error);
            return;
        }

        snapshotStage.whenComplete((snapshot, sourceError) -> {
            if (sourceError != null) {
                handleSourceFailure(context, resolution, result, unwrap(sourceError));
                return;
            }
            if (snapshot == null) {
                failWithText(context, result, "InventoryDataSource returned no snapshot", null);
                return;
            }
            try {
                if (offlineStore != null && snapshot.getPlayerUuid() != null) offlineStore.saveAsync(snapshot);
                context.getScheduler().runAsync(() -> renderAndSend(
                    context, snapshot, InventoryRenderMetadata.realtime(snapshot.getCapturedAt()), result
                ));
            } catch (Throwable error) {
                failWithText(context, result, "Could not schedule inventory render", error);
            }
        });
    }

    private void renderAndSend(
        CommandContext context,
        InventorySnapshot snapshot,
        InventoryRenderMetadata metadata,
        CompletableFuture<CommandResult> result
    ) {
        RenderResult rendered;
        try {
            BufferedImage preview = previewService == null ? null : previewService.preview(snapshot);
            rendered = renderer.render(snapshot, preview, metadata);
            if (rendered.getByteSize() > config.getMaxOutputBytes()) {
                throw new IllegalStateException(
                    "Rendered PNG exceeds configured limit: " + rendered.getByteSize() + " bytes"
                );
            }
        } catch (Throwable error) {
            failWithText(context, result, "Inventory renderer failed", error);
            return;
        }

        CompletionStage<SendResult> sendStage;
        try {
            String fileName = mode == Mode.MOCK
                ? config.getImageFileName()
                : mode == Mode.ENDER_CHEST
                    ? config.getEnderChestImageFileName()
                    : config.getOnlineImageFileName();
            String caption = mode == Mode.MOCK
                ? config.captionForMock(snapshot.getPlayerName())
                : mode == Mode.ENDER_CHEST
                    ? config.captionForEnderChest(snapshot.getPlayerName())
                    : config.captionForOnline(snapshot.getPlayerName());
            sendStage = context.replyImage(
                rendered.getBytes(),
                rendered.getMimeType(),
                fileName,
                caption
            );
            if (sendStage == null) throw new IllegalStateException("MessageGateway returned null");
        } catch (Throwable error) {
            failWithText(context, result, "QQ image send could not start", error);
            return;
        }

        sendStage.whenComplete((sendResult, sendError) -> {
            if (sendError != null) {
                failWithText(context, result, "QQ image send failed", unwrap(sendError));
            } else if (sendResult == null || !sendResult.isSuccess()) {
                String diagnostic = sendResult == null ? "no SendResult" : sendResult.getDiagnostic();
                failWithText(
                    context,
                    result,
                    "QQ image send returned " + (sendResult == null ? "null" : sendResult.getStatus()),
                    new IllegalStateException(diagnostic == null ? "no diagnostic" : diagnostic)
                );
            } else {
                logger.info(
                    "Sent " + (mode == Mode.MOCK ? "mock" :
                        metadata.getFreshness() == InventoryRenderMetadata.Freshness.OFFLINE_SNAPSHOT
                            ? "offline snapshot" : "online") +
                        (mode == Mode.ENDER_CHEST ? " Ender Chest PNG for " : " inventory PNG for ") +
                        snapshot.getPlayerName() + " " +
                        rendered.getWidth() + "x" + rendered.getHeight() +
                        " (" + rendered.getByteSize() + " bytes)"
                );
                result.complete(CommandResult.handled());
            }
        });
    }

    private void handleSourceFailure(
        CommandContext context,
        TargetResolution resolution,
        CompletableFuture<CommandResult> result,
        Throwable error
    ) {
        Throwable cause = unwrap(error);
        if (cause instanceof InventoryDataSourceException) {
            InventoryDataSourceException sourceError = (InventoryDataSourceException) cause;
            if (sourceError.getReason() == InventoryDataSourceException.Reason.PLAYER_OFFLINE) {
                tryOfflineSnapshot(context, resolution, result);
                return;
            }
            if (sourceError.getReason() == InventoryDataSourceException.Reason.PLAYER_STATE_CHANGED) {
                completeKnownText(context, result, playerStateChangedMessage());
                return;
            }
        }
        failWithText(context, result, "InventoryDataSource failed", cause);
    }

    private void tryOfflineSnapshot(
        CommandContext context,
        TargetResolution resolution,
        CompletableFuture<CommandResult> result
    ) {
        if (!offlineEnabled() || offlineStore == null || resolution.binding == null) {
            completeKnownText(context, result, playerOfflineMessage());
            return;
        }
        BindingVerificationState state = resolution.binding.getVerificationState();
        if (state == BindingVerificationState.LEGACY_UNVERIFIED && !allowLegacyOfflineSnapshots()) {
            completeKnownText(context, result, offlineLegacyDeniedMessage());
            return;
        }
        if (state != BindingVerificationState.VERIFIED && state != BindingVerificationState.LEGACY_UNVERIFIED) {
            completeKnownText(context, result, config.getBindingVerificationRequiredMessage());
            return;
        }
        UUID observed = resolution.binding.getObservedUuid().orElse(
            resolution.binding.getPlayerUuid().orElse(null)
        );
        if (observed == null) {
            completeKnownText(context, result, config.getBindingVerificationRequiredMessage());
            return;
        }
        try {
            context.getScheduler().runAsync(() -> {
                Optional<InventorySnapshot> stored = offlineStore.load(observed);
                if (!stored.isPresent()) {
                    completeKnownText(context, result, offlineSnapshotMissingMessage());
                    return;
                }
                InventorySnapshot snapshot = stored.get();
                if (!observed.equals(snapshot.getPlayerUuid())) {
                    completeKnownText(context, result, config.getBindingVerificationRequiredMessage());
                    return;
                }
                renderAndSend(
                    context, snapshot, InventoryRenderMetadata.offline(snapshot.getCapturedAt()), result
                );
            });
        } catch (Throwable error) {
            failWithText(context, result, "Could not schedule offline inventory render", error);
        }
    }

    private synchronized boolean acquireCooldown(CommandContext context, String target) {
        return acquireCooldown(requesterKey(context), target);
    }

    private synchronized boolean acquireCooldown(String requester, String target) {
        int seconds = mode == Mode.ENDER_CHEST
            ? config.getEnderChestCooldownSeconds()
            : config.getOnlineCooldownSeconds();
        if (seconds == 0) return true;
        long now = System.currentTimeMillis();
        String normalizedTarget = target.toLowerCase(java.util.Locale.ROOT);
        Long requesterUntil = requesterCooldowns.get(requester);
        Long targetUntil = targetCooldowns.get(normalizedTarget);
        if ((requesterUntil != null && requesterUntil > now) || (targetUntil != null && targetUntil > now)) {
            return false;
        }
        long until = now + seconds * 1000L;
        requesterCooldowns.put(requester, until);
        targetCooldowns.put(normalizedTarget, until);
        if (requesterCooldowns.size() + targetCooldowns.size() > 4096) {
            removeExpired(requesterCooldowns, now);
            removeExpired(targetCooldowns, now);
        }
        return true;
    }

    private static void removeExpired(Map<String, Long> entries, long now) {
        Iterator<Map.Entry<String, Long>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= now) iterator.remove();
        }
    }

    private static String requesterKey(CommandContext context) {
        if (context.getPrincipal().getOpenId() != null) return "open:" + context.getPrincipal().getOpenId();
        if (context.getPrincipal().getId() != null) return "id:" + context.getPrincipal().getId();
        return "name:" + context.getPrincipal().getUsername();
    }

    private CompletionStage<CommandResult> replyKnownText(CommandContext context, String text) {
        CompletableFuture<CommandResult> result = new CompletableFuture<CommandResult>();
        completeKnownText(context, result, text);
        return result;
    }

    private CompletionStage<CommandResult> replySelection(
        CommandContext context,
        String markdown,
        String fallbackMessage,
        List<InventoryButton> buttons
    ) {
        CompletableFuture<CommandResult> result = new CompletableFuture<CommandResult>();
        CompletionStage<SendResult> reply;
        try {
            reply = buttonBridge.replySelection(context.getMessage().toReference(), markdown, buttons);
            if (reply == null) throw new IllegalStateException("Inventory button bridge returned null");
        } catch (Throwable error) {
            logger.warning("Button selection reply could not start; falling back to text: " + error.getMessage());
            completeKnownText(context, result, fallbackMessage);
            return result;
        }
        reply.whenComplete((sendResult, replyError) -> {
            if (replyError == null && sendResult != null && sendResult.isSuccess()) {
                result.complete(CommandResult.handled());
            } else {
                String diagnostic = replyError != null
                    ? unwrap(replyError).getMessage()
                    : sendResult == null ? "no SendResult" : sendResult.getDiagnostic();
                logger.warning("Button selection reply was not sent; falling back to text: " + diagnostic);
                completeKnownText(context, result, fallbackMessage);
            }
        });
        return result;
    }

    private void completeKnownText(
        CommandContext context,
        CompletableFuture<CommandResult> result,
        String text
    ) {
        if (result.isDone()) return;
        CompletionStage<SendResult> reply;
        try {
            reply = context.replyText(text);
            if (reply == null) throw new IllegalStateException("MessageGateway returned null for text reply");
        } catch (Throwable error) {
            result.complete(CommandResult.failed("Known inventory reply could not start: " + error.getMessage()));
            return;
        }
        reply.whenComplete((sendResult, replyError) -> {
            if (replyError == null && sendResult != null && sendResult.isSuccess()) {
                result.complete(CommandResult.handled());
            } else {
                String diagnostic = replyError != null
                    ? unwrap(replyError).getMessage()
                    : sendResult == null ? "no SendResult" : sendResult.getDiagnostic();
                result.complete(CommandResult.failed("Known inventory reply was not sent: " + diagnostic));
            }
        });
    }

    private void failWithText(
        CommandContext context,
        CompletableFuture<CommandResult> result,
        String phase,
        Throwable error
    ) {
        if (result.isDone()) return;
        Throwable cause = error == null ? new IllegalStateException(phase) : error;
        logger.error(phase, cause);
        CompletionStage<SendResult> reply;
        try {
            reply = context.replyText(failureMessage());
            if (reply == null) throw new IllegalStateException("MessageGateway returned null for failure reply");
        } catch (Throwable replyError) {
            result.complete(CommandResult.failed(phase + "; failure reply could not start: " + replyError.getMessage()));
            return;
        }
        reply.whenComplete((sendResult, replyError) -> {
            if (replyError == null && sendResult != null && sendResult.isSuccess()) {
                result.complete(CommandResult.handled());
            } else {
                String diagnostic = replyError != null
                    ? unwrap(replyError).getMessage()
                    : sendResult == null ? "no SendResult" : sendResult.getDiagnostic();
                result.complete(CommandResult.failed(phase + "; failure reply was not sent: " + diagnostic));
            }
        });
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException) &&
            current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String usageMessage() {
        return mode == Mode.ENDER_CHEST ? config.getEnderChestUsageMessage() : config.getUsageMessage();
    }

    private String notAuthorizedMessage() {
        return mode == Mode.ENDER_CHEST
            ? config.getEnderChestNotAuthorizedMessage()
            : config.getNotAuthorizedMessage();
    }

    private String playerOfflineMessage() {
        return mode == Mode.ENDER_CHEST
            ? config.getEnderChestPlayerOfflineMessage()
            : config.getPlayerOfflineMessage();
    }

    private String playerStateChangedMessage() {
        return mode == Mode.ENDER_CHEST
            ? config.getEnderChestPlayerStateChangedMessage()
            : config.getPlayerStateChangedMessage();
    }

    private boolean offlineEnabled() {
        return mode == Mode.ENDER_CHEST
            ? config.isOfflineEnderChestEnabled()
            : config.isOfflineInventoryEnabled();
    }

    private boolean allowLegacyOfflineSnapshots() {
        return mode == Mode.ENDER_CHEST
            ? config.isAllowLegacyOfflineEnderChestSnapshots()
            : config.isAllowLegacyOfflineSnapshots();
    }

    private String offlineSnapshotMissingMessage() {
        return mode == Mode.ENDER_CHEST
            ? config.getEnderChestOfflineSnapshotMissingMessage()
            : config.getOfflineSnapshotMissingMessage();
    }

    private String offlineLegacyDeniedMessage() {
        return mode == Mode.ENDER_CHEST
            ? config.getEnderChestOfflineLegacyDeniedMessage()
            : config.getOfflineLegacyDeniedMessage();
    }

    private String failureMessage() {
        return mode == Mode.ENDER_CHEST
            ? config.getEnderChestFailureMessage()
            : config.getFailureMessage();
    }

    private static final class TargetResolution {
        private final String target;
        private final String message;
        private final String fallbackMessage;
        private final PlayerBinding binding;
        private final List<InventoryButton> buttons;

        private TargetResolution(
            String target,
            String message,
            String fallbackMessage,
            PlayerBinding binding,
            List<InventoryButton> buttons
        ) {
            this.target = target;
            this.message = message;
            this.fallbackMessage = fallbackMessage;
            this.binding = binding;
            this.buttons = buttons;
        }

        private static TargetResolution resolved(String target, PlayerBinding binding) {
            return new TargetResolution(target, null, null, binding, null);
        }

        private static TargetResolution rejected(String message) {
            return new TargetResolution(null, message, message, null, null);
        }

        private static TargetResolution rejected(
            String message,
            String fallbackMessage,
            List<InventoryButton> buttons
        ) {
            return new TargetResolution(null, message, fallbackMessage, null, buttons);
        }

        private boolean isResolved() { return target != null; }
    }

    private enum SelectionStatus { SELECTED, INVALID_OPTION, EXPIRED }

    private static final class SelectionResult {
        final SelectionStatus status;
        final SelectionOption option;

        private SelectionResult(SelectionStatus status, SelectionOption option) {
            this.status = status;
            this.option = option;
        }

        static SelectionResult selected(SelectionOption option) {
            return new SelectionResult(SelectionStatus.SELECTED, option);
        }

        static SelectionResult invalid() {
            return new SelectionResult(SelectionStatus.INVALID_OPTION, null);
        }

        static SelectionResult expired() {
            return new SelectionResult(SelectionStatus.EXPIRED, null);
        }
    }

    private static final class PendingSelection {
        final long expiresAt;
        final String nonce;
        final List<SelectionOption> options;

        PendingSelection(long expiresAt, String nonce, List<SelectionOption> options) {
            this.expiresAt = expiresAt;
            this.nonce = nonce;
            this.options = Collections.unmodifiableList(new ArrayList<SelectionOption>(options));
        }
    }

    private static final class ConsumedButtonSelection {
        final String groupId;
        final String userId;
        final long expiresAt;

        ConsumedButtonSelection(String groupId, String userId, long expiresAt) {
            this.groupId = groupId;
            this.userId = userId;
            this.expiresAt = expiresAt;
        }

        boolean matches(String candidateGroupId, String candidateUserId) {
            return groupId.equals(candidateGroupId) && userId.equals(candidateUserId);
        }
    }

    private enum ButtonSelectionStatus { SELECTED, INVALID_OPTION, EXPIRED, DUPLICATE, FORBIDDEN }

    private static final class ButtonSelectionResult {
        final ButtonSelectionStatus status;
        final SelectionOption option;

        private ButtonSelectionResult(ButtonSelectionStatus status, SelectionOption option) {
            this.status = status;
            this.option = option;
        }

        static ButtonSelectionResult selected(SelectionOption option) {
            return new ButtonSelectionResult(ButtonSelectionStatus.SELECTED, option);
        }

        static ButtonSelectionResult invalid() {
            return new ButtonSelectionResult(ButtonSelectionStatus.INVALID_OPTION, null);
        }

        static ButtonSelectionResult expired() {
            return new ButtonSelectionResult(ButtonSelectionStatus.EXPIRED, null);
        }

        static ButtonSelectionResult duplicate() {
            return new ButtonSelectionResult(ButtonSelectionStatus.DUPLICATE, null);
        }

        static ButtonSelectionResult forbidden() {
            return new ButtonSelectionResult(ButtonSelectionStatus.FORBIDDEN, null);
        }
    }

    private static final class ProactiveMessageGateway implements MessageGateway {
        private final MessageGateway delegate;

        ProactiveMessageGateway(MessageGateway delegate) { this.delegate = delegate; }

        @Override
        public CompletionStage<SendResult> replyText(MessageReference reference, String text) {
            return delegate.sendText(reference.getGroupOpenId(), text);
        }

        @Override
        public CompletionStage<SendResult> replyImage(
            MessageReference reference,
            byte[] bytes,
            String mimeType,
            String fileName,
            String optionalText
        ) {
            return delegate.sendImage(reference.getGroupOpenId(), bytes, mimeType, fileName, optionalText);
        }

        @Override
        public CompletionStage<SendResult> sendText(String groupOpenId, String text) {
            return delegate.sendText(groupOpenId, text);
        }

        @Override
        public CompletionStage<SendResult> sendImage(
            String groupOpenId,
            byte[] bytes,
            String mimeType,
            String fileName,
            String optionalText
        ) {
            return delegate.sendImage(groupOpenId, bytes, mimeType, fileName, optionalText);
        }
    }

    private static final class SelectionOption {
        final String bindingId;
        final String playerName;
        final int slot;

        SelectionOption(PlayerBinding value) {
            this.bindingId = value.getBindingId().orElse(null);
            this.playerName = value.getPlayerName();
            this.slot = value.getSlot();
        }

        boolean matches(PlayerBinding value) {
            if (bindingId != null && value.getBindingId().isPresent()) {
                return bindingId.equals(value.getBindingId().get());
            }
            return playerName.equalsIgnoreCase(value.getPlayerName()) &&
                (slot == 0 || value.getSlot() == 0 || slot == value.getSlot());
        }
    }
}
