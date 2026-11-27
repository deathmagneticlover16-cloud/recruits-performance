package dev.lastpagans.recruitsperformance.client.compat;

import net.minecraft.world.entity.Entity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Reuses Embeddium's entity occlusion result when the distant recruit sweep bypasses vanilla. */
public final class EmbeddiumCompat {
    private static final Access ACCESS = createAccess();

    private EmbeddiumCompat() {}

    public static boolean isVisible(Entity entity) {
        if (ACCESS == null) return true;
        try {
            Object renderer = ACCESS.instance.invoke(null);
            return renderer == null || (boolean) ACCESS.isEntityVisible.invoke(renderer, entity);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return true;
        }
    }

    private static Access createAccess() {
        try {
            Class<?> renderer = Class.forName(
                    "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer", false,
                    EmbeddiumCompat.class.getClassLoader());
            return new Access(renderer.getMethod("instanceNullable"),
                    renderer.getMethod("isEntityVisible", Entity.class));
        } catch (ClassNotFoundException | NoSuchMethodException | RuntimeException absent) {
            return null;
        }
    }

    private record Access(Method instance, Method isEntityVisible) {}
}
