import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Queue;

/** One-time JVM asset normalization tool; not included in the addon JAR. */
public final class ThemeAssetProcessor {
    private ThemeAssetProcessor() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: ThemeAssetProcessor <background> <atlas> <output-theme-dir>");
        }
        Path output = Paths.get(args[2]);
        Files.createDirectories(output.resolve("items"));
        writeBackground(Paths.get(args[0]), output.resolve("background.png"));
        writeIcons(Paths.get(args[1]), output.resolve("items"));
    }

    private static void writeBackground(Path sourcePath, Path outputPath) throws IOException {
        BufferedImage source = read(sourcePath);
        BufferedImage target = new BufferedImage(704, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, 704, 600, null);
        } finally {
            graphics.dispose();
        }
        write(target, outputPath);
    }

    private static void writeIcons(Path atlasPath, Path outputDirectory) throws IOException {
        String[] names = {
            "stone", "diamond", "diamond_sword", "diamond_pickaxe",
            "golden_apple", "firework_rocket", "bread", "unknown"
        };
        BufferedImage atlas = read(atlasPath);
        for (int index = 0; index < names.length; index++) {
            int column = index % 4;
            int row = index / 4;
            int left = (int) Math.round(column * atlas.getWidth() / 4.0d);
            int right = (int) Math.round((column + 1) * atlas.getWidth() / 4.0d);
            int top = (int) Math.round(row * atlas.getHeight() / 2.0d);
            int bottom = (int) Math.round((row + 1) * atlas.getHeight() / 2.0d);
            BufferedImage cell = copy(atlas.getSubimage(left, top, right - left, bottom - top));
            removeConnectedCheckerboard(cell);
            Rectangle bounds = opaqueBounds(cell);
            if (bounds.isEmpty()) throw new IllegalStateException("No icon pixels found for " + names[index]);

            BufferedImage icon = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = icon.createGraphics();
            try {
                graphics.setComposite(AlphaComposite.SrcOver);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                double scale = Math.min(56.0d / bounds.width, 56.0d / bounds.height);
                int width = Math.max(1, (int) Math.round(bounds.width * scale));
                int height = Math.max(1, (int) Math.round(bounds.height * scale));
                int x = (64 - width) / 2;
                int y = (64 - height) / 2;
                graphics.drawImage(
                    cell,
                    x,
                    y,
                    x + width,
                    y + height,
                    bounds.x,
                    bounds.y,
                    bounds.x + bounds.width,
                    bounds.y + bounds.height,
                    null
                );
            } finally {
                graphics.dispose();
            }
            write(icon, outputDirectory.resolve(names[index] + ".png"));
        }
    }

    private static void removeConnectedCheckerboard(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[][] visited = new boolean[width][height];
        Queue<Point> queue = new ArrayDeque<Point>();
        for (int x = 0; x < width; x++) {
            tryQueue(image, x, 0, visited, queue);
            tryQueue(image, x, height - 1, visited, queue);
        }
        for (int y = 0; y < height; y++) {
            tryQueue(image, 0, y, visited, queue);
            tryQueue(image, width - 1, y, visited, queue);
        }
        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dy = {-1, -1, -1, 0, 0, 1, 1, 1};
        while (!queue.isEmpty()) {
            Point point = queue.remove();
            image.setRGB(point.x, point.y, 0);
            for (int index = 0; index < dx.length; index++) {
                tryQueue(image, point.x + dx[index], point.y + dy[index], visited, queue);
            }
        }
    }

    private static void tryQueue(
        BufferedImage image,
        int x,
        int y,
        boolean[][] visited,
        Queue<Point> queue
    ) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight() || visited[x][y]) return;
        visited[x][y] = true;
        Color color = new Color(image.getRGB(x, y), true);
        int max = Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()));
        int min = Math.min(color.getRed(), Math.min(color.getGreen(), color.getBlue()));
        if (min >= 215 && max - min <= 14) queue.add(new Point(x, y));
    }

    private static Rectangle opaqueBounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) continue;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        return maxX < minX ? new Rectangle() : new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    private static BufferedImage read(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new IOException("Unreadable PNG: " + path);
        return image;
    }

    private static void write(BufferedImage image, Path path) throws IOException {
        if (!ImageIO.write(image, "png", path.toFile())) throw new IOException("No PNG writer for " + path);
    }
}
