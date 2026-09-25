package cn.huohuas001.huhobot.inventory.skin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DefaultPlayerSkinProviderTest {
    @Test
    void loadsVerifiedVanillaSteveFromBundledClientAsset() {
        PlayerSkin skin = new DefaultPlayerSkinProvider().getFallback();

        assertEquals(64, skin.getImage().getWidth());
        assertEquals(64, skin.getImage().getHeight());
        assertEquals(DefaultPlayerSkinProvider.EXPECTED_SHA256.toLowerCase(), skin.getCacheKey());
        assertEquals("MINECRAFT_CLIENT_26.1.2", skin.getSource());
        assertFalse(skin.isSlim());
    }
}
