package dev.lastpagans.recruitsperformance.client.event;

import dev.lastpagans.recruitsperformance.RecruitsPerformance;
import dev.lastpagans.recruitsperformance.client.compat.IcysHorsesCompat;
import dev.lastpagans.recruitsperformance.client.render.CrowdRenderer;
import dev.lastpagans.recruitsperformance.config.CrowdRendererConfig;
import dev.lastpagans.recruitsperformance.runtime.RecruitIntrospector;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class CrowdRenderEvents {
    private CrowdRenderEvents() {}

    @Mod.EventBusSubscriber(modid = RecruitsPerformance.MOD_ID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeEvents {
        private ForgeEvents() {}

        // Run before animation/anchor Pre listeners. If one of those listeners pushes a
        // PoseStack transform before cancellation, its matching Post listener never pops it.
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void beforeLiving(RenderLivingEvent.Pre<?, ?> event) {
            LivingEntity entity = event.getEntity();
            if (event.isCanceled() || (!RecruitIntrospector.isRecruit(entity)
                    && !IcysHorsesCompat.isMountedRecruitHorse(entity))) return;
            if (CrowdRenderer.get().submit(entity, event.getRenderer(), event.getPartialTick(), event.getPackedLight())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void renderCrowd(RenderLevelStageEvent event) {
            CrowdRenderer.get().render(event);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void renderShaderCrowdBeforeGui(RenderGuiEvent.Pre event) {
            CrowdRenderer.get().renderDeferredShaderCrowd();
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void finishShaderCrowdWithoutGui(TickEvent.RenderTickEvent event) {
            // RenderGuiEvent is not fired while the HUD is hidden. RenderTick END is only a
            // fallback; the GUI path consumes the deferred frame first during normal play.
            if (event.phase == TickEvent.Phase.END) CrowdRenderer.get().renderDeferredShaderCrowd();
        }

    }

    @Mod.EventBusSubscriber(modid = RecruitsPerformance.MOD_ID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void reloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> CrowdRenderer.get().reload());
        }
    }
}
