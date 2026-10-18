package dev.lastpagans.recruitsperformance.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class PerformanceConfig {
    public static final ForgeConfigSpec SPEC;

    public static ForgeConfigSpec.BooleanValue newBrain;
    public static ForgeConfigSpec.BooleanValue aiScheduler;
    public static ForgeConfigSpec.BooleanValue simulationLod;
    public static ForgeConfigSpec.BooleanValue goalSuppression;
    public static ForgeConfigSpec.BooleanValue sharedPerception;
    public static ForgeConfigSpec.BooleanValue localSteering;
    public static ForgeConfigSpec.BooleanValue friendlyCollisionSuppression;
    public static ForgeConfigSpec.BooleanValue navigationDeduplication;
    public static ForgeConfigSpec.BooleanValue customTacticalNavigation;
    public static ForgeConfigSpec.BooleanValue clientRenderLod;
    public static ForgeConfigSpec.BooleanValue simplifiedArmor;
    public static ForgeConfigSpec.BooleanValue simplifiedWeapons;
    public static ForgeConfigSpec.BooleanValue renderBatching;
    public static ForgeConfigSpec.BooleanValue networkLod;
    public static ForgeConfigSpec.BooleanValue targetMemory;
    public static ForgeConfigSpec.BooleanValue targetMemoryPursuit;
    public static ForgeConfigSpec.BooleanValue formationApi;

    public static ForgeConfigSpec.BooleanValue debugLogSummary;
    public static ForgeConfigSpec.IntValue debugLogIntervalTicks;
    public static ForgeConfigSpec.IntValue registryAuditIntervalTicks;
    public static ForgeConfigSpec.IntValue rtsHeartbeatTimeoutTicks;

    public static ForgeConfigSpec.IntValue hotInterval;
    public static ForgeConfigSpec.IntValue warmInterval;
    public static ForgeConfigSpec.IntValue coldInterval;
    public static ForgeConfigSpec.IntValue dormantInterval;
    public static ForgeConfigSpec.DoubleValue hotPlayerDistance;
    public static ForgeConfigSpec.DoubleValue warmPlayerDistance;
    public static ForgeConfigSpec.DoubleValue coldPlayerDistance;
    public static ForgeConfigSpec.IntValue urgentWakeHotTicks;

    public static ForgeConfigSpec.IntValue targetMemoryTicks;
    public static ForgeConfigSpec.DoubleValue targetMemoryMaxDistance;
    public static ForgeConfigSpec.IntValue targetMemoryCheckInterval;
    public static ForgeConfigSpec.IntValue targetMemoryRepathInterval;
    public static ForgeConfigSpec.DoubleValue targetMemoryArrivalDistance;
    public static ForgeConfigSpec.DoubleValue targetMemoryPursuitSpeed;

    public static ForgeConfigSpec.IntValue criticalBudget;
    public static ForgeConfigSpec.IntValue highBudget;
    public static ForgeConfigSpec.IntValue mediumBudget;
    public static ForgeConfigSpec.IntValue lowBudget;
    public static ForgeConfigSpec.IntValue perceptionBudget;
    public static ForgeConfigSpec.IntValue lineOfSightBudget;
    public static ForgeConfigSpec.IntValue pathRequestBudget;
    public static ForgeConfigSpec.IntValue collisionQueryBudget;
    public static ForgeConfigSpec.IntValue maintenanceBudget;
    public static ForgeConfigSpec.IntValue dormantBudget;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("features");
        newBrain = b.comment("Enable lightweight runtime brains.").define("newBrain", true);
        aiScheduler = b.comment("Distribute Performance-mod work through the global scheduler.").define("aiScheduler", true);
        simulationLod = b.comment("Assign HOT/WARM/COLD/DORMANT update tiers.").define("simulationLod", true);
        goalSuppression = future(b, "goalSuppression");
        sharedPerception = future(b, "sharedPerception");
        localSteering = future(b, "localSteering");
        friendlyCollisionSuppression = future(b, "friendlyCollisionSuppression");
        navigationDeduplication = future(b, "navigationDeduplication");
        customTacticalNavigation = future(b, "customTacticalNavigation");
        clientRenderLod = future(b, "clientRenderLod");
        simplifiedArmor = future(b, "simplifiedArmor");
        simplifiedWeapons = future(b, "simplifiedWeapons");
        renderBatching = future(b, "renderBatching");
        networkLod = future(b, "networkLod");
        targetMemory = b.comment("Keep a valid combat target briefly after line of sight is lost.").define("targetMemory", true);
        targetMemoryPursuit = b.comment("Allow eligible recruits to investigate the target's last-known position.").define("targetMemoryPursuit", true);
        formationApi = b.comment("Expose deterministic formation geometry and stable slot-assignment helpers to RTS addons.").define("formationApi", true);
        b.pop();

        b.push("diagnostics");
        debugLogSummary = b.comment("Write a compact periodic summary to the server log.").define("debugLogSummary", false);
        debugLogIntervalTicks = b.defineInRange("debugLogIntervalTicks", 1200, 100, 72000);
        registryAuditIntervalTicks = b.comment("How often to remove stale registry entries without scanning the world.").defineInRange("registryAuditIntervalTicks", 1200, 100, 72000);
        rtsHeartbeatTimeoutTicks = b.comment("Fallback runtime authority when the RTS controller stops sending heartbeats.").defineInRange("rtsHeartbeatTimeoutTicks", 100, 20, 1200);
        b.pop();

        b.push("simulation");
        hotInterval = b.defineInRange("hotUpdateIntervalTicks", 1, 1, 100);
        warmInterval = b.defineInRange("warmUpdateIntervalTicks", 5, 1, 200);
        coldInterval = b.defineInRange("coldUpdateIntervalTicks", 20, 1, 1200);
        dormantInterval = b.defineInRange("dormantUpdateIntervalTicks", 100, 5, 7200);
        hotPlayerDistance = b.defineInRange("hotPlayerDistance", 32.0, 4.0, 512.0);
        warmPlayerDistance = b.defineInRange("warmPlayerDistance", 96.0, 8.0, 1024.0);
        coldPlayerDistance = b.defineInRange("coldPlayerDistance", 192.0, 16.0, 2048.0);
        urgentWakeHotTicks = b.comment("How long damage, commands, and emergencies force HOT simulation. LOAD does not force HOT.").defineInRange("urgentWakeHotTicks", 40, 0, 1200);
        b.pop();

        b.push("targetMemory");
        targetMemoryTicks = b.comment("Maximum ticks to remember a target after losing line of sight.").defineInRange("memoryTicks", 100, 1, 1200);
        targetMemoryMaxDistance = b.comment("Forget remembered targets beyond this distance.").defineInRange("maxDistance", 96.0, 8.0, 512.0);
        targetMemoryCheckInterval = b.comment("Ticks between visibility and validity checks while remembering a target.").defineInRange("checkIntervalTicks", 5, 1, 100);
        targetMemoryRepathInterval = b.comment("Minimum ticks between pursuit navigation requests.").defineInRange("repathIntervalTicks", 20, 2, 200);
        targetMemoryArrivalDistance = b.comment("Stop pursuing after reaching this distance from the last-known position.").defineInRange("arrivalDistance", 2.5, 0.5, 16.0);
        targetMemoryPursuitSpeed = b.comment("Navigation speed used while investigating the last-known position.").defineInRange("pursuitSpeed", 1.0, 0.1, 2.0);
        b.pop();

        b.push("scheduler");
        criticalBudget = b.defineInRange("criticalTasksPerTick", 512, 1, 100000);
        highBudget = b.defineInRange("highTasksPerTick", 256, 1, 100000);
        mediumBudget = b.defineInRange("mediumTasksPerTick", 128, 1, 100000);
        lowBudget = b.defineInRange("lowTasksPerTick", 64, 1, 100000);
        perceptionBudget = b.defineInRange("broadPerceptionUpdatesPerTick", 32, 0, 100000);
        lineOfSightBudget = b.defineInRange("lineOfSightTestsPerTick", 64, 0, 100000);
        pathRequestBudget = b.defineInRange("newPathRequestsPerTick", 16, 0, 100000);
        collisionQueryBudget = b.defineInRange("expensiveCollisionQueriesPerTick", 64, 0, 100000);
        maintenanceBudget = b.defineInRange("maintenanceUpdatesPerTick", 32, 0, 100000);
        dormantBudget = b.defineInRange("dormantUpdatesPerTick", 32, 0, 100000);
        b.pop();

        SPEC = b.build();
    }

    private PerformanceConfig() {}

    private static ForgeConfigSpec.BooleanValue future(ForgeConfigSpec.Builder b, String name) {
        return b.comment("Reserved for a later measured phase; leave disabled unless testing that subsystem.").define(name, false);
    }
}
