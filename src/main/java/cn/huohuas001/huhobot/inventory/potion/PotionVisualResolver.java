package cn.huohuas001.huhobot.inventory.potion;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Locale;

/** Resolves the exact client-facing potion tint through Paper's effective-color API. */
public final class PotionVisualResolver {
    public PotionVisualDescriptor resolve(Material material, ItemMeta meta, boolean glint) {
        String itemType = materialKey(material);
        if (!PotionVisualDescriptor.supports(itemType)) return null;

        String basePotion = null;
        int tint = PotionVisualDescriptor.CLIENT_DEFAULT_TINT;
        boolean customColor = false;
        if (meta instanceof PotionMeta) {
            PotionMeta potion = (PotionMeta) meta;
            if (potion.hasBasePotionType()) {
                PotionType type = potion.getBasePotionType();
                if (type != null) basePotion = type.getKey().toString();
            }
            customColor = potion.hasColor();
            Color effective = potion.computeEffectiveColor();
            if (effective != null) tint = effective.asRGB() & 0xffffff;
        }
        return new PotionVisualDescriptor(itemType, basePotion, tint, customColor, glint);
    }

    private static String materialKey(Material material) {
        return material == null ? "" : "minecraft:" + material.name().toLowerCase(Locale.ROOT);
    }
}
