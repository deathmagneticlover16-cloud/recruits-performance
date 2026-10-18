package dev.lastpagans.recruitsperformance.runtime;

import dev.lastpagans.recruitsperformance.telemetry.Metric;
import dev.lastpagans.recruitsperformance.telemetry.PerformanceMetrics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Avoids a hard compile-time dependency while still recognizing the real Recruits base class. */
public final class RecruitIntrospector {
    private static final String ABSTRACT_RECRUIT = "com.talhanation.recruits.entities.AbstractRecruitEntity";
    private static final ConcurrentHashMap<Class<?>, Accessors> ACCESSORS = new ConcurrentHashMap<>();

    private RecruitIntrospector() {}

    public static boolean isRecruit(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        for (Class<?> type = entity.getClass(); type != null; type = type.getSuperclass()) {
            if (ABSTRACT_RECRUIT.equals(type.getName())) return true;
        }
        return false;
    }

    public static NativeRecruitSnapshot snapshot(LivingEntity recruit, PerformanceMetrics metrics) {
        Accessors a = ACCESSORS.computeIfAbsent(recruit.getClass(), Accessors::new);
        try {
            LivingEntity target = value(a.getTarget, recruit, LivingEntity.class, null);
            return new NativeRecruitSnapshot(
                    integer(a.getState, recruit),
                    integer(a.getFollowState, recruit),
                    bool(a.getShouldFollow, recruit),
                    bool(a.getShouldHoldPos, recruit),
                    bool(a.getShouldMovePos, recruit),
                    bool(a.getShouldProtect, recruit),
                    bool(a.getShouldRest, recruit),
                    bool(a.getShouldMount, recruit),
                    bool(a.isFleeing, recruit),
                    value(a.getGroup, recruit, UUID.class, null),
                    target == null ? null : target.getUUID(),
                    value(a.getMovePos, recruit, BlockPos.class, null)
            );
        } catch (ReflectiveOperationException | RuntimeException ex) {
            metrics.increment(Metric.REFLECTION_FAILURES);
            return new NativeRecruitSnapshot(0, 0, false, false, false, false, false, false, false, null, null, null);
        }
    }

    private static int integer(Method method, Object target) throws ReflectiveOperationException {
        Object value = invoke(method, target);
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static boolean bool(Method method, Object target) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(invoke(method, target));
    }

    private static Object invoke(Method method, Object target) throws ReflectiveOperationException {
        return method == null ? null : method.invoke(target);
    }

    private static <T> T value(Method method, Object target, Class<T> type, T fallback) throws ReflectiveOperationException {
        Object value = invoke(method, target);
        return type.isInstance(value) ? type.cast(value) : fallback;
    }

    private static final class Accessors {
        private final Method getState;
        private final Method getFollowState;
        private final Method getShouldFollow;
        private final Method getShouldHoldPos;
        private final Method getShouldMovePos;
        private final Method getShouldProtect;
        private final Method getShouldRest;
        private final Method getShouldMount;
        private final Method isFleeing;
        private final Method getGroup;
        private final Method getTarget;
        private final Method getMovePos;

        private Accessors(Class<?> type) {
            getState = find(type, "getState");
            getFollowState = find(type, "getFollowState");
            getShouldFollow = find(type, "getShouldFollow");
            getShouldHoldPos = find(type, "getShouldHoldPos");
            getShouldMovePos = find(type, "getShouldMovePos");
            getShouldProtect = find(type, "getShouldProtect");
            getShouldRest = find(type, "getShouldRest");
            getShouldMount = find(type, "getShouldMount");
            isFleeing = find(type, "isFleeing");
            getGroup = find(type, "getGroup");
            getTarget = find(type, "getTarget");
            getMovePos = find(type, "getMovePos");
        }

        private static Method find(Class<?> type, String name) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }
    }
}

