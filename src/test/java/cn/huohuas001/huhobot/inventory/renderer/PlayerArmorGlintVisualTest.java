package cn.huohuas001.huhobot.inventory.renderer;

import cn.huohuas001.huhobot.inventory.armor.ArmorEquipmentSet;
import cn.huohuas001.huhobot.inventory.armor.ArmorVisualDescriptor;
import cn.huohuas001.huhobot.inventory.armor.EquipmentAssetResolver;
import cn.huohuas001.huhobot.inventory.asset.VanillaImportedAssetProvider;
import cn.huohuas001.huhobot.inventory.model.InventorySlot;
import cn.huohuas001.huhobot.inventory.model.InventorySnapshot;
import cn.huohuas001.huhobot.inventory.model.ItemSnapshot;
import cn.huohuas001.huhobot.inventory.model.SlotType;
import cn.huohuas001.huhobot.inventory.skin.DefaultPlayerSkinProvider;
import cn.huohuas001.huhobot.inventory.skin.PlayerModelRenderer;
import cn.huohuas001.huhobot.inventory.skin.PlayerSkin;
import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Final 704x664 composition gate for the PV8 direct-size deterministic armor glint layer. */
class PlayerArmorGlintVisualTest {
    private static final Path OUTPUT = Paths.get(
        "data", "visual-audit", "26.1.2-B1B315857266-MB7-PD1337875", "player-armor-glint"
    );

    @Test
    void writesFinalInventoryAndClientFacts() throws Exception {
        ArmorVisualDescriptor head = armor(ArmorVisualDescriptor.Slot.HEAD, "netherite", false);
        ArmorVisualDescriptor chest = armor(ArmorVisualDescriptor.Slot.CHEST, "netherite", true);
        ArmorVisualDescriptor legs = armor(ArmorVisualDescriptor.Slot.LEGS, "netherite", false);
        ArmorVisualDescriptor feet = armor(ArmorVisualDescriptor.Slot.FEET, "netherite", true);
        InventorySnapshot snapshot = snapshot(head, chest, legs, feet);

        EquipmentAssetResolver equipmentAssets = new EquipmentAssetResolver(Paths.get("src", "armor-assets"));
        PlayerSkin skin = new DefaultPlayerSkinProvider().getFallback();
        Theme theme = ThemeLoader.load(
            Paths.get("src/main/resources/themes/faithful32x"),
            VanillaImportedAssetProvider.open(Paths.get("data/imported-assets/vanilla"))
        );
        java.awt.Rectangle previewArea = theme.getLayout().getPlayerPreview();
        java.awt.image.BufferedImage preview = new PlayerModelRenderer(previewArea.width, previewArea.height).render(
            skin, ArmorEquipmentSet.from(snapshot), equipmentAssets
        );
        RenderResult result = new Java2DInventoryRenderer(theme).render(snapshot, preview);
        assertEquals(704, result.getWidth());
        assertEquals(664, result.getHeight());
        assertTrue(result.getByteSize() > 30_000);

        Files.createDirectories(OUTPUT);
        Files.write(OUTPUT.resolve("armor-glint-final-inventory.png"), result.getBytes());
        Files.write(OUTPUT.resolve("armor-glint-client-facts.txt"), Arrays.asList(
            "Client=26.1.2",
            "Texture=assets/minecraft/textures/misc/enchanted_glint_armor.png",
            "TextureSize=128x128",
            "ArmorAndItemGlintTextures=separate",
            "ArmorUvScale=0.16",
            "UvRotationDegrees=10",
            "ClientDefaultGlintStrength=0.75",
            "Blend=SRC_COLOR + ONE; alpha ZERO + ONE",
            "LayerOrder=first equipment layer -> armor entity glint -> remaining layers -> trim",
            "StaticPhase=client-equivalent t=242s (110s/30s cycles)",
            "Output=deterministic static PNG",
            "GlintVertexColor=white (independent from leather dye)",
            "PreviewCache=PV8-198x283",
            "PreviewRasterization=direct final theme rectangle; no 128x256 intermediate resample",
            "MB7=mixed-resolution"
        ), StandardCharsets.UTF_8);
    }

    private static InventorySnapshot snapshot(ArmorVisualDescriptor... descriptors) {
        List<InventorySlot> storage = new ArrayList<InventorySlot>();
        for (int index = 0; index < 27; index++) storage.add(InventorySlot.empty(SlotType.STORAGE, index));
        List<InventorySlot> hotbar = new ArrayList<InventorySlot>();
        for (int index = 0; index < 9; index++) hotbar.add(InventorySlot.empty(SlotType.HOTBAR, index));
        List<InventorySlot> armor = Arrays.asList(
            slot(SlotType.ARMOR_HEAD, descriptors[0]),
            slot(SlotType.ARMOR_CHEST, descriptors[1]),
            slot(SlotType.ARMOR_LEGS, descriptors[2]),
            slot(SlotType.ARMOR_FEET, descriptors[3])
        );
        return new InventorySnapshot(
            InventorySnapshot.CURRENT_SCHEMA_VERSION,
            UUID.nameUUIDFromBytes("player-armor-glint".getBytes(StandardCharsets.UTF_8)),
            "ArmorGlint", Instant.parse("2026-08-29T05:00:00Z"), "local-audit", "inventory-1.14.1-candidate",
            storage, hotbar, armor, InventorySlot.empty(SlotType.OFFHAND, 0)
        );
    }

    private static InventorySlot slot(SlotType slotType, ArmorVisualDescriptor descriptor) {
        ItemSnapshot item = new ItemSnapshot(
            descriptor.getBaseMaterialKey(), 1, 0, 0, null, null,
            descriptor.hasGlint(), null, descriptor
        );
        return InventorySlot.of(slotType, 0, item);
    }

    private static ArmorVisualDescriptor armor(
        ArmorVisualDescriptor.Slot slot, String family, boolean glint
    ) {
        String suffix;
        switch (slot) {
            case HEAD: suffix = "helmet"; break;
            case CHEST: suffix = "chestplate"; break;
            case LEGS: suffix = "leggings"; break;
            case FEET: suffix = "boots"; break;
            default: throw new IllegalArgumentException();
        }
        return new ArmorVisualDescriptor(
            slot, "minecraft:" + family + '_' + suffix, "minecraft:" + family,
            "minecraft:coast", "minecraft:gold", null, glint
        );
    }
}
