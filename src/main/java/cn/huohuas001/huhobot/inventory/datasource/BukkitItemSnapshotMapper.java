package cn.huohuas001.huhobot.inventory.datasource;

import cn.huohuas001.huhobot.inventory.armor.ArmorVisualDescriptor;
import cn.huohuas001.huhobot.inventory.armor.ArmorVisualResolver;
import cn.huohuas001.huhobot.inventory.model.ItemSnapshot;
import cn.huohuas001.huhobot.inventory.potion.PotionVisualDescriptor;
import cn.huohuas001.huhobot.inventory.potion.PotionVisualResolver;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/** Converts Bukkit ItemStacks into immutable, renderer-neutral item snapshots on the main thread. */
public final class BukkitItemSnapshotMapper {
    private final ArmorVisualResolver armorResolver = new ArmorVisualResolver();
    private final PotionVisualResolver potionResolver = new PotionVisualResolver();

    public ItemSnapshot map(ItemStack stack) {
        if (stack == null) return null;
        Material material = stack.getType();
        if (isAir(material) || stack.getAmount() <= 0) return null;

        ItemMeta meta = stack.getItemMeta();
        int damage = 0;
        if (meta instanceof Damageable) damage = ((Damageable) meta).getDamage();
        String displayName = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : null;
        Integer customModelData = meta != null && meta.hasCustomModelData() ? meta.getCustomModelData() : null;
        boolean enchantmentGlint = meta != null && (
            meta.hasEnchants() || (meta.hasEnchantmentGlintOverride() && Boolean.TRUE.equals(meta.getEnchantmentGlintOverride()))
        );

        ArmorVisualDescriptor armor = armorResolver.resolve(material, meta, enchantmentGlint);
        PotionVisualDescriptor potion = potionResolver.resolve(material, meta, enchantmentGlint);
        return mapValues(
            material, stack.getAmount(), damage, displayName, customModelData, enchantmentGlint, armor, potion
        );
    }

    ItemSnapshot mapValues(
        Material material,
        int amount,
        int rawDamage,
        String displayName,
        Integer customModelData,
        boolean enchantmentGlint
    ) {
        return mapValues(material, amount, rawDamage, displayName, customModelData, enchantmentGlint, null);
    }

    ItemSnapshot mapValues(
        Material material,
        int amount,
        int rawDamage,
        String displayName,
        Integer customModelData,
        boolean enchantmentGlint,
        ArmorVisualDescriptor armorVisual
    ) {
        return mapValues(
            material, amount, rawDamage, displayName, customModelData, enchantmentGlint, armorVisual, null
        );
    }

    ItemSnapshot mapValues(
        Material material,
        int amount,
        int rawDamage,
        String displayName,
        Integer customModelData,
        boolean enchantmentGlint,
        ArmorVisualDescriptor armorVisual,
        PotionVisualDescriptor potionVisual
    ) {
        if (isAir(material) || amount <= 0) return null;
        int maxDamage = maxDamage(material);
        int damage = maxDamage == 0 ? 0 : Math.max(0, Math.min(rawDamage, maxDamage));
        return new ItemSnapshot(
            "minecraft:" + material.name().toLowerCase(java.util.Locale.ROOT),
            amount,
            damage,
            maxDamage,
            displayName,
            customModelData,
            enchantmentGlint,
            null,
            armorVisual,
            potionVisual
        );
    }

    private static boolean isAir(Material material) {
        if (material == null) return true;
        String name = material.name();
        return "AIR".equals(name) || "CAVE_AIR".equals(name) || "VOID_AIR".equals(name);
    }

    private static int maxDamage(Material material) {
        if (Bukkit.getServer() != null) return Math.max(0, material.getMaxDurability());
        // Paper's standalone API intentionally has no live item registry. Keep pure unit tests deterministic.
        String name = material.name();
        if (name.startsWith("DIAMOND_")) return 1561;
        if (name.startsWith("NETHERITE_")) return 2031;
        if (name.startsWith("IRON_")) return 250;
        if (name.startsWith("STONE_")) return 131;
        if (name.startsWith("WOODEN_")) return 59;
        if (name.startsWith("GOLDEN_")) return 32;
        return 0;
    }
}
