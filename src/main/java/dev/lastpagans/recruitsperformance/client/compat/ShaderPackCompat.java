package dev.lastpagans.recruitsperformance.client.compat;

import java.lang.reflect.Method;

/** Selects a late instanced pass for Iris/Oculus without disabling GPU rendering. */
public final class ShaderPackCompat {
    private static final String[] API_CLASSES = {
            "net.irisshaders.iris.api.v0.IrisApi",
            "net.coderbot.iris.api.v0.IrisApi"
    };
    private static final long CHECK_INTERVAL_NANOS = 250_000_000L;

    private static boolean searched;
    private static Object api;
    private static Method shaderPackInUse;
    private static long lastCheck;
    private static boolean cachedActive;

    private ShaderPackCompat() {}

    public static boolean isShaderPackActive() {
        initialize();
        if (api == null || shaderPackInUse == null) return false;
        long now = System.nanoTime();
        if (now - lastCheck < CHECK_INTERVAL_NANOS) return cachedActive;
        lastCheck = now;
        try {
            cachedActive = (boolean) shaderPackInUse.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            cachedActive = false;
        }
        return cachedActive;
    }

    private static void initialize() {
        if (searched) return;
        searched = true;
        for (String className : API_CLASSES) {
            try {
                Class<?> type = Class.forName(className, false, ShaderPackCompat.class.getClassLoader());
                api = type.getMethod("getInstance").invoke(null);
                shaderPackInUse = type.getMethod("isShaderPackInUse");
                return;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Try the other package name used by 1.20.1 Oculus/Iris builds.
            }
        }
    }
}
