package dev.lastpagans.recruitsperformance.client.compat;

import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

/** Applies Player Animation Library poses without making it a required dependency. */
public final class PlayerAnimationCompat {
    private static final String GET_ANIMATION = "playerAnimator_getAnimation";

    private static final ClassValue<Optional<EntityAccess>> ENTITY_ACCESS = new ClassValue<>() {
        @Override
        protected Optional<EntityAccess> computeValue(Class<?> type) {
            try {
                Method getter = type.getMethod(GET_ANIMATION);
                return Optional.of(new EntityAccess(getter));
            } catch (NoSuchMethodException absent) {
                return Optional.empty();
            }
        }
    };

    private static final ClassValue<Optional<ApplierAccess>> APPLIER_ACCESS = new ClassValue<>() {
        @Override
        protected Optional<ApplierAccess> computeValue(Class<?> type) {
            try {
                Method transform = null;
                for (Method candidate : type.getMethods()) {
                    if (candidate.getName().equals("get3DTransform") && candidate.getParameterCount() == 3) {
                        transform = candidate;
                        break;
                    }
                }
                if (transform == null) return Optional.empty();
                Class<?> transformType = transform.getParameterTypes()[1];
                Class<?> vectorType = transform.getParameterTypes()[2];
                Field zero = vectorType.getField("ZERO");
                return Optional.of(new ApplierAccess(
                        type.getMethod("setTickDelta", float.class),
                        type.getMethod("isActive"),
                        transform,
                        enumValue(transformType, "POSITION"),
                        enumValue(transformType, "ROTATION"),
                        zero.get(null),
                        vectorType.getMethod("getX"),
                        vectorType.getMethod("getY"),
                        vectorType.getMethod("getZ")));
            } catch (NoSuchMethodException | NoSuchFieldException | IllegalAccessException absent) {
                return Optional.empty();
            }
        }
    };

    private PlayerAnimationCompat() {}

    /** Mirrors the renderer's setupRotations tick-delta update before HumanoidModel.setupAnim runs. */
    public static void prepare(LivingEntity entity, float partialTick) {
        Object applier = applier(entity);
        if (applier == null) return;
        Optional<ApplierAccess> access = APPLIER_ACCESS.get(applier.getClass());
        if (access.isEmpty()) return;
        try {
            access.get().setTickDelta.invoke(applier, partialTick);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            // The model keeps its ordinary Minecraft pose when the optional bridge is unavailable.
        }
    }

    public static Matrix4f itemTransform(LivingEntity entity, boolean leftHand, float partialTick) {
        Object applier = applier(entity);
        if (applier == null) return new Matrix4f();
        Optional<ApplierAccess> access = APPLIER_ACCESS.get(applier.getClass());
        if (access.isEmpty()) return new Matrix4f();
        try {
            ApplierAccess methods = access.get();
            methods.setTickDelta.invoke(applier, partialTick);
            if (!(boolean) methods.isActive.invoke(applier)) return new Matrix4f();
            String part = leftHand ? "leftItem" : "rightItem";
            Object position = methods.transform.invoke(applier, part, methods.positionType, methods.zeroVector);
            Object rotation = methods.transform.invoke(applier, part, methods.rotationType, methods.zeroVector);
            float x = component(position, methods.getX) / 16.0F;
            float y = component(position, methods.getY) / 16.0F;
            float z = component(position, methods.getZ) / 16.0F;
            float xRot = component(rotation, methods.getX);
            float yRot = component(rotation, methods.getY);
            float zRot = component(rotation, methods.getZ);
            return new Matrix4f().translation(x, y, z).rotateZ(zRot).rotateY(yRot).rotateX(xRot);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return new Matrix4f();
        }
    }

    /** Matches Mob Player Animator's setupRotations body transform for the whole model. */
    public static Matrix4f bodyTransform(LivingEntity entity, float partialTick) {
        Object applier = applier(entity);
        if (applier == null) return new Matrix4f();
        Optional<ApplierAccess> access = APPLIER_ACCESS.get(applier.getClass());
        if (access.isEmpty()) return new Matrix4f();
        try {
            ApplierAccess methods = access.get();
            methods.setTickDelta.invoke(applier, partialTick);
            if (!(boolean) methods.isActive.invoke(applier)) return new Matrix4f();
            Object position = methods.transform.invoke(applier, "body", methods.positionType, methods.zeroVector);
            Object rotation = methods.transform.invoke(applier, "body", methods.rotationType, methods.zeroVector);
            float x = component(position, methods.getX);
            float y = component(position, methods.getY);
            float z = component(position, methods.getZ);
            float xRot = component(rotation, methods.getX);
            float yRot = component(rotation, methods.getY);
            float zRot = component(rotation, methods.getZ);
            return new Matrix4f().translation(x, y + 0.7F, z)
                    .rotateZ(zRot).rotateY(yRot).rotateX(xRot)
                    .translate(0.0F, -0.7F, 0.0F);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return new Matrix4f();
        }
    }

    private static Object applier(LivingEntity entity) {
        Optional<EntityAccess> access = ENTITY_ACCESS.get(entity.getClass());
        if (access.isEmpty()) return null;
        try {
            return access.get().getter.invoke(entity);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), name);
    }

    private static float component(Object vector, Method component)
            throws InvocationTargetException, IllegalAccessException {
        return ((Number) component.invoke(vector)).floatValue();
    }

    private record EntityAccess(Method getter) {}
    private record ApplierAccess(Method setTickDelta, Method isActive, Method transform,
                                 Object positionType, Object rotationType,
                                 Object zeroVector, Method getX, Method getY, Method getZ) {}
}
