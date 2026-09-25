package cn.huohuas001.huhobot.inventory.qq;

import java.util.Arrays;

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
        assertEquals("账号选择已超时，请重新发送 /我的背包。",
            InventoryButtonResult.EXPIRED_INVENTORY.getFeedbackMessage());
        assertEquals(1, InventoryButtonResult.EXPIRED_ENDER_CHEST.getPlatformCode());
        assertEquals("账号选择已超时，请重新发送 /我的末影箱。",
            InventoryButtonResult.EXPIRED_ENDER_CHEST.getFeedbackMessage());
        assertNull(InventoryButtonResult.DUPLICATE.getFeedbackMessage());
    }

}
