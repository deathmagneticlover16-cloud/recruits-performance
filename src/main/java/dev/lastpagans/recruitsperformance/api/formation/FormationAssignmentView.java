package dev.lastpagans.recruitsperformance.api.formation;

import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public record FormationAssignmentView(UUID formationId, int slotIndex, Vec3 desiredPosition, Vec3 desiredVelocity) {}
