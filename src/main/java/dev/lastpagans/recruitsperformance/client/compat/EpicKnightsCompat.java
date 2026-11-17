package dev.lastpagans.recruitsperformance.client.compat;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Uses Epic Knights' registered renderer layer so every installed addon's real model is retained. */
public final class EpicKnightsCompat {
    private static final String LAYER_CLASS =
            "com.magistuarmory.client.render.entity.layer.ArmorDecorationLayer";
    private static final Map<LivingEntityRenderer<?, ?>, Layer> LAYERS = new WeakHashMap<>();

    private EpicKnightsCompat() {}

    public static Layer layer(LivingEntityRenderer<?, ?> renderer) {
        if (LAYERS.containsKey(renderer)) return LAYERS.get(renderer);
        Layer found = find(renderer);
        LAYERS.put(renderer, found);
        return found;
    }

    public static HumanoidModel<?> decorationModel(Layer layer, ResourceLocation location) {
        Object model = invoke(layer.access().decorationModel(), layer.instance(), location);
        if (model == null) {
            throw new IllegalStateException("Epic Knights has no registered decoration model for " + location);
        }
        if (!(model instanceof HumanoidModel<?> humanoid)) {
            throw new IllegalStateException("Epic Knights decoration is not humanoid: " + model.getClass());
        }
        return humanoid;
    }

    public static HumanoidModel<?> coatModel(Layer layer) {
        Object model = invoke(layer.access().coatModel(), layer.instance());
        if (!(model instanceof HumanoidModel<?> humanoid)) {
            throw new IllegalStateException("Epic Knights surcoat model is not humanoid");
        }
        return humanoid;
    }

    public static ResourceLocation texture(Layer layer, ResourceLocation location, boolean overlay) {
        Object texture = invoke(layer.access().texture(), layer.instance(), location, overlay);
        if (!(texture instanceof ResourceLocation resource)) {
            throw new IllegalStateException("Epic Knights returned no decoration texture for " + location);
        }
        return resource;
    }

    public static ResourceLocation baseTexture(Layer layer) {
        return resource(invoke(layer.access().baseTexture(), layer.instance()), "surcoat base");
    }

    public static ResourceLocation patternTexture(Layer layer, ResourceLocation pattern) {
        return resource(invoke(layer.access().patternTexture(), layer.instance(), pattern), "surcoat pattern");
    }

    private static Layer find(LivingEntityRenderer<?, ?> renderer) {
        for (Class<?> owner = renderer.getClass(); owner != null; owner = owner.getSuperclass()) {
            for (Field field : owner.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(renderer);
                    if (!(value instanceof List<?> candidates)) continue;
                    for (Object candidate : candidates) {
                        if (candidate == null || !isClass(candidate, LAYER_CLASS)) continue;
                        Class<?> type = candidate.getClass();
                        return new Layer(candidate, new Access(
                                type.getMethod("getArmorDecorationModel", ResourceLocation.class),
                                type.getMethod("getCoatModel"),
                                type.getMethod("getTexture", ResourceLocation.class, boolean.class),
                                type.getMethod("getBaseTexture"),
                                type.getMethod("getPatternTexture", ResourceLocation.class)));
                    }
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Epic Knights armor-layer contract changed", failure);
                } catch (RuntimeException ignored) {
                    // Continue through custom renderer fields until the real layer list is found.
                }
            }
        }
        return null;
    }

    private static boolean isClass(Object value, String name) {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(name)) return true;
        }
        return false;
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException failure) {
            throw new IllegalStateException("Epic Knights armor-layer invocation failed: " + method.getName(),
                    failure);
        }
    }

    private static ResourceLocation resource(Object value, String purpose) {
        if (value instanceof ResourceLocation resource) return resource;
        throw new IllegalStateException("Epic Knights returned no " + purpose + " texture");
    }

    public record Layer(Object instance, Access access) {}

    public record Access(Method decorationModel, Method coatModel, Method texture,
                         Method baseTexture, Method patternTexture) {}
}
