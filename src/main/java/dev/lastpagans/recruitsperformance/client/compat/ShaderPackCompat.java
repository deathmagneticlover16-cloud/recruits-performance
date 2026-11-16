package dev.lastpagans.recruitsperformance.client.compat;

import dev.lastpagans.recruitsperformance.RecruitsPerformance;

import java.lang.reflect.Method;

/** Keeps raw OpenGL crowd draws out of shader-pack framebuffers they cannot describe. */
public final class ShaderPackCompat {
    private static final String[] API_CLASSES = {
            "net.irisshaders.iris.api.v0.IrisApi",
            "net.coderbot.iris.api.v0.IrisApi"
    };

    private static boolean searched;
    private static boolean warned;
    private static Object api;
    private static Method shaderPackInUse;

    private ShaderPackCompat() {}

    public static boolean isShaderPackActive() {
        initialize();
        if (api == null || shaderPackInUse == null) return false;
        try {
            boolean active = (boolean) shaderPackInUse.invoke(api);
            if (active && !warned) {
                warned = true;
                RecruitsPerformance.LOGGER.info(
                        "Shader pack detected; preserving the source entity renderer for correct shader-pack output");
            }
            return active;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            if (!warned) {
                warned = true;
                RecruitsPerformance.LOGGER.warn(
                        "Could not query the installed Iris/Oculus API; preserving the source renderer", failure);
            }
            return true;
        }
    }

    private static void initialize() {
        if (searched) return;
        searched = true;
        for (String className : API_CLASSES) {
            try {
                Class<?> type = Class.forName(className, false, ShaderPackCompat.class.getClassLoader());
                Method getInstance = type.getMethod("getInstance");
                api = getInstance.invoke(null);
                shaderPackInUse = type.getMethod("isShaderPackInUse");
                return;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Try the other package name used by 1.20.1 Oculus/Iris builds.
            }
        }
    }
}
