package cn.huohuas001.huhobot.inventory.potion;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotionVisualResolverTest {
    private final PotionVisualResolver resolver = new PotionVisualResolver();

    @Test
    void usesPaperEffectiveColorAndPreservesBaseTypeAndCustomPriority() {
        PotionVisualDescriptor healing = resolver.resolve(
            Material.POTION, meta(PotionType.HEALING, 0xf82423, false), false
        );
        PotionVisualDescriptor custom = resolver.resolve(
            Material.POTION, meta(PotionType.HEALING, 0x1234ab, true), true
        );

        assertEquals("minecraft:healing", healing.getBasePotionKey());
        assertEquals(0xf82423, healing.getResolvedTintRgb());
        assertFalse(healing.hasCustomColor());
        assertEquals(0x1234ab, custom.getResolvedTintRgb());
        assertTrue(custom.hasCustomColor());
        assertTrue(custom.hasGlint());
        assertNotEquals(healing.visualKey(), custom.visualKey());
    }

    @Test
    void coversAllFourPotionItemsAndRejectsOrdinaryItems() {
        for (Material material : new Material[] {
            Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION, Material.TIPPED_ARROW
        }) {
            PotionVisualDescriptor descriptor = resolver.resolve(
                material, meta(PotionType.POISON, 0x4e9331, false), false
            );
            assertEquals("minecraft:" + material.name().toLowerCase(java.util.Locale.ROOT), descriptor.getItemTypeKey());
            assertEquals(0x4e9331, descriptor.getResolvedTintRgb());
        }
        assertNull(resolver.resolve(Material.STONE, null, false));
    }

    private static PotionMeta meta(PotionType type, int effectiveRgb, boolean custom) {
        return (PotionMeta) Proxy.newProxyInstance(
            PotionMeta.class.getClassLoader(), new Class<?>[] { PotionMeta.class },
            (proxy, method, args) -> {
                String name = method.getName();
                if ("hasBasePotionType".equals(name)) return true;
                if ("getBasePotionType".equals(name)) return type;
                if ("hasColor".equals(name)) return custom;
                if ("getColor".equals(name) || "computeEffectiveColor".equals(name)) {
                    return Color.fromRGB(effectiveRgb);
                }
                if ("clone".equals(name)) return proxy;
                Class<?> returnType = method.getReturnType();
                if (returnType == boolean.class) return false;
                if (returnType == int.class) return 0;
                if (returnType == double.class) return 0.0d;
                if (returnType == float.class) return 0.0f;
                if (returnType == long.class) return 0L;
                return null;
            }
        );
    }
}
