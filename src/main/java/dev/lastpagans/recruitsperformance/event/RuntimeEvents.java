package dev.lastpagans.recruitsperformance.event;

import dev.lastpagans.recruitsperformance.RecruitsPerformance;
import dev.lastpagans.recruitsperformance.api.WakeReason;
import dev.lastpagans.recruitsperformance.command.RecruitsPerfCommands;
import dev.lastpagans.recruitsperformance.runtime.RecruitIntrospector;
import dev.lastpagans.recruitsperformance.runtime.ServerRuntime;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RecruitsPerformance.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RuntimeEvents {
    private RuntimeEvents() {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity living) || !RecruitIntrospector.isRecruit(living)) return;
        ServerRuntime.get(event.getLevel().getServer()).onRecruitLoaded(living);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !RecruitIntrospector.isRecruit(event.getEntity())) return;
        ServerRuntime.get(event.getLevel().getServer()).onRecruitUnloaded(event.getEntity().getUUID(), false);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide() || !RecruitIntrospector.isRecruit(event.getEntity())) return;
        ServerRuntime.get(event.getEntity().getServer()).onRecruitUnloaded(event.getEntity().getUUID(), true);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !RecruitIntrospector.isRecruit(entity)) return;
        boolean projectile = event.getSource().getDirectEntity() instanceof Projectile;
        ServerRuntime runtime = ServerRuntime.get(entity.getServer());
        runtime.onDamage(entity.getUUID(), projectile, entity.level().getGameTime());
        if (event.getSource().is(DamageTypeTags.IS_FIRE)) runtime.wake(entity.getUUID(), WakeReason.FIRE);
        if (event.getSource().is(DamageTypeTags.IS_EXPLOSION)) runtime.wake(entity.getUUID(), WakeReason.EXPLOSION);
    }

    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob recruit) || recruit.level().isClientSide()
                || !RecruitIntrospector.isRecruit(recruit)) return;
        LivingEntity replacement = ServerRuntime.get(recruit.getServer()).targetMemory().onTargetChange(
                recruit, event.getOriginalTarget(), event.getNewTarget(), recruit.level().getGameTime());
        if (replacement != event.getNewTarget()) event.setNewTarget(replacement);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) ServerRuntime.get(event.getServer()).tick();
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        RecruitsPerfCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ServerRuntime.remove(event.getServer());
    }
}

