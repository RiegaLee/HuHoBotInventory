package cn.huohuas001.huhobot.inventory.skin;

import net.skinsrestorer.api.PropertyUtils;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.VersionProvider;
import net.skinsrestorer.api.property.SkinProperty;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

/** Optional SkinsRestorer v15 adapter. It never performs a Mojang profile lookup. */
public final class SkinsRestorerSkinProvider implements PlayerSkinProvider {
    private static final int MAX_SKIN_BYTES = 1024 * 1024;

    private final SkinsRestorer api;
    private final Path rawCache;
    private final boolean allowDownloads;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public SkinsRestorerSkinProvider(
        Path cacheRoot,
        boolean allowDownloads,
        int connectTimeoutMillis,
        int readTimeoutMillis
    ) {
        if (!VersionProvider.isCompatibleWith("15")) {
            throw new IllegalStateException("SkinsRestorer v15 API is required");
        }
        this.api = SkinsRestorerProvider.get();
        this.rawCache = cacheRoot.toAbsolutePath().normalize().resolve("raw");
        this.allowDownloads = allowDownloads;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    @Override
    public Optional<PlayerSkin> findSkin(PlayerIdentity player) throws Exception {
        Optional<SkinProperty> property = api.getPlayerStorage().getSkinForPlayer(player.getUuid(), player.getName());
        if (!property.isPresent()) return Optional.empty();
        String textureUrl = PropertyUtils.getSkinTextureUrl(property.get());
        TextureLocation location = validateTextureLocation(textureUrl);
        Path cached = rawCache.resolve(location.cacheKey + ".png").normalize();
        if (!cached.startsWith(rawCache)) throw new IllegalStateException("skin cache path escaped its root");
        BufferedImage image = readSkin(cached);
        if (image == null && allowDownloads) {
            image = download(location.url);
            Files.createDirectories(rawCache);
            Path temporary = Files.createTempFile(rawCache, location.cacheKey + "-", ".tmp");
            try {
                if (!ImageIO.write(image, "png", temporary.toFile())) throw new IllegalStateException("No PNG writer");
                try {
                    Files.move(temporary, cached, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, cached, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        if (image == null) return Optional.empty();
        return Optional.of(new PlayerSkin(image, location.cacheKey, "SKINSRESTORER", isSlim(property.get())));
    }

    private BufferedImage download(URL url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "HuHoBotInventory/1.10.0");
        try {
            if (connection.getResponseCode() != 200) {
                throw new IllegalStateException("Skin CDN returned HTTP " + connection.getResponseCode());
            }
            int declared = connection.getContentLength();
            if (declared > MAX_SKIN_BYTES) throw new IllegalStateException("Skin response is too large");
            byte[] bytes;
            try (InputStream input = connection.getInputStream()) {
                bytes = readLimited(input, MAX_SKIN_BYTES);
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            validateDimensions(image);
            return image;
        } finally {
            connection.disconnect();
        }
    }

    private static BufferedImage readSkin(Path path) {
        if (!Files.isRegularFile(path)) return null;
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            validateDimensions(image);
            return image;
        } catch (Exception invalid) {
            return null;
        }
    }

    private static void validateDimensions(BufferedImage image) {
        if (image == null || image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
            throw new IllegalArgumentException("Skin texture is not 64x64 or legacy 64x32");
        }
    }

    private static TextureLocation validateTextureLocation(String value) throws Exception {
        URI parsed = new URI(value);
        if (!"http".equalsIgnoreCase(parsed.getScheme()) && !"https".equalsIgnoreCase(parsed.getScheme())) {
            throw new IllegalArgumentException("Unsupported skin texture scheme");
        }
        if (!"textures.minecraft.net".equalsIgnoreCase(parsed.getHost()) || parsed.getQuery() != null || parsed.getFragment() != null) {
            throw new IllegalArgumentException("Skin texture must be on textures.minecraft.net");
        }
        String path = parsed.getPath();
        if (path == null || !path.matches("/texture/[A-Fa-f0-9]{32,128}")) {
            throw new IllegalArgumentException("Invalid Minecraft skin texture path");
        }
        String textureId = path.substring("/texture/".length()).toLowerCase(Locale.ROOT);
        return new TextureLocation(new URL("https", "textures.minecraft.net", path), sha256(textureId));
    }

    private static boolean isSlim(SkinProperty property) {
        try {
            String json = new String(Base64.getDecoder().decode(property.getValue()), StandardCharsets.UTF_8);
            return json.matches("(?s).*\\\"model\\\"\\s*:\\s*\\\"slim\\\".*");
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static String sha256(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte current : hash) result.append(String.format(Locale.ROOT, "%02x", current & 0xff));
        return result.toString();
    }

    private static byte[] readLimited(InputStream input, int maximum) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > maximum) throw new IllegalStateException("Skin response exceeds limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static final class TextureLocation {
        final URL url;
        final String cacheKey;
        TextureLocation(URL url, String cacheKey) { this.url = url; this.cacheKey = cacheKey; }
    }
}
