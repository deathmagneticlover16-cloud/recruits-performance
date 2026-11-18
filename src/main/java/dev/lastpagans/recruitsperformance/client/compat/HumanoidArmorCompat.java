package dev.lastpagans.recruitsperformance.client.compat;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EquipmentSlot;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Resolves the actual inner/outer armor models installed on a living renderer. */
public final class HumanoidArmorCompat {
    private static final String ARMOR_LAYER =
            "net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer";
    private static final Map<LivingEntityRenderer<?, ?>, Models> MODELS = new WeakHashMap<>();

    private HumanoidArmorCompat() {}

    public static HumanoidModel<?> model(LivingEntityRenderer<?, ?> renderer, EquipmentSlot slot) {
        Models models = MODELS.get(renderer);
        if (models == null) {
            models = find(renderer);
            MODELS.put(renderer, models);
        }
        return slot == EquipmentSlot.LEGS ? models.inner() : models.outer();
    }

    private static Models find(LivingEntityRenderer<?, ?> renderer) {
        for (Object layer : rendererLayers(renderer)) {
            if (layer == null || !isClass(layer, ARMOR_LAYER)) continue;
            Method getter = armorModelGetter(layer.getClass());
            if (getter != null) {
                return new Models(invoke(getter, layer, EquipmentSlot.LEGS),
                        invoke(getter, layer, EquipmentSlot.CHEST));
            }
            List<HumanoidModel<?>> models = armorModelFields(layer);
            if (models.size() >= 2) {
                // HumanoidArmorLayer declares innerModel before outerModel in 1.20.1.
                return new Models(models.get(0), models.get(1));
            }
            throw new IllegalStateException("Humanoid armor layer exposes no inner/outer models");
        }
        throw new IllegalStateException("Recruits renderer has no HumanoidArmorLayer");
    }

    private static Method armorModelGetter(Class<?> layerType) {
        for (Class<?> owner = layerType; owner != null; owner = owner.getSuperclass()) {
            for (Method method : owner.getDeclaredMethods()) {
                if (method.getParameterCount() != 1
                        || method.getParameterTypes()[0] != EquipmentSlot.class
                        || !HumanoidModel.class.isAssignableFrom(method.getReturnType())) continue;
                try {
                    method.setAccessible(true);
                    return method;
                } catch (RuntimeException ignored) {
                    // Fall through to direct model fields when access widening is unavailable.
                }
            }
        }
        return null;
    }

    private static HumanoidModel<?> invoke(Method method, Object target, EquipmentSlot slot) {
        try {
            Object value = method.invoke(target, slot);
            if (value instanceof HumanoidModel<?> model) return model;
            throw new IllegalStateException("Humanoid armor layer returned no model for " + slot);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException failure) {
            throw new IllegalStateException("Could not resolve the Recruits armor model for " + slot, failure);
        }
    }

    private static List<HumanoidModel<?>> armorModelFields(Object layer) {
        List<HumanoidModel<?>> result = new ArrayList<>(2);
        for (Class<?> owner = layer.getClass(); owner != null; owner = owner.getSuperclass()) {
            for (Field field : owner.getDeclaredFields()) {
                if (!HumanoidModel.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(layer);
                    if (value instanceof HumanoidModel<?> model) result.add(model);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Keep scanning mapped/superclass fields.
                }
            }
        }
        return result;
    }

    private static List<?> rendererLayers(LivingEntityRenderer<?, ?> renderer) {
        for (Class<?> owner = renderer.getClass(); owner != null; owner = owner.getSuperclass()) {
            for (Field field : owner.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(renderer);
                    if (value instanceof List<?> list && list.stream().anyMatch(
                            layer -> layer != null && isClass(layer, ARMOR_LAYER))) return list;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Continue until the living renderer's actual layer list is found.
                }
            }
        }
        throw new IllegalStateException("Could not access the Recruits renderer layers");
    }

    private static boolean isClass(Object value, String name) {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(name)) return true;
        }
        return false;
    }

    private record Models(HumanoidModel<?> inner, HumanoidModel<?> outer) {}
}
