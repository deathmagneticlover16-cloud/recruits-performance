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
    private final TargetMemoryController targetMemory = new TargetMemoryController(this, metrics);
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
        scheduleBrain(brain, time, WorkPriority.HIGH);
    }

    public void onRecruitUnloaded(UUID recruitId, boolean died) {
        targetMemory.remove(recruitId);
        registry.unregister(recruitId, died);
    }

    public boolean wake(UUID recruitId, WakeReason reason) {
        Optional<RecruitRuntimeBrain> found = registry.get(recruitId);
        if (found.isEmpty()) return false;
        long time = currentGameTime();
        RecruitRuntimeBrain brain = found.get();
        brain.wake(reason, time);
        scheduleBrain(brain, time, reason == WakeReason.DAMAGE || reason == WakeReason.PROJECTILE_DAMAGE ? WorkPriority.CRITICAL : WorkPriority.HIGH);
        return true;
    }

    public void onDamage(UUID recruitId, boolean projectile, long gameTime) {
        registry.get(recruitId).ifPresent(brain -> {
            brain.onDamage(gameTime, projectile);
            brain.wake(projectile ? WakeReason.PROJECTILE_DAMAGE : WakeReason.DAMAGE, gameTime);
            metrics.increment(projectile ? Metric.PROJECTILE_WAKE_EVENTS : Metric.DAMAGE_WAKE_EVENTS);
            scheduleBrain(brain, gameTime, WorkPriority.CRITICAL);
        });
    }

    public void tick() {
        long gameTime = currentGameTime();
        if (PerformanceConfig.aiScheduler.get()) scheduler.tick(gameTime);
        else runUnscheduled(gameTime);
        targetMemory.tick(gameTime);

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
    public TargetMemoryController targetMemory() { return targetMemory; }

    private void scheduleBrain(RecruitRuntimeBrain brain, long due, WorkPriority priority) {
        if (!PerformanceConfig.newBrain.get() || !brain.loaded()) return;
        RecruitRuntimeBrain.ScheduleTicket ticket = brain.requestSchedule(due);
        if (ticket == null) return;
        WorkType type = brain.workType();
        scheduler.schedule(new ScheduledWork() {
            @Override public long dueGameTime() { return due; }
            @Override public WorkPriority priority() { return priority; }
            @Override public WorkType type() { return type; }
            @Override public void run(long gameTime) {
                if (!brain.acceptSchedule(ticket)) return;
                if (brain.observe(gameTime, metrics)) {
                    registry.refreshIndexes(brain);
                    scheduleBrain(brain, gameTime + brain.nextInterval(), brain.priority());
                }
            }
        });
    }

    private void runUnscheduled(long gameTime) {
        for (RecruitRuntimeBrain brain : List.copyOf(registry.all())) {
            if (brain.observe(gameTime, metrics)) registry.refreshIndexes(brain);
        }
    }

    private long currentGameTime() { return server.overworld() == null ? 0L : server.overworld().getGameTime(); }

    private void close() {
        scheduler.clear();
        targetMemory.clear();
        registry.clear();
    }
}
