import cn.huohuas001.huhobot.inventory.head.PlayerHeadIconCache;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Rebuilds RC21 special icons from the pinned Faithful 32x Java 1.21.11 textures. */
public final class GenerateFaithfulRc21Icons {
    private static final int ICON_SIZE = 64;

    private GenerateFaithfulRc21Icons() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: <Faithful textures> <theme directory>");
        Path textures = Path.of(args[0]);
        Path output = Path.of(args[1]).resolve("special-variants/minecraft");
        Files.createDirectories(output);
        copy(textures, output, "item/clock_00.png", "clock.png");
        copy(textures, output, "item/compass_00.png", "compass.png");
        copy(textures, output, "item/recovery_compass_00.png", "recovery_compass.png");
        renderSkull(textures, output, "entity/creeper/creeper.png", "creeper_head.png");
        renderSkull(textures, output, "entity/skeleton/skeleton.png", "skeleton_skull.png");
        renderSkull(textures, output, "entity/skeleton/wither_skeleton.png", "wither_skeleton_skull.png");
        renderSkull(textures, output, "entity/zombie/zombie.png", "zombie_head.png");
        renderPiglin(textures, output);
        renderDragon(textures, output);
        BufferedImage steve = read(textures.resolve("entity/player/wide/steve.png"));
        ImageIO.write(PlayerHeadIconCache.renderHeadItem(steve), "PNG", output.resolve("player_head.png").toFile());
    }

    private static void copy(Path textures, Path output, String source, String target) throws IOException {
        Files.copy(textures.resolve(source), output.resolve(target), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void renderSkull(Path textures, Path output, String source, String target) throws IOException {
        BufferedImage texture = read(textures.resolve(source));
        BufferedImage baseOnly = new BufferedImage(texture.getWidth(), texture.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = baseOnly.createGraphics();
        try {
            // Mob atlases use the standard base-head UVs but do not have a player hat layer.
            // Copy only the 0..32 x 0..16 vanilla-unit region and leave the false overlay area transparent.
            int atlasScale = texture.getWidth() / 64;
            graphics.drawImage(texture, 0, 0, 32 * atlasScale, 16 * atlasScale,
                0, 0, 32 * atlasScale, 16 * atlasScale, null);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(PlayerHeadIconCache.renderHeadItem(baseOnly), "PNG", output.resolve(target).toFile());
    }

    private static void renderPiglin(Path textures, Path output) throws IOException {
        BufferedImage texture = read(textures.resolve("entity/piglin/piglin.png"));
        List<Cuboid> boxes = new ArrayList<>();
        boxes.add(Cuboid.box(-5, -8, -4, 10, 8, 8, 0, 0));
        boxes.add(Cuboid.box(-2, -4, -5, 4, 4, 1, 31, 1));
        boxes.add(Cuboid.box(2, -2, -5, 1, 2, 1, 2, 4));
        boxes.add(Cuboid.box(-3, -2, -5, 1, 2, 1, 2, 0));
        boxes.add(Cuboid.rotated(-1, 0, -2, 1, 5, 4, 39, 6, -4.5, -6, 0, 0, 0, Math.toRadians(30)));
        boxes.add(Cuboid.rotated(0, 0, -2, 1, 5, 4, 51, 6, 4.5, -6, 0, 0, 0, Math.toRadians(-30)));
        ImageIO.write(renderModel(texture, 64, boxes), "PNG", output.resolve("piglin_head.png").toFile());
    }

    private static void renderDragon(Path textures, Path output) throws IOException {
        BufferedImage texture = read(textures.resolve("entity/enderdragon/dragon.png"));
        List<Cuboid> boxes = new ArrayList<>();
        boxes.add(Cuboid.box(-6, -1, -24, 12, 5, 16, 176, 44));
        boxes.add(Cuboid.box(-8, -8, -10, 16, 16, 16, 112, 30));
        boxes.add(Cuboid.box(-5, -12, -4, 2, 4, 6, 0, 0));
        boxes.add(Cuboid.box(3, -12, -4, 2, 4, 6, 0, 0));
        boxes.add(Cuboid.box(-5, -3, -22, 2, 2, 4, 112, 0));
        boxes.add(Cuboid.box(3, -3, -22, 2, 2, 4, 112, 0));
        // Vanilla jaw: child pivot (0,4,-8), box (-6,0,-16), slightly open at progress zero.
        boxes.add(Cuboid.rotated(-6, 0, -16, 12, 4, 16, 176, 65, 0, 4, -8, 0.20, 0, 0));
        ImageIO.write(renderModel(texture, 256, boxes), "PNG", output.resolve("dragon_head.png").toFile());
    }

    private static BufferedImage read(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new IOException("Not a PNG: " + path);
        return image;
    }

    private static BufferedImage renderModel(BufferedImage texture, int atlasWidth, List<Cuboid> cuboids) {
        int textureScale = texture.getWidth() / atlasWidth;
        if (textureScale < 1 || texture.getWidth() % atlasWidth != 0) throw new IllegalArgumentException("Bad atlas size");
        List<Face> faces = new ArrayList<>();
        List<Point> points = new ArrayList<>();
        for (Cuboid cuboid : cuboids) {
            cuboid.faces(faces);
            for (Vec vertex : cuboid.vertices) points.add(project(vertex));
        }
        double minX = points.stream().mapToDouble(Point::x).min().orElse(0);
        double maxX = points.stream().mapToDouble(Point::x).max().orElse(1);
        double minY = points.stream().mapToDouble(Point::y).min().orElse(0);
        double maxY = points.stream().mapToDouble(Point::y).max().orElse(1);
        double scale = Math.min(58.0 / (maxX - minX), 58.0 / (maxY - minY));
        double offsetX = (ICON_SIZE - (maxX - minX) * scale) / 2.0 - minX * scale;
        double offsetY = (ICON_SIZE - (maxY - minY) * scale) / 2.0 - minY * scale;
        faces.sort(Comparator.comparingDouble(Face::depth));

        BufferedImage icon = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = icon.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            for (Face face : faces) drawFace(graphics, texture, textureScale, face, scale, offsetX, offsetY);
        } finally {
            graphics.dispose();
        }
        return icon;
    }

    private static void drawFace(Graphics2D out, BufferedImage atlas, int textureScale, Face face,
                                 double scale, double offsetX, double offsetY) {
        Point p0 = screen(face.a, scale, offsetX, offsetY);
        Point p1 = screen(face.b, scale, offsetX, offsetY);
        Point p3 = screen(face.d, scale, offsetX, offsetY);
        int sx = face.u * textureScale, sy = face.v * textureScale;
        int sw = face.textureWidth * textureScale, sh = face.textureHeight * textureScale;
        if (sx < 0 || sy < 0 || sx + sw > atlas.getWidth() || sy + sh > atlas.getHeight()) return;
        BufferedImage source = atlas.getSubimage(sx, sy, sw, sh);
        BufferedImage layer = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = layer.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(source, new AffineTransform(
                (p1.x - p0.x) / sw, (p1.y - p0.y) / sw,
                (p3.x - p0.x) / sh, (p3.y - p0.y) / sh, p0.x, p0.y), null);
            if (face.shade > 0) {
                graphics.setComposite(AlphaComposite.SrcAtop);
                graphics.setColor(new Color(0f, 0f, 0f, face.shade));
                graphics.fillRect(0, 0, ICON_SIZE, ICON_SIZE);
            }
        } finally {
            graphics.dispose();
        }
        out.drawImage(layer, 0, 0, null);
    }

    private static Point screen(Vec vertex, double scale, double offsetX, double offsetY) {
        Point point = project(vertex);
        return new Point(point.x * scale + offsetX, point.y * scale + offsetY, point.depth);
    }

    private static Point project(Vec vertex) {
        // Same upright GUI basis used by PlayerHeadIconCache: front remains readable while
        // top and right planes show depth. This avoids the mathematically valid but visually
        // wrong diagonal "thin plate" produced by the former camera projection.
        // Render the actual opposite GUI view instead of horizontally mirroring a completed
        // texture. Mirroring reverses UVs and moves rear/side details onto the facial seam.
        double x = 3.125 * vertex.x - 3.0 * vertex.z;
        double y = -1.5 * vertex.x + 3.5 * vertex.y - 1.5 * vertex.z;
        double depth = -vertex.z - vertex.x - vertex.y;
        return new Point(x, y, depth);
    }

    private record Vec(double x, double y, double z) {}
    private record Point(double x, double y, double depth) {}
    private record Face(Vec a, Vec b, Vec c, Vec d, int u, int v, int textureWidth, int textureHeight, float shade) {
        double depth() { return (project(a).depth + project(b).depth + project(c).depth + project(d).depth) / 4.0; }
    }

    private static final class Cuboid {
        private final Vec[] vertices;
        private final int dx, dy, dz, u, v;

        private Cuboid(Vec[] vertices, int dx, int dy, int dz, int u, int v) {
            this.vertices = vertices; this.dx = dx; this.dy = dy; this.dz = dz; this.u = u; this.v = v;
        }

        static Cuboid box(double x, double y, double z, int dx, int dy, int dz, int u, int v) {
            return rotated(x, y, z, dx, dy, dz, u, v, 0, 0, 0, 0, 0, 0);
        }

        static Cuboid rotated(double x, double y, double z, int dx, int dy, int dz, int u, int v,
                              double pivotX, double pivotY, double pivotZ,
                              double rotationX, double rotationY, double rotationZ) {
            Vec[] points = {
                new Vec(x,y,z), new Vec(x+dx,y,z), new Vec(x+dx,y+dy,z), new Vec(x,y+dy,z),
                new Vec(x,y,z+dz), new Vec(x+dx,y,z+dz), new Vec(x+dx,y+dy,z+dz), new Vec(x,y+dy,z+dz)
            };
            for (int i = 0; i < points.length; i++) points[i] = transform(points[i], pivotX, pivotY, pivotZ, rotationX, rotationY, rotationZ);
            return new Cuboid(points, dx, dy, dz, u, v);
        }

        private static Vec transform(Vec value, double px, double py, double pz, double rx, double ry, double rz) {
            double x=value.x, y=value.y, z=value.z;
            double ny=y*Math.cos(rx)-z*Math.sin(rx); z=y*Math.sin(rx)+z*Math.cos(rx); y=ny;
            double nx=x*Math.cos(ry)+z*Math.sin(ry); z=-x*Math.sin(ry)+z*Math.cos(ry); x=nx;
            nx=x*Math.cos(rz)-y*Math.sin(rz); y=x*Math.sin(rz)+y*Math.cos(rz); x=nx;
            return new Vec(x+px,y+py,z+pz);
        }

        void faces(List<Face> faces) {
            faces.add(new Face(vertices[0],vertices[1],vertices[2],vertices[3],u+dz,v+dz,dx,dy,0.02f));
            // The GUI view exposes the model's left face. Use its own UV strip instead of
            // drawing the right face and reflecting the finished image.
            faces.add(new Face(vertices[4],vertices[0],vertices[3],vertices[7],u,v+dz,dz,dy,0.16f));
            faces.add(new Face(vertices[4],vertices[5],vertices[1],vertices[0],u+dz,v,dx,dz,0.00f));
        }
    }
}
