package dev.lastpagans.recruitsperformance.api;

import dev.lastpagans.recruitsperformance.runtime.state.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.UUID;

public record RecruitRuntimeView(
        UUID recruitId,
        ResourceKey<Level> dimension,
        UUID groupId,
        UUID formationId,
        OrderState orderState,
        CombatState combatState,
        MobilityState mobilityState,
        FormationState formationState,
        SimulationTier simulationTier,
        MovementAuthority movementAuthority,
        Set<DirtyFlag> dirtyFlags,
        long lastUpdateGameTime,
        long lastWakeGameTime,
        boolean loaded
) {}

