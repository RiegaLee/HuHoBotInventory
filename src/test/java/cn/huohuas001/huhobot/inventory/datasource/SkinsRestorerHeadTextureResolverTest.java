package cn.huohuas001.huhobot.inventory.datasource;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SkinsRestorerHeadTextureResolverTest {
    @Test
    void extractsOnlyMinecraftTextureHashFromSkinProperty() {
        String hash = "bfe2ae0026d54128eb758c8118b4412941225c3089f116c3cf7c8e9767691311";
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
        String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(hash, SkinsRestorerHeadTextureResolver.textureHash(encoded));
        assertNull(SkinsRestorerHeadTextureResolver.textureHash("not base64"));
    }
}
