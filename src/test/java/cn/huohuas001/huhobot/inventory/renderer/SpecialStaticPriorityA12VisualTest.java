package cn.huohuas001.huhobot.inventory.renderer;

import cn.huohuas001.huhobot.inventory.asset.VanillaImportedAssetProvider;
import cn.huohuas001.huhobot.inventory.model.InventorySlot;
import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;
import cn.huohuas001.huhobot.inventory.model.ItemSnapshot;
import cn.huohuas001.huhobot.inventory.model.SlotType;
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

/** Audit-only local visual gate for the Priority A1+A2 static candidates. */
class SpecialStaticPriorityA12VisualTest {
    private static final Path THEME = Paths.get("src", "main", "resources", "themes", "faithful32x");
    private static final Path OVERRIDES = THEME.resolve("overrides/items/minecraft");
    private static final Path OUTPUT = Paths.get(
        "data", "visual-audit", "26.1.2-B1B315857266-MB7-PD1337875", "special-priority-a1-a2"
    );
    private static final List<String> CHESTS = Arrays.asList(
        "ender_chest", "trapped_chest", "copper_chest", "exposed_copper_chest",
        "weathered_copper_chest", "oxidized_copper_chest", "waxed_copper_chest",
        "waxed_exposed_copper_chest", "waxed_weathered_copper_chest", "waxed_oxidized_copper_chest"
    );
    private static final List<String> SHULKERS = Arrays.asList(
        "shulker_box", "white_shulker_box", "orange_shulker_box", "magenta_shulker_box",
        "light_blue_shulker_box", "yellow_shulker_box", "lime_shulker_box", "pink_shulker_box",
        "gray_shulker_box", "light_gray_shulker_box", "cyan_shulker_box", "purple_shulker_box",
        "blue_shulker_box", "brown_shulker_box", "green_shulker_box", "red_shulker_box",
        "black_shulker_box"
    );

    @Test
    void writesReferenceContactSheetAndFinalInventoryPage() throws Exception {
        VanillaImportedAssetProvider imported = VanillaImportedAssetProvider.open(
            Paths.get("data", "imported-assets", "vanilla")
        );
        assertTrue(imported.isAvailable());
        Theme theme = ThemeLoader.load(THEME, imported);
        List<String> itemIds = new ArrayList<String>();
        itemIds.addAll(CHESTS);
        itemIds.addAll(SHULKERS);
        assertEquals(27, itemIds.size());

        for (String id : itemIds) {
            TextureResolver.ResolvedTexture resolved = theme.getTextures().resolve(
                ItemSnapshot.basic("minecraft:" + id, 1)
            );
            assertEquals(TextureResolver.Source.EXPLICIT_OVERRIDE, resolved.getSource(), id);
            assertFalse(resolved.isFallback(), id);
        }

        Files.createDirectories(OUTPUT);
        writeReferenceSheet(OUTPUT.resolve("special-priority-a1-a2-reference.png"));
        writeCandidateSheet(OUTPUT.resolve("special-priority-a1-a2-contact-sheet-candidate.png"), itemIds);
        writePassSheet(OUTPUT.resolve("special-priority-a1-a2-contact-sheet-pass.png"), itemIds);

        List<ItemSnapshot> items = new ArrayList<ItemSnapshot>();
        for (int index = 0; index < itemIds.size(); index++) {
            int count = index % 6 == 0 ? 64 : 1;
            items.add(ItemSnapshot.basic("minecraft:" + itemIds.get(index), count));
        }
        InventorySnapshot snapshot = snapshot(items);
        Java2DInventoryRenderer renderer = new Java2DInventoryRenderer(theme);
        PlayerSkin skin = new DefaultPlayerSkinProvider().getFallback();
        java.awt.Rectangle previewArea = theme.getLayout().getPlayerPreview();
        RenderResult result = renderer.render(
            snapshot, new PlayerModelRenderer(previewArea.width, previewArea.height).render(skin)
        );
        assertEquals(704, result.getWidth());
        assertEquals(664, result.getHeight());
        assertTrue(result.getByteSize() > 30_000);
        Files.write(OUTPUT.resolve("special-priority-a1-a2-final-inventory.png"), result.getBytes());
    }

    private static void writeReferenceSheet(Path target) throws Exception {
        List<Reference> references = new ArrayList<Reference>();
        references.add(new Reference("chest baseline", OVERRIDES.resolve("chest.png")));
        references.add(new Reference("trapped NORMAL", OVERRIDES.resolve("trapped_chest.png")));
        references.add(new Reference(
            "trapped CHRISTMAS", THEME.resolve("special-variants/minecraft/trapped_chest_christmas.png")
        ));
        for (String chest : CHESTS) if (!"trapped_chest".equals(chest)) {
            references.add(new Reference(chest, OVERRIDES.resolve(chest + ".png")));
        }
        for (String shulker : SHULKERS) references.add(new Reference(shulker, OVERRIDES.resolve(shulker + ".png")));
        drawSheet(target, references, "LOCAL VISUAL GATE — Chest Family + Shulker Family", "CANDIDATE");
    }

    private static void writeCandidateSheet(Path target, List<String> ids) throws Exception {
        List<Reference> references = new ArrayList<Reference>();
        for (String id : ids) references.add(new Reference(id, OVERRIDES.resolve(id + ".png")));
        drawSheet(target, references, "SPECIAL STATIC AUDIT CONTACT SHEET", "CANDIDATE");
    }

    private static void writePassSheet(Path target, List<String> ids) throws Exception {
        List<Reference> references = new ArrayList<Reference>();
        for (String id : ids) references.add(new Reference(id, OVERRIDES.resolve(id + ".png")));
        drawSheet(target, references, "SPECIAL STATIC LOCAL VISUAL GATE", "PASS");
    }

    private static void drawSheet(Path target, List<Reference> references, String title, String status)
        throws Exception {
        int columns=5, cellWidth=190, cellHeight=126, header=54;
        int rows=(references.size()+columns-1)/columns;
        BufferedImage sheet=new BufferedImage(columns*cellWidth,header+rows*cellHeight,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=sheet.createGraphics();
        try {
            graphics.setColor(new Color(32,37,44));
            graphics.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.MONOSPACED,Font.BOLD,18));
            graphics.drawString(title,18,32);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            for(int index=0;index<references.size();index++){
                Reference reference=references.get(index);
                int x=(index%columns)*cellWidth;
                int y=header+(index/columns)*cellHeight;
                graphics.setColor(new Color(65,74,86));
                graphics.fillRect(x+3,y+3,cellWidth-6,cellHeight-6);
                BufferedImage icon=ImageIO.read(reference.file.toFile());
                graphics.drawImage(icon,x+63,y+7,64,64,null);
                graphics.setFont(new Font(Font.MONOSPACED,Font.PLAIN,11));
                graphics.setColor(Color.WHITE);
                graphics.drawString(clip(reference.label,28),x+9,y+88);
                graphics.setColor(new Color(255,190,75));
                graphics.drawString(status,x+9,y+106);
            }
        } finally {
            graphics.dispose();
        }
        ImageIO.write(sheet,"png",target.toFile());
    }

    private static String clip(String value, int length) {
        return value.length() <= length ? value : value.substring(0,length-1) + "~";
    }

    private static InventorySnapshot snapshot(List<ItemSnapshot> values) {
        List<InventorySlot> storage=new ArrayList<InventorySlot>();
        List<InventorySlot> hotbar=new ArrayList<InventorySlot>();
        for(int index=0;index<27;index++) storage.add(InventorySlot.of(SlotType.STORAGE,index,values.get(index)));
        for(int index=0;index<9;index++) hotbar.add(InventorySlot.empty(SlotType.HOTBAR,index));
        List<InventorySlot> armor=Arrays.asList(
            InventorySlot.empty(SlotType.ARMOR_HEAD,0), InventorySlot.empty(SlotType.ARMOR_CHEST,0),
            InventorySlot.empty(SlotType.ARMOR_LEGS,0), InventorySlot.empty(SlotType.ARMOR_FEET,0)
        );
        return new InventorySnapshot(
            InventorySnapshot.CURRENT_SCHEMA_VERSION,
            UUID.nameUUIDFromBytes("special-priority-a1-a2".getBytes(StandardCharsets.UTF_8)),
            "Special-A1-A2", Instant.parse("2026-08-29T00:00:00Z"), "local-audit", "inventory-1.12.0-candidate",
            storage, hotbar, armor, InventorySlot.empty(SlotType.OFFHAND,0)
        );
    }

    private static final class Reference {
        final String label;
        final Path file;
        Reference(String label, Path file){this.label=label;this.file=file;}
    }
}
