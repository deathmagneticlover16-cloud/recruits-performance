package dev.lastpagans.recruitsperformance.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps Cruor's own living wound painter and shared-UV remapper authoritative while the parent
 * Recruits renderer is replaced by the instanced crowd pass.
 */
public final class CruorBodyCompat {
    private static final String CLIENT_BLOOD = "com.raiiiden.cruor.client.ClientBlood";
    private static final String WOUND_LAYER = "com.raiiiden.cruor.client.body.WoundLayer";
    private static final Map<LivingEntityRenderer<?, ?>, Object> LAYERS = new WeakHashMap<>();
    private static boolean searched;
    private static Object bodies;
    private static Method body;
    private static Method render;
    private static Method layerRender;

    private CruorBodyCompat() {}

    /** Returns Cruor's registered layer only when this entity currently owns body-wound state. */
    public static Object woundLayer(LivingEntityRenderer<?, ?> renderer, LivingEntity entity) {
        if (!initialize()) return null;
        try {
            if (body.invoke(bodies, entity.getId()) == null) return null;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException unavailable) {
            return null;
        }
        if (LAYERS.containsKey(renderer)) return LAYERS.get(renderer);
        Object found = findLayer(renderer);
        LAYERS.put(renderer, found);
        return found;
    }

    public static void render(Object layer, PoseStack poseStack, MultiBufferSource buffers,
                              int packedLight, LivingEntity entity, float partialTick) {
        if (layer == null || render == null) return;
        try {
            render.invoke(layer, poseStack, buffers, packedLight, entity,
                    0.0F, 0.0F, partialTick, entity.tickCount + partialTick, 0.0F, 0.0F);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cruor wound layer is inaccessible", failure);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Cruor wound layer failed", cause);
        }
    }

    /** Registers a Recruits material layer with Cruor before its wound painter settles the hit. */
    public static void registerLayer(EntityModel<?> model, ResourceLocation texture,
                                     PoseStack poseStack, MultiBufferSource buffers,
                                     LivingEntity entity, int packedLight) {
        if (layerRender == null) return;
        try {
            layerRender.invoke(null, model, texture, poseStack, buffers,
                    entity, packedLight, false);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            // The body wound still renders against the base skin if a foreign layer cannot register.
        }
    }

    public static void clear() {
        LAYERS.clear();
    }

    private static boolean initialize() {
        if (searched) return bodies != null;
        searched = true;
        try {
            ClassLoader loader = CruorBodyCompat.class.getClassLoader();
            Class<?> clientBlood = Class.forName(CLIENT_BLOOD, false, loader);
            bodies = clientBlood.getField("BODIES").get(null);
            body = bodies.getClass().getDeclaredMethod("body", int.class);
            body.setAccessible(true);
            Class<?> layer = Class.forName(WOUND_LAYER, false, loader);
            for (Method candidate : layer.getMethods()) {
                Class<?>[] parameters = candidate.getParameterTypes();
                if (candidate.getName().equals("render") && parameters.length == 10
                        && parameters[0] == PoseStack.class
                        && MultiBufferSource.class.isAssignableFrom(parameters[1])) {
                    render = candidate;
                    break;
                }
            }
            if (render == null) throw new NoSuchMethodException("WoundLayer.render");
            Class<?> layerWounds = Class.forName(
                    "com.raiiiden.cruor.client.body.LayerWounds", false, loader);
            for (Method candidate : layerWounds.getMethods()) {
                Class<?>[] parameters = candidate.getParameterTypes();
                if (candidate.getName().equals("render") && parameters.length == 7
                        && Modifier.isStatic(candidate.getModifiers())
                        && EntityModel.class.isAssignableFrom(parameters[0])
                        && parameters[1] == ResourceLocation.class) {
                    layerRender = candidate;
                    break;
                }
            }
            if (layerRender == null) throw new NoSuchMethodException("LayerWounds.render");
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            bodies = null;
            body = null;
            render = null;
            layerRender = null;
            return false;
        }
    }

    private static Object findLayer(LivingEntityRenderer<?, ?> renderer) {
        for (Class<?> owner = renderer.getClass(); owner != null; owner = owner.getSuperclass()) {
            for (Field field : owner.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(renderer);
                    if (!(value instanceof List<?> layers)) continue;
                    for (Object layer : layers) {
                        if (layer != null && layer.getClass().getName().equals(WOUND_LAYER)) return layer;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Continue through the renderer hierarchy.
                }
            }
        }
        return null;
    }
}
