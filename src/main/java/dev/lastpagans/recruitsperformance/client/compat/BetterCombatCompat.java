package dev.lastpagans.recruitsperformance.client.compat;

import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Optional Better Mob Combat bridge without a compile-time dependency. */
public final class BetterCombatCompat {
    private static final String ACTIVE_ATTACK = "bettermobcombat$hasActiveAttackAnimation";
    private static final Map<Class<?>, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    private BetterCombatCompat() {}

    public static boolean hasActiveAttackAnimation(LivingEntity entity) {
        Optional<Method> method = METHODS.computeIfAbsent(entity.getClass(), BetterCombatCompat::findMethod);
        if (method.isEmpty()) return false;
        try {
            return (boolean) method.get().invoke(entity);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return true;
        }
    }

    private static Optional<Method> findMethod(Class<?> type) {
        try {
            return Optional.of(type.getMethod(ACTIVE_ATTACK));
        } catch (NoSuchMethodException absent) {
            return Optional.empty();
        }
    }
}
