package dev.lastpagans.recruitsperformance.client.compat;

import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.WeakHashMap;

/** Optional Better Mob Combat bridge without a compile-time dependency. */
public final class BetterCombatCompat {
    private static final String ACTIVE_ATTACK = "bettermobcombat$hasActiveAttackAnimation";
    private static final String COMBO_COUNT = "getComboCount";
    private static final ClassValue<Optional<Method>> METHODS = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            try {
                return Optional.of(type.getMethod(ACTIVE_ATTACK));
            } catch (NoSuchMethodException absent) {
                return Optional.empty();
            }
        }
    };
    private static final ClassValue<Optional<Method>> COMBO_METHODS = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            try {
                return Optional.of(type.getMethod(COMBO_COUNT));
            } catch (NoSuchMethodException absent) {
                return Optional.empty();
            }
        }
    };
    private static final WeakHashMap<LivingEntity, State> STATES = new WeakHashMap<>();

    private BetterCombatCompat() {}

    public static boolean hasActiveAttackAnimation(LivingEntity entity) {
        return state(entity).active;
    }

    public static float animationPhase(LivingEntity entity, float partialTick) {
        State state = state(entity);
        return state.active ? entity.tickCount - state.startedTick + partialTick : 0.0F;
    }

    public static int combo(LivingEntity entity) {
        Optional<Method> method = COMBO_METHODS.get(entity.getClass());
        if (method.isEmpty()) return 0;
        try {
            return ((Number) method.get().invoke(entity)).intValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 0;
        }
    }

    private static State state(LivingEntity entity) {
        State cached = STATES.get(entity);
        if (cached != null && cached.sampledTick == entity.tickCount) return cached;
        boolean active = query(entity);
        int started = active && cached != null && cached.active ? cached.startedTick : entity.tickCount;
        State current = new State(entity.tickCount, started, active);
        STATES.put(entity, current);
        return current;
    }

    private static boolean query(LivingEntity entity) {
        Optional<Method> method = METHODS.get(entity.getClass());
        if (method.isEmpty()) return false;
        try {
            return (boolean) method.get().invoke(entity);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private record State(int sampledTick, int startedTick, boolean active) {}
}
