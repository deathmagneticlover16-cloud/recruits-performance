package dev.lastpagans.recruitsperformance.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class CrowdRendererConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue enabled;
    public static final ForgeConfigSpec.BooleanValue gpuAnimation;
    public static final ForgeConfigSpec.BooleanValue optimizedShadows;
    public static final ForgeConfigSpec.BooleanValue diagnostics;
    public static final ForgeConfigSpec.IntValue maximumInstances;
    public static final ForgeConfigSpec.DoubleValue renderDistance;
    public static final ForgeConfigSpec.IntValue meshCacheMegabytes;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("crowdRenderer");
        enabled = builder.comment("Replace supported Recruits renderers with the GPU crowd renderer.")
                .define("enabled", true);
        gpuAnimation = builder.comment("Evaluate shared skeletal animation in the vertex shader.")
                .define("gpuAnimation", true);
        optimizedShadows = builder.comment("Suppress the legacy per-entity blob-shadow pass for converted recruits.")
                .define("optimizedShadows", true);
        diagnostics = builder.comment("Show crowd-renderer counters in the debug overlay.")
                .define("diagnostics", false);
        maximumInstances = builder.comment("Hard safety limit for visible virtual soldiers.")
                .defineInRange("maximumInstances", 8192, 128, 32768);
        renderDistance = builder.comment("Maximum crowd-render distance in blocks.")
                .defineInRange("renderDistance", 384.0, 32.0, 2048.0);
        meshCacheMegabytes = builder.comment("Approximate GPU mesh-cache budget.")
                .defineInRange("meshCacheMegabytes", 384, 64, 2048);
        builder.pop();
        SPEC = builder.build();
    }

    private CrowdRendererConfig() {}
}
