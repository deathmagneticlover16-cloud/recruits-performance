package dev.lastpagans.recruitsperformance.client.compat;

import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Keeps Recruits Serious Animations from replacing Better Mob Combat's weapon poses or adding its
 * fallback melee swing on top. The conflicting layer is only suspended for the pose sample and is
 * restored before control returns to Minecraft.
 */
public final class BetterCombatCompat {
    private static final String ACTIVE_COMBAT = "bettermobcombat$isCombatAnimationActive";
    private static final String GET_STACK = "getAnimationStack";
    private static final String SPA_ACTION = "com.lastpagans.recruitsspa.RecruitActionLayer";

    private static final ClassValue<Optional<EntityAccess>> ENTITY_ACCESS = new ClassValue<>() {
        @Override
        protected Optional<EntityAccess> computeValue(Class<?> type) {
            try {
                return Optional.of(new EntityAccess(
                        type.getMethod(ACTIVE_COMBAT),
                        type.getMethod(GET_STACK)));
            } catch (NoSuchMethodException absent) {
                return Optional.empty();
            }
        }
    };

    private static final ClassValue<Optional<StackAccess>> STACK_ACCESS = new ClassValue<>() {
        @Override
        protected Optional<StackAccess> computeValue(Class<?> type) {
            try {
                Method accessor;
                try {
                    accessor = type.getMethod("mobplayeranimator$getLayers");
                } catch (NoSuchMethodException absent) {
                    accessor = null;
                }
                Field layers = null;
                if (accessor == null) {
                    for (Class<?> owner = type; owner != null && layers == null; owner = owner.getSuperclass()) {
                        try {
                            layers = owner.getDeclaredField("layers");
                            layers.setAccessible(true);
                        } catch (NoSuchFieldException ignored) {
                            // Continue through optional library implementation classes.
                        }
                    }
                }
                if (accessor == null && layers == null) return Optional.empty();
                return Optional.of(new StackAccess(accessor, layers));
            } catch (RuntimeException inaccessible) {
                return Optional.empty();
            }
        }
    };

    private static final ClassValue<Optional<Method>> PAIR_RIGHT = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            try {
                return Optional.of(type.getMethod("getRight"));
            } catch (NoSuchMethodException absent) {
                return Optional.empty();
            }
        }
    };

    private BetterCombatCompat() {}

    public static boolean hasActiveCombatAnimation(LivingEntity entity) {
        Optional<EntityAccess> access = ENTITY_ACCESS.get(entity.getClass());
        if (access.isEmpty()) return false;
        try {
            return (boolean) access.get().activeCombat().invoke(entity);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return false;
        }
    }

    public static AnimationScope preferBetterCombat(LivingEntity entity) {
        Optional<EntityAccess> entityAccess = ENTITY_ACCESS.get(entity.getClass());
        if (entityAccess.isEmpty()) return AnimationScope.EMPTY;
        List<?> layers = null;
        List<RemovedLayer> removed = new ArrayList<>(1);
        try {
            Object stack = entityAccess.get().animationStack().invoke(entity);
            if (stack == null) return AnimationScope.EMPTY;
            Optional<StackAccess> stackAccess = STACK_ACCESS.get(stack.getClass());
            if (stackAccess.isEmpty()) return AnimationScope.EMPTY;
            layers = stackAccess.get().layers(stack);
            if (layers == null || layers.isEmpty()) return AnimationScope.EMPTY;

            for (int index = layers.size() - 1; index >= 0; index--) {
                Object pair = layers.get(index);
                Optional<Method> right = PAIR_RIGHT.get(pair.getClass());
                if (right.isEmpty()) continue;
                Object animation = right.get().invoke(pair);
                if (animation != null && animation.getClass().getName().equals(SPA_ACTION)) {
                    removed.add(new RemovedLayer(index, pair));
                    layers.remove(index);
                }
            }
            if (removed.isEmpty()) return AnimationScope.EMPTY;
            removed.sort(Comparator.comparingInt(RemovedLayer::index));
            return new AnimationScope(layers, removed);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            restore(layers, removed);
            return AnimationScope.EMPTY;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restore(List<?> layers, List<RemovedLayer> removed) {
        if (layers == null || removed.isEmpty()) return;
        List target = layers;
        removed.sort(Comparator.comparingInt(RemovedLayer::index));
        for (RemovedLayer layer : removed) {
            target.add(Math.min(layer.index(), target.size()), layer.pair());
        }
    }

    private record EntityAccess(Method activeCombat, Method animationStack) {}

    private record StackAccess(Method accessor, Field field) {
        @SuppressWarnings("unchecked")
        private List<Object> layers(Object stack) throws InvocationTargetException, IllegalAccessException {
            Object value = accessor != null ? accessor.invoke(stack) : field.get(stack);
            return value instanceof List<?> list ? (List<Object>) list : null;
        }
    }

    private record RemovedLayer(int index, Object pair) {}

    public static final class AnimationScope implements AutoCloseable {
        private static final AnimationScope EMPTY = new AnimationScope(null, List.of());

        private List<?> layers;
        private final List<RemovedLayer> removed;

        private AnimationScope(List<?> layers, List<RemovedLayer> removed) {
            this.layers = layers;
            this.removed = removed;
        }

        @Override
        public void close() {
            restore(layers, removed);
            layers = null;
        }
    }
}
