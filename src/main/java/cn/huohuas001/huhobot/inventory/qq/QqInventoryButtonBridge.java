package cn.huohuas001.huhobot.inventory.qq;

import cn.huohuas001.bot.QClient;
import cn.huohuas001.huhobot.api.MessageReference;
import cn.huohuas001.huhobot.api.Registration;
import cn.huohuas001.huhobot.api.Registrations;
import cn.huohuas001.huhobot.api.SendResult;
import io.github.kloping.qqbot.Start0;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
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
            !Integer.valueOf(1).equals(raw.getChat_type()) || raw.getData() == null ||
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
                raw.getId(), raw.getGroup_openid(), raw.getGroup_member_openid(), data
            ));
            if (result == null || result == InventoryButtonResult.NOT_HANDLED) result = InventoryButtonResult.FAILED;
        } catch (Throwable error) {
            logger.log(Level.WARNING, "Inventory 按钮回调失败：" + concise(error), error);
            result = InventoryButtonResult.FAILED;
        }
        try {
            acknowledgeInteraction(raw.getId(), result.getPlatformCode());
        } catch (Throwable error) {
            logger.log(Level.WARNING, "Inventory 按钮 PUT ACK 失败：" + concise(error));
        }
    }

    private void acknowledgeInteraction(String interactionId, int code) throws IOException {
        Starter connected = starter;
        if (connected == null) throw new IOException("HuHoBot QQ client is not connected");
        Start0 start = authenticationContext(connected);
        if (start == null) throw new IOException("HuHoBot QQ authentication context is unavailable");
        putInteractionAcknowledgement(
            connected.net,
            new HashMap<String, String>(start.getHeaders()),
            interactionId,
            code
        );
    }

    private static Start0 authenticationContext(Starter connected) throws IOException {
        try {
            Field field = Starter.class.getDeclaredField("contextManager");
            field.setAccessible(true);
            Object context = field.get(connected);
            if (context == null) return null;
            Object value = context.getClass()
                .getMethod("getContextEntity", Class.class)
                .invoke(context, Start0.class);
            return value instanceof Start0 ? (Start0) value : null;
        } catch (ReflectiveOperationException error) {
            throw new IOException("Could not read HuHoBot QQ authentication context", error);
        }
    }

    /**
     * Sends the callback response without SpringTool 0.6.4's HTTP proxy. That proxy only applies
     * POST explicitly and silently turns annotated PUT requests into GET, which QQ rejects as 405.
     */
    static void putInteractionAcknowledgement(
        String baseUrl,
        Map<String, String> headers,
        String interactionId,
        int code
    ) throws IOException {
        if (baseUrl == null || baseUrl.trim().isEmpty()) throw new IOException("QQ API base URL is blank");
        if (interactionId == null || interactionId.trim().isEmpty()) {
            throw new IOException("QQ interaction id is blank");
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        String encodedId = URLEncoder.encode(interactionId, "UTF-8").replace("+", "%20");
        URL target = new URL(normalizedBase + "interactions/" + encodedId);
        byte[] body = ("{\"code\":" + code + "}").getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) target.openConnection();
        try {
            connection.setRequestMethod("PUT");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        connection.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }
            }
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            int status = connection.getResponseCode();
            String responseBody = readResponseBody(connection, status);
            if (status < 200 || status >= 300) {
                throw new IOException(
                    "QQ interaction ACK returned HTTP " + status +
                        (responseBody.isEmpty() ? "" : ": " + responseBody)
                );
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readResponseBody(HttpURLConnection connection, int status) throws IOException {
        InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (input == null) return "";
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[512];
            int remaining = 4096;
            while (remaining > 0) {
                int count = stream.read(buffer, 0, Math.min(buffer.length, remaining));
                if (count < 0) break;
                output.write(buffer, 0, count);
                remaining -= count;
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
        }
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
}
