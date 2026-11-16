package dev.lastpagans.recruitsperformance.client.compat;

import dev.lastpagans.recruitsperformance.client.mesh.HorseModelAdapter;
import dev.lastpagans.recruitsperformance.runtime.RecruitIntrospector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** Optional 2.1.6 adapter; no Icy's Better Horses classes are linked at compile time. */
public final class IcysHorsesCompat {
    private static final String ENTITY_CLASS = "icy.betterhorses.net.entity.BhBreedHorse";
    private static final String TACK_CLASS = "icy.betterhorses.net.client.render.BhTackLayer";
    private static final String HORN_CLASS = "icy.betterhorses.net.client.render.BhUnicornHornLayer";

    private IcysHorsesCompat() {}

    public static boolean isMountedRecruitHorse(LivingEntity entity) {
        if (!isClass(entity, ENTITY_CLASS)) return false;
        for (Entity passenger : entity.getPassengers()) {
            if (RecruitIntrospector.isRecruit(passenger)) return true;
        }
        return false;
    }

    public static List<Tack> tacks(LivingEntityRenderer<?, ?> renderer, LivingEntity horse) {
        if (booleanMethod(horse, "bh_hasStabilizerItem")) {
            throw new IllegalStateException("Icy horse stabilizer requires its GeckoLib renderer");
        }
        Object banner = method(horse, "bh_getBanner");
        if (banner instanceof ItemStack stack && !stack.isEmpty()) {
            throw new IllegalStateException("Icy horse banner requires its dynamic item renderer");
        }

        List<Tack> result = new ArrayList<>();
        for (Object layer : layers(renderer)) {
            if (isClass(layer, HORN_CLASS)) {
                throw new IllegalStateException("Icy horse unicorn horn requires its optional renderer");
            }
            if (!isClass(layer, TACK_CLASS)) continue;
            try {
                Field textureField = layer.getClass().getDeclaredField("textureGetter");
                Field tintField = layer.getClass().getDeclaredField("tint");
                Field modelField = layer.getClass().getDeclaredField(horse.isBaby() ? "babyModel" : "adultModel");
                textureField.setAccessible(true);
                tintField.setAccessible(true);
                modelField.setAccessible(true);
                @SuppressWarnings("unchecked") Function<LivingEntity, ResourceLocation> textureGetter =
                        (Function<LivingEntity, ResourceLocation>) textureField.get(layer);
                @SuppressWarnings("unchecked") ToIntFunction<LivingEntity> tintGetter =
                        (ToIntFunction<LivingEntity>) tintField.get(layer);
                ResourceLocation texture = textureGetter.apply(horse);
                if (texture == null) continue;
                Object model = modelField.get(layer);
                if (!HorseModelAdapter.supports(model)) {
                    throw new IllegalStateException("Unsupported Icy horse tack model " + model.getClass());
                }
                result.add(new Tack(model, texture, tintGetter.applyAsInt(horse)));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Icy's Better Horses 2.1.6 tack contract changed", failure);
            }
        }
        return result;
    }

    private static List<?> layers(Object renderer) {
        for (Class<?> type = renderer.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(renderer);
                    if (value instanceof List<?> list) return list;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Continue to the mapped superclass field.
                }
            }
        }
        throw new IllegalStateException("Could not access living renderer layers");
    }

    private static boolean booleanMethod(Object target, String name) {
        Object result = method(target, name);
        return result instanceof Boolean value && value;
    }

    private static Object method(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Icy's Better Horses 2.1.6 entity contract changed: " + name, failure);
        }
    }

    private static boolean isClass(Object value, String name) {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(name)) return true;
        }
        return false;
    }

    public record Tack(Object model, ResourceLocation texture, int tint) {}
}
