package dev.lastpagans.recruitsperformance.client.compat;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Reads Distant Horizons' public graphics configuration without making DH mandatory. */
public final class DistantHorizonsCompat {
    private static final Access ACCESS = createAccess();
    private static long nextRefreshNanos;
    private static double dhDistanceBlocks = Double.POSITIVE_INFINITY;

    private DistantHorizonsCompat() {}

    public static double renderDistance(double configuredDistance) {
        if (ACCESS == null) return configuredDistance;
        long now = System.nanoTime();
        if (now < nextRefreshNanos) return Math.min(configuredDistance, dhDistanceBlocks);
        nextRefreshNanos = now + 1_000_000_000L;
        try {
            Object configs = ACCESS.configs.get(null);
            if (configs == null) {
                dhDistanceBlocks = Double.POSITIVE_INFINITY;
                return configuredDistance;
            }
            Object graphics = ACCESS.graphics.invoke(configs);
            Object enabledValue = ACCESS.renderingEnabled.invoke(graphics);
            if (!Boolean.TRUE.equals(ACCESS.getValue.invoke(enabledValue))) {
                dhDistanceBlocks = Double.POSITIVE_INFINITY;
                return configuredDistance;
            }
            Object distanceValue = ACCESS.chunkRenderDistance.invoke(graphics);
            Object chunks = ACCESS.getValue.invoke(distanceValue);
            if (!(chunks instanceof Number number) || number.intValue() <= 0) {
                dhDistanceBlocks = Double.POSITIVE_INFINITY;
                return configuredDistance;
            }
            dhDistanceBlocks = number.doubleValue() * 16.0D;
            return Math.min(configuredDistance, dhDistanceBlocks);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            dhDistanceBlocks = Double.POSITIVE_INFINITY;
            return configuredDistance;
        }
    }

    private static Access createAccess() {
        try {
            ClassLoader loader = DistantHorizonsCompat.class.getClassLoader();
            Class<?> delayed = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed", false, loader);
            Class<?> config = Class.forName(
                    "com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig", false, loader);
            Class<?> graphics = Class.forName(
                    "com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig", false, loader);
            Class<?> value = Class.forName(
                    "com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue", false, loader);
            return new Access(delayed.getField("configs"), config.getMethod("graphics"),
                    graphics.getMethod("renderingEnabled"), graphics.getMethod("chunkRenderDistance"),
                    value.getMethod("getValue"));
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException | RuntimeException absent) {
            return null;
        }
    }

    private record Access(Field configs, Method graphics, Method renderingEnabled,
                          Method chunkRenderDistance, Method getValue) {}
}
