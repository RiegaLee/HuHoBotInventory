package cn.huohuas001.huhobot.inventory.datasource;

import cn.huohuas001.huhobot.inventory.head.PlayerHeadIconCache;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves owner-only Paper head profiles through SkinsRestorer without a hard plugin dependency. */
public final class SkinsRestorerHeadTextureResolver implements PlayerHeadIconCache.OwnerTextureResolver {
    private static final Pattern TEXTURE_URL = Pattern.compile(
        "https?://textures\\.minecraft\\.net/texture/([A-Fa-f0-9]{32,128})"
    );

    @Override
    public String resolve(UUID ownerUuid, String ownerName) throws Exception {
        Class<?> provider;
        try {
            provider = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
        } catch (ClassNotFoundException unavailable) {
            return null;
        }
        Object api = provider.getMethod("get").invoke(null);
        Class<?> apiType = Class.forName("net.skinsrestorer.api.SkinsRestorer");
        Object storage = apiType.getMethod("getPlayerStorage").invoke(api);
        Class<?> storageType = Class.forName("net.skinsrestorer.api.storage.PlayerStorage");

        Optional<?> property = optional(storageType.getMethod("getSkinOfPlayer", UUID.class)
            .invoke(storage, ownerUuid));
        if (!property.isPresent()) {
            property = optional(storageType.getMethod("getSkinForPlayer", UUID.class, String.class)
                .invoke(storage, ownerUuid, ownerName));
        }
        if (!property.isPresent()) return null;
        Class<?> propertyType = Class.forName("net.skinsrestorer.api.property.SkinProperty");
        String value = (String) propertyType.getMethod("getValue").invoke(property.get());
        return textureHash(value);
    }

    static String textureHash(String encodedProperty) {
        if (encodedProperty == null || encodedProperty.trim().isEmpty()) return null;
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedProperty);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
        String json = new String(decoded, StandardCharsets.UTF_8);
        Matcher matcher = TEXTURE_URL.matcher(json);
        return matcher.find() ? matcher.group(1).toLowerCase(java.util.Locale.ROOT) : null;
    }

    private static Optional<?> optional(Object value) {
        return value instanceof Optional ? (Optional<?>) value : Optional.empty();
    }
}
