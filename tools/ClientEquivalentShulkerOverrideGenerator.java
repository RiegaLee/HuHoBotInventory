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

/** Offline-only Minecraft 26.1.2 closed Shulker Box GUI baker. */
public final class ClientEquivalentShulkerOverrideGenerator {
    private static final int OUTPUT_SIZE = 64;
    private static final double ROTATION_X = Math.toRadians(30.0);
    private static final double ROTATION_Y = Math.toRadians(45.0);
    private static final double GUI_SCALE = 0.625;
    private static final double WRAPPER_SCALE = 0.9995;
    private static final Vec3 LIGHT_0 = new Vec3(-0.933439195, -0.262694716, -0.244300157);
    private static final Vec3 LIGHT_1 = new Vec3(-0.103571370, -0.976606786, 0.188446417);

    private ClientEquivalentShulkerOverrideGenerator() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException(
                "Usage: ClientEquivalentShulkerOverrideGenerator <entity/shulker.png> " +
                    "<output-64.png> [evidence-directory]"
            );
        }
        Path texturePath = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputPath = Paths.get(args[1]).toAbsolutePath().normalize();
        Path evidence = args.length == 3 ? Paths.get(args[2]).toAbsolutePath().normalize() : null;
        BufferedImage texture = readPng(texturePath);
        if (texture.getWidth() != texture.getHeight() || texture.getWidth() % 64 != 0) {
            throw new IllegalArgumentException("Shulker entity texture must be a square multiple of 64 pixels");
        }
        List<Face> geometry = clientGeometry(texture, texture.getWidth() / 64);
        BufferedImage final64 = renderTextured(geometry, OUTPUT_SIZE);
        Files.createDirectories(outputPath.getParent());
        ImageIO.write(final64, "png", outputPath.toFile());
        if (evidence != null) {
            Files.createDirectories(evidence);
            writeGeometry(evidence.resolve("01-geometry.png"));
            writeWireframe(evidence.resolve("02-transformed-wireframe.png"), geometry);
            ImageIO.write(renderTextured(geometry, 512), "png", evidence.resolve("03-textured-pre-slot.png").toFile());
            ImageIO.write(final64, "png", evidence.resolve("04-final-64.png").toFile());
            writeReport(evidence.resolve("shulker-client-equivalent.txt"), texturePath, final64, geometry);
        }
    }

    private static BufferedImage readPng(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new IllegalArgumentException("Unreadable PNG: " + path);
        return image;
    }

    private static List<Face> clientGeometry(BufferedImage texture, int textureScale) {
        List<Face> faces = new ArrayList<Face>();
        // ShulkerModel.createShellMesh(): lid addBox(-8,-16,-8,16,12,16), offset(0,24,0).
        addWrappedCuboid(faces, "LID", texture, textureScale, -8, 8, -8, 8, 20, 8, 0, 0, 16, 12, 16);
        // base addBox(-8,-8,-8,16,8,16), offset(0,24,0).
        addWrappedCuboid(faces, "BASE", texture, textureScale, -8, 16, -8, 8, 24, 8, 0, 28, 16, 8, 16);
        return faces;
    }

    private static void addWrappedCuboid(
        List<Face> faces, String part, BufferedImage texture, int scale,
        double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
        int u, int v, int width, int height, int depth
    ) {
        addFace(faces, part, texture, rect(scale, u + depth + width + depth, v + depth, width, height),
            vertices(maxX,minY,maxZ, minX,minY,maxZ, minX,maxY,maxZ, maxX,maxY,maxZ), new Vec3(0,0,1));
        addFace(faces, part, texture, rect(scale, u + depth, v + depth, width, height),
            vertices(minX,minY,minZ, maxX,minY,minZ, maxX,maxY,minZ, minX,maxY,minZ), new Vec3(0,0,-1));
        addFace(faces, part, texture, rect(scale, u, v + depth, depth, height),
            vertices(minX,minY,maxZ, minX,minY,minZ, minX,maxY,minZ, minX,maxY,maxZ), new Vec3(-1,0,0));
        addFace(faces, part, texture, rect(scale, u + depth + width, v + depth, depth, height),
            vertices(maxX,minY,minZ, maxX,minY,maxZ, maxX,maxY,maxZ, maxX,maxY,minZ), new Vec3(1,0,0));
        addFace(faces, part, texture, rect(scale, u + depth + width, v, width, depth),
            vertices(minX,maxY,minZ, maxX,maxY,minZ, maxX,maxY,maxZ, minX,maxY,maxZ), new Vec3(0,1,0));
        addFace(faces, part, texture, rect(scale, u + depth, v, width, depth),
            vertices(minX,minY,maxZ, maxX,minY,maxZ, maxX,minY,minZ, minX,minY,minZ), new Vec3(0,-1,0));
    }

    private static void addFace(
        List<Face> faces, String part, BufferedImage texture, Rect uv, Vec3[] modelVertices, Vec3 normal
    ) {
        Vec3[] wrapped = new Vec3[4];
        for (int index = 0; index < wrapped.length; index++) wrapped[index] = wrapper(modelVertices[index]);
        // Item JSON wrapper quaternion [1,0,0,0] is a 180-degree X rotation.
        faces.add(new Face(part, texture, uv, wrapped, new Vec3(normal.x, -normal.y, -normal.z)));
    }

    private static Vec3 wrapper(Vec3 pixels) {
        return new Vec3(
            0.5 + pixels.x / 16.0 * WRAPPER_SCALE,
            1.4995 - pixels.y / 16.0 * WRAPPER_SCALE,
            0.5 - pixels.z / 16.0 * WRAPPER_SCALE
        );
    }

    private static BufferedImage renderTextured(List<Face> geometry, int slotSize) {
        List<ProjectedFace> visible = projectVisible(geometry, slotSize);
        BufferedImage image = new BufferedImage(slotSize, slotSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
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
            double depth = 0;
            for (int index = 0; index < 4; index++) {
                Vec3 transformed = transformPosition(face.vertices[index]);
                points[index] = new Point2(
                    slotSize * .5 + transformed.x * slotSize,
                    slotSize * .5 - transformed.y * slotSize
                );
                depth += transformed.z;
            }
            visible.add(new ProjectedFace(face, points, depth / 4.0, clientLight(face.normal)));
        }
        Collections.sort(visible, Comparator.comparingDouble(face -> face.depth));
        return visible;
    }

    private static Vec3 transformPosition(Vec3 normalizedModelPosition) {
        return rotate(new Vec3(
            normalizedModelPosition.x - .5,
            normalizedModelPosition.y - .5,
            normalizedModelPosition.z - .5
        ).scale(GUI_SCALE));
    }

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
        Vec3 guiNormal = new Vec3(transformed.x, -transformed.y, transformed.z).normalize();
        double light0 = Math.max(0, guiNormal.dot(LIGHT_0));
        double light1 = Math.max(0, guiNormal.dot(LIGHT_1));
        return Math.min(1, (light0 + light1) * .6 + .4);
    }

    private static void drawTexturedFace(Graphics2D destination, ProjectedFace projected) {
        BufferedImage pixels = shaded(projected.face.texture, projected.face.uv, projected.light);
        int[] x = new int[4];
        int[] y = new int[4];
        for (int index = 0; index < 4; index++) {
            x[index] = (int) Math.round(projected.points[index].x);
            y[index] = (int) Math.round(projected.points[index].y);
        }
        Graphics2D graphics = (Graphics2D) destination.create();
        try {
            graphics.setClip(new Polygon(x, y, 4));
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
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
        for (int y = 0; y < area.height; y++) for (int x = 0; x < area.width; x++) {
            int argb = source.getRGB(area.x + x, area.y + y);
            int alpha = argb >>> 24;
            int red = Math.min(255, (int) Math.round(((argb >>> 16) & 255) * light));
            int green = Math.min(255, (int) Math.round(((argb >>> 8) & 255) * light));
            int blue = Math.min(255, (int) Math.round((argb & 255) * light));
            result.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
        }
        return result;
    }

    private static void writeGeometry(Path target) throws IOException {
        BufferedImage image = debugCanvas();
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(105, 116, 135));
            graphics.fillRect(96, 264, 320, 160);
            graphics.setColor(new Color(130, 82, 162, 210));
            graphics.fillRect(96, 104, 320, 240);
            graphics.setColor(Color.WHITE);
            graphics.setStroke(new BasicStroke(4));
            graphics.drawRect(96, 104, 320, 320);
            label(graphics, "CLIENT ShulkerBox closed geometry", 18, 30);
            label(graphics, "Base: x=0..16 y=0..8 z=0..16", 18, 58);
            label(graphics, "Lid:  x=0..16 y=4..16 z=0..16", 18, 84);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", target.toFile());
    }

    private static void writeWireframe(Path target, List<Face> geometry) throws IOException {
        BufferedImage image = debugCanvas();
        Graphics2D graphics = image.createGraphics();
        try {
            for (ProjectedFace face : projectVisible(geometry, 512)) {
                int[] x = new int[4];
                int[] y = new int[4];
                for (int index = 0; index < 4; index++) {
                    x[index] = (int) Math.round(face.points[index].x);
                    y[index] = (int) Math.round(face.points[index].y);
                }
                Color color = "LID".equals(face.face.part) ? new Color(130,82,162,150) : new Color(105,116,135,170);
                graphics.setColor(color);
                graphics.fillPolygon(x, y, 4);
                graphics.setColor(Color.WHITE);
                graphics.setStroke(new BasicStroke(3));
                graphics.drawPolygon(x, y, 4);
            }
            label(graphics, "rotationXYZ [30,45,0] / scale .625 / fixed slot", 10, 26);
            label(graphics, "closed openness=0; purple=Lid gray=Base", 10, 52);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", target.toFile());
    }

    private static void writeReport(Path target, Path texture, BufferedImage final64, List<Face> geometry)
        throws Exception {
        List<String> lines = new ArrayList<String>();
        lines.add("Minecraft 26.1.2 Client-Equivalent Closed Shulker Static Candidate");
        lines.add("Geometry: ShulkerModel.createBoxLayer/createShellMesh");
        lines.add("Lid raw addBox=(-8,-16,-8,16,12,16), offset=(0,24,0), texOffs=(0,0)");
        lines.add("Base raw addBox=(-8,-8,-8,16,8,16), offset=(0,24,0), texOffs=(0,28)");
        lines.add("Wrapper: quaternion=[1,0,0,0], translation=[.5,1.4995,.5], scale=[.9995,.9995,.9995]");
        lines.add("Openness=0; lid yRot=0; no ItemStack argument");
        lines.add("GUI rotationXYZ=[30,45,0], scale=[.625,.625,.625], fixed 64x64 slot");
        lines.add("Visible bbox fit=DISABLED; post projection recenter=DISABLED; extra tint=NONE");
        lines.add("Texture=" + texture);
        lines.add("Texture SHA-256=" + sha256(texture));
        lines.add("Projected Base bbox=" + partBounds(geometry, "BASE", OUTPUT_SIZE));
        lines.add("Projected Lid bbox=" + partBounds(geometry, "LID", OUTPUT_SIZE));
        lines.add("Final alpha bbox=" + alphaBounds(final64));
        Files.write(target, lines, StandardCharsets.UTF_8);
    }

    private static String partBounds(List<Face> geometry, String part, int slotSize) {
        Bounds bounds = new Bounds();
        for (ProjectedFace face : projectVisible(geometry, slotSize)) if (part.equals(face.face.part)) {
            for (Point2 point : face.points) bounds.include(point.x, point.y);
        }
        return bounds.decimal();
    }

    private static String alphaBounds(BufferedImage image) {
        int minX=image.getWidth(), minY=image.getHeight(), maxX=-1, maxY=-1;
        for (int y=0; y<image.getHeight(); y++) for (int x=0; x<image.getWidth(); x++) {
            if ((image.getRGB(x,y) >>> 24) == 0) continue;
            minX=Math.min(minX,x); minY=Math.min(minY,y); maxX=Math.max(maxX,x); maxY=Math.max(maxY,y);
        }
        return maxX < minX ? "EMPTY" : String.format(Locale.ROOT, "x=%d..%d y=%d..%d size=%dx%d",
            minX,maxX,minY,maxY,maxX-minX+1,maxY-minY+1);
    }

    private static BufferedImage debugCanvas() {
        BufferedImage image = new BufferedImage(512,512,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(35,39,45));
        graphics.fillRect(0,0,512,512);
        graphics.dispose();
        return image;
    }

    private static void label(Graphics2D graphics, String value, int x, int y) {
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 15));
        graphics.setColor(Color.WHITE);
        graphics.drawString(value, x, y);
    }

    private static String sha256(Path file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
        StringBuilder result = new StringBuilder();
        for (byte part : digest) result.append(String.format(Locale.ROOT, "%02X", part & 255));
        return result.toString();
    }

    private static Rect rect(int scale, int x, int y, int width, int height) {
        return new Rect(x*scale,y*scale,width*scale,height*scale);
    }

    private static Vec3[] vertices(double... values) {
        Vec3[] result = new Vec3[4];
        for (int index=0; index<4; index++) result[index] = new Vec3(values[index*3],values[index*3+1],values[index*3+2]);
        return result;
    }

    private static final class Face {
        final String part; final BufferedImage texture; final Rect uv; final Vec3[] vertices; final Vec3 normal;
        Face(String part, BufferedImage texture, Rect uv, Vec3[] vertices, Vec3 normal) {
            this.part=part; this.texture=texture; this.uv=uv; this.vertices=vertices; this.normal=normal;
        }
    }
    private static final class ProjectedFace {
        final Face face; final Point2[] points; final double depth; final double light;
        ProjectedFace(Face face, Point2[] points, double depth, double light) {
            this.face=face; this.points=points; this.depth=depth; this.light=light;
        }
    }
    private static final class Rect {
        final int x,y,width,height;
        Rect(int x,int y,int width,int height){this.x=x;this.y=y;this.width=width;this.height=height;}
    }
    private static final class Point2 {
        final double x,y;
        Point2(double x,double y){this.x=x;this.y=y;}
    }
    private static final class Vec3 {
        final double x,y,z;
        Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        Vec3 scale(double value){return new Vec3(x*value,y*value,z*value);}
        double dot(Vec3 other){return x*other.x+y*other.y+z*other.z;}
        Vec3 normalize(){double length=Math.sqrt(x*x+y*y+z*z);return new Vec3(x/length,y/length,z/length);}
    }
    private static final class Bounds {
        double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
        void include(double x,double y){minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);}
        String decimal(){return String.format(Locale.ROOT,"x=%.2f..%.2f y=%.2f..%.2f",minX,maxX,minY,maxY);}
    }
}
