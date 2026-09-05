import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Builds the compact 9x3 Ender Chest panel from Faithful's generic_54 GUI texture. */
public final class EnderChestBackgroundGenerator {
    private static final int VISIBLE_WIDTH = 352;
    private static final int THREE_ROW_TOP_HEIGHT = 142;
    private static final int BOTTOM_BORDER_Y = 432;
    private static final int BOTTOM_BORDER_HEIGHT = 12;
    private static final int OUTPUT_SCALE = 2;

    private EnderChestBackgroundGenerator() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                "Usage: EnderChestBackgroundGenerator <generic_54.png> <output.png>"
            );
        }
        Path source = Paths.get(args[0]).toAbsolutePath().normalize();
        Path target = Paths.get(args[1]).toAbsolutePath().normalize();
        BufferedImage texture = ImageIO.read(source.toFile());
        if (texture == null || texture.getWidth() < VISIBLE_WIDTH ||
            texture.getHeight() < BOTTOM_BORDER_Y + BOTTOM_BORDER_HEIGHT) {
            throw new IllegalArgumentException("Unexpected generic_54 texture: " + source);
        }

        int compactHeight = THREE_ROW_TOP_HEIGHT + BOTTOM_BORDER_HEIGHT;
        BufferedImage compact = new BufferedImage(VISIBLE_WIDTH, compactHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D compactGraphics = compact.createGraphics();
        try {
            compactGraphics.setComposite(AlphaComposite.Src);
            compactGraphics.drawImage(
                texture,
                0, 0, VISIBLE_WIDTH, THREE_ROW_TOP_HEIGHT,
                0, 0, VISIBLE_WIDTH, THREE_ROW_TOP_HEIGHT,
                null
            );
            compactGraphics.drawImage(
                texture,
                0, THREE_ROW_TOP_HEIGHT, VISIBLE_WIDTH, compactHeight,
                0, BOTTOM_BORDER_Y, VISIBLE_WIDTH, BOTTOM_BORDER_Y + BOTTOM_BORDER_HEIGHT,
                null
            );
        } finally {
            compactGraphics.dispose();
        }

        BufferedImage output = new BufferedImage(
            VISIBLE_WIDTH * OUTPUT_SCALE,
            compactHeight * OUTPUT_SCALE,
            BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D outputGraphics = output.createGraphics();
        try {
            outputGraphics.setComposite(AlphaComposite.Src);
            outputGraphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            );
            outputGraphics.drawImage(compact, 0, 0, output.getWidth(), output.getHeight(), null);
        } finally {
            outputGraphics.dispose();
        }
        Files.createDirectories(target.getParent());
        if (!ImageIO.write(output, "png", target.toFile())) {
            throw new IllegalStateException("No PNG writer is available");
        }
        System.out.println("Ender Chest background: " + output.getWidth() + "x" + output.getHeight() + " -> " + target);
    }
}
