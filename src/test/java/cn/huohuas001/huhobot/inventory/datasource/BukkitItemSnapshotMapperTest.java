package cn.huohuas001.huhobot.inventory.datasource;

import cn.huohuas001.huhobot.inventory.armor.ArmorVisualDescriptor;
import cn.huohuas001.huhobot.inventory.model.ItemSnapshot;
import org.bukkit.Material;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitItemSnapshotMapperTest {
    private final BukkitItemSnapshotMapper mapper = new BukkitItemSnapshotMapper();

    @Test
    void mapsNamespacedKeyAmountDamageMetadataAndGlint() {
        ItemSnapshot item = mapper.mapValues(Material.DIAMOND_SWORD, 2, 212, "Named Sword", 17, true);

        assertEquals("minecraft:diamond_sword", item.getMaterialKey());
        assertEquals(2, item.getAmount());
        assertEquals(212, item.getDamage());
        assertEquals(1561, item.getMaxDamage());
        assertEquals("Named Sword", item.getDisplayName());
        assertEquals(17, item.getCustomModelData());
        assertTrue(item.hasEnchantmentGlint());
    }

    @Test
    void normalizesNullAirAndNonPositiveStacksToEmpty() {
        assertNull(mapper.map(null));
        assertNull(mapper.map(TestItemStack.create(Material.AIR, 1, null)));
        assertNull(mapper.map(TestItemStack.create(Material.STONE, 0, null)));
    }

    @Test
    void clampsPluginSuppliedDamageToMaterialMaximum() {
        ItemSnapshot item = mapper.mapValues(
            Material.DIAMOND_PICKAXE,
            1,
            Integer.MAX_VALUE,
            null,
            null,
            false
        );
        assertEquals(item.getMaxDamage(), item.getDamage());
    }

    @Test
    void carriesArmorTrimLeatherColorAndGlintIntoStableItemSnapshot() {
        ArmorVisualDescriptor diamondVisual = new ArmorVisualDescriptor(
            ArmorVisualDescriptor.Slot.CHEST,
            "minecraft:diamond_chestplate", "minecraft:diamond",
            "minecraft:spire", "minecraft:redstone", null, true
        );
        ItemSnapshot diamond = mapper.mapValues(
            Material.DIAMOND_CHESTPLATE, 1, 0, null, null, true, diamondVisual
        );
        assertEquals(ArmorVisualDescriptor.Slot.CHEST, diamondVisual.getSlot());
        assertEquals("minecraft:diamond", diamondVisual.getEquipmentModelKey());
        assertEquals("minecraft:spire", diamondVisual.getTrimPatternKey());
        assertEquals("minecraft:redstone", diamondVisual.getTrimMaterialKey());
        assertTrue(diamondVisual.hasGlint());

        ArmorVisualDescriptor leatherVisual = new ArmorVisualDescriptor(
            ArmorVisualDescriptor.Slot.HEAD,
            "minecraft:leather_helmet", "minecraft:leather",
            null, null, Integer.valueOf(0x5a31c8), false
        );
        ItemSnapshot leather = mapper.mapValues(
            Material.LEATHER_HELMET, 1, 0, null, null, false, leatherVisual
        );
        assertEquals(Integer.valueOf(0x5a31c8), leather.getArmorVisual().getLeatherColor());
        assertNotEquals(diamondVisual.visualKey(), leather.getArmorVisual().visualKey());
    }

    @Test
    void mapsPaperEffectivePotionColorIntoStableSnapshot() {
        PotionMeta meta = (PotionMeta) Proxy.newProxyInstance(
            PotionMeta.class.getClassLoader(), new Class<?>[] { PotionMeta.class },
            (proxy, method, args) -> {
                if ("hasBasePotionType".equals(method.getName())) return true;
                if ("getBasePotionType".equals(method.getName())) return PotionType.POISON;
                if ("hasColor".equals(method.getName())) return true;
                if ("getColor".equals(method.getName()) || "computeEffectiveColor".equals(method.getName())) {
                    return Color.fromRGB(0x315ac8);
                }
                if ("clone".equals(method.getName())) return proxy;
                Class<?> type=method.getReturnType();
                if(type==boolean.class)return false;if(type==int.class)return 0;
                if(type==double.class)return 0.0d;if(type==float.class)return 0.0f;if(type==long.class)return 0L;
                return null;
            }
        );
        ItemSnapshot item = mapper.map(TestItemStack.create(Material.LINGERING_POTION, 2, meta));
        assertEquals("minecraft:lingering_potion", item.getPotionVisual().getItemTypeKey());
        assertEquals("minecraft:poison", item.getPotionVisual().getBasePotionKey());
        assertEquals(0x315ac8, item.getPotionVisual().getResolvedTintRgb());
        assertTrue(item.getPotionVisual().hasCustomColor());
    }

    static final class TestItemStack extends ItemStack {
        private Material material;
        private int amount;
        private ItemMeta meta;

        TestItemStack(Material material, int amount, ItemMeta meta) {
            super(material, Math.max(1, amount));
            this.material = material;
            this.amount = amount;
            this.meta = meta;
        }

        static TestItemStack create(Material material, int amount, ItemMeta meta) {
            try {
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                Field field = unsafeClass.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                Object unsafe = field.get(null);
                TestItemStack stack = (TestItemStack) unsafeClass
                    .getMethod("allocateInstance", Class.class).invoke(unsafe, TestItemStack.class);
                stack.material = material;
                stack.amount = amount;
                stack.meta = meta;
                return stack;
            } catch (Exception error) {
                throw new IllegalStateException("Could not allocate test ItemStack without a server", error);
            }
        }

        @Override public Material getType() { return material; }
        @Override public int getAmount() { return amount; }
        @Override public ItemMeta getItemMeta() { return meta; }
    }
}
