package dev.lastpagans.recruitsperformance.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class CrowdRendererConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue enabled;
    public static final ForgeConfigSpec.BooleanValue gpuAnimation;
    public static final ForgeConfigSpec.BooleanValue diagnostics;
    public static final ForgeConfigSpec.IntValue maximumInstances;
    public static final ForgeConfigSpec.DoubleValue renderDistance;
    public static final ForgeConfigSpec.DoubleValue exactPoseDistance;
    public static final ForgeConfigSpec.DoubleValue geometryLodDistance;
    public static final ForgeConfigSpec.DoubleValue cruorStainDistance;
    public static final ForgeConfigSpec.IntValue meshCacheMegabytes;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("crowdRenderer");
        enabled = builder.comment("Replace supported Recruits renderers with the GPU crowd renderer.")
                .define("enabled", true);
        gpuAnimation = builder.comment("Evaluate shared skeletal animation in the vertex shader.")
                .define("gpuAnimation", true);
        diagnostics = builder.comment("Show crowd-renderer counters in the debug overlay.")
                .define("diagnostics", false);
        maximumInstances = builder.comment("Hard safety limit for visible virtual soldiers.")
                .defineInRange("maximumInstances", 8192, 128, 32768);
        renderDistance = builder.comment("Maximum crowd-render distance in blocks.")
                .defineInRange("renderDistance", 384.0, 32.0, 2048.0);
        exactPoseDistance = builder.comment("Small near-field distance for per-recruit source-model pose evaluation; farther recruits use shared cached GPU animation states.")
                .defineInRange("exactPoseDistance", 5.0, 0.0, 5.0);
        geometryLodDistance = builder.comment("Distance where secondary body and equipment geometry is omitted while armor and the core silhouette remain.")
                .defineInRange("geometryLodDistance", 160.0, 32.0, 1024.0);
        cruorStainDistance = builder.comment("Maximum distance for unique Cruor weapon-stain material passes; farther weapons retain their shared base material.")
                .defineInRange("cruorStainDistance", 16.0, 4.0, 64.0);
        meshCacheMegabytes = builder.comment("Approximate GPU mesh-cache budget.")
                .defineInRange("meshCacheMegabytes", 384, 64, 2048);
        builder.pop();
        SPEC = builder.build();
    }

    private CrowdRendererConfig() {}
}
