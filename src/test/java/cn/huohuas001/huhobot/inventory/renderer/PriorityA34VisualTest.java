package cn.huohuas001.huhobot.inventory.renderer;

import cn.huohuas001.huhobot.inventory.asset.VanillaImportedAssetProvider;
import cn.huohuas001.huhobot.inventory.model.InventorySlot;
import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;
import cn.huohuas001.huhobot.inventory.model.ItemSnapshot;
import cn.huohuas001.huhobot.inventory.model.SlotType;
import cn.huohuas001.huhobot.inventory.potion.PotionVisualDescriptor;
import cn.huohuas001.huhobot.inventory.skin.DefaultPlayerSkinProvider;
import cn.huohuas001.huhobot.inventory.skin.PlayerModelRenderer;
import cn.huohuas001.huhobot.inventory.skin.PlayerSkin;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Local visual gate for Priority A3+A4 before any Paper/QQ release claim. */
class PriorityA34VisualTest {
    private static final Path OUTPUT = Paths.get(
        "data", "visual-audit", "26.1.2-B1B315857266-MB7-PD1337875", "priority-a3-a4"
    );

    @Test
    void writesReferenceComparisonAndFinalInventory() throws Exception {
        Theme theme = ThemeLoader.load(
            Paths.get("src/main/resources/themes/faithful32x"),
            VanillaImportedAssetProvider.open(Paths.get("data/imported-assets/vanilla"))
        );
        List<Sample> samples = samples();
        List<BufferedImage> icons = new ArrayList<BufferedImage>();
        for (Sample sample : samples) {
            TextureResolver.ResolvedTexture resolved = theme.getTextures().resolve(sample.item);
            TextureResolver.Source expected = sample.item.getMaterialKey().equals("minecraft:trident")
                ? TextureResolver.Source.GUI_MODEL : TextureResolver.Source.RUNTIME_COMPOSITE;
            assertEquals(expected, resolved.getSource(), sample.label);
            assertFalse(resolved.isFallback(), sample.label);
            icons.add(resolved.getImage());
        }

        Files.createDirectories(OUTPUT);
        drawSheet(
            OUTPUT.resolve("priority-a3-a4-reference.png"), samples, icons,
            "PRIORITY A3+A4 — LOCAL VISUAL REFERENCE", "CANDIDATE"
        );
        drawSheet(
            OUTPUT.resolve("priority-a3-a4-contact-sheet-pass.png"), samples, icons,
            "PRIORITY A3+A4 — LOCAL VISUAL GATE", "PASS"
        );

        List<ItemSnapshot> inventoryItems = new ArrayList<ItemSnapshot>();
        for (int index=0; index<samples.size(); index++) {
            ItemSnapshot source=samples.get(index).item;
            inventoryItems.add(new ItemSnapshot(
                source.getMaterialKey(), index % 4 == 0 ? 64 : 1, 0, 0, null, null,
                source.hasEnchantmentGlint(), null, null, source.getPotionVisual()
            ));
        }
        InventorySnapshot snapshot=snapshot(inventoryItems);
        Java2DInventoryRenderer renderer=new Java2DInventoryRenderer(theme);
        PlayerSkin skin=new DefaultPlayerSkinProvider().getFallback();
        java.awt.Rectangle previewArea=theme.getLayout().getPlayerPreview();
        RenderResult result=renderer.render(
            snapshot,new PlayerModelRenderer(previewArea.width,previewArea.height).render(skin)
        );
        assertEquals(704,result.getWidth());
        assertEquals(664,result.getHeight());
        assertTrue(result.getByteSize()>25_000);
        Files.write(OUTPUT.resolve("priority-a3-a4-final-inventory.png"),result.getBytes());

        Files.write(OUTPUT.resolve("priority-a3-a4-client-facts.txt"), Arrays.asList(
            "Client=26.1.2",
            "TridentGUI=display_context select -> minecraft:model item/trident",
            "TridentHandFallback=minecraft:special (not used by Inventory)",
            "PotionModels=generated layer0 tint + untinted layer1",
            "PotionTint=custom_color first; otherwise visible effects weighted by amplifier+1",
            "PaperSource=PotionMeta.computeEffectiveColor()",
            "ClientDefaultTint=#385DC6",
            "MB7=mixed-resolution"
        ),StandardCharsets.UTF_8);
    }

    private static List<Sample> samples() {
        return Arrays.asList(
            new Sample("Trident GUI",ItemSnapshot.basic("minecraft:trident",1)),
            sample("Potion Water/default","minecraft:potion","minecraft:water",0x385dc6,false),
            sample("Potion Healing","minecraft:potion","minecraft:healing",0xf82423,false),
            sample("Potion Poison","minecraft:potion","minecraft:poison",0x4e9331,false),
            sample("Potion Strength","minecraft:potion","minecraft:strength",0x932423,false),
            sample("Potion Custom #7F3FBF","minecraft:potion","minecraft:healing",0x7f3fbf,true),
            sample("Splash Poison","minecraft:splash_potion","minecraft:poison",0x4e9331,false),
            sample("Splash Custom","minecraft:splash_potion","minecraft:water",0x18b7c9,true),
            sample("Lingering Healing","minecraft:lingering_potion","minecraft:healing",0xf82423,false),
            sample("Lingering Strength","minecraft:lingering_potion","minecraft:strength",0x932423,false),
            sample("Tipped Arrow Poison","minecraft:tipped_arrow","minecraft:poison",0x4e9331,false),
            sample("Tipped Arrow Custom","minecraft:tipped_arrow","minecraft:water",0xd43fd4,true)
        );
    }

    private static Sample sample(String label,String material,String base,int tint,boolean custom) {
        PotionVisualDescriptor visual=new PotionVisualDescriptor(material,base,tint,custom,false);
        return new Sample(label,new ItemSnapshot(material,1,0,0,null,null,false,null,null,visual));
    }

    private static void drawSheet(
        Path target,List<Sample> samples,List<BufferedImage> icons,String title,String status
    ) throws Exception {
        int columns=4,cellWidth=240,cellHeight=132,header=54;
        int rows=(samples.size()+columns-1)/columns;
        BufferedImage sheet=new BufferedImage(columns*cellWidth,header+rows*cellHeight,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=sheet.createGraphics();
        try {
            graphics.setColor(new Color(32,37,44));graphics.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
            graphics.setColor(Color.WHITE);graphics.setFont(new Font(Font.MONOSPACED,Font.BOLD,18));
            graphics.drawString(title,18,32);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            for(int index=0;index<samples.size();index++){
                int x=(index%columns)*cellWidth,y=header+(index/columns)*cellHeight;
                graphics.setColor(new Color(65,74,86));graphics.fillRect(x+3,y+3,cellWidth-6,cellHeight-6);
                graphics.drawImage(icons.get(index),x+88,y+8,64,64,null);
                graphics.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));graphics.setColor(Color.WHITE);
                graphics.drawString(samples.get(index).label,x+9,y+91);
                graphics.setColor(new Color(255,190,75));graphics.drawString(status,x+9,y+111);
            }
        } finally { graphics.dispose(); }
        ImageIO.write(sheet,"png",target.toFile());
    }

    private static InventorySnapshot snapshot(List<ItemSnapshot> values) {
        List<InventorySlot> storage=new ArrayList<InventorySlot>();
        for(int index=0;index<27;index++) storage.add(index<values.size()
            ? InventorySlot.of(SlotType.STORAGE,index,values.get(index))
            : InventorySlot.empty(SlotType.STORAGE,index));
        List<InventorySlot> hotbar=new ArrayList<InventorySlot>();
        for(int index=0;index<9;index++) hotbar.add(InventorySlot.empty(SlotType.HOTBAR,index));
        List<InventorySlot> armor=Arrays.asList(
            InventorySlot.empty(SlotType.ARMOR_HEAD,0),InventorySlot.empty(SlotType.ARMOR_CHEST,0),
            InventorySlot.empty(SlotType.ARMOR_LEGS,0),InventorySlot.empty(SlotType.ARMOR_FEET,0)
        );
        return new InventorySnapshot(
            InventorySnapshot.CURRENT_SCHEMA_VERSION,
            UUID.nameUUIDFromBytes("priority-a3-a4".getBytes(StandardCharsets.UTF_8)),
            "Priority-A3-A4",Instant.parse("2026-08-29T00:00:00Z"),"local-audit","inventory-1.13.0-candidate",
            storage,hotbar,armor,InventorySlot.empty(SlotType.OFFHAND,0)
        );
    }

    private static final class Sample {
        final String label;final ItemSnapshot item;
        Sample(String label,ItemSnapshot item){this.label=label;this.item=item;}
    }
}
