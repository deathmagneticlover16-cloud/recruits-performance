package dev.lastpagans.recruitsperformance.runtime;

import dev.lastpagans.recruitsperformance.api.WakeReason;
import dev.lastpagans.recruitsperformance.config.PerformanceConfig;
import dev.lastpagans.recruitsperformance.telemetry.Metric;
import dev.lastpagans.recruitsperformance.telemetry.PerformanceMetrics;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Bounded bridge over Recruits' eager target clearing. It only owns the brief
 * interval after LOS is lost; native targeting remains authoritative otherwise.
 */
public final class TargetMemoryController {
    private final ServerRuntime runtime;
    private final PerformanceMetrics metrics;
    private final Set<UUID> active = new HashSet<>();
    private final Set<UUID> clearBypass = new HashSet<>();

    TargetMemoryController(ServerRuntime runtime, PerformanceMetrics metrics) {
        this.runtime = runtime;
        this.metrics = metrics;
    }

    public LivingEntity onTargetChange(Mob recruit, LivingEntity originalTarget, LivingEntity newTarget, long gameTime) {
        UUID recruitId = recruit.getUUID();
        RecruitRuntimeBrain brain = runtime.registry().get(recruitId).orElse(null);
        if (brain == null) return newTarget;

        if (clearBypass.remove(recruitId)) {
            brain.clearTargetMemory();
            return newTarget;
        }

        if (newTarget != null) {
            if (brain.targetMemoryActive()) {
                brain.clearTargetMemory();
                active.remove(recruitId);
                metrics.increment(Metric.TARGET_MEMORY_REACQUIRED);
            }
            runtime.wake(recruitId, WakeReason.TARGET_CHANGE);
            return newTarget;
        }

        if (!PerformanceConfig.targetMemory.get() || originalTarget == null || !validTarget(recruit, originalTarget)) {
            brain.clearTargetMemory();
            active.remove(recruitId);
            return null;
        }

        if (brain.targetMemoryActive() && originalTarget.getUUID().equals(brain.rememberedTarget())) {
            if (!brain.targetMemoryExpired(gameTime)) {
                metrics.increment(Metric.TARGET_MEMORY_RETENTIONS);
                return originalTarget;
            }
            return null;
        }

        // A visible target being cleared is normally an explicit native/command decision. Only
        // convert genuine LOS loss into memory so cease-fire and invalidation still work.
        if (recruit.getSensing().hasLineOfSight(originalTarget)) return null;

        if (brain.beginOrRetainTargetMemory(originalTarget, gameTime)) {
            active.add(recruitId);
            metrics.increment(Metric.TARGET_MEMORY_STARTED);
            runtime.wake(recruitId, WakeReason.TARGET_CHANGE);
            return originalTarget;
        }
        return null;
    }

    public void tick(long gameTime) {
        if (!PerformanceConfig.targetMemory.get() || active.isEmpty()) return;
        Iterator<UUID> iterator = active.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            RecruitRuntimeBrain brain = runtime.registry().get(id).orElse(null);
            if (brain == null || !brain.targetMemoryActive() || !(brain.entityOrNull() instanceof Mob recruit)) {
                iterator.remove();
                continue;
            }
            LivingEntity target = recruit.getTarget();
            if (target == null || !target.isAlive() || !target.getUUID().equals(brain.rememberedTarget())
                    || !validTarget(recruit, target) || brain.targetMemoryExpired(gameTime)) {
                expire(recruit, brain, iterator);
                continue;
            }
            if (!brain.targetMemoryCheckDue(gameTime)) continue;
            brain.markTargetMemoryChecked(gameTime);
            metrics.increment(Metric.LINE_OF_SIGHT_TESTS);
            if (recruit.getSensing().hasLineOfSight(target)) {
                brain.clearTargetMemory();
                iterator.remove();
                metrics.increment(Metric.TARGET_MEMORY_REACQUIRED);
                continue;
            }
            pursueLastKnownPosition(recruit, brain, gameTime);
        }
    }

    public int activeCount() { return active.size(); }

    public void remove(UUID recruitId) {
        active.remove(recruitId);
        clearBypass.remove(recruitId);
    }

    public void clear() {
        active.clear();
        clearBypass.clear();
    }

    private void pursueLastKnownPosition(Mob recruit, RecruitRuntimeBrain brain, long gameTime) {
        if (!PerformanceConfig.targetMemoryPursuit.get() || !brain.permitsTargetMemoryPursuit()
                || !brain.targetMemoryPursuitDue(gameTime)) return;
        Vec3 destination = brain.lastKnownTargetPosition();
        if (destination == null || recruit.position().distanceToSqr(destination)
                <= square(PerformanceConfig.targetMemoryArrivalDistance.get())) return;
        recruit.getNavigation().moveTo(destination.x, destination.y, destination.z,
                PerformanceConfig.targetMemoryPursuitSpeed.get());
        brain.markTargetMemoryPursuit(gameTime);
        metrics.increment(Metric.TARGET_MEMORY_PURSUIT_COMMANDS);
    }

    private void expire(Mob recruit, RecruitRuntimeBrain brain, Iterator<UUID> iterator) {
        clearBypass.add(recruit.getUUID());
        recruit.setTarget(null);
        clearBypass.remove(recruit.getUUID());
        brain.clearTargetMemory();
        iterator.remove();
        metrics.increment(Metric.TARGET_MEMORY_EXPIRED);
    }

    private static boolean validTarget(Mob recruit, LivingEntity target) {
        if (!target.isAlive() || target.isRemoved() || target.level() != recruit.level()) return false;
        if (target instanceof ServerPlayer player && (player.isCreative() || player.isSpectator())) return false;
        return recruit.distanceToSqr(target) <= square(PerformanceConfig.targetMemoryMaxDistance.get());
    }

    private static double square(double value) { return value * value; }
}
