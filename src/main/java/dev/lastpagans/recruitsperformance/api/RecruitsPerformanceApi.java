package dev.lastpagans.recruitsperformance.api;

import dev.lastpagans.recruitsperformance.runtime.RecruitRuntimeBrain;
import dev.lastpagans.recruitsperformance.runtime.ServerRuntime;
import dev.lastpagans.recruitsperformance.runtime.state.DirtyFlag;
import dev.lastpagans.recruitsperformance.api.formation.*;
import dev.lastpagans.recruitsperformance.config.PerformanceConfig;
import dev.lastpagans.recruitsperformance.telemetry.Metric;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

/**
 * Stable integration surface for RTS addons. Tactical ownership remains with
 * the RTS controller; this API supplies runtime state and deterministic slots.
 */
public final class RecruitsPerformanceApi {
    private RecruitsPerformanceApi() {}

    public static Optional<RecruitRuntimeView> view(MinecraftServer server, UUID recruitId) {
        return ServerRuntime.find(server).flatMap(runtime -> runtime.registry().get(recruitId)).map(RecruitRuntimeBrain::view);
    }

    public static boolean registerRtsControl(MinecraftServer server, UUID recruitId, UUID formationId) {
        return mutateAndWake(server, recruitId, brain -> brain.setRtsFormation(formationId));
    }

    public static boolean unregisterRtsControl(MinecraftServer server, UUID recruitId) {
        return mutateAndWake(server, recruitId, RecruitRuntimeBrain::clearRtsFormation);
    }

    public static boolean heartbeat(MinecraftServer server, UUID recruitId, long gameTime) {
        return mutate(server, recruitId, brain -> brain.rtsHeartbeat(gameTime));
    }

    public static boolean updateFormationIntent(MinecraftServer server, UUID recruitId, Vec3 slotPosition, Vec3 slotVelocity) {
        return mutateAndWake(server, recruitId, brain -> brain.setFormationIntent(slotPosition, slotVelocity));
    }

    public static boolean assignFormationSlot(MinecraftServer server, UUID recruitId, int slotIndex,
                                              Vec3 slotPosition, Vec3 slotVelocity) {
        return mutateAndWake(server, recruitId,
                brain -> brain.assignFormationSlot(slotIndex, slotPosition, slotVelocity));
    }

    public static Optional<FormationAssignmentView> formationAssignment(MinecraftServer server, UUID recruitId) {
        return ServerRuntime.find(server).flatMap(runtime -> runtime.registry().get(recruitId))
                .filter(brain -> brain.formationId() != null && brain.formationSlotIndex() >= 0)
                .map(brain -> new FormationAssignmentView(brain.formationId(), brain.formationSlotIndex(),
                        brain.desiredSlotPosition(), brain.desiredSlotVelocity()));
    }

    public static List<FormationSlot> createFormationLayout(MinecraftServer server, FormationRequest request) {
        if (!PerformanceConfig.formationApi.get()) return List.of();
        List<FormationSlot> slots = FormationLayouts.generate(request);
        ServerRuntime.find(server).ifPresent(runtime -> runtime.metrics().increment(Metric.FORMATION_LAYOUTS_GENERATED));
        return slots;
    }

    public static boolean requestAuthority(MinecraftServer server, UUID recruitId, MovementAuthority authority) {
        return mutateAndWake(server, recruitId, brain -> brain.setMovementAuthority(authority));
    }

    public static boolean markDirty(MinecraftServer server, UUID recruitId, DirtyFlag... flags) {
        EnumSet<DirtyFlag> set = EnumSet.noneOf(DirtyFlag.class);
        java.util.Collections.addAll(set, flags);
        return mutate(server, recruitId, brain -> brain.markDirty(set));
    }

    public static boolean wake(MinecraftServer server, UUID recruitId, WakeReason reason) {
        return ServerRuntime.find(server).map(runtime -> runtime.wake(recruitId, reason)).orElse(false);
    }

    private static boolean mutate(MinecraftServer server, UUID recruitId, java.util.function.Consumer<RecruitRuntimeBrain> action) {
        Optional<RecruitRuntimeBrain> brain = ServerRuntime.find(server).flatMap(runtime -> runtime.registry().get(recruitId));
        brain.ifPresent(action);
        return brain.isPresent();
    }

    private static boolean mutateAndWake(MinecraftServer server, UUID recruitId,
                                         java.util.function.Consumer<RecruitRuntimeBrain> action) {
        Optional<ServerRuntime> runtime = ServerRuntime.find(server);
        if (runtime.isEmpty()) return false;
        Optional<RecruitRuntimeBrain> brain = runtime.get().registry().get(recruitId);
        brain.ifPresent(action);
        if (brain.isPresent()) runtime.get().wake(recruitId, WakeReason.FORMATION_ORDER);
        return brain.isPresent();
    }
}
