package dev.lastpagans.recruitsperformance;

import com.mojang.logging.LogUtils;
import dev.lastpagans.recruitsperformance.config.PerformanceConfig;
import dev.lastpagans.recruitsperformance.config.CrowdRendererConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(RecruitsPerformance.MOD_ID)
public final class RecruitsPerformance {
    public static final String MOD_ID = "recruitsperformance";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RecruitsPerformance() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PerformanceConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CrowdRendererConfig.SPEC,
                "recruits-performance-client.toml");
        LOGGER.info("Recruits Performance 1.0.0-alpha.13 loaded with GPU crowd rendering");
    }
}

