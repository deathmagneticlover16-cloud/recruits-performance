package dev.lastpagans.recruitsperformance.runtime;

import net.minecraft.core.BlockPos;

import java.util.UUID;

public record NativeRecruitSnapshot(
        int nativeCombatMode,
        int followState,
        boolean shouldFollow,
        boolean shouldHold,
        boolean shouldMove,
        boolean shouldProtect,
        boolean shouldRest,
        boolean shouldMount,
        boolean fleeing,
        UUID groupId,
        UUID targetId,
        BlockPos movePosition
) {}

