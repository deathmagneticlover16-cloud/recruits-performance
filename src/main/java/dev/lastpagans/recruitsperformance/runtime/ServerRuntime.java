package dev.lastpagans.recruitsperformance.runtime;

import dev.lastpagans.recruitsperformance.RecruitsPerformance;
import dev.lastpagans.recruitsperformance.api.WakeReason;
import dev.lastpagans.recruitsperformance.config.PerformanceConfig;
import dev.lastpagans.recruitsperformance.scheduler.*;
import dev.lastpagans.recruitsperformance.telemetry.Metric;
import dev.lastpagans.recruitsperformance.telemetry.PerformanceMetrics;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

import java.util.*;

public final class ServerRuntime {
    private static final Map<MinecraftServer, ServerRuntime> INSTANCES = Collections.synchronizedMap(new WeakHashMap<>());

    private final MinecraftServer server;
    private final PerformanceMetrics metrics = new PerformanceMetrics();
    private final RecruitRuntimeRegistry registry = new RecruitRuntimeRegistry(metrics);
    private final RecruitAIScheduler scheduler = new RecruitAIScheduler(metrics);
    private long lastAudit;
    private long lastLog;

    private ServerRuntime(MinecraftServer server) { this.server = server; }

    public static ServerRuntime get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ServerRuntime::new);
    }

    public static Optional<ServerRuntime> find(MinecraftServer server) { return Optional.ofNullable(INSTANCES.get(server)); }

    public static void remove(MinecraftServer server) {
        ServerRuntime runtime = INSTANCES.remove(server);
        if (runtime != null) runtime.close();
    }

    public void onRecruitLoaded(LivingEntity recruit) {
        long time = recruit.level().getGameTime();
        RecruitRuntimeBrain brain = registry.register(recruit, time);
        brain.wake(WakeReason.LOAD, time);
    }

    public void onRecruitUnloaded(UUID recruitId, boolean died) {
        registry.unregister(recruitId, died);
    }

    public boolean wake(UUID recruitId, WakeReason reason) {
        Optional<RecruitRuntimeBrain> found = registry.get(recruitId);
        if (found.isEmpty()) return false;
        long time = currentGameTime();
        RecruitRuntimeBrain brain = found.get();
        brain.wake(reason, time);
        return true;
    }

    public void onDamage(UUID recruitId, boolean projectile, long gameTime) {
        registry.get(recruitId).ifPresent(brain -> {
            brain.onDamage(gameTime, projectile);
            brain.wake(projectile ? WakeReason.PROJECTILE_DAMAGE : WakeReason.DAMAGE, gameTime);
            metrics.increment(projectile ? Metric.PROJECTILE_WAKE_EVENTS : Metric.DAMAGE_WAKE_EVENTS);
        });
    }

    public void tick() {
        long gameTime = currentGameTime();
        // Only explicitly queued Performance-mod work runs here. Native Recruits goals,
        // navigation, move controls, attributes, and entity ticks are never throttled.
        scheduler.tick(gameTime);
        if (gameTime - lastAudit >= PerformanceConfig.registryAuditIntervalTicks.get()) {
            registry.audit();
            lastAudit = gameTime;
        }
        if (PerformanceConfig.debugLogSummary.get() && gameTime - lastLog >= PerformanceConfig.debugLogIntervalTicks.get()) {
            RecruitsPerformance.LOGGER.info("[Recruits Performance] {}", metrics.compact(registry, scheduler));
            lastLog = gameTime;
        }
    }

    public RecruitRuntimeRegistry registry() { return registry; }
    public RecruitAIScheduler scheduler() { return scheduler; }
    public PerformanceMetrics metrics() { return metrics; }

    private long currentGameTime() { return server.overworld() == null ? 0L : server.overworld().getGameTime(); }

    private void close() {
        scheduler.clear();
        registry.clear();
    }
}
