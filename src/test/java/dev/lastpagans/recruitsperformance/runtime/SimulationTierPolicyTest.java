package dev.lastpagans.recruitsperformance.runtime;

import dev.lastpagans.recruitsperformance.runtime.state.CombatState;
import dev.lastpagans.recruitsperformance.runtime.state.OrderState;
import dev.lastpagans.recruitsperformance.runtime.state.SimulationTier;

public final class SimulationTierPolicyTest {
    public static void main(String[] args) {
        double hot = 32.0 * 32.0;
        double warm = 96.0 * 96.0;
        double cold = 192.0 * 192.0;
        require(decide(OrderState.IDLE, CombatState.SAFE, false, false, 4.0, hot, warm, cold) == SimulationTier.WARM,
                "nearby idle recruit was incorrectly pinned HOT");
        require(decide(OrderState.IDLE, CombatState.ENGAGED, false, false, 40000.0, hot, warm, cold) == SimulationTier.HOT,
                "combat recruit was not HOT");
        require(decide(OrderState.IDLE, CombatState.SAFE, false, false, 100000.0, hot, warm, cold) == SimulationTier.DORMANT,
                "distant idle recruit was not DORMANT");
        require(decide(OrderState.MOVE, CombatState.SAFE, true, false, 100000.0, hot, warm, cold) == SimulationTier.WARM,
                "moving recruit was not WARM");
        System.out.println("SimulationTierPolicyTest passed");
    }

    private static SimulationTier decide(OrderState order, CombatState combat, boolean moving, boolean urgent,
                                         double nearest, double hot, double warm, double cold) {
        return SimulationTierPolicy.decide(order, combat, moving, urgent, nearest, hot, warm, cold);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
