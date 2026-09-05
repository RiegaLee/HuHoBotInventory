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
import javax.imageio.ImageIO;

/**
 * Offline Minecraft 26.1.2 plain-shield GUI baker.
 *
 * Geometry follows ShieldModel.createLayer(), the texture is Faithful's unmodified
 * shield_base_nopattern.png, and the projection follows the shield GUI ItemTransform. The
 * generated PNG is a bounded static fallback: banner base colors and patterns remain deferred.
 */
public final class ClientEquivalentShieldOverrideGenerator {
    private static final int OUTPUT_SIZE = 64;
    private static final double MODEL_SIZE = 16.0;
    private static final double ROTATION_X = Math.toRadians(15.0);
    private static final double ROTATION_Y = Math.toRadians(-25.0);
    private static final double ROTATION_Z = Math.toRadians(-5.0);
    private static final double TRANSLATION_X = 2.0 / 16.0;
    // A static PNG cannot render beyond its own slot like the live client model can. Keep the
    // Faithful/client geometry, angle and scale, but vertically center the complete projection.
    private static final double TRANSLATION_Y = 23.0 / 1024.0;
    private static final double GUI_SCALE = 0.65;

    // shield.json declares gui_light=front, so GuiItemAtlas selects ITEMS_FLAT.
    private static final Vec3 LIGHT_0 = new Vec3(-0.222518998, -0.171498595, 0.959725756);
    private static final Vec3 LIGHT_1 = new Vec3(-0.215012132, -0.971825317, 0.096567776);

    private ClientEquivalentShieldOverrideGenerator() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                "Usage: ClientEquivalentShieldOverrideGenerator <shield_base_nopattern.png> " +
                    "<historical-shield.png> <evidence-directory>"
            );
        }
        Path texturePath = Paths.get(args[0]).toAbsolutePath().normalize();
        Path historicalPath = Paths.get(args[1]).toAbsolutePath().normalize();
        Path evidence = Paths.get(args[2]).toAbsolutePath().normalize();
        BufferedImage texture = readPng(texturePath);
        BufferedImage historical = readPng(historicalPath);
        Files.createDirectories(evidence);

        BufferedImage generated = generate(texture);
        ImageIO.write(generated, "png", evidence.resolve("shield-client-equivalent-64.png").toFile());
        writeComparison(evidence.resolve("shield-historical-vs-faithful.png"), historical, generated);
        writeReport(evidence.resolve("shield-client-equivalent.txt"), texturePath, historicalPath,
            texture, historical, generated);
    }

    public static BufferedImage generate(BufferedImage texture) {
        if (texture.getWidth() != texture.getHeight() || texture.getWidth() % 64 != 0) {
            throw new IllegalArgumentException("Shield entity texture must be a square multiple of 64 pixels");
        }
        return render(clientGeometry(texture, texture.getWidth() / 64), OUTPUT_SIZE);
    }

    private static BufferedImage readPng(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new IllegalArgumentException("Unreadable PNG: " + path);
        return image;
    }

    private static List<Face> clientGeometry(BufferedImage texture, int textureScale) {
        List<Face> faces = new ArrayList<Face>();
        // ShieldModel.createLayer(): plate texOffs(0,0), addBox(-6,-11,-2,12,22,1).
        addCuboid(faces, "PLATE", texture, textureScale,
            -6, -11, -2, 6, 11, -1, 0, 0, 12, 22, 1);
        // handle texOffs(26,0), addBox(-1,-3,-1,2,6,6).
        addCuboid(faces, "HANDLE", texture, textureScale,
            -1, -3, -1, 1, 3, 5, 26, 0, 2, 6, 6);
        return faces;
    }

    private static void addCuboid(
        List<Face> faces, String part, BufferedImage texture, int textureScale,
        double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
        int u, int v, int width, int height, int depth
    ) {
        faces.add(new Face(part, texture,
            rect(textureScale, u + depth + width + depth, v + depth, width, height),
            vertices(maxX,minY,maxZ, minX,minY,maxZ, minX,maxY,maxZ, maxX,maxY,maxZ),
            new Vec3(0,0,1)));
        faces.add(new Face(part, texture,
            rect(textureScale, u + depth, v + depth, width, height),
            vertices(minX,minY,minZ, maxX,minY,minZ, maxX,maxY,minZ, minX,maxY,minZ),
            new Vec3(0,0,-1)));
        faces.add(new Face(part, texture,
            rect(textureScale, u, v + depth, depth, height),
            vertices(minX,minY,maxZ, minX,minY,minZ, minX,maxY,minZ, minX,maxY,maxZ),
            new Vec3(-1,0,0)));
        faces.add(new Face(part, texture,
            rect(textureScale, u + depth + width, v + depth, depth, height),
            vertices(maxX,minY,minZ, maxX,minY,maxZ, maxX,maxY,maxZ, maxX,maxY,minZ),
            new Vec3(1,0,0)));
        faces.add(new Face(part, texture,
            rect(textureScale, u + depth + width, v, width, depth),
            vertices(minX,maxY,minZ, maxX,maxY,minZ, maxX,maxY,maxZ, minX,maxY,maxZ),
            new Vec3(0,1,0)));
        faces.add(new Face(part, texture,
            rect(textureScale, u + depth, v, width, depth),
            vertices(minX,minY,maxZ, maxX,minY,maxZ, maxX,minY,minZ, minX,minY,minZ),
            new Vec3(0,-1,0)));
    }

    private static BufferedImage render(List<Face> geometry, int size) {
        List<ProjectedFace> visible = projectVisible(geometry, size);
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            for (ProjectedFace face : visible) drawFace(graphics, face);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static List<ProjectedFace> projectVisible(List<Face> geometry, int size) {
        List<ProjectedFace> visible = new ArrayList<ProjectedFace>();
        for (Face face : geometry) {
            Vec3 normal = transformNormal(face.normal);
            if (normal.z <= 0.000001) continue;
            Point2[] points = new Point2[4];
            double depth = 0.0;
            for (int i=0; i<4; i++) {
                Vec3 point = transformPosition(face.vertices[i]);
                points[i] = new Point2(size * 0.5 + point.x * size,
                    size * 0.5 - point.y * size);
                depth += point.z;
            }
            visible.add(new ProjectedFace(face, points, depth / 4.0, clientLight(normal)));
        }
        Collections.sort(visible, new Comparator<ProjectedFace>() {
            @Override public int compare(ProjectedFace left, ProjectedFace right) {
                return Double.compare(left.depth, right.depth);
            }
        });
        return visible;
    }

    private static Vec3 transformPosition(Vec3 modelPixels) {
        // ShieldSpecialRenderer.DEFAULT_TRANSFORMATION has scale [1,-1,-1].
        Vec3 special = new Vec3(
            modelPixels.x / MODEL_SIZE,
            -modelPixels.y / MODEL_SIZE,
            -modelPixels.z / MODEL_SIZE
        ).scale(GUI_SCALE);
        Vec3 rotated = rotate(special);
        return new Vec3(rotated.x + TRANSLATION_X, rotated.y + TRANSLATION_Y, rotated.z);
    }

    private static Vec3 transformNormal(Vec3 normal) {
        return rotate(new Vec3(normal.x, -normal.y, -normal.z)).normalize();
    }

    // Quaternionf.rotationXYZ is applied to a vector as Z, then Y, then X.
    private static Vec3 rotate(Vec3 value) {
        double zx = value.x * Math.cos(ROTATION_Z) - value.y * Math.sin(ROTATION_Z);
        double zy = value.x * Math.sin(ROTATION_Z) + value.y * Math.cos(ROTATION_Z);
        Vec3 z = new Vec3(zx, zy, value.z);
        double yx = z.x * Math.cos(ROTATION_Y) + z.z * Math.sin(ROTATION_Y);
        double yz = -z.x * Math.sin(ROTATION_Y) + z.z * Math.cos(ROTATION_Y);
        return new Vec3(
            yx,
            z.y * Math.cos(ROTATION_X) - yz * Math.sin(ROTATION_X),
            z.y * Math.sin(ROTATION_X) + yz * Math.cos(ROTATION_X)
        );
    }

    private static double clientLight(Vec3 normal) {
        Vec3 guiNormal = new Vec3(normal.x, -normal.y, normal.z).normalize();
        double light0 = Math.max(0.0, guiNormal.dot(LIGHT_0));
        double light1 = Math.max(0.0, guiNormal.dot(LIGHT_1));
        return Math.min(1.0, (light0 + light1) * 0.6 + 0.4);
    }

    private static void drawFace(Graphics2D destination, ProjectedFace projected) {
        BufferedImage pixels = shaded(projected.face.texture, projected.face.uv, projected.light);
        int[] x = new int[4];
        int[] y = new int[4];
        for (int i=0; i<4; i++) {
            x[i] = (int)Math.round(projected.points[i].x);
            y[i] = (int)Math.round(projected.points[i].y);
        }
        Graphics2D graphics = (Graphics2D)destination.create();
        try {
            graphics.setClip(new Polygon(x,y,4));
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            Point2 topLeft = projected.points[0];
            Point2 topRight = projected.points[1];
            Point2 bottomLeft = projected.points[3];
            graphics.drawImage(pixels, new AffineTransform(
                (topRight.x-topLeft.x)/pixels.getWidth(),
                (topRight.y-topLeft.y)/pixels.getWidth(),
                (bottomLeft.x-topLeft.x)/pixels.getHeight(),
                (bottomLeft.y-topLeft.y)/pixels.getHeight(),
                topLeft.x, topLeft.y
            ), null);
        } finally {
            graphics.dispose();
        }
    }

    private static BufferedImage shaded(BufferedImage source, Rect uv, double light) {
        BufferedImage result = new BufferedImage(uv.width, uv.height, BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<uv.height; y++) for (int x=0; x<uv.width; x++) {
            int argb = source.getRGB(uv.x+x,uv.y+y);
            int alpha=argb>>>24;
            int red=(int)Math.round(((argb>>>16)&255)*light);
            int green=(int)Math.round(((argb>>>8)&255)*light);
            int blue=(int)Math.round((argb&255)*light);
            result.setRGB(x,y,(alpha<<24)|(Math.min(255,red)<<16)|
                (Math.min(255,green)<<8)|Math.min(255,blue));
        }
        return result;
    }

    private static void writeComparison(Path target, BufferedImage historical, BufferedImage generated)
        throws IOException {
        BufferedImage image = new BufferedImage(640,344,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=image.createGraphics();
        try {
            graphics.setColor(new Color(35,39,45));
            graphics.fillRect(0,0,image.getWidth(),image.getHeight());
            graphics.setColor(new Color(68,74,83));
            graphics.fillRect(32,48,256,256);
            graphics.fillRect(352,48,256,256);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(historical,32,48,256,256,null);
            graphics.drawImage(generated,352,48,256,256,null);
            label(graphics,"Historical handmade 32x",48,30);
            label(graphics,"Faithful entity + client model 64x",330,30);
            label(graphics,"replaced",118,330);
            label(graphics,"plain shield; patterns deferred",360,330);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image,"png",target.toFile());
    }

    private static void label(Graphics2D graphics,String value,int x,int y) {
        graphics.setFont(new Font(Font.MONOSPACED,Font.BOLD,14));
        graphics.setColor(Color.WHITE);
        graphics.drawString(value,x,y);
    }

    private static void writeReport(
        Path target, Path texturePath, Path historicalPath, BufferedImage texture,
        BufferedImage historical, BufferedImage generated
    ) throws Exception {
        List<String> lines=new ArrayList<String>();
        lines.add("Minecraft 26.1.2 / Faithful 32x Plain Shield Static Override");
        lines.add("Status: LOCAL_CANDIDATE_ONLY");
        lines.add("");
        lines.add("Texture="+texturePath);
        lines.add("Texture size="+texture.getWidth()+"x"+texture.getHeight());
        lines.add("Texture SHA-256="+sha256(texturePath));
        lines.add("Texture use=unmodified Faithful shield_base_nopattern.png");
        lines.add("Geometry=ShieldModel.createLayer(): plate(-6,-11,-2;12x22x1), handle(-1,-3,-1;2x6x6)");
        lines.add("Special default scale=[1,-1,-1]");
        lines.add("GUI transform=rotation[15,-25,-5], translation[2,3,0], scale[0.65,0.65,0.65]");
        lines.add("Bounded static slot correction=v9 complete vertical center; v8 clipped alpha y=14..63 is replaced");
        lines.add("Projection=fixed orthographic 64x64 slot; nearest-neighbor texture sampling");
        lines.add("Lighting=Minecraft 26.1.2 ITEMS_FLAT two-direction diffuse (gui_light=front)");
        lines.add("");
        lines.add("Historical icon="+historicalPath);
        lines.add("Historical size="+historical.getWidth()+"x"+historical.getHeight());
        lines.add("Historical SHA-256="+sha256(historicalPath));
        lines.add("Generated alpha bbox="+alphaBounds(generated));
        lines.add("Generated visible pixels="+visiblePixels(generated));
        lines.add("Generated SHA-256="+sha256(generated));
        lines.add("Banner patterns/base colors=DEFERRED (Snapshot has no shield banner component data)");
        Files.write(target,lines,StandardCharsets.UTF_8);
    }

    private static String alphaBounds(BufferedImage image) {
        int minX=image.getWidth(),minY=image.getHeight(),maxX=-1,maxY=-1;
        for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
            if((image.getRGB(x,y)>>>24)==0) continue;
            minX=Math.min(minX,x); minY=Math.min(minY,y);
            maxX=Math.max(maxX,x); maxY=Math.max(maxY,y);
        }
        return maxX<0 ? "EMPTY" : minX+","+minY+".."+maxX+","+maxY;
    }

    private static int visiblePixels(BufferedImage image) {
        int count=0;
        for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++)
            if((image.getRGB(x,y)>>>24)!=0) count++;
        return count;
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        byte[] data=Files.readAllBytes(path);
        digest.update(data);
        StringBuilder value=new StringBuilder();
        for(byte next:digest.digest()) value.append(String.format("%02X",next&255));
        return value.toString();
    }

    private static String sha256(BufferedImage image) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
            int argb=image.getRGB(x,y);
            digest.update((byte)(argb>>>24)); digest.update((byte)(argb>>>16));
            digest.update((byte)(argb>>>8)); digest.update((byte)argb);
        }
        StringBuilder value=new StringBuilder();
        for(byte next:digest.digest()) value.append(String.format("%02X",next&255));
        return value.toString();
    }

    private static Rect rect(int scale,int x,int y,int width,int height) {
        return new Rect(x*scale,y*scale,width*scale,height*scale);
    }

    private static Vec3[] vertices(double... values) {
        Vec3[] result=new Vec3[values.length/3];
        for(int i=0;i<result.length;i++) result[i]=new Vec3(values[i*3],values[i*3+1],values[i*3+2]);
        return result;
    }

    private static final class Face {
        final String part; final BufferedImage texture; final Rect uv; final Vec3[] vertices; final Vec3 normal;
        Face(String part,BufferedImage texture,Rect uv,Vec3[] vertices,Vec3 normal) {
            this.part=part; this.texture=texture; this.uv=uv; this.vertices=vertices; this.normal=normal;
        }
    }
    private static final class ProjectedFace {
        final Face face; final Point2[] points; final double depth; final double light;
        ProjectedFace(Face face,Point2[] points,double depth,double light) {
            this.face=face; this.points=points; this.depth=depth; this.light=light;
        }
    }
    private static final class Rect {
        final int x,y,width,height;
        Rect(int x,int y,int width,int height) { this.x=x; this.y=y; this.width=width; this.height=height; }
    }
    private static final class Point2 {
        final double x,y;
        Point2(double x,double y) { this.x=x; this.y=y; }
    }
    private static final class Vec3 {
        final double x,y,z;
        Vec3(double x,double y,double z) { this.x=x; this.y=y; this.z=z; }
        Vec3 scale(double factor) { return new Vec3(x*factor,y*factor,z*factor); }
        double dot(Vec3 other) { return x*other.x+y*other.y+z*other.z; }
        Vec3 normalize() {
            double length=Math.sqrt(x*x+y*y+z*z);
            return length==0 ? this : new Vec3(x/length,y/length,z/length);
        }
    }
}
