package dev.lastpagans.recruitsperformance.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.lastpagans.recruitsperformance.api.MovementAuthority;
import dev.lastpagans.recruitsperformance.config.PerformanceConfig;
import dev.lastpagans.recruitsperformance.runtime.RecruitRuntimeRegistry;
import dev.lastpagans.recruitsperformance.runtime.ServerRuntime;
import dev.lastpagans.recruitsperformance.runtime.state.SimulationTier;
import dev.lastpagans.recruitsperformance.scheduler.RecruitAIScheduler;
import dev.lastpagans.recruitsperformance.scheduler.WorkPriority;
import dev.lastpagans.recruitsperformance.telemetry.Metric;
import dev.lastpagans.recruitsperformance.telemetry.PerformanceMetrics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class RecruitsPerfCommands {
    private RecruitsPerfCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("recruitsperf")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("stats").executes(RecruitsPerfCommands::stats))
                .then(Commands.literal("resetstats").executes(RecruitsPerfCommands::reset))
                .then(Commands.literal("dump").executes(RecruitsPerfCommands::dump))
                .then(Commands.literal("scheduler").executes(RecruitsPerfCommands::scheduler))
                .then(Commands.literal("ai").executes(RecruitsPerfCommands::ai))
                .then(Commands.literal("targetmemory").executes(RecruitsPerfCommands::targetMemory))
                .then(Commands.literal("navigation").executes(RecruitsPerfCommands::navigation))
                .then(Commands.literal("rendering").executes(RecruitsPerfCommands::rendering))
                .then(Commands.literal("features").executes(RecruitsPerfCommands::features)));
    }

    private static int stats(CommandContext<CommandSourceStack> context) {
        ServerRuntime runtime = ServerRuntime.get(context.getSource().getServer());
        send(context, runtime.metrics().compact(runtime.registry(), runtime.scheduler()));
        return runtime.registry().size();
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        ServerRuntime.get(context.getSource().getServer()).metrics().reset();
        send(context, "Performance counters reset. Registry and scheduler state were preserved.");
        return 1;
    }

    private static int dump(CommandContext<CommandSourceStack> context) {
        ServerRuntime runtime = ServerRuntime.get(context.getSource().getServer());
        send(context, "--- Recruits Performance dump ---");
        stats(context);
        ai(context);
        targetMemory(context);
        scheduler(context);
        navigation(context);
        rendering(context);
        return 1;
    }

    private static int scheduler(CommandContext<CommandSourceStack> context) {
        ServerRuntime runtime = ServerRuntime.get(context.getSource().getServer());
        RecruitAIScheduler scheduler = runtime.scheduler();
        PerformanceMetrics metrics = runtime.metrics();
        send(context, "scheduler queue=" + scheduler.queueSize()
                + " critical=" + scheduler.queueSize(WorkPriority.CRITICAL)
                + " high=" + scheduler.queueSize(WorkPriority.HIGH)
                + " medium=" + scheduler.queueSize(WorkPriority.MEDIUM)
                + " low=" + scheduler.queueSize(WorkPriority.LOW)
                + " run=" + metrics.get(Metric.SCHEDULER_TASKS_RUN)
                + " overruns=" + metrics.get(Metric.SCHEDULER_BUDGET_OVERRUNS));
        return scheduler.queueSize();
    }

    private static int ai(CommandContext<CommandSourceStack> context) {
        ServerRuntime runtime = ServerRuntime.get(context.getSource().getServer());
        RecruitRuntimeRegistry registry = runtime.registry();
        PerformanceMetrics metrics = runtime.metrics();
        long updates = metrics.get(Metric.BRAIN_UPDATES);
        double avgMicros = updates == 0 ? 0.0 : metrics.get(Metric.BRAIN_NANOS) / 1000.0 / updates;
        send(context, "ai loaded=" + registry.size()
                + " hot=" + registry.countTier(SimulationTier.HOT)
                + " warm=" + registry.countTier(SimulationTier.WARM)
                + " cold=" + registry.countTier(SimulationTier.COLD)
                + " dormant=" + registry.countTier(SimulationTier.DORMANT)
                + " nativeAuthority=" + registry.countAuthority(MovementAuthority.NATIVE_RECRUITS)
                + " rtsAuthority=" + registry.countAuthority(MovementAuthority.RTS_FORMATION)
                + " updates=" + updates
                + " tierTransitions=" + metrics.get(Metric.SIMULATION_TIER_TRANSITIONS)
                + " avgUs=" + String.format(Locale.ROOT, "%.2f", avgMicros));
        return registry.size();
    }

    private static int targetMemory(CommandContext<CommandSourceStack> context) {
        ServerRuntime runtime = ServerRuntime.get(context.getSource().getServer());
        PerformanceMetrics m = runtime.metrics();
        send(context, "targetMemory active=" + runtime.targetMemory().activeCount()
                + " started=" + m.get(Metric.TARGET_MEMORY_STARTED)
                + " retainedClears=" + m.get(Metric.TARGET_MEMORY_RETENTIONS)
                + " reacquired=" + m.get(Metric.TARGET_MEMORY_REACQUIRED)
                + " expired=" + m.get(Metric.TARGET_MEMORY_EXPIRED)
                + " pursuitCommands=" + m.get(Metric.TARGET_MEMORY_PURSUIT_COMMANDS));
        return runtime.targetMemory().activeCount();
    }

    private static int navigation(CommandContext<CommandSourceStack> context) {
        PerformanceMetrics m = ServerRuntime.get(context.getSource().getServer()).metrics();
        send(context, "navigation requests=" + m.get(Metric.PATH_REQUESTS)
                + " recalculations=" + m.get(Metric.PATH_RECALCULATIONS)
                + " duplicates=" + m.get(Metric.DUPLICATE_ROUTE_REQUESTS)
                + " failures=" + m.get(Metric.PATH_FAILURES)
                + " localRecovery=" + m.get(Metric.LOCAL_RECOVERY_EVENTS)
                + " fullRecovery=" + m.get(Metric.FULL_RECOVERY_PATHS)
                + " targetMemoryPursuit=" + m.get(Metric.TARGET_MEMORY_PURSUIT_COMMANDS));
        return 1;
    }

    private static int rendering(CommandContext<CommandSourceStack> context) {
        PerformanceMetrics m = ServerRuntime.get(context.getSource().getServer()).metrics();
        send(context, "rendering visible=" + m.get(Metric.RENDER_VISIBLE)
                + " rendered=" + m.get(Metric.RENDER_RENDERED)
                + " lod0=" + m.get(Metric.RENDER_LOD0)
                + " lod1=" + m.get(Metric.RENDER_LOD1)
                + " lod2=" + m.get(Metric.RENDER_LOD2)
                + " lod3=" + m.get(Metric.RENDER_LOD3)
                + " culled=" + m.get(Metric.RENDER_CULLED)
                + " (client render instrumentation begins in Phase 12)");
        return 1;
    }

    private static int features(CommandContext<CommandSourceStack> context) {
        send(context, "features brain=" + PerformanceConfig.newBrain.get()
                + " scheduler=" + PerformanceConfig.aiScheduler.get()
                + " simLOD=" + PerformanceConfig.simulationLod.get()
                + " goalSuppression=" + PerformanceConfig.goalSuppression.get()
                + " perception=" + PerformanceConfig.sharedPerception.get()
                + " steering=" + PerformanceConfig.localSteering.get()
                + " navDedup=" + PerformanceConfig.navigationDeduplication.get()
                + " renderLOD=" + PerformanceConfig.clientRenderLod.get()
                + " batching=" + PerformanceConfig.renderBatching.get()
                + " networkLOD=" + PerformanceConfig.networkLod.get());
        send(context, "features targetMemory=" + PerformanceConfig.targetMemory.get()
                + " memoryPursuit=" + PerformanceConfig.targetMemoryPursuit.get()
                + " formationAPI=" + PerformanceConfig.formationApi.get());
        return 1;
    }

    private static void send(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal("[Recruits Performance] " + message), false);
    }
}
