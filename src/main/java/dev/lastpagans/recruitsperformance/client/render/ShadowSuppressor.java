package dev.lastpagans.recruitsperformance.client.render;

import dev.lastpagans.recruitsperformance.RecruitsPerformance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Disables the dispatcher's per-entity blob-shadow pass for converted recruit renderer instances. */
final class ShadowSuppressor {
    private static final Set<EntityRenderer<?>> DONE = Collections.newSetFromMap(new WeakHashMap<>());
    private static Field shadowRadius;
    private static boolean searched;

    private ShadowSuppressor() {}

    static void suppress(EntityRenderer<?> renderer) {
        if (!DONE.add(renderer)) return;
        try {
            Field field = field();
            if (field != null) field.setFloat(renderer, 0.0F);
        } catch (ReflectiveOperationException failure) {
            RecruitsPerformance.LOGGER.debug("Could not suppress legacy recruit shadow", failure);
        }
    }

    private static Field field() {
        if (searched) return shadowRadius;
        searched = true;
        try {
            shadowRadius = ObfuscationReflectionHelper.findField(EntityRenderer.class, "f_114477_");
            shadowRadius.setAccessible(true);
            return shadowRadius;
        } catch (RuntimeException ignored) {
            // Fall through for development mappings and nonstandard loader transformations.
        }
        for (Class<?> type = EntityRenderer.class; type != null; type = type.getSuperclass()) {
            try {
                Field candidate = type.getDeclaredField("shadowRadius");
                candidate.setAccessible(true);
                shadowRadius = candidate;
                break;
            } catch (NoSuchFieldException ignored) {
                // Continue through mapped or inherited renderer implementations.
            }
        }
        return shadowRadius;
    }
}
