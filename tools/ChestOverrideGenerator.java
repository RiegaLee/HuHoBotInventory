import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pre-bakes a static 32x32 chest icon from a local 64x chest entity texture and three cuboids. */
public final class ChestOverrideGenerator {
    private static final int OUTPUT_SIZE = 32;
    // Match the client GUI icon: narrow side on the left, broad front face on the right.
    private static final double YAW = Math.toRadians(30.0);
    private static final double PITCH = Math.toRadians(24.0);

    private ChestOverrideGenerator() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException(
                "Usage: ChestOverrideGenerator <normal.png> <output.png> [geometry-debug-directory]"
            );
        }
        BufferedImage texture = ImageIO.read(Paths.get(args[0]).toFile());
        if (texture == null || texture.getWidth() != texture.getHeight() || texture.getWidth() % 64 != 0) {
            throw new IllegalArgumentException("Chest texture must be a square multiple of 64 pixels");
        }
        int textureScale = texture.getWidth() / 64;
        List<Face> faces = new ArrayList<Face>();

        // Vanilla chest entity proportions: 14x10x14 base, 14x5x14 lid, 2x4x1 front latch.
        addCuboid(faces, "BODY", texture, textureScale, -7, 0, -7, 7, 10, 7, 0, 19, 14, 10, 14);
        addCuboid(faces, "LID", texture, textureScale, -7, 10, -7, 7, 15, 7, 0, 0, 14, 5, 14);
        // The lock belongs to the lid and hangs one unit below its seam. Keeping three units
        // above y=10 avoids the visually low "sticker" position seen in the 1.9.2 QQ render.
        addCuboid(faces, "LATCH", texture, textureScale, -1, 9, 7, 1, 13, 8, 0, 0, 2, 4, 1);

        List<ProjectedFace> visible = new ArrayList<ProjectedFace>();
        for (Face face : faces) {
            ProjectedFace projected = project(face);
            if (projected != null) visible.add(projected);
        }
        Collections.sort(visible, new Comparator<ProjectedFace>() {
            @Override public int compare(ProjectedFace left, ProjectedFace right) {
                return Double.compare(left.depth, right.depth);
            }
        });
        FitResult fit = fit(visible);
        visible = fit.faces;

        BufferedImage output = new BufferedImage(OUTPUT_SIZE, OUTPUT_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            for (ProjectedFace face : visible) drawFace(graphics, face);
        } finally {
            graphics.dispose();
        }

        Path target = Paths.get(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        if (!ImageIO.write(output, "png", target.toFile())) throw new IllegalStateException("PNG writer unavailable");
        if (args.length == 3) writeDiagnostics(Paths.get(args[2]), fit, output);
    }

    private static void addCuboid(
        List<Face> faces, String part, BufferedImage texture, int scale,
        double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
        int u, int v, int width, int height, int depth
    ) {
        faces.add(new Face(part,"FRONT_+Z",texture, rect(scale, u + depth, v + depth, width, height), 1.00,
            vertices(minX,maxY,maxZ, maxX,maxY,maxZ, maxX,minY,maxZ, minX,minY,maxZ), 0,0,1));
        faces.add(new Face(part,"BACK_-Z",texture, rect(scale, u + depth + width + depth, v + depth, width, height), 0.70,
            vertices(maxX,maxY,minZ, minX,maxY,minZ, minX,minY,minZ, maxX,minY,minZ), 0,0,-1));
        faces.add(new Face(part,"LEFT_-X",texture, rect(scale, u, v + depth, depth, height), 0.76,
            vertices(minX,maxY,minZ, minX,maxY,maxZ, minX,minY,maxZ, minX,minY,minZ), -1,0,0));
        faces.add(new Face(part,"RIGHT_+X",texture, rect(scale, u + depth + width, v + depth, depth, height), 0.84,
            vertices(maxX,maxY,maxZ, maxX,maxY,minZ, maxX,minY,minZ, maxX,minY,maxZ), 1,0,0));
        faces.add(new Face(part,"TOP_+Y",texture, rect(scale, u + depth, v, width, depth), 1.00,
            vertices(minX,maxY,minZ, maxX,maxY,minZ, maxX,maxY,maxZ, minX,maxY,maxZ), 0,1,0));
        faces.add(new Face(part,"BOTTOM_-Y",texture, rect(scale, u + depth + width, v, width, depth), 0.68,
            vertices(minX,minY,maxZ, maxX,minY,maxZ, maxX,minY,minZ, minX,minY,minZ), 0,-1,0));
    }

    private static ProjectedFace project(Face face) {
        Vec3 normal = rotate(face.normal);
        if (normal.z <= 0.01) return null;
        Point2[] points = new Point2[4];
        double depth = 0;
        for (int index = 0; index < 4; index++) {
            Vec3 point = rotate(face.vertices[index]);
            points[index] = new Point2(point.x, -point.y);
            depth += point.z;
        }
        return new ProjectedFace(face, points, depth / 4.0);
    }

    private static FitResult fit(List<ProjectedFace> source) {
        double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY;
        double maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
        for (ProjectedFace face : source) for (Point2 point : face.points) {
            minX=Math.min(minX,point.x); maxX=Math.max(maxX,point.x);
            minY=Math.min(minY,point.y); maxY=Math.max(maxY,point.y);
        }
        double scale = Math.min(28.0 / (maxX-minX), 28.0 / (maxY-minY));
        double offsetX = (OUTPUT_SIZE - (maxX-minX)*scale)/2.0 - minX*scale;
        double offsetY = (OUTPUT_SIZE - (maxY-minY)*scale)/2.0 - minY*scale;
        List<ProjectedFace> result = new ArrayList<ProjectedFace>();
        for (ProjectedFace face : source) {
            Point2[] points = new Point2[4];
            for (int index=0;index<4;index++) points[index]=new Point2(
                offsetX+face.points[index].x*scale, offsetY+face.points[index].y*scale
            );
            result.add(new ProjectedFace(face.face,points,face.depth));
        }
        return new FitResult(result, scale, offsetX, offsetY);
    }

    private static void writeDiagnostics(Path directory, FitResult fit, BufferedImage finalIcon) throws Exception {
        Path output = directory.toAbsolutePath().normalize();
        Files.createDirectories(output);
        ImageIO.write(finalIcon, "png", output.resolve("chest-final-32.png").toFile());
        writeFrontDebug(output.resolve("chest-front-debug.png"));
        writeGeometryDebug(output.resolve("chest-geometry-debug.png"), fit);

        Vec3 bodyCenter = new Vec3(0,5,0);
        Vec3 bodyFrontCenter = new Vec3(0,5,7);
        Vec3 frontLatchAnchor = new Vec3(0,11,7);
        Vec3 latchCenter = new Vec3(0,11,7.5);
        Vec3 modelCenter = new Vec3(0,7.5,0);
        List<String> report = new ArrayList<String>();
        report.add("Chest Geometry Diagnostic");
        report.add("Body: min=(-7,0,-7) max=(7,10,7)");
        report.add("Lid: min=(-7,10,-7) max=(7,15,7)");
        report.add("Latch: min=(-1,9,7) max=(1,13,8)");
        report.add("Model origin / yaw-pitch pivot: (0,0,0)");
        report.add("Geometry bounds center: (0,7.5,0)");
        report.add("FRONT: +Z; body front plane z=7; latch attachment plane z=7; latch front plane z=8");
        report.add("Transform: shared yaw=+30deg, pitch=+24deg, orthographic x/-y, shared depth sort and fit");
        report.add("No final-PNG latch positioning or screen-space offset exists");
        report.add("");
        report.add(pointLine("bodyCenter", bodyCenter, fit));
        report.add(pointLine("bodyFrontFaceGeometricCenter", bodyFrontCenter, fit));
        report.add(pointLine("frontFaceLatchAnchor", frontLatchAnchor, fit));
        report.add(pointLine("latchCenter", latchCenter, fit));
        report.add(pointLine("modelCenter", modelCenter, fit));
        Point2 front = fit.project(frontLatchAnchor);
        Point2 latch = fit.project(latchCenter);
        report.add("finalIconCenter=(16.0000,16.0000)");
        report.add(String.format(Locale.ROOT,
            "delta latch-vs-frontAnchor=(%.4f,%.4f)", latch.x-front.x, latch.y-front.y));
        report.add(String.format(Locale.ROOT,
            "delta latch-vs-iconCenter=(%.4f,%.4f)", latch.x-16.0, latch.y-16.0));
        Files.write(output.resolve("chest-projection.txt"), report, StandardCharsets.UTF_8);
    }

    private static String pointLine(String label, Vec3 local, FitResult fit) {
        Point2 raw = rawProject(local);
        Point2 projected = fit.project(local);
        return String.format(Locale.ROOT,
            "%s local=(%.4f,%.4f,%.4f) raw=(%.4f,%.4f) fitted=(%.4f,%.4f)",
            label,local.x,local.y,local.z,raw.x,raw.y,projected.x,projected.y);
    }

    private static Point2 rawProject(Vec3 local) {
        Vec3 rotated = rotate(local);
        return new Point2(rotated.x, -rotated.y);
    }

    private static void writeFrontDebug(Path target) throws Exception {
        BufferedImage image = new BufferedImage(512,512,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(35,39,45)); graphics.fillRect(0,0,512,512);
            int left=64, width=384, bottom=448;
            double scale=24.0;
            graphics.setColor(new Color(125,125,125));
            graphics.fillRect(left,bottom-(int)(10*scale),width,(int)(10*scale));
            graphics.setColor(new Color(70,130,210));
            graphics.fillRect(left,bottom-(int)(15*scale),width,(int)(5*scale));
            graphics.setColor(new Color(230,70,70));
            graphics.setStroke(new BasicStroke(6)); graphics.drawRect(left,bottom-(int)(15*scale),width,(int)(15*scale));
            graphics.setColor(new Color(255,215,40));
            int latchX=256-(int)scale, latchY=bottom-(int)(13*scale);
            graphics.fillRect(latchX,latchY,(int)(2*scale),(int)(4*scale));
            graphics.setColor(Color.BLACK); cross(graphics,256,bottom-(int)(7.5*scale),12);
            graphics.setColor(new Color(255,0,255)); cross(graphics,256,bottom-(int)(11*scale),16);
            graphics.setFont(new Font(Font.MONOSPACED,Font.BOLD,16));
            graphics.setColor(Color.WHITE); graphics.drawString("FRONT +Z (local X/Y)",16,24);
            graphics.drawString("black=model center; magenta=front anchor/latch center XY",16,50);
            graphics.drawString("anchor z=7; latch center z=7.5",16,74);
        } finally { graphics.dispose(); }
        ImageIO.write(image,"png",target.toFile());
    }

    private static void writeGeometryDebug(Path target, FitResult fit) throws Exception {
        BufferedImage image = new BufferedImage(512,512,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(35,39,45)); graphics.fillRect(0,0,512,512);
            for (ProjectedFace face : fit.faces) {
                int[] x=new int[4],y=new int[4];
                for(int i=0;i<4;i++){x[i]=(int)Math.round(face.points[i].x*16);y[i]=(int)Math.round(face.points[i].y*16);}
                graphics.setColor(debugColor(face.face)); graphics.fillPolygon(x,y,4);
                graphics.setColor(new Color(20,20,20)); graphics.setStroke(new BasicStroke(3)); graphics.drawPolygon(x,y,4);
            }
            Point2 model=fit.project(new Vec3(0,7.5,0));
            Point2 front=fit.project(new Vec3(0,11,7));
            Point2 latch=fit.project(new Vec3(0,11,7.5));
            graphics.setColor(Color.BLACK); cross(graphics,(int)(model.x*16),(int)(model.y*16),12);
            graphics.setColor(new Color(255,0,255)); cross(graphics,(int)(front.x*16),(int)(front.y*16),15);
            graphics.setColor(new Color(255,255,0)); cross(graphics,(int)(latch.x*16),(int)(latch.y*16),8);
            graphics.setFont(new Font(Font.MONOSPACED,Font.BOLD,16));
            graphics.setColor(Color.WHITE); graphics.drawString("gray=body blue=lid red=body front green=side yellow=latch",8,22);
            graphics.drawString("black=model center magenta=front anchor yellow=latch center",8,46);
        } finally { graphics.dispose(); }
        ImageIO.write(image,"png",target.toFile());
    }

    private static Color debugColor(Face face) {
        if ("LATCH".equals(face.part)) return new Color(255,215,40);
        if ("LID".equals(face.part)) return new Color(70,130,210);
        if ("FRONT_+Z".equals(face.name)) return new Color(225,70,70);
        if (face.name.contains("X")) return new Color(70,180,95);
        return new Color(125,125,125);
    }

    private static void cross(Graphics2D graphics,int x,int y,int radius) {
        graphics.setStroke(new BasicStroke(4));
        graphics.drawLine(x-radius,y,x+radius,y); graphics.drawLine(x,y-radius,x,y+radius);
    }

    private static Vec3 rotate(Vec3 value) {
        double x=value.x*Math.cos(YAW)+value.z*Math.sin(YAW);
        double z=-value.x*Math.sin(YAW)+value.z*Math.cos(YAW);
        return new Vec3(x,value.y*Math.cos(PITCH)-z*Math.sin(PITCH),value.y*Math.sin(PITCH)+z*Math.cos(PITCH));
    }

    private static void drawFace(Graphics2D destination, ProjectedFace projected) {
        BufferedImage pixels = shaded(projected.face.texture, projected.face.uv, projected.face.shade);
        int[] x=new int[4],y=new int[4];
        for(int i=0;i<4;i++){x[i]=(int)Math.round(projected.points[i].x);y[i]=(int)Math.round(projected.points[i].y);}
        Graphics2D graphics=(Graphics2D)destination.create();
        try {
            graphics.setClip(new Polygon(x,y,4));
            Point2 tl=projected.points[0],tr=projected.points[1],bl=projected.points[3];
            graphics.drawImage(pixels,new AffineTransform(
                (tr.x-tl.x)/pixels.getWidth(),(tr.y-tl.y)/pixels.getWidth(),
                (bl.x-tl.x)/pixels.getHeight(),(bl.y-tl.y)/pixels.getHeight(),tl.x,tl.y
            ),null);
        } finally { graphics.dispose(); }
    }

    private static BufferedImage shaded(BufferedImage source, Rect area, double shade) {
        BufferedImage result=new BufferedImage(area.width,area.height,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<area.height;y++)for(int x=0;x<area.width;x++){
            int argb=source.getRGB(area.x+x,area.y+y),a=argb>>>24;
            int r=(int)Math.round(((argb>>>16)&255)*shade),g=(int)Math.round(((argb>>>8)&255)*shade);
            int b=(int)Math.round((argb&255)*shade);
            result.setRGB(x,y,(a<<24)|(Math.min(255,r)<<16)|(Math.min(255,g)<<8)|Math.min(255,b));
        }
        return result;
    }

    private static Rect rect(int scale,int x,int y,int width,int height){return new Rect(x*scale,y*scale,width*scale,height*scale);}
    private static Vec3[] vertices(double...v){Vec3[]r=new Vec3[4];for(int i=0;i<4;i++)r[i]=new Vec3(v[i*3],v[i*3+1],v[i*3+2]);return r;}
    private static final class Face{final String part,name;final BufferedImage texture;final Rect uv;final double shade;final Vec3[]vertices;final Vec3 normal;Face(String p,String n,BufferedImage t,Rect u,double s,Vec3[]v,double x,double y,double z){part=p;name=n;texture=t;uv=u;shade=s;vertices=v;normal=new Vec3(x,y,z);}}
    private static final class ProjectedFace{final Face face;final Point2[]points;final double depth;ProjectedFace(Face f,Point2[]p,double d){face=f;points=p;depth=d;}}
    private static final class FitResult{final List<ProjectedFace>faces;final double scale,offsetX,offsetY;FitResult(List<ProjectedFace>f,double s,double x,double y){faces=f;scale=s;offsetX=x;offsetY=y;}Point2 project(Vec3 local){Point2 raw=rawProject(local);return new Point2(offsetX+raw.x*scale,offsetY+raw.y*scale);}}
    private static final class Vec3{final double x,y,z;Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}}
    private static final class Point2{final double x,y;Point2(double x,double y){this.x=x;this.y=y;}}
    private static final class Rect{final int x,y,width,height;Rect(int x,int y,int w,int h){this.x=x;this.y=y;width=w;height=h;}}
}
