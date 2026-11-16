package dev.lastpagans.recruitsperformance.client.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Keeps Cruor's client-owned, transient gear coating in Cruor's own renderer. */
public final class CruorCompat {
    private static boolean searched;
    private static Object gear;
    private static Field items;
    private static Method holder;
    private static Method item;

    private CruorCompat() {}

    public static boolean hasBlood(LivingEntity entity, ItemStack stack) {
        if (stack.isEmpty() || !initialize()) return false;
        try {
            Object value = items.get(gear);
            if (!(value instanceof Map<?, ?> coatings)) return false;
            for (Object key : coatings.keySet()) {
                if ((int) holder.invoke(key) == entity.getId() && item.invoke(key) == stack.getItem()) return true;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // If Cruor changes its private cache contract, normal item conversion remains available.
        }
        return false;
    }

    private static boolean initialize() {
        if (searched) return gear != null;
        searched = true;
        try {
            Class<?> clientBlood = Class.forName("com.raiiiden.cruor.client.ClientBlood", false,
                    CruorCompat.class.getClassLoader());
            gear = clientBlood.getField("GEAR").get(null);
            items = gear.getClass().getDeclaredField("items");
            items.setAccessible(true);
            Class<?> key = Class.forName("com.raiiiden.cruor.client.body.GearBlood$Key", false,
                    CruorCompat.class.getClassLoader());
            holder = key.getMethod("holder");
            item = key.getMethod("item");
            return true;
        } catch (ReflectiveOperationException | LinkageError absent) {
            gear = null;
            return false;
        }
    }
}
