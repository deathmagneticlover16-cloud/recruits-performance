package dev.lastpagans.recruitsperformance.client.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Snapshots Cruor's transient gear coatings for the separate GPU stain pass. */
public final class CruorCompat {
    private static boolean searched;
    private static Object gear;
    private static Field items;
    private static Method holder;
    private static Method item;
    private static Field coats;
    private static Field coatLocation;
    private static Field coatTexture;
    private static Field coatUsed;
    private static long cachedGameTime = Long.MIN_VALUE;
    private static Map<GearKey, Stain> cachedStains = Map.of();
    private static Map<GearKey, Object> cachedBlood = Map.of();

    private CruorCompat() {}

    public static Stain stain(LivingEntity entity, ItemStack stack) {
        return stain(entity, stack, false);
    }

    public static Stain refreshStain(LivingEntity entity, ItemStack stack) {
        return stain(entity, stack, true);
    }

    private static Stain stain(LivingEntity entity, ItemStack stack, boolean forceRefresh) {
        if (stack.isEmpty() || !initialize()) return null;
        long gameTime = entity.level().getGameTime();
        if (cachedGameTime != gameTime) refresh(gameTime);
        GearKey key = new GearKey(entity.getId(), stack.getItem());
        if (forceRefresh) refreshOne(key);
        return cachedStains.get(key);
    }

    private static void refresh(long gameTime) {
        Map<GearKey, Stain> snapshot = new HashMap<>();
        Map<GearKey, Object> bloodSnapshot = new HashMap<>();
        try {
            Object value = items.get(gear);
            if (value instanceof Map<?, ?> coatings) {
                for (Map.Entry<?, ?> entry : coatings.entrySet()) {
                    int entityId = (int) holder.invoke(entry.getKey());
                    Item stackItem = (Item) item.invoke(entry.getKey());
                    GearKey key = new GearKey(entityId, stackItem);
                    snapshot.put(key, inspect(entry.getValue()));
                    bloodSnapshot.put(key, entry.getValue());
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            snapshot.clear();
            bloodSnapshot.clear();
        }
        cachedStains = snapshot;
        cachedBlood = bloodSnapshot;
        cachedGameTime = gameTime;
    }

    private static void refreshOne(GearKey key) {
        Object blood = cachedBlood.get(key);
        if (blood == null) return;
        try {
            cachedStains.put(key, inspect(blood));
        } catch (IllegalAccessException ignored) {
            cachedStains.remove(key);
        }
    }

    private static Stain inspect(Object blood) throws IllegalAccessException {
        Object value = coats.get(blood);
        if (!(value instanceof Object[] entries)) return new Stain(null);
        ResourceLocation selected = null;
        long newest = Long.MIN_VALUE;
        for (Object coat : entries) {
            if (coat == null || coatTexture.get(coat) == null) continue;
            long used = coatUsed.getLong(coat);
            if (used >= newest) {
                newest = used;
                selected = (ResourceLocation) coatLocation.get(coat);
            }
        }
        return new Stain(selected);
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
            // GearBlood.Key is package-private even though its record accessors are public.
            // Method.invoke still checks the declaring class, so make both accessors accessible.
            holder = key.getDeclaredMethod("holder");
            item = key.getDeclaredMethod("item");
            holder.setAccessible(true);
            item.setAccessible(true);
            Class<?> blood = Class.forName("com.raiiiden.cruor.client.body.GearBlood$Blood", false,
                    CruorCompat.class.getClassLoader());
            Class<?> coat = Class.forName("com.raiiiden.cruor.client.body.GearBlood$Coat", false,
                    CruorCompat.class.getClassLoader());
            coats = blood.getDeclaredField("coats");
            coatLocation = coat.getDeclaredField("location");
            coatTexture = coat.getDeclaredField("texture");
            coatUsed = coat.getDeclaredField("used");
            coats.setAccessible(true);
            coatLocation.setAccessible(true);
            coatTexture.setAccessible(true);
            coatUsed.setAccessible(true);
            return true;
        } catch (ReflectiveOperationException | LinkageError absent) {
            gear = null;
            return false;
        }
    }

    private record GearKey(int entityId, Item item) {}

    public record Stain(ResourceLocation texture) {}
}
