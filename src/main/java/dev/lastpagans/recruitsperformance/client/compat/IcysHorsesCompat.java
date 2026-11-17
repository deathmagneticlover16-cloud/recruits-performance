package dev.lastpagans.recruitsperformance.client.compat;

import dev.lastpagans.recruitsperformance.client.mesh.HorseModelAdapter;
import dev.lastpagans.recruitsperformance.runtime.RecruitIntrospector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

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
    private static final String MOTION_CLASS = "icy.betterhorses.net.client.render.BhRiderMotion";
    private static final String SEAT_CLASS = "icy.betterhorses.net.BhRiderSeat";
    private static final boolean SERIOUS_ANIMATIONS =
            ModList.get().isLoaded("recruits_serious_animations");
    private static volatile RiderAccess riderAccess;

    private IcysHorsesCompat() {}

    public static boolean isMountedRecruitHorse(LivingEntity entity) {
        if (!isClass(entity, ENTITY_CLASS)) return false;
        for (Entity passenger : entity.getPassengers()) {
            if (RecruitIntrospector.isRecruit(passenger)) return true;
        }
        return false;
    }

    public static boolean isRecruitRidingIcysHorse(LivingEntity entity) {
        Entity vehicle = entity.getVehicle();
        return RecruitIntrospector.isRecruit(entity) && vehicle != null && isClass(vehicle, ENTITY_CLASS);
    }

    /** Reads the exact saddle transform published by Icy's own horse renderer. */
    public static RiderTransform riderTransform(LivingEntity entity, float partialTick) {
        if (!isRecruitRidingIcysHorse(entity) || !(entity.getVehicle() instanceof LivingEntity horse)) {
            return RiderTransform.NONE;
        }
        try {
            RiderAccess access = riderAccess();
            Object motion = access.motionGet.invoke(null, horse.getId());
            Vec3 seat = (Vec3) access.seatApplied.invoke(null, horse.getId());
            float right = number(access.right.invoke(motion));
            float up = number(access.up.invoke(motion));
            float forward = number(access.forward.invoke(motion));
            float pitch = number(access.pitch.invoke(motion));
            float roll = number(access.roll.invoke(motion));
            float horseYawDegrees = Mth.rotLerp(partialTick, horse.yBodyRotO, horse.yBodyRot);
            float radians = horseYawDegrees * Mth.DEG_TO_RAD;
            float cosine = Mth.cos(radians);
            float sine = Mth.sin(radians);
            return new RiderTransform(
                    right * -cosine + forward * -sine - (float) seat.x,
                    up - (float) seat.y,
                    right * -sine + forward * cosine - (float) seat.z,
                    Mth.PI - radians,
                    pitch,
                    roll,
                    SERIOUS_ANIMATIONS ? -0.45F : 0.0F);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            throw new IllegalStateException("Icy's Better Horses 2.1.6 rider-motion contract changed", failure);
        }
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

    private static RiderAccess riderAccess() throws ReflectiveOperationException {
        RiderAccess cached = riderAccess;
        if (cached != null) return cached;
        synchronized (IcysHorsesCompat.class) {
            cached = riderAccess;
            if (cached != null) return cached;
            Class<?> motion = Class.forName(MOTION_CLASS);
            Class<?> seat = Class.forName(SEAT_CLASS);
            cached = new RiderAccess(
                    motion.getMethod("get", int.class),
                    motion.getMethod("right"),
                    motion.getMethod("up"),
                    motion.getMethod("forward"),
                    motion.getMethod("pitch"),
                    motion.getMethod("roll"),
                    seat.getMethod("applied", int.class));
            riderAccess = cached;
            return cached;
        }
    }

    private static float number(Object value) {
        return ((Number) value).floatValue();
    }

    public record Tack(Object model, ResourceLocation texture, int tint) {}

    public record RiderTransform(float x, float y, float z, float horseYaw, float pitch, float roll,
                                 float anchor) {
        private static final RiderTransform NONE = new RiderTransform(0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F);
    }

    private record RiderAccess(Method motionGet, Method right, Method up, Method forward,
                               Method pitch, Method roll, Method seatApplied) {}
}
