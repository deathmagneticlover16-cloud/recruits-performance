package dev.lastpagans.recruitsperformance.runtime;

import dev.lastpagans.recruitsperformance.config.PerformanceConfig;
import dev.lastpagans.recruitsperformance.runtime.state.CombatState;
import dev.lastpagans.recruitsperformance.runtime.state.OrderState;
import dev.lastpagans.recruitsperformance.runtime.state.SimulationTier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class SimulationTierPolicy {
    private SimulationTierPolicy() {}

    public static SimulationTier choose(LivingEntity recruit, OrderState order, CombatState combat, boolean moving,
                                        boolean urgentWake) {
        if (!PerformanceConfig.simulationLod.get()) return SimulationTier.HOT;
        if (urgentWake || combat == CombatState.ENGAGED || combat == CombatState.UNDER_FIRE || combat == CombatState.FLEEING) {
            return SimulationTier.HOT;
        }

        double nearest = nearestPlayerDistanceSquared(recruit);
        double hot = square(PerformanceConfig.hotPlayerDistance.get());
        double warm = square(PerformanceConfig.warmPlayerDistance.get());
        double cold = square(PerformanceConfig.coldPlayerDistance.get());

        return decide(order, combat, moving, urgentWake, nearest, hot, warm, cold);
    }

    static SimulationTier decide(OrderState order, CombatState combat, boolean moving, boolean urgentWake,
                                 double nearest, double hot, double warm, double cold) {
        if (urgentWake || combat == CombatState.ENGAGED || combat == CombatState.UNDER_FIRE || combat == CombatState.FLEEING) {
            return SimulationTier.HOT;
        }
        // Player proximity alone must never pin an entire singleplayer army HOT. Nearby idle
        // recruits are WARM; HOT is reserved for urgent or combat work.
        if (moving || order == OrderState.MOVE || order == OrderState.FOLLOW || order == OrderState.RTS_CONTROLLED || nearest <= hot) return SimulationTier.WARM;
        if (nearest <= warm) return SimulationTier.COLD;
        if (nearest <= cold || combat != CombatState.SAFE || order == OrderState.PATROL || order == OrderState.PROTECT) return SimulationTier.COLD;
        return SimulationTier.DORMANT;
    }

    private static double nearestPlayerDistanceSquared(LivingEntity recruit) {
        if (!(recruit.level() instanceof ServerLevel level)) return Double.POSITIVE_INFINITY;
        double best = Double.POSITIVE_INFINITY;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            best = Math.min(best, player.distanceToSqr(recruit));
        }
        return best;
    }

    private static double square(double value) { return value * value; }
}

