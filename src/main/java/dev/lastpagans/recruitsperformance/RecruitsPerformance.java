package dev.lastpagans.recruitsperformance;

import com.mojang.logging.LogUtils;
import dev.lastpagans.recruitsperformance.config.PerformanceConfig;
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
        LOGGER.info("Recruits Performance 0.4.0-alpha.2 loaded: target memory and formation API enabled");
    }
}

