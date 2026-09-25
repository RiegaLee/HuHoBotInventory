package cn.huohuas001.huhobot.inventory.qq;

import cn.huohuas001.bot.QClient;
import cn.huohuas001.huhobot.api.MessageReference;
import cn.huohuas001.huhobot.api.Registration;
import cn.huohuas001.huhobot.api.Registrations;
import cn.huohuas001.huhobot.api.SendResult;
import io.github.kloping.qqbot.Starter;
import io.github.kloping.qqbot.api.event.InterActionEvent;
import io.github.kloping.qqbot.entities.ex.Keyboard;
import io.github.kloping.qqbot.entities.ex.Markdown;
import io.github.kloping.qqbot.entities.qqpd.Channel;
import io.github.kloping.qqbot.entities.qqpd.InterAction;
import io.github.kloping.qqbot.http.data.V2MsgData;
import io.github.kloping.qqbot.http.data.V2Result;
import io.github.kloping.qqbot.impl.ListenerHost;
import io.github.kloping.qqbot.impl.ListenerHost.EventReceiver;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pure-Addon adapter: attaches an Inventory-owned listener to HuHoBot's existing QQ Starter.
 * No HuHoBot Core source or stable API changes are required.
 */
public final class QqInventoryButtonBridge implements InventoryButtonBridge {
    private final Logger logger;
    private final Map<String, InventoryButtonHandler> routes =
        new ConcurrentHashMap<String, InventoryButtonHandler>();
    private final Map<String, ButtonMessage> buttonMessagesByData =
        new ConcurrentHashMap<String, ButtonMessage>();
    private final Map<String, ButtonMessage> buttonMessagesByOwner =
        new ConcurrentHashMap<String, ButtonMessage>();
    private final ScheduledExecutorService recallExecutor =
        Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "huhobot-inventory-button-recall");
            thread.setDaemon(true);
            return thread;
        });
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile Starter starter;
    private volatile InventoryInteractionListener listener;

    private QqInventoryButtonBridge(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public static InventoryButtonBridge connect(Logger logger) {
        Objects.requireNonNull(logger, "logger");
        QqInventoryButtonBridge bridge = new QqInventoryButtonBridge(logger);
        if (bridge.ensureConnected() == null) {
            logger.info("HuHoBot QQ 客户端尚未初始化；Inventory 将在首次发送按钮时自动接入");
        }
        return bridge;
    }

    @Override public boolean isAvailable() { return !closed.get(); }

    @Override
    public CompletionStage<SendResult> replySelection(
        MessageReference reference,
        String markdown,
        List<InventoryButton> buttons
    ) {
        if (closed.get()) return failed("Inventory QQ button bridge is closed");
        if (reference == null || markdown == null || markdown.trim().isEmpty() || buttons == null || buttons.isEmpty()) {
            return failed("Invalid Inventory QQ button reply");
        }
        try {
            Starter activeStarter = ensureConnected();
            if (activeStarter == null) {
                String diagnostic = "HuHoBot QQ client is not initialized yet";
                logger.warning("Inventory 暂时无法接入 QQ 按钮，将回退文字账号选择");
                return failed(diagnostic);
            }
            Keyboard keyboard = keyboard(buttons);
            V2MsgData payload = requestPayload(
                markdown,
                keyboard,
                reference.getMessageId(),
                reference.getMessageSequence()
            );
            V2Result response = activeStarter.getBot().groupBaseV2.send(
                reference.getGroupOpenId(),
                payload.toString(),
                Channel.SEND_MESSAGE_HEADERS
            );
            if (response != null && response.getId() != null && !response.getId().trim().isEmpty()) {
                rememberButtonMessage(reference.getGroupOpenId(), response.getId(), buttons);
                return CompletableFuture.completedFuture(SendResult.success());
            }
            String diagnostic = "QQ did not return a message id for the custom keyboard";
            if (response != null) {
                diagnostic += " (ret=" + response.getRet() +
                    (response.getMsg() == null || response.getMsg().trim().isEmpty()
                        ? "" : ", msg=" + response.getMsg()) + ")";
            }
            logger.warning(
                "Inventory QQ 按钮消息被拒绝；请确认机器人已开通自定义消息按钮能力。" + diagnostic
            );
            return failed(diagnostic);
        } catch (Throwable error) {
            String diagnostic = "QQ custom keyboard request failed: " + concise(error);
            logger.log(
                Level.WARNING,
                "Inventory QQ 按钮消息发送失败；请确认机器人已开通自定义消息按钮能力。" + diagnostic
            );
            return failed(diagnostic);
        }
    }

    @Override
    public Registration register(String dataPrefix, InventoryButtonHandler handler) {
        if (closed.get()) throw new IllegalStateException("Inventory QQ button bridge is closed");
        String prefix = Objects.requireNonNull(dataPrefix, "dataPrefix").trim();
        if (prefix.isEmpty()) throw new IllegalArgumentException("Button data prefix must not be blank");
        Objects.requireNonNull(handler, "handler");
        for (String existing : routes.keySet()) {
            if (prefix.startsWith(existing) || existing.startsWith(prefix)) {
                throw new IllegalArgumentException("Conflicting Inventory button prefix: " + prefix);
            }
        }
        if (routes.putIfAbsent(prefix, handler) != null) {
            throw new IllegalArgumentException("Duplicate Inventory button prefix: " + prefix);
        }
        return Registrations.create(() -> routes.remove(prefix, handler));
    }

    private void onInteraction(InterActionEvent event) {
        InterAction raw = event.getInterAction();
        if (raw == null || !Integer.valueOf(11).equals(raw.getType()) ||
            !Integer.valueOf(1).equals(raw.getChatType()) || raw.getData() == null ||
            raw.getData().getResolved() == null) return;
        String data = raw.getData().getResolved().getButton_data();
        if (data == null) return;
        InventoryButtonHandler handler = null;
        for (Map.Entry<String, InventoryButtonHandler> entry : routes.entrySet()) {
            if (data.startsWith(entry.getKey())) {
                handler = entry.getValue();
                break;
            }
        }
        if (handler == null) return;

        InventoryButtonResult result;
        try {
            result = handler.handle(new InventoryButtonInteraction(
                raw.getId(), raw.getGroupOpenid(), raw.getGroupMemberOpenid(), data
            ));
            if (result == null || result == InventoryButtonResult.NOT_HANDLED) result = InventoryButtonResult.FAILED;
        } catch (Throwable error) {
            logger.log(Level.WARNING, "Inventory 按钮回调失败：" + concise(error), error);
            result = InventoryButtonResult.FAILED;
        }
        ButtonMessage buttonMessage = buttonMessagesByData.get(data);
        if (buttonMessage != null &&
            buttonMessage.allowedUserOpenIds.contains(raw.getGroupMemberOpenid()) &&
            result != InventoryButtonResult.FORBIDDEN) {
            queueRecall(buttonMessage);
        }
        try {
            acknowledgeInteraction(raw.getId(), result.getPlatformCode());
        } catch (Throwable error) {
            logger.log(Level.WARNING, "Inventory 按钮 PUT ACK 失败：" + concise(error));
        }
    }

    private void acknowledgeInteraction(String interactionId, int code) throws IOException {
        if (!QClient.INSTANCE.respondInteraction(interactionId, code)) {
            throw new IOException("HuHoBot rejected the QQ interaction acknowledgement");
        }
    }

    private void rememberButtonMessage(
        String groupOpenId,
        String messageId,
        List<InventoryButton> buttons
    ) {
        Set<String> data = new HashSet<String>();
        Set<String> owners = new HashSet<String>();
        for (InventoryButton button : buttons) {
            data.add(button.getData());
            owners.add(button.getAllowedUserOpenId());
        }
        ButtonMessage message = new ButtonMessage(groupOpenId, messageId, data, owners);
        for (String value : data) buttonMessagesByData.put(value, message);
        for (String owner : owners) {
            ButtonMessage previous = buttonMessagesByOwner.put(ownerKey(groupOpenId, owner), message);
            if (previous != null && previous != message) queueRecall(previous);
        }
        recallExecutor.schedule(
            () -> queueRecall(message),
            BUTTON_LIFETIME_SECONDS,
            TimeUnit.SECONDS
        );
    }

    private void queueRecall(ButtonMessage message) {
        if (!message.recallQueued.compareAndSet(false, true)) return;
        for (String data : message.buttonData) buttonMessagesByData.remove(data, message);
        for (String owner : message.allowedUserOpenIds) {
            buttonMessagesByOwner.remove(ownerKey(message.groupOpenId, owner), message);
        }
        try {
            recallExecutor.execute(() -> recall(message));
        } catch (RuntimeException error) {
            if (!closed.get()) logger.log(Level.WARNING, "提交 Inventory QQ 按钮消息撤回失败：" + concise(error));
        }
    }

    private void recall(ButtonMessage message) {
        if (closed.get()) return;
        try {
            if (!QClient.INSTANCE.recallMessage(message.groupOpenId, message.messageId)) {
                throw new IOException("HuHoBot rejected the QQ message recall");
            }
        } catch (Throwable error) {
            if (!closed.get()) {
                logger.log(Level.WARNING, "Inventory 撤回已结束的 QQ 按钮消息失败：" + concise(error));
            }
        }
    }

    private static String ownerKey(String groupOpenId, String userOpenId) {
        return groupOpenId + "\n" + userOpenId;
    }

    static Keyboard keyboard(List<InventoryButton> buttons) {
        Keyboard.KeyboardBuilder builder = Keyboard.KeyboardBuilder.create();
        for (InventoryButton button : buttons) {
            builder.addRow().addButton()
                .setLabel(button.getLabel())
                .setVisitedLabel(button.getVisitedLabel())
                .setStyle(button.getStyle())
                .setActionType(1)
                .setActionData(button.getData())
                .setPermission(new Keyboard.Permission(
                    new String[0], new String[] {button.getAllowedUserOpenId()}, 0
                ))
                .setUnSupportTips("当前 QQ 客户端版本过低")
                .build()
                .build();
        }
        return builder.build();
    }

    private Starter ensureConnected() {
        Starter current = starter;
        if (current != null || closed.get()) return current;
        synchronized (this) {
            if (starter != null || closed.get()) return starter;
            try {
                Field field = QClient.class.getDeclaredField("starter");
                field.setAccessible(true);
                Object owner = Modifier.isStatic(field.getModifiers()) ? null : QClient.INSTANCE;
                Object value = field.get(owner);
                if (!(value instanceof Starter)) return null;
                Starter connected = (Starter) value;
                InventoryInteractionListener connectedListener = new InventoryInteractionListener(this);
                connected.registerListenerHost(connectedListener);
                listener = connectedListener;
                starter = connected;
                logger.info("Inventory 已以独立 Addon 方式接入 QQ 消息按钮；HuHoBot Core 未修改");
                return connected;
            } catch (Throwable error) {
                logger.log(Level.FINE, "Inventory 等待 HuHoBot QQ 客户端初始化：" + concise(error));
                return null;
            }
        }
    }

    /** Builds the current QQ group Markdown payload: empty content and one top-level keyboard only. */
    static V2MsgData requestPayload(String markdown, Keyboard keyboard, String messageId, int messageSequence) {
        return new V2MsgData()
            .setMsg_type(2)
            .setMarkdown(new Markdown().setContent(markdown))
            .setKeyboard(keyboard)
            .setMsg_id(messageId)
            .setMsg_seq(messageSequence);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        routes.clear();
        buttonMessagesByData.clear();
        buttonMessagesByOwner.clear();
        recallExecutor.shutdownNow();
        synchronized (this) {
            try {
                Starter connected = starter;
                InventoryInteractionListener connectedListener = listener;
                if (connected != null && connectedListener != null) {
                    connected.getConfig().getListenerHosts().remove(connectedListener);
                }
            } catch (Throwable error) {
                logger.log(Level.WARNING, "注销 Inventory QQ 按钮监听器失败：" + concise(error));
            }
        }
    }

    private static CompletionStage<SendResult> failed(String diagnostic) {
        return CompletableFuture.completedFuture(SendResult.of(SendResult.Status.FAILED, diagnostic));
    }

    private static String concise(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null && cursor.getCause() != cursor) cursor = cursor.getCause();
        return cursor.getClass().getSimpleName() + (cursor.getMessage() == null ? "" : ": " + cursor.getMessage());
    }

    private static final class InventoryInteractionListener extends ListenerHost {
        private final QqInventoryButtonBridge owner;

        private InventoryInteractionListener(QqInventoryButtonBridge owner) { this.owner = owner; }

        @EventReceiver
        public void onInteraction(InterActionEvent event) { owner.onInteraction(event); }
    }

    private static final class ButtonMessage {
        final String groupOpenId;
        final String messageId;
        final Set<String> buttonData;
        final Set<String> allowedUserOpenIds;
        final AtomicBoolean recallQueued = new AtomicBoolean(false);

        ButtonMessage(
            String groupOpenId,
            String messageId,
            Set<String> buttonData,
            Set<String> allowedUserOpenIds
        ) {
            this.groupOpenId = groupOpenId;
            this.messageId = messageId;
            this.buttonData = buttonData;
            this.allowedUserOpenIds = allowedUserOpenIds;
        }
    }

    private static final long BUTTON_LIFETIME_SECONDS = 60L;
}
