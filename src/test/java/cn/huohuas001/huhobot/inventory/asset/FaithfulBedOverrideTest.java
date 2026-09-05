package cn.huohuas001.huhobot.inventory.asset;

import cn.huohuas001.huhobot.inventory.model.ItemSnapshot;
import cn.huohuas001.huhobot.inventory.renderer.TextureResolver;
import cn.huohuas001.huhobot.inventory.renderer.Theme;
import cn.huohuas001.huhobot.inventory.renderer.ThemeLoader;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaithfulBedOverrideTest {
    private static final Path THEME = Paths.get("src", "main", "resources", "themes", "faithful32x");
    private static final Path OVERRIDES = THEME.resolve("overrides/items/minecraft");
    private static final List<String> COLORS = Arrays.asList(
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    );

    @Test
    void allSixteenBedsAreCompleteFixedSlotIcons() throws Exception {
        for (String color : COLORS) {
            String item = color + "_bed";
            BufferedImage image = ImageIO.read(OVERRIDES.resolve(item + ".png").toFile());
            assertEquals(64, image.getWidth(), item);
            assertEquals(64, image.getHeight(), item);
            int minX=64,minY=64,maxX=-1,maxY=-1,pixels=0;
            for (int y=0;y<64;y++) for (int x=0;x<64;x++) {
                if ((image.getRGB(x,y) >>> 24) == 0) continue;
                minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);pixels++;
            }
            assertEquals(7,minX,item);assertEquals(61,maxX,item);
            assertEquals(7,minY,item);assertEquals(61,maxY,item);
            assertTrue(pixels >= 1800,item + " alpha coverage");
        }
        assertEquals(16,COLORS.size());
    }

    @Test
    void bedFamilyWinsBeforeUnsupportedSpecialFallback() throws Exception {
        VanillaImportedAssetProvider imported = VanillaImportedAssetProvider.open(
            Paths.get("data", "imported-assets", "vanilla")
        );
        assertTrue(imported.isAvailable());
        Theme theme = ThemeLoader.load(THEME, imported);
        for (String color : COLORS) {
            String item = "minecraft:" + color + "_bed";
            TextureResolver.ResolvedTexture resolved = theme.getTextures().resolve(ItemSnapshot.basic(item,1));
            assertEquals(TextureResolver.Source.EXPLICIT_OVERRIDE,resolved.getSource(),item);
            assertFalse(resolved.isFallback(),item);
        }
    }

    @Test
    void representativeBedColorsStayDistinctWithoutRuntimeTint() throws Exception {
        int[] white=average("white_bed"),red=average("red_bed"),blue=average("blue_bed"),black=average("black_bed");
        assertTrue(white[0] > 125 && white[1] > 125 && white[2] > 125);
        assertTrue(red[0] > red[1] * 2 && red[0] > red[2] * 2);
        assertTrue(blue[2] > blue[0] && blue[2] > blue[1]);
        assertTrue(black[0] < 70 && black[1] < 70 && black[2] < 70);
        assertFalse(Arrays.equals(white,red));
        assertFalse(Arrays.equals(red,blue));
        assertFalse(Arrays.equals(blue,black));
    }

    private static int[] average(String item) throws Exception {
        BufferedImage image=ImageIO.read(OVERRIDES.resolve(item+".png").toFile());
        long red=0,green=0,blue=0,count=0;
        for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++){
            int argb=image.getRGB(x,y);if((argb>>>24)==0)continue;
            red+=(argb>>>16)&255;green+=(argb>>>8)&255;blue+=argb&255;count++;
        }
        return new int[]{(int)(red/count),(int)(green/count),(int)(blue/count)};
    }
}
