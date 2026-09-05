package cn.huohuas001.huhobot.inventory.host;

import cn.huohuas001.bot.QClient;
import cn.huohuas001.huhobot.api.MessageGateway;
import cn.huohuas001.huhobot.api.MessageReference;
import cn.huohuas001.huhobot.api.SendResult;
import io.github.kloping.qqbot.Starter;
import io.github.kloping.qqbot.entities.qqpd.Channel;
import io.github.kloping.qqbot.http.data.V2MsgData;
import io.github.kloping.qqbot.http.data.V2Result;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** QQ transport implemented inside Inventory against APIs shared by pristine HuHoBot releases. */
final class OfficialQqMessageGateway implements MessageGateway {
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

    @Override
    public CompletionStage<SendResult> replyText(MessageReference reference, String text) {
        if (reference == null || isBlank(text)) return invalid("Reply reference and text are required");
        return attempt(() -> QClient.INSTANCE.replyText(
            reference.getGroupOpenId(), reference.getMessageId(), reference.getMessageSequence(), text
        ));
    }

    @Override
    public CompletionStage<SendResult> replyImage(
        MessageReference reference,
        byte[] bytes,
        String mimeType,
        String fileName,
        String optionalText
    ) {
        if (reference == null) return invalid("Reply reference is required");
        String invalid = validateImage(bytes, mimeType, fileName);
        if (invalid != null) return invalid(invalid);
        byte[] copy = bytes.clone();
        return attempt(() -> sendImageInternal(
            reference.getGroupOpenId(), copy, optionalText,
            reference.getMessageId(), Integer.valueOf(reference.getMessageSequence())
        ));
    }

    @Override
    public CompletionStage<SendResult> sendText(String groupOpenId, String text) {
        if (isBlank(groupOpenId) || isBlank(text)) return invalid("Group and text are required");
        return attempt(() -> sendTextCompatible(groupOpenId, text));
    }

    @Override
    public CompletionStage<SendResult> sendImage(
        String groupOpenId,
        byte[] bytes,
        String mimeType,
        String fileName,
        String optionalText
    ) {
        if (isBlank(groupOpenId)) return invalid("Group is required");
        String invalid = validateImage(bytes, mimeType, fileName);
        if (invalid != null) return invalid(invalid);
        byte[] copy = bytes.clone();
        return attempt(() -> sendImageInternal(groupOpenId, copy, optionalText, null, (Integer) null));
    }

    private static boolean sendTextCompatible(String groupOpenId, String text) throws Exception {
        try {
            Method mainline = QClient.class.getMethod("sendText", String.class, String.class);
            Object value = mainline.invoke(QClient.INSTANCE, groupOpenId, text);
            return !(value instanceof Boolean) || ((Boolean) value).booleanValue();
        } catch (NoSuchMethodException ignored) {
            // AGENT renamed the one-group proactive send method and returns Unit/void.
            if (starter() == null) return false;
            Method agent = QClient.class.getMethod("sendTextToGroup", String.class, String.class);
            agent.invoke(QClient.INSTANCE, groupOpenId, text);
            return true;
        }
    }

    private static boolean sendImageInternal(
        String groupOpenId,
        byte[] bytes,
        String optionalText,
        String messageId,
        Integer messageSequence
    ) throws Exception {
        Starter starter = starter();
        if (starter == null || starter.getBot() == null || starter.getBot().groupBaseV2 == null) return false;
        String body = "{\"file_type\":1,\"file_data\":\"" +
            Base64.getEncoder().encodeToString(bytes) + "\",\"srv_send_msg\":false}";
        V2Result uploaded = starter.getBot().groupBaseV2.sendFile(
            groupOpenId, body, Channel.SEND_MESSAGE_HEADERS
        );
        String fileInfo = uploaded == null ? null : uploaded.getFile_info();
        if (isBlank(fileInfo)) return false;

        V2MsgData payload = new V2MsgData()
            .setContent(optionalText == null ? "" : optionalText)
            .setMsg_type(Integer.valueOf(7))
            .setMedia(new V2MsgData.Media(fileInfo));
        if (!isBlank(messageId)) payload.setMsg_id(messageId);
        if (messageSequence != null) payload.setMsg_seq(messageSequence);
        V2Result sent = starter.getBot().groupBaseV2.send(
            groupOpenId, payload.toString(), Channel.SEND_MESSAGE_HEADERS
        );
        return sent != null && (sent.getId() != null || sent.getRet() == null || sent.getRet().intValue() == 0);
    }

    private static Starter starter() throws Exception {
        Field field = QClient.class.getDeclaredField("starter");
        field.setAccessible(true);
        Object owner = Modifier.isStatic(field.getModifiers()) ? null : QClient.INSTANCE;
        Object value = field.get(owner);
        return value instanceof Starter ? (Starter) value : null;
    }

    private static String validateImage(byte[] bytes, String mimeType, String fileName) {
        if (bytes == null || bytes.length == 0) return "Image bytes are required";
        if (bytes.length > MAX_IMAGE_BYTES) return "Image exceeds the 8 MiB host limit";
        if (mimeType == null || !mimeType.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) {
            return "Unsupported image MIME type";
        }
        return isBlank(fileName) ? "Image file name is required" : null;
    }

    private static CompletionStage<SendResult> attempt(CheckedBoolean action) {
        try {
            return CompletableFuture.completedFuture(
                action.run()
                    ? SendResult.success()
                    : SendResult.of(SendResult.Status.FAILED, "QQ platform rejected or could not send the message")
            );
        } catch (Throwable error) {
            CompletableFuture<SendResult> future = new CompletableFuture<SendResult>();
            future.completeExceptionally(error);
            return future;
        }
    }

    private static CompletionStage<SendResult> invalid(String message) {
        return CompletableFuture.completedFuture(SendResult.of(SendResult.Status.INVALID_REQUEST, message));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private interface CheckedBoolean {
        boolean run() throws Exception;
    }
}
