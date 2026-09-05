import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Offline-only Minecraft 26.1.2 two-piece Bed GUI baker. */
public final class ClientEquivalentBedOverrideGenerator {
    private static final int OUTPUT_SIZE = 64;
    private static final double GUI_ROTATION_X = Math.toRadians(30.0);
    private static final double GUI_ROTATION_Y = Math.toRadians(160.0);
    private static final double GUI_SCALE = 0.5325;
    private static final double GUI_TRANSLATE_X = 2.0 / 16.0;
    private static final double GUI_TRANSLATE_Y = 3.0 / 16.0;
    private static final double NODE_SCALE = 0.9999999;
    private static final Vec3 LIGHT_0 = new Vec3(-0.933439195, -0.262694716, -0.244300157);
    private static final Vec3 LIGHT_1 = new Vec3(-0.103571370, -0.976606786, 0.188446417);
    private static final List<String> COLORS = Arrays.asList(
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    );

    private ClientEquivalentBedOverrideGenerator() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException(
                "Usage: ClientEquivalentBedOverrideGenerator <26.1.2-client.jar> " +
                    "<override-output-directory> [evidence-directory]"
            );
        }
        Path clientJar = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputDirectory = Paths.get(args[1]).toAbsolutePath().normalize();
        Path evidence = args.length == 3 ? Paths.get(args[2]).toAbsolutePath().normalize() : null;
        Files.createDirectories(outputDirectory);
        if (evidence != null) Files.createDirectories(evidence);

        List<Generated> generated = new ArrayList<Generated>();
        try (ZipFile archive = new ZipFile(clientJar.toFile())) {
            for (String color : COLORS) {
                String entryName = "assets/minecraft/textures/entity/bed/" + color + ".png";
                BufferedImage texture = readPng(archive, entryName);
                if (texture.getWidth() != 64 || texture.getHeight() != 64) {
                    throw new IllegalArgumentException(entryName + " must be exactly 64x64");
                }
                BufferedImage icon = renderTextured(clientGeometry(texture), OUTPUT_SIZE);
                Path target = outputDirectory.resolve(color + "_bed.png");
                ImageIO.write(icon, "png", target.toFile());
                generated.add(new Generated(color, entryName, sha256(archive, entryName), target, icon));
            }
        }

        if (evidence != null) {
            writeContactSheet(evidence.resolve("bed-family-client-equivalent-contact-sheet.png"), generated);
            BufferedImage white = generated.get(0).image;
            ImageIO.write(scaleNearest(white, 512), "png", evidence.resolve("white-bed-final-64-upscaled.png").toFile());
            writeReport(evidence.resolve("bed-client-equivalent.txt"), clientJar, generated);
        }
    }

    private static BufferedImage readPng(ZipFile archive, String entryName) throws Exception {
        ZipEntry entry = archive.getEntry(entryName);
        if (entry == null) throw new IllegalArgumentException("Missing client entry: " + entryName);
        try (InputStream input = archive.getInputStream(entry)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) throw new IllegalArgumentException("Unreadable PNG: " + entryName);
            return image;
        }
    }

    private static List<Face> clientGeometry(BufferedImage texture) {
        List<Face> faces = new ArrayList<Face>();
        Set<Direction> headMainFaces = EnumSet.allOf(Direction.class);
        headMainFaces.remove(Direction.UP);
        Set<Direction> footMainFaces = EnumSet.allOf(Direction.class);
        footMainFaces.remove(Direction.DOWN);
        Set<Direction> legFaces = EnumSet.allOf(Direction.class);
        legFaces.remove(Direction.DOWN);

        // BedRenderer.createHeadLayer().
        addCuboid(faces, "HEAD_MAIN", texture, 0, 0, 0, 0, 0, 16, 16, 6,
            headMainFaces, 0, 0, 0, Node.HEAD);
        addCuboid(faces, "HEAD_LEFT_LEG", texture, 50, 6, 0, 6, 0, 3, 3, 3,
            legFaces, Math.PI / 2.0, 0, Math.PI / 2.0, Node.HEAD);
        addCuboid(faces, "HEAD_RIGHT_LEG", texture, 50, 18, -16, 6, 0, 3, 3, 3,
            legFaces, Math.PI / 2.0, 0, Math.PI, Node.HEAD);

        // BedRenderer.createFootLayer().
        addCuboid(faces, "FOOT_MAIN", texture, 0, 22, 0, 0, 0, 16, 16, 6,
            footMainFaces, 0, 0, 0, Node.FOOT);
        addCuboid(faces, "FOOT_LEFT_LEG", texture, 50, 0, 0, 6, -16, 3, 3, 3,
            legFaces, Math.PI / 2.0, 0, 0, Node.FOOT);
        addCuboid(faces, "FOOT_RIGHT_LEG", texture, 50, 12, -16, 6, -16, 3, 3, 3,
            legFaces, Math.PI / 2.0, 0, Math.PI * 1.5, Node.FOOT);
        return faces;
    }

    private static void addCuboid(
        List<Face> faces, String part, BufferedImage texture, int u, int v,
        double x, double y, double z, double width, double height, double depth,
        Set<Direction> visible, double partX, double partY, double partZ, Node node
    ) {
        double maxX=x+width,maxY=y+height,maxZ=z+depth;
        addIf(faces, visible, Direction.SOUTH, part, texture, rect(u+depth+width+depth,v+depth,width,height),
            vertices(maxX,y,maxZ, x,y,maxZ, x,maxY,maxZ, maxX,maxY,maxZ), partX,partY,partZ,node);
        addIf(faces, visible, Direction.NORTH, part, texture, rect(u+depth,v+depth,width,height),
            vertices(x,y,z, maxX,y,z, maxX,maxY,z, x,maxY,z), partX,partY,partZ,node);
        addIf(faces, visible, Direction.WEST, part, texture, rect(u,v+depth,depth,height),
            vertices(x,y,maxZ, x,y,z, x,maxY,z, x,maxY,maxZ), partX,partY,partZ,node);
        addIf(faces, visible, Direction.EAST, part, texture, rect(u+depth+width,v+depth,depth,height),
            vertices(maxX,y,z, maxX,y,maxZ, maxX,maxY,maxZ, maxX,maxY,z), partX,partY,partZ,node);
        addIf(faces, visible, Direction.UP, part, texture, rect(u+depth+width,v,width,depth),
            vertices(x,maxY,z, maxX,maxY,z, maxX,maxY,maxZ, x,maxY,maxZ), partX,partY,partZ,node);
        addIf(faces, visible, Direction.DOWN, part, texture, rect(u+depth,v,width,depth),
            vertices(x,y,maxZ, maxX,y,maxZ, maxX,y,z, x,y,z), partX,partY,partZ,node);
    }

    private static void addIf(
        List<Face> faces, Set<Direction> visible, Direction direction, String part,
        BufferedImage texture, Rect uv, Vec3[] vertices,
        double partX, double partY, double partZ, Node node
    ) {
        if (!visible.contains(direction)) return;
        Vec3[] transformed = new Vec3[4];
        for (int index=0; index<4; index++) {
            transformed[index] = nodeTransform(rotateZYX(vertices[index],partX,partY,partZ).scale(1.0/16.0), node);
        }
        Vec3 normal = nodeRotate(rotateZYX(direction.normal,partX,partY,partZ));
        faces.add(new Face(part,texture,uv,transformed,normal));
    }

    private static Vec3 nodeTransform(Vec3 value, Node node) {
        Vec3 rotated = nodeRotate(value.scale(NODE_SCALE));
        return new Vec3(rotated.x+1.0, rotated.y+0.5625, rotated.z+(node==Node.HEAD ? 1.0 : 0.0));
    }

    // Item-model quaternion [0,-sqrt(.5),sqrt(.5),0].
    private static Vec3 nodeRotate(Vec3 value) {
        return new Vec3(-value.x,-value.z,-value.y);
    }

    private static Vec3 rotateZYX(Vec3 value, double xAngle, double yAngle, double zAngle) {
        Vec3 xRotated = new Vec3(
            value.x,
            value.y*Math.cos(xAngle)-value.z*Math.sin(xAngle),
            value.y*Math.sin(xAngle)+value.z*Math.cos(xAngle)
        );
        Vec3 yRotated = new Vec3(
            xRotated.x*Math.cos(yAngle)+xRotated.z*Math.sin(yAngle),
            xRotated.y,
            -xRotated.x*Math.sin(yAngle)+xRotated.z*Math.cos(yAngle)
        );
        return new Vec3(
            yRotated.x*Math.cos(zAngle)-yRotated.y*Math.sin(zAngle),
            yRotated.x*Math.sin(zAngle)+yRotated.y*Math.cos(zAngle),
            yRotated.z
        );
    }

    private static BufferedImage renderTextured(List<Face> geometry, int size) {
        List<ProjectedFace> visible = projectVisible(geometry,size);
        BufferedImage image = new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            for(ProjectedFace face:visible) drawTexturedFace(graphics,face);
        } finally { graphics.dispose(); }
        return image;
    }

    private static List<ProjectedFace> projectVisible(List<Face> geometry, int size) {
        List<ProjectedFace> visible=new ArrayList<ProjectedFace>();
        for(Face face:geometry){
            Vec3 normal=rotateGui(face.normal);
            if(normal.z<=0.000001) continue;
            Point2[] points=new Point2[4]; double depth=0;
            for(int index=0;index<4;index++){
                Vec3 transformed=transformPosition(face.vertices[index]);
                points[index]=new Point2(size*.5+transformed.x*size,size*.5-transformed.y*size);
                depth+=transformed.z;
            }
            visible.add(new ProjectedFace(face,points,depth/4.0,clientLight(face.normal)));
        }
        Collections.sort(visible,Comparator.comparingDouble(face->face.depth));
        return visible;
    }

    private static Vec3 transformPosition(Vec3 value) {
        Vec3 centered = new Vec3(value.x-.5,value.y-.5,value.z-.5).scale(GUI_SCALE);
        Vec3 rotated = rotateGui(centered);
        return new Vec3(rotated.x+GUI_TRANSLATE_X,rotated.y+GUI_TRANSLATE_Y,rotated.z);
    }

    private static Vec3 rotateGui(Vec3 value) {
        double x=value.x*Math.cos(GUI_ROTATION_Y)+value.z*Math.sin(GUI_ROTATION_Y);
        double z=-value.x*Math.sin(GUI_ROTATION_Y)+value.z*Math.cos(GUI_ROTATION_Y);
        return new Vec3(x,value.y*Math.cos(GUI_ROTATION_X)-z*Math.sin(GUI_ROTATION_X),
            value.y*Math.sin(GUI_ROTATION_X)+z*Math.cos(GUI_ROTATION_X));
    }

    private static double clientLight(Vec3 localNormal) {
        Vec3 transformed=rotateGui(localNormal);
        Vec3 guiNormal=new Vec3(transformed.x,-transformed.y,transformed.z).normalize();
        double light0=Math.max(0,guiNormal.dot(LIGHT_0));
        double light1=Math.max(0,guiNormal.dot(LIGHT_1));
        return Math.min(1,(light0+light1)*.6+.4);
    }

    private static void drawTexturedFace(Graphics2D destination, ProjectedFace projected) {
        BufferedImage pixels=shaded(projected.face.texture,projected.face.uv,projected.light);
        int[] x=new int[4],y=new int[4];
        for(int index=0;index<4;index++){
            x[index]=(int)Math.round(projected.points[index].x);
            y[index]=(int)Math.round(projected.points[index].y);
        }
        Graphics2D graphics=(Graphics2D)destination.create();
        try {
            graphics.setClip(new Polygon(x,y,4));
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            Point2 topLeft=projected.points[0],topRight=projected.points[1],bottomLeft=projected.points[3];
            graphics.drawImage(pixels,new AffineTransform(
                (topRight.x-topLeft.x)/pixels.getWidth(),(topRight.y-topLeft.y)/pixels.getWidth(),
                (bottomLeft.x-topLeft.x)/pixels.getHeight(),(bottomLeft.y-topLeft.y)/pixels.getHeight(),
                topLeft.x,topLeft.y
            ),null);
        } finally { graphics.dispose(); }
    }

    private static BufferedImage shaded(BufferedImage source, Rect area, double light) {
        BufferedImage result=new BufferedImage(area.width,area.height,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<area.height;y++) for(int x=0;x<area.width;x++){
            int argb=source.getRGB(area.x+x,area.y+y),alpha=argb>>>24;
            int red=Math.min(255,(int)Math.round(((argb>>>16)&255)*light));
            int green=Math.min(255,(int)Math.round(((argb>>>8)&255)*light));
            int blue=Math.min(255,(int)Math.round((argb&255)*light));
            result.setRGB(x,y,(alpha<<24)|(red<<16)|(green<<8)|blue);
        }
        return result;
    }

    private static BufferedImage scaleNearest(BufferedImage source, int size) {
        BufferedImage result=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=result.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(source,0,0,size,size,null); graphics.dispose(); return result;
    }

    private static void writeContactSheet(Path target, List<Generated> generated) throws Exception {
        int columns=4,cellWidth=180,cellHeight=124,header=55;
        BufferedImage sheet=new BufferedImage(columns*cellWidth,header+4*cellHeight,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=sheet.createGraphics();
        try {
            graphics.setColor(new Color(32,37,44)); graphics.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
            graphics.setColor(Color.WHITE); graphics.setFont(new Font(Font.MONOSPACED,Font.BOLD,17));
            graphics.drawString("BED FAMILY / CLIENT-EQUIVALENT / 26.1.2",16,32);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            for(int index=0;index<generated.size();index++){
                Generated value=generated.get(index); int x=(index%columns)*cellWidth,y=header+(index/columns)*cellHeight;
                graphics.setColor(new Color(65,74,86)); graphics.fillRect(x+3,y+3,cellWidth-6,cellHeight-6);
                graphics.drawImage(value.image,x+58,y+8,64,64,null);
                graphics.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12)); graphics.setColor(Color.WHITE);
                graphics.drawString(value.color+"_bed",x+9,y+91);
                graphics.setColor(new Color(139,190,142)); graphics.drawString(alphaBounds(value.image),x+9,y+108);
            }
        } finally { graphics.dispose(); }
        ImageIO.write(sheet,"png",target.toFile());
    }

    private static void writeReport(Path target, Path clientJar, List<Generated> generated) throws Exception {
        List<String> lines=new ArrayList<String>();
        lines.add("Minecraft 26.1.2 Client-Equivalent Bed Family Static Candidate");
        lines.add("Client JAR="+clientJar.getFileName());
        lines.add("Client JAR SHA-256="+sha256(clientJar));
        lines.add("Model=BedRenderer.createHeadLayer/createFootLayer; two fixed special-model nodes");
        lines.add("Node quaternion=[0,-0.70710677,0.70710677,0]; scale~=.9999999");
        lines.add("Head translation=[.99999994,.5625,.99999994]; foot=[.99999994,.5625,-5.9604645E-8]");
        lines.add("GUI rotationXYZ=[30,160,0], translation=[2,3,0], scale=[.5325,.5325,.5325]");
        lines.add("Visible bbox fit=DISABLED; post projection recenter=DISABLED; extra tint=NONE");
        lines.add("color\ttexture_entry\ttexture_sha256\toutput\toutput_sha256\talpha_bbox");
        for(Generated value:generated) lines.add(value.color+'\t'+value.entry+'\t'+value.textureHash+'\t'+
            value.output.getFileName()+'\t'+sha256(value.output)+'\t'+alphaBounds(value.image));
        Files.write(target,lines,StandardCharsets.UTF_8);
    }

    private static String alphaBounds(BufferedImage image) {
        int minX=image.getWidth(),minY=image.getHeight(),maxX=-1,maxY=-1;
        for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) if((image.getRGB(x,y)>>>24)!=0){
            minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);
        }
        return maxX<minX?"EMPTY":String.format(Locale.ROOT,"x=%d..%d y=%d..%d",minX,maxX,minY,maxY);
    }

    private static String sha256(Path file) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
        return hex(digest);
    }

    private static String sha256(ZipFile archive, String entryName) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream input=archive.getInputStream(archive.getEntry(entryName))){
            byte[] buffer=new byte[8192]; int read;
            while((read=input.read(buffer))>=0) digest.update(buffer,0,read);
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] digest) {
        StringBuilder result=new StringBuilder();
        for(byte value:digest) result.append(String.format(Locale.ROOT,"%02X",value&255));
        return result.toString();
    }

    private static Rect rect(double x,double y,double width,double height){return new Rect((int)x,(int)y,(int)width,(int)height);}
    private static Vec3[] vertices(double... values){Vec3[] result=new Vec3[4];for(int index=0;index<4;index++)result[index]=new Vec3(values[index*3],values[index*3+1],values[index*3+2]);return result;}

    private enum Node { HEAD, FOOT }
    private enum Direction {
        DOWN(new Vec3(0,-1,0)), UP(new Vec3(0,1,0)), NORTH(new Vec3(0,0,-1)),
        SOUTH(new Vec3(0,0,1)), WEST(new Vec3(-1,0,0)), EAST(new Vec3(1,0,0));
        final Vec3 normal; Direction(Vec3 normal){this.normal=normal;}
    }
    private static final class Generated {
        final String color,entry,textureHash; final Path output; final BufferedImage image;
        Generated(String color,String entry,String textureHash,Path output,BufferedImage image){this.color=color;this.entry=entry;this.textureHash=textureHash;this.output=output;this.image=image;}
    }
    private static final class Face {
        final String part; final BufferedImage texture; final Rect uv; final Vec3[] vertices; final Vec3 normal;
        Face(String part,BufferedImage texture,Rect uv,Vec3[] vertices,Vec3 normal){this.part=part;this.texture=texture;this.uv=uv;this.vertices=vertices;this.normal=normal;}
    }
    private static final class ProjectedFace {
        final Face face; final Point2[] points; final double depth,light;
        ProjectedFace(Face face,Point2[] points,double depth,double light){this.face=face;this.points=points;this.depth=depth;this.light=light;}
    }
    private static final class Rect {final int x,y,width,height;Rect(int x,int y,int width,int height){this.x=x;this.y=y;this.width=width;this.height=height;}}
    private static final class Point2 {final double x,y;Point2(double x,double y){this.x=x;this.y=y;}}
    private static final class Vec3 {
        final double x,y,z;Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        Vec3 scale(double value){return new Vec3(x*value,y*value,z*value);}
        double dot(Vec3 other){return x*other.x+y*other.y+z*other.z;}
        Vec3 normalize(){double length=Math.sqrt(x*x+y*y+z*z);return new Vec3(x/length,y/length,z/length);}
    }
}
