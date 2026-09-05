import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Offline-only Minecraft 26.1.2 single-chest GUI baker.
 *
 * This intentionally does not share the approximate 1.9.4 ChestOverrideGenerator. Geometry comes
 * from ChestModel.createSingleBodyLayer(), transformation comes from template_chest display.gui,
 * and projection uses a fixed GUI slot. No visible-alpha bbox fit or screen-space latch offset exists.
 */
public final class ClientEquivalentChestOverrideGenerator {
    private static final int OUTPUT_SIZE = 64;
    private static final double MODEL_SIZE = 16.0;
    private static final double ROTATION_X = Math.toRadians(30.0);
    private static final double ROTATION_Y = Math.toRadians(45.0);
    private static final double GUI_SCALE = 0.625;

    // Minecraft 26.1.2 Lighting.Entry.ITEMS_3D directions, calculated from the local client class.
    private static final Vec3 LIGHT_0 = new Vec3(-0.933439195, -0.262694716, -0.244300157);
    private static final Vec3 LIGHT_1 = new Vec3(-0.103571370, -0.976606786, 0.188446417);
    private static final double LIGHT_POWER = 0.6;
    private static final double AMBIENT_LIGHT = 0.4;

    private ClientEquivalentChestOverrideGenerator() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                "Usage: ClientEquivalentChestOverrideGenerator <entity/chest/normal.png> " +
                    "<current-1.9.4-chest.png> <evidence-directory>"
            );
        }

        Path texturePath = Paths.get(args[0]).toAbsolutePath().normalize();
        Path currentPath = Paths.get(args[1]).toAbsolutePath().normalize();
        Path output = Paths.get(args[2]).toAbsolutePath().normalize();
        BufferedImage texture = readPng(texturePath);
        BufferedImage current = readPng(currentPath);
        if (texture.getWidth() != texture.getHeight() || texture.getWidth() % 64 != 0) {
            throw new IllegalArgumentException("Chest entity texture must be a square multiple of 64 pixels");
        }
        Files.createDirectories(output);

        List<Face> geometry = clientGeometry(texture, texture.getWidth() / 64);
        BufferedImage final64 = renderTextured(geometry, OUTPUT_SIZE);

        writeGeometry(output.resolve("01-geometry.png"));
        writeWireframe(output.resolve("02-transformed-wireframe.png"), geometry);
        ImageIO.write(renderTextured(geometry, 512), "png", output.resolve("03-textured-pre-slot.png").toFile());
        ImageIO.write(final64, "png", output.resolve("04-final-64.png").toFile());
        writeComparison(output.resolve("05-1.9.4-vs-client-equivalent.png"), current, final64);
        writeReport(
            output.resolve("chest-client-equivalent.txt"), texturePath, currentPath, texture,
            current, final64, geometry
        );
    }

    private static BufferedImage readPng(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new IllegalArgumentException("Unreadable PNG: " + path);
        return image;
    }

    private static List<Face> clientGeometry(BufferedImage texture, int textureScale) {
        List<Face> faces = new ArrayList<Face>();

        // ChestModel.createSingleBodyLayer(): texOffs(0,19), addBox(1,0,1,14,10,14), ZERO.
        addCuboid(faces, "BODY", texture, textureScale, 1, 0, 1, 15, 10, 15, 0, 19, 14, 10, 14);

        // texOffs(0,0), addBox(1,0,0,14,5,14), PartPose.offset(0,9,1).
        addCuboid(faces, "LID", texture, textureScale, 1, 9, 1, 15, 14, 15, 0, 0, 14, 5, 14);

        // texOffs(0,0), addBox(7,-2,14,2,4,1), PartPose.offset(0,9,1).
        addCuboid(faces, "LOCK", texture, textureScale, 7, 7, 15, 9, 11, 16, 0, 0, 2, 4, 1);
        return faces;
    }

    private static void addCuboid(
        List<Face> faces, String part, BufferedImage texture, int textureScale,
        double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
        int u, int v, int width, int height, int depth
    ) {
        // ModelPart.Cube's unfolded entity UV layout is not the conventional shorthand used by
        // the old baker. SOUTH (+Z) starts after WEST, NORTH, and EAST; NORTH (-Z) is the first
        // width-wide side. Keeping those directions distinct is essential for chest textures.
        faces.add(new Face(part, "FRONT_+Z", texture,
            rect(textureScale, u + depth + width + depth, v + depth, width, height),
            // Polygon UV mapping: source TL/TR/BR/BL -> Cube vertices 1/0/3/2.
            vertices(maxX,minY,maxZ, minX,minY,maxZ, minX,maxY,maxZ, maxX,maxY,maxZ),
            new Vec3(0,0,1)));
        faces.add(new Face(part, "BACK_-Z", texture,
            rect(textureScale, u + depth, v + depth, width, height),
            vertices(minX,minY,minZ, maxX,minY,minZ, maxX,maxY,minZ, minX,maxY,minZ),
            new Vec3(0,0,-1)));
        faces.add(new Face(part, "LEFT_-X", texture,
            rect(textureScale, u, v + depth, depth, height),
            vertices(minX,minY,maxZ, minX,minY,minZ, minX,maxY,minZ, minX,maxY,maxZ),
            new Vec3(-1,0,0)));
        faces.add(new Face(part, "RIGHT_+X", texture,
            rect(textureScale, u + depth + width, v + depth, depth, height),
            vertices(maxX,minY,minZ, maxX,minY,maxZ, maxX,maxY,maxZ, maxX,maxY,minZ),
            new Vec3(1,0,0)));
        faces.add(new Face(part, "TOP_+Y", texture,
            rect(textureScale, u + depth + width, v, width, depth),
            vertices(minX,maxY,minZ, maxX,maxY,minZ, maxX,maxY,maxZ, minX,maxY,maxZ),
            new Vec3(0,1,0)));
        faces.add(new Face(part, "BOTTOM_-Y", texture,
            rect(textureScale, u + depth, v, width, depth),
            vertices(minX,minY,maxZ, maxX,minY,maxZ, maxX,minY,minZ, minX,minY,minZ),
            new Vec3(0,-1,0)));
    }

    private static BufferedImage renderTextured(List<Face> geometry, int slotSize) {
        List<ProjectedFace> visible = projectVisible(geometry, slotSize);
        BufferedImage image = new BufferedImage(slotSize, slotSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            );
            for (ProjectedFace face : visible) drawTexturedFace(graphics, face);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static List<ProjectedFace> projectVisible(List<Face> geometry, int slotSize) {
        List<ProjectedFace> visible = new ArrayList<ProjectedFace>();
        for (Face face : geometry) {
            Vec3 rotatedNormal = rotate(face.normal);
            if (rotatedNormal.z <= 0.000001) continue;
            Point2[] points = new Point2[4];
            double depth = 0.0;
            for (int i = 0; i < 4; i++) {
                Vec3 transformed = transformPosition(face.vertices[i]);
                points[i] = new Point2(
                    slotSize * 0.5 + transformed.x * slotSize,
                    slotSize * 0.5 - transformed.y * slotSize
                );
                depth += transformed.z;
            }
            visible.add(new ProjectedFace(face, points, depth / 4.0, clientLight(face.normal)));
        }
        Collections.sort(visible, new Comparator<ProjectedFace>() {
            @Override public int compare(ProjectedFace left, ProjectedFace right) {
                return Double.compare(left.depth, right.depth);
            }
        });
        return visible;
    }

    private static Vec3 transformPosition(Vec3 modelPixels) {
        Vec3 centered = new Vec3(
            modelPixels.x / MODEL_SIZE - 0.5,
            modelPixels.y / MODEL_SIZE - 0.5,
            modelPixels.z / MODEL_SIZE - 0.5
        ).scale(GUI_SCALE);
        return rotate(centered);
    }

    // Quaternionf.rotationXYZ(30deg,45deg,0) is equivalent here to Y followed by X.
    private static Vec3 rotate(Vec3 value) {
        double x = value.x * Math.cos(ROTATION_Y) + value.z * Math.sin(ROTATION_Y);
        double z = -value.x * Math.sin(ROTATION_Y) + value.z * Math.cos(ROTATION_Y);
        return new Vec3(
            x,
            value.y * Math.cos(ROTATION_X) - z * Math.sin(ROTATION_X),
            value.y * Math.sin(ROTATION_X) + z * Math.cos(ROTATION_X)
        );
    }

    private static double clientLight(Vec3 localNormal) {
        Vec3 transformed = rotate(localNormal);
        // GuiItemAtlas applies scale(+slot,-slot,+slot); PoseStack applies the sign to normal Y.
        Vec3 guiNormal = new Vec3(transformed.x, -transformed.y, transformed.z).normalize();
        double light0 = Math.max(0.0, guiNormal.dot(LIGHT_0));
        double light1 = Math.max(0.0, guiNormal.dot(LIGHT_1));
        return Math.min(1.0, (light0 + light1) * LIGHT_POWER + AMBIENT_LIGHT);
    }

    private static void drawTexturedFace(Graphics2D destination, ProjectedFace projected) {
        BufferedImage pixels = shaded(projected.face.texture, projected.face.uv, projected.light);
        int[] x = new int[4];
        int[] y = new int[4];
        for (int i = 0; i < 4; i++) {
            x[i] = (int) Math.round(projected.points[i].x);
            y[i] = (int) Math.round(projected.points[i].y);
        }
        Graphics2D graphics = (Graphics2D) destination.create();
        try {
            graphics.setClip(new Polygon(x, y, 4));
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            );
            Point2 topLeft = projected.points[0];
            Point2 topRight = projected.points[1];
            Point2 bottomLeft = projected.points[3];
            graphics.drawImage(pixels, new AffineTransform(
                (topRight.x - topLeft.x) / pixels.getWidth(),
                (topRight.y - topLeft.y) / pixels.getWidth(),
                (bottomLeft.x - topLeft.x) / pixels.getHeight(),
                (bottomLeft.y - topLeft.y) / pixels.getHeight(),
                topLeft.x, topLeft.y
            ), null);
        } finally {
            graphics.dispose();
        }
    }

    private static BufferedImage shaded(BufferedImage source, Rect area, double light) {
        BufferedImage result = new BufferedImage(area.width, area.height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < area.height; y++) {
            for (int x = 0; x < area.width; x++) {
                int argb = source.getRGB(area.x + x, area.y + y);
                int alpha = argb >>> 24;
                int red = (int) Math.round(((argb >>> 16) & 255) * light);
                int green = (int) Math.round(((argb >>> 8) & 255) * light);
                int blue = (int) Math.round((argb & 255) * light);
                result.setRGB(x, y,
                    (alpha << 24) | (Math.min(255, red) << 16) |
                        (Math.min(255, green) << 8) | Math.min(255, blue));
            }
        }
        return result;
    }

    private static void writeGeometry(Path target) throws IOException {
        BufferedImage image = debugCanvas();
        Graphics2D graphics = image.createGraphics();
        try {
            int scale = 24;
            int left = 64;
            int bottom = 456;
            graphics.setColor(new Color(125,125,125));
            graphics.fillRect(left + scale, bottom - 10 * scale, 14 * scale, 10 * scale);
            graphics.setColor(new Color(65,125,210));
            graphics.fillRect(left + scale, bottom - 14 * scale, 14 * scale, 5 * scale);
            graphics.setColor(new Color(255,210,35));
            graphics.fillRect(left + 7 * scale, bottom - 11 * scale, 2 * scale, 4 * scale);
            graphics.setColor(new Color(230,70,70));
            graphics.setStroke(new BasicStroke(5));
            graphics.drawRect(left + scale, bottom - 14 * scale, 14 * scale, 14 * scale);
            label(graphics, "CLIENT ChestModel front (+Z)", 16, 24);
            label(graphics, "Body x=1..15 y=0..10 z=1..15", 16, 50);
            label(graphics, "Lid  x=1..15 y=9..14 z=1..15", 16, 74);
            label(graphics, "Lock x=7..9  y=7..11 z=15..16", 16, 98);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", target.toFile());
    }

    private static void writeWireframe(Path target, List<Face> geometry) throws IOException {
        BufferedImage image = debugCanvas();
        Graphics2D graphics = image.createGraphics();
        try {
            List<ProjectedFace> faces = projectVisible(geometry, 512);
            for (ProjectedFace face : faces) {
                int[] x = new int[4];
                int[] y = new int[4];
                for (int i = 0; i < 4; i++) {
                    x[i] = (int) Math.round(face.points[i].x);
                    y[i] = (int) Math.round(face.points[i].y);
                }
                Color base = partColor(face.face.part);
                graphics.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 150));
                graphics.fillPolygon(x, y, 4);
                graphics.setColor(new Color(235,235,235));
                graphics.setStroke(new BasicStroke(3));
                graphics.drawPolygon(x, y, 4);
            }
            label(graphics, "rotationXYZ [30,45,0] / scale 0.625 / fixed 64px slot", 10, 24);
            label(graphics, "gray=Body blue=Lid yellow=Lock; no bbox fit", 10, 50);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", target.toFile());
    }

    private static BufferedImage debugCanvas() {
        BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(35,39,45));
        graphics.fillRect(0,0,512,512);
        graphics.dispose();
        return image;
    }

    private static Color partColor(String part) {
        if ("LOCK".equals(part)) return new Color(255,210,35);
        if ("LID".equals(part)) return new Color(65,125,210);
        return new Color(125,125,125);
    }

    private static void label(Graphics2D graphics, String value, int x, int y) {
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
        graphics.setColor(Color.WHITE);
        graphics.drawString(value, x, y);
    }

    private static void writeComparison(
        Path target, BufferedImage current, BufferedImage clientEquivalent
    ) throws IOException {
        BufferedImage image = new BufferedImage(640, 344, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(35,39,45));
            graphics.fillRect(0,0,image.getWidth(),image.getHeight());
            label(graphics, "Inventory 1.9.4", 78, 30);
            label(graphics, "Client-equivalent candidate", 348, 30);
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            );
            graphics.setColor(new Color(68,74,83));
            graphics.fillRect(32,48,256,256);
            graphics.fillRect(352,48,256,256);
            graphics.drawImage(current, 32,48,256,256,null);
            graphics.drawImage(clientEquivalent,352,48,256,256,null);
            label(graphics, "approximate 28px bbox fit", 48, 330);
            label(graphics, "fixed 0.625 GUI slot", 376, 330);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", target.toFile());
    }

    private static void writeReport(
        Path target, Path texturePath, Path currentPath, BufferedImage texture,
        BufferedImage current, BufferedImage final64, List<Face> geometry
    ) throws Exception {
        List<String> report = new ArrayList<String>();
        report.add("Minecraft 26.1.2 Client-Equivalent Chest Static Override Candidate");
        report.add("Status: LOCAL_CANDIDATE_ONLY; minecraft:chest remains Visual Audit FAIL");
        report.add("");
        report.add("Geometry source: ChestModel.createSingleBodyLayer()");
        report.add("Body: x=1..15 y=0..10 z=1..15; texOffs=(0,19); size=14x10x14");
        report.add("Lid: x=1..15 y=9..14 z=1..15; texOffs=(0,0); size=14x5x14");
        report.add("Lock: x=7..9 y=7..11 z=15..16; texOffs=(0,0); size=2x4x1");
        report.add("Openness: 0; Lid/Lock xRot=0");
        report.add("");
        report.add("ItemTransform rotationXYZ=[30,45,0]");
        report.add("ItemTransform translation=[0,0,0]");
        report.add("ItemTransform scale=[0.625,0.625,0.625]");
        report.add("Item model center translation=[-0.5,-0.5,-0.5]");
        report.add("Projection: fixed orthographic 64x64 GUI slot; center=(32,32); y axis inverted");
        report.add("Visible-alpha bbox fit: DISABLED");
        report.add("Post-projection recenter: DISABLED");
        report.add("Screen-space Lock offset: NONE");
        report.add("");
        report.add("Lighting: Minecraft 26.1.2 ITEMS_3D two-direction diffuse");
        report.add("Light0=(-0.933439195,-0.262694716,-0.244300157)");
        report.add("Light1=(-0.103571370,-0.976606786,0.188446417)");
        report.add("light=min(1,(max(0,dot0)+max(0,dot1))*0.6+0.4)");
        report.add("");
        report.add("Texture=" + texturePath);
        report.add("Texture size=" + texture.getWidth() + "x" + texture.getHeight());
        report.add("Texture SHA-256=" + sha256(texturePath));
        report.add("Current 1.9.4=" + currentPath);
        report.add("Current 1.9.4 SHA-256=" + sha256(currentPath));
        report.add("");
        report.add("Projected Body bbox=" + partBounds(geometry, "BODY", OUTPUT_SIZE));
        report.add("Projected Lid bbox=" + partBounds(geometry, "LID", OUTPUT_SIZE));
        report.add("Projected Lock bbox=" + partBounds(geometry, "LOCK", OUTPUT_SIZE));
        report.add("Current 1.9.4 alpha bbox=" + alphaBounds(current));
        report.add("Client-equivalent final alpha bbox=" + alphaBounds(final64));
        report.add("Client-equivalent PNG SHA-256=" + sha256(final64));
        Files.write(target, report, StandardCharsets.UTF_8);
    }

    private static String partBounds(List<Face> geometry, String part, int slotSize) {
        Bounds bounds = new Bounds();
        for (ProjectedFace face : projectVisible(geometry, slotSize)) {
            if (!part.equals(face.face.part)) continue;
            for (Point2 point : face.points) bounds.include(point.x, point.y);
        }
        return bounds.decimal();
    }

    private static String alphaBounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x,y) >>> 24) == 0) continue;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < minX) return "EMPTY";
        return String.format(Locale.ROOT, "x=%d..%d y=%d..%d size=%dx%d",
            minX,maxX,minY,maxY,maxX-minX+1,maxY-minY+1);
    }

    private static String sha256(Path file) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private static String sha256(BufferedImage image) throws Exception {
        Path temporary = Files.createTempFile("client-equivalent-chest-", ".png");
        try {
            ImageIO.write(image, "png", temporary.toFile());
            return sha256(temporary);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder();
        for (byte part : value) result.append(String.format(Locale.ROOT, "%02X", part & 255));
        return result.toString();
    }

    private static Rect rect(int scale, int x, int y, int width, int height) {
        return new Rect(x * scale, y * scale, width * scale, height * scale);
    }

    private static Vec3[] vertices(double... values) {
        Vec3[] result = new Vec3[4];
        for (int i = 0; i < 4; i++) {
            result[i] = new Vec3(values[i*3], values[i*3+1], values[i*3+2]);
        }
        return result;
    }

    private static final class Face {
        final String part;
        final String name;
        final BufferedImage texture;
        final Rect uv;
        final Vec3[] vertices;
        final Vec3 normal;
        Face(String part, String name, BufferedImage texture, Rect uv, Vec3[] vertices, Vec3 normal) {
            this.part=part; this.name=name; this.texture=texture; this.uv=uv;
            this.vertices=vertices; this.normal=normal;
        }
    }

    private static final class ProjectedFace {
        final Face face;
        final Point2[] points;
        final double depth;
        final double light;
        ProjectedFace(Face face, Point2[] points, double depth, double light) {
            this.face=face; this.points=points; this.depth=depth; this.light=light;
        }
    }

    private static final class Rect {
        final int x,y,width,height;
        Rect(int x,int y,int width,int height) {
            this.x=x; this.y=y; this.width=width; this.height=height;
        }
    }

    private static final class Point2 {
        final double x,y;
        Point2(double x,double y) { this.x=x; this.y=y; }
    }

    private static final class Vec3 {
        final double x,y,z;
        Vec3(double x,double y,double z) { this.x=x; this.y=y; this.z=z; }
        Vec3 scale(double factor) { return new Vec3(x*factor,y*factor,z*factor); }
        Vec3 normalize() {
            double length=Math.sqrt(x*x+y*y+z*z);
            return length == 0 ? this : new Vec3(x/length,y/length,z/length);
        }
        double dot(Vec3 other) { return x*other.x+y*other.y+z*other.z; }
    }

    private static final class Bounds {
        double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY;
        double maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
        void include(double x,double y) {
            minX=Math.min(minX,x); minY=Math.min(minY,y);
            maxX=Math.max(maxX,x); maxY=Math.max(maxY,y);
        }
        String decimal() {
            return String.format(Locale.ROOT,"x=%.4f..%.4f y=%.4f..%.4f size=%.4fx%.4f",
                minX,maxX,minY,maxY,maxX-minX,maxY-minY);
        }
    }
}
