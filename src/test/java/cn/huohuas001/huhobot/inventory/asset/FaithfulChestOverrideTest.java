package cn.huohuas001.huhobot.inventory.asset;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaithfulChestOverrideTest {
    @Test
    void clientEquivalentChestOccupiesFixedSlotAndKeepsLockOnRightFront() throws Exception {
        Path asset = Paths.get(
            "src", "main", "resources", "themes", "faithful32x", "overrides", "items", "minecraft", "chest.png"
        );
        BufferedImage image = ImageIO.read(asset.toFile());
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());

        int count = 0;
        int alphaMinX = image.getWidth();
        int alphaMaxX = -1;
        int alphaMinY = image.getHeight();
        int alphaMaxY = -1;
        int minX = image.getWidth();
        int maxX = -1;
        int minY = image.getHeight();
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = argb >>> 24;
                int red = (argb >>> 16) & 255;
                int green = (argb >>> 8) & 255;
                int blue = argb & 255;
                int maximum = Math.max(red, Math.max(green, blue));
                int minimum = Math.min(red, Math.min(green, blue));
                if (alpha > 0) {
                    alphaMinX = Math.min(alphaMinX, x); alphaMaxX = Math.max(alphaMaxX, x);
                    alphaMinY = Math.min(alphaMinY, y); alphaMaxY = Math.max(alphaMaxY, y);
                }
                if (alpha > 0 && maximum > 70 && maximum - minimum < 18) {
                    count++;
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                }
            }
        }

        assertEquals(7, alphaMinX, "fixed GUI projection must not be bbox-fitted horizontally");
        assertEquals(56, alphaMaxX, "fixed GUI projection must not be bbox-fitted horizontally");
        assertEquals(7, alphaMinY, "fixed GUI projection must preserve the client top margin");
        assertEquals(60, alphaMaxY, "fixed GUI projection must preserve the client bottom extent");
        assertTrue(count >= 10, "Faithful gray lock pixels must remain visible");
        assertTrue(minX >= 43 && maxX <= 46, "lock must remain on the broad right front face");
        assertTrue(minY >= 31 && maxY <= 41, "lock must remain at the client lid/body seam");
    }
}
