package cn.huohuas001.huhobot.inventory.qq;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.github.kloping.qqbot.entities.ex.Keyboard;
import io.github.kloping.qqbot.http.data.V2MsgData;
import com.alibaba.fastjson.JSONArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class QqInventoryButtonBridgeTest {
    @Test
    void placesEachAccountButtonOnItsOwnRow() {
        Keyboard keyboard = QqInventoryButtonBridge.keyboard(Arrays.asList(
            new InventoryButton("1 Admin_Lee", "已选择", "hbi:i:nonce:1", "owner", 1),
            new InventoryButton("2 abcdefghijklmnop", "已选择", "hbi:i:nonce:2", "owner", 1)
        ));

        JSONArray rows = keyboard.getContent().getJSONArray("rows");
        assertEquals(2, rows.size());
        assertEquals(1, rows.getJSONObject(0).getJSONArray("buttons").size());
        assertEquals(1, rows.getJSONObject(1).getJSONArray("buttons").size());
        assertEquals(
            "1 Admin_Lee",
            rows.getJSONObject(0).getJSONArray("buttons")
                .getJSONObject(0).getJSONObject("render_data").getString("label")
        );
        assertEquals(
            "2 abcdefghijklmnop",
            rows.getJSONObject(1).getJSONArray("buttons")
                .getJSONObject(0).getJSONObject("render_data").getString("label")
        );
    }

    @Test
    void buildsCurrentGroupMarkdownPayloadWithTopLevelKeyboardOnly() {
        Keyboard keyboard = Keyboard.KeyboardBuilder.create().addRow().addButton()
            .setLabel("账号一")
            .setVisitedLabel("已选择")
            .setStyle(1)
            .setActionType(1)
            .setActionData("hbi:i:nonce:1")
            .setPermission(new Keyboard.Permission(new String[0], new String[] {"owner"}, 0))
            .setUnSupportTips("版本过低")
            .build().build().build();

        V2MsgData payload = QqInventoryButtonBridge.requestPayload(
            "请选择账号", keyboard, "message-id", 7
        );

        assertEquals(Integer.valueOf(2), payload.getMsg_type());
        assertEquals("", payload.getContent());
        assertEquals("请选择账号", payload.getMarkdown().getContent());
        assertNull(payload.getMarkdown().getKeyboard());
        assertSame(keyboard, payload.getKeyboard());
        assertEquals("message-id", payload.getMsg_id());
        assertEquals(Integer.valueOf(7), payload.getMsg_seq());
    }

    @Test
    void expiredResultsUseSuccessAckAndExplicitRetryText() {
        assertEquals(1, InventoryButtonResult.EXPIRED_INVENTORY.getPlatformCode());
        assertEquals("账号选择已超时，请重新发送 /背包。",
            InventoryButtonResult.EXPIRED_INVENTORY.getFeedbackMessage());
        assertEquals(1, InventoryButtonResult.EXPIRED_ENDER_CHEST.getPlatformCode());
        assertEquals("账号选择已超时，请重新发送 /末影箱。",
            InventoryButtonResult.EXPIRED_ENDER_CHEST.getFeedbackMessage());
        assertNull(InventoryButtonResult.DUPLICATE.getFeedbackMessage());
    }

    @Test
    void sendsInteractionAcknowledgementAsPut() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<CapturedRequest> captured = CompletableFuture.supplyAsync(() -> {
                try (Socket socket = server.accept()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                        socket.getInputStream(), StandardCharsets.UTF_8
                    ));
                    String requestLine = reader.readLine();
                    int contentLength = 0;
                    String authorization = null;
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        int separator = line.indexOf(':');
                        if (separator <= 0) continue;
                        String name = line.substring(0, separator).trim();
                        String value = line.substring(separator + 1).trim();
                        if ("Content-Length".equalsIgnoreCase(name)) contentLength = Integer.parseInt(value);
                        if ("Authorization".equalsIgnoreCase(name)) authorization = value;
                    }
                    char[] body = new char[contentLength];
                    int offset = 0;
                    while (offset < contentLength) {
                        int count = reader.read(body, offset, contentLength - offset);
                        if (count < 0) break;
                        offset += count;
                    }
                    byte[] response = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n{}"
                        .getBytes(StandardCharsets.US_ASCII);
                    OutputStream output = socket.getOutputStream();
                    output.write(response);
                    output.flush();
                    return new CapturedRequest(requestLine, authorization, new String(body, 0, offset));
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });

            QqInventoryButtonBridge.putInteractionAcknowledgement(
                "http://127.0.0.1:" + server.getLocalPort(),
                Collections.singletonMap("Authorization", "QQBot test-token"),
                "interaction-123",
                3
            );

            CapturedRequest request = captured.get(5, TimeUnit.SECONDS);
            assertEquals("PUT /interactions/interaction-123 HTTP/1.1", request.requestLine);
            assertEquals("QQBot test-token", request.authorization);
            assertEquals("{\"code\":3}", request.body);
        }
    }

    private static final class CapturedRequest {
        private final String requestLine;
        private final String authorization;
        private final String body;

        private CapturedRequest(String requestLine, String authorization, String body) {
            this.requestLine = requestLine;
            this.authorization = authorization;
            this.body = body;
        }
    }
}
