package cn.huohuas001.huhobot.inventory.armor;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.inventory.meta.trim.ArmorTrim;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Reads supported armor semantics exclusively through public Bukkit/Paper API. */
public final class ArmorVisualResolver {
    private static final Set<String> SUPPORTED_MODELS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
        "minecraft:leather", "minecraft:chainmail", "minecraft:iron", "minecraft:gold",
        "minecraft:diamond", "minecraft:netherite", "minecraft:turtle_scute", "minecraft:copper"
    )));

    public ArmorVisualDescriptor resolve(Material material, ItemMeta meta, boolean glint) {
        if (material == null || meta == null) return null;
        String materialKey = "minecraft:" + material.name().toLowerCase(Locale.ROOT);
        ArmorVisualDescriptor.Slot slot = slot(meta, materialKey);
        String equipmentModel = equipmentModel(meta, materialKey);
        if (slot == null || equipmentModel == null || !SUPPORTED_MODELS.contains(equipmentModel)) return null;

        String trimPattern = null;
        String trimMaterial = null;
        if (meta instanceof ArmorMeta) {
            ArmorMeta armor = (ArmorMeta) meta;
            if (armor.hasTrim()) {
                ArmorTrim trim = armor.getTrim();
                if (trim != null) {
                    trimPattern = trim.getPattern().getKey().toString();
                    trimMaterial = trim.getMaterial().getKey().toString();
                }
            }
        }
        Integer leatherColor = meta instanceof LeatherArmorMeta
            ? Integer.valueOf(((LeatherArmorMeta) meta).getColor().asRGB()) : null;
        return new ArmorVisualDescriptor(
            slot, materialKey, equipmentModel, trimPattern, trimMaterial, leatherColor, glint
        );
    }

    private static ArmorVisualDescriptor.Slot slot(ItemMeta meta, String materialKey) {
        if (meta.hasEquippable()) {
            EquippableComponent component = meta.getEquippable();
            if (component != null) {
                ArmorVisualDescriptor.Slot resolved = slot(component.getSlot());
                if (resolved != null) return resolved;
            }
        }
        if (materialKey.endsWith("_helmet") || "minecraft:turtle_helmet".equals(materialKey)) {
            return ArmorVisualDescriptor.Slot.HEAD;
        }
        if (materialKey.endsWith("_chestplate")) return ArmorVisualDescriptor.Slot.CHEST;
        if (materialKey.endsWith("_leggings")) return ArmorVisualDescriptor.Slot.LEGS;
        if (materialKey.endsWith("_boots")) return ArmorVisualDescriptor.Slot.FEET;
        return null;
    }

    private static ArmorVisualDescriptor.Slot slot(EquipmentSlot slot) {
        if (slot == null) return null;
        switch (slot) {
            case HEAD: return ArmorVisualDescriptor.Slot.HEAD;
            case CHEST: return ArmorVisualDescriptor.Slot.CHEST;
            case LEGS: return ArmorVisualDescriptor.Slot.LEGS;
            case FEET: return ArmorVisualDescriptor.Slot.FEET;
            default: return null;
        }
    }

    private static String equipmentModel(ItemMeta meta, String materialKey) {
        if (meta.hasEquippable()) {
            EquippableComponent component = meta.getEquippable();
            NamespacedKey model = component == null ? null : component.getModel();
            if (model != null) return model.toString().toLowerCase(Locale.ROOT);
        }
        String path = materialKey.substring(materialKey.indexOf(':') + 1);
        if (path.startsWith("golden_")) return "minecraft:gold";
        if (path.equals("turtle_helmet")) return "minecraft:turtle_scute";
        for (String family : Arrays.asList("leather", "chainmail", "iron", "diamond", "netherite", "copper")) {
            if (path.startsWith(family + "_")) return "minecraft:" + family;
        }
        return null;
    }
}
