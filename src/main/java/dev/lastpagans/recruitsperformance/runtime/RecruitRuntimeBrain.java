package dev.lastpagans.recruitsperformance.runtime;

import dev.lastpagans.recruitsperformance.api.*;
import dev.lastpagans.recruitsperformance.config.PerformanceConfig;
import dev.lastpagans.recruitsperformance.runtime.state.*;
import dev.lastpagans.recruitsperformance.scheduler.WorkPriority;
import dev.lastpagans.recruitsperformance.scheduler.WorkType;
import dev.lastpagans.recruitsperformance.telemetry.Metric;
import dev.lastpagans.recruitsperformance.telemetry.PerformanceMetrics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class RecruitRuntimeBrain {
    public record ScheduleTicket(long version, long dueGameTime) {}

    private final UUID recruitId;
    private WeakReference<LivingEntity> entity;
    private ResourceKey<Level> dimension;
    private boolean loaded;

    private OrderState orderState = OrderState.IDLE;
    private CombatState combatState = CombatState.SAFE;
    private MobilityState mobilityState = MobilityState.FOOT;
    private FormationState formationState = FormationState.NONE;
    private SimulationTier simulationTier = SimulationTier.HOT;
    private MovementAuthority movementAuthority = MovementAuthority.NATIVE_RECRUITS;
    private final EnumSet<DirtyFlag> dirtyFlags = EnumSet.allOf(DirtyFlag.class);

    private NativeRecruitSnapshot lastNative;
    private UUID groupId;
    private UUID formationId;
    private int formationSlotIndex = -1;
    private UUID assignedTarget;
    private UUID rememberedTarget;
    private Vec3 lastKnownTargetPosition;
    private long targetLostGameTime = Long.MIN_VALUE;
    private long lastTargetMemoryCheckGameTime = Long.MIN_VALUE;
    private long lastTargetMemoryPursuitGameTime = Long.MIN_VALUE;
    private Vec3 desiredSlotPosition;
    private Vec3 desiredSlotVelocity = Vec3.ZERO;
    private Vec3 lastPosition;
    private long lastUpdateGameTime;
    private long lastWakeGameTime;
    private long lastDamageGameTime = Long.MIN_VALUE;
    private long lastProjectileDamageGameTime = Long.MIN_VALUE;
    private long lastRtsHeartbeatGameTime = Long.MIN_VALUE;
    private WakeReason lastWakeReason = WakeReason.LOAD;

    private long scheduleVersion;
    private long scheduledDue = Long.MAX_VALUE;

    public RecruitRuntimeBrain(LivingEntity entity, long gameTime) {
        this.recruitId = entity.getUUID();
        attach(entity);
        this.lastPosition = entity.position();
        this.lastWakeGameTime = gameTime;
    }

    public void attach(LivingEntity value) {
        this.entity = new WeakReference<>(value);
        this.dimension = value.level().dimension();
        this.loaded = true;
    }

    public void detach() {
        loaded = false;
        entity.clear();
    }

    public boolean observe(long gameTime, PerformanceMetrics metrics) {
        LivingEntity recruit = entity.get();
        if (!loaded || recruit == null || recruit.isRemoved() || !recruit.isAlive()) return false;
        long start = System.nanoTime();
        NativeRecruitSnapshot current = RecruitIntrospector.snapshot(recruit, metrics);
        boolean moving = lastPosition != null && recruit.position().distanceToSqr(lastPosition) > 0.0025;

        if (lastNative != null && !lastNative.equals(current)) {
            metrics.increment(Metric.NATIVE_STATE_CHANGES);
            classifyNativeChanges(lastNative, current);
        }

        groupId = current.groupId();
        assignedTarget = current.targetId();
        orderState = deriveOrder(current);
        combatState = deriveCombat(current, gameTime);
        mobilityState = deriveMobility(recruit, current);
        formationState = formationId == null ? FormationState.NONE : FormationState.MEMBER;
        boolean urgentWake = lastWakeReason != WakeReason.LOAD
                && gameTime - lastWakeGameTime <= PerformanceConfig.urgentWakeHotTicks.get();
        SimulationTier newTier = SimulationTierPolicy.choose(recruit, orderState, combatState, moving, urgentWake);
        if (newTier != simulationTier) {
            dirtyFlags.add(DirtyFlag.SIMULATION_DIRTY);
            metrics.increment(Metric.SIMULATION_TIER_TRANSITIONS);
        }
        simulationTier = newTier;

        lastNative = current;
        lastPosition = recruit.position();
        lastUpdateGameTime = gameTime;
        watchdogRtsAuthority(gameTime, metrics);
        dirtyFlags.clear();
        metrics.increment(Metric.BRAIN_UPDATES);
        metrics.add(Metric.BRAIN_NANOS, System.nanoTime() - start);
        return true;
    }

    public void onDamage(long gameTime, boolean projectile) {
        lastDamageGameTime = gameTime;
        if (projectile) lastProjectileDamageGameTime = gameTime;
        combatState = projectile ? CombatState.UNDER_FIRE : CombatState.ALERT;
        markDirty(EnumSet.of(DirtyFlag.COMBAT_DIRTY, DirtyFlag.PERCEPTION_DIRTY, DirtyFlag.SIMULATION_DIRTY));
    }

    public void wake(WakeReason reason, long gameTime) {
        lastWakeReason = reason;
        lastWakeGameTime = gameTime;
        simulationTier = SimulationTier.HOT;
        dirtyFlags.add(DirtyFlag.SIMULATION_DIRTY);
    }

    public void setRtsFormation(UUID id) {
        formationId = Objects.requireNonNull(id, "formationId");
        formationState = FormationState.MEMBER;
        orderState = OrderState.RTS_CONTROLLED;
        setMovementAuthority(MovementAuthority.RTS_FORMATION);
        markDirty(EnumSet.of(DirtyFlag.ORDER_DIRTY, DirtyFlag.FORMATION_DIRTY, DirtyFlag.NAVIGATION_DIRTY));
    }

    public void clearRtsFormation() {
        formationId = null;
        formationSlotIndex = -1;
        desiredSlotPosition = null;
        desiredSlotVelocity = Vec3.ZERO;
        formationState = FormationState.NONE;
        setMovementAuthority(MovementAuthority.NATIVE_RECRUITS);
        markDirty(EnumSet.of(DirtyFlag.ORDER_DIRTY, DirtyFlag.FORMATION_DIRTY, DirtyFlag.NAVIGATION_DIRTY));
    }

    public void setFormationIntent(Vec3 position, Vec3 velocity) {
        desiredSlotPosition = Objects.requireNonNull(position, "position");
        desiredSlotVelocity = velocity == null ? Vec3.ZERO : velocity;
        markDirty(EnumSet.of(DirtyFlag.FORMATION_DIRTY, DirtyFlag.NAVIGATION_DIRTY));
    }

    public void assignFormationSlot(int slotIndex, Vec3 position, Vec3 velocity) {
        if (slotIndex < 0) throw new IllegalArgumentException("slotIndex must be non-negative");
        formationSlotIndex = slotIndex;
        setFormationIntent(position, velocity);
    }

    public void rtsHeartbeat(long gameTime) { lastRtsHeartbeatGameTime = gameTime; }

    public void setMovementAuthority(MovementAuthority authority) {
        if (movementAuthority != authority) {
            movementAuthority = Objects.requireNonNull(authority, "authority");
            dirtyFlags.add(DirtyFlag.NAVIGATION_DIRTY);
        }
    }

    public void markDirty(EnumSet<DirtyFlag> flags) { dirtyFlags.addAll(flags); }

    public boolean beginOrRetainTargetMemory(LivingEntity target, long gameTime) {
        if (target == null || !target.isAlive()) return false;
        if (!target.getUUID().equals(rememberedTarget)) {
            rememberedTarget = target.getUUID();
            lastKnownTargetPosition = target.position();
            targetLostGameTime = gameTime;
            lastTargetMemoryCheckGameTime = Long.MIN_VALUE;
            lastTargetMemoryPursuitGameTime = Long.MIN_VALUE;
            markDirty(EnumSet.of(DirtyFlag.TARGET_DIRTY, DirtyFlag.NAVIGATION_DIRTY));
        }
        return gameTime - targetLostGameTime <= PerformanceConfig.targetMemoryTicks.get();
    }

    public void clearTargetMemory() {
        rememberedTarget = null;
        lastKnownTargetPosition = null;
        targetLostGameTime = Long.MIN_VALUE;
        lastTargetMemoryCheckGameTime = Long.MIN_VALUE;
        lastTargetMemoryPursuitGameTime = Long.MIN_VALUE;
    }

    public boolean targetMemoryActive() { return rememberedTarget != null && lastKnownTargetPosition != null; }
    public boolean targetMemoryExpired(long gameTime) {
        return !targetMemoryActive() || gameTime - targetLostGameTime > PerformanceConfig.targetMemoryTicks.get();
    }
    public boolean targetMemoryCheckDue(long gameTime) {
        return gameTime - lastTargetMemoryCheckGameTime >= PerformanceConfig.targetMemoryCheckInterval.get();
    }
    public void markTargetMemoryChecked(long gameTime) { lastTargetMemoryCheckGameTime = gameTime; }
    public boolean targetMemoryPursuitDue(long gameTime) {
        return gameTime - lastTargetMemoryPursuitGameTime >= PerformanceConfig.targetMemoryRepathInterval.get();
    }
    public void markTargetMemoryPursuit(long gameTime) { lastTargetMemoryPursuitGameTime = gameTime; }
    public boolean permitsTargetMemoryPursuit() {
        return orderState == OrderState.IDLE || orderState == OrderState.PROTECT || orderState == OrderState.PATROL;
    }

    public ScheduleTicket requestSchedule(long dueGameTime) {
        if (dueGameTime >= scheduledDue) return null;
        scheduledDue = dueGameTime;
        return new ScheduleTicket(++scheduleVersion, dueGameTime);
    }

    public boolean acceptSchedule(ScheduleTicket ticket) {
        if (ticket == null || ticket.version != scheduleVersion || ticket.dueGameTime != scheduledDue) return false;
        scheduledDue = Long.MAX_VALUE;
        return true;
    }

    public long nextInterval() {
        return switch (simulationTier) {
            case HOT -> PerformanceConfig.hotInterval.get();
            case WARM -> PerformanceConfig.warmInterval.get();
            case COLD -> PerformanceConfig.coldInterval.get();
            case DORMANT -> PerformanceConfig.dormantInterval.get();
        };
    }

    public WorkPriority priority() {
        return switch (simulationTier) {
            case HOT -> WorkPriority.HIGH;
            case WARM -> WorkPriority.MEDIUM;
            case COLD, DORMANT -> WorkPriority.LOW;
        };
    }

    public WorkType workType() { return simulationTier == SimulationTier.DORMANT ? WorkType.DORMANT_UPDATE : WorkType.BRAIN_UPDATE; }

    public RecruitRuntimeView view() {
        Set<DirtyFlag> flags = dirtyFlags.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(dirtyFlags));
        return new RecruitRuntimeView(recruitId, dimension, groupId, formationId, orderState, combatState, mobilityState,
                formationState, simulationTier, movementAuthority, flags, lastUpdateGameTime, lastWakeGameTime, loaded);
    }

    public UUID id() { return recruitId; }
    public UUID groupId() { return groupId; }
    public UUID formationId() { return formationId; }
    public SimulationTier simulationTier() { return simulationTier; }
    public MovementAuthority movementAuthority() { return movementAuthority; }
    public ResourceKey<Level> dimension() { return dimension; }
    public boolean loaded() { return loaded && entity.get() != null; }
    public WakeReason lastWakeReason() { return lastWakeReason; }
    public Vec3 desiredSlotPosition() { return desiredSlotPosition; }
    public Vec3 desiredSlotVelocity() { return desiredSlotVelocity; }
    public int formationSlotIndex() { return formationSlotIndex; }
    public UUID rememberedTarget() { return rememberedTarget; }
    public Vec3 lastKnownTargetPosition() { return lastKnownTargetPosition; }
    public OrderState orderState() { return orderState; }
    public LivingEntity entityOrNull() { return entity.get(); }

    private void watchdogRtsAuthority(long gameTime, PerformanceMetrics metrics) {
        if (movementAuthority != MovementAuthority.RTS_FORMATION) return;
        if (lastRtsHeartbeatGameTime == Long.MIN_VALUE) lastRtsHeartbeatGameTime = gameTime;
        if (gameTime - lastRtsHeartbeatGameTime > PerformanceConfig.rtsHeartbeatTimeoutTicks.get()) {
            clearRtsFormation();
            metrics.increment(Metric.RTS_WATCHDOG_FALLBACKS);
        }
    }

    private void classifyNativeChanges(NativeRecruitSnapshot before, NativeRecruitSnapshot after) {
        if (before.followState() != after.followState() || before.shouldFollow() != after.shouldFollow()
                || before.shouldHold() != after.shouldHold() || before.shouldMove() != after.shouldMove()
                || before.shouldProtect() != after.shouldProtect() || before.shouldRest() != after.shouldRest()
                || !Objects.equals(before.movePosition(), after.movePosition())) {
            dirtyFlags.add(DirtyFlag.ORDER_DIRTY);
            dirtyFlags.add(DirtyFlag.NAVIGATION_DIRTY);
        }
        if (!Objects.equals(before.targetId(), after.targetId())) {
            dirtyFlags.add(DirtyFlag.TARGET_DIRTY);
            dirtyFlags.add(DirtyFlag.COMBAT_DIRTY);
        }
        if (!Objects.equals(before.groupId(), after.groupId())) dirtyFlags.add(DirtyFlag.FORMATION_DIRTY);
        if (before.shouldMount() != after.shouldMount()) dirtyFlags.add(DirtyFlag.MOBILITY_DIRTY);
        if (before.fleeing() != after.fleeing() || before.nativeCombatMode() != after.nativeCombatMode()) dirtyFlags.add(DirtyFlag.COMBAT_DIRTY);
    }

    private OrderState deriveOrder(NativeRecruitSnapshot state) {
        if (formationId != null || movementAuthority == MovementAuthority.RTS_FORMATION) return OrderState.RTS_CONTROLLED;
        if (state.shouldRest()) return OrderState.REST;
        if (state.shouldMove() || state.movePosition() != null) return OrderState.MOVE;
        return switch (state.followState()) {
            case 1 -> OrderState.FOLLOW;
            case 2, 3, 4 -> OrderState.HOLD;
            case 5 -> OrderState.PROTECT;
            case 6 -> OrderState.UPKEEP;
            default -> OrderState.IDLE;
        };
    }

    private CombatState deriveCombat(NativeRecruitSnapshot state, long gameTime) {
        if (state.fleeing()) return CombatState.FLEEING;
        if (gameTime - lastProjectileDamageGameTime <= 80) return CombatState.UNDER_FIRE;
        if (state.targetId() != null) return CombatState.ENGAGED;
        if (gameTime - lastDamageGameTime <= 40) return CombatState.ALERT;
        if (state.nativeCombatMode() == 1 || state.nativeCombatMode() == 2) return CombatState.SEARCHING;
        return CombatState.SAFE;
    }

    private MobilityState deriveMobility(LivingEntity recruit, NativeRecruitSnapshot state) {
        if (recruit.isPassenger()) {
            String vehicleClass = recruit.getVehicle() == null ? "" : recruit.getVehicle().getClass().getName().toLowerCase(java.util.Locale.ROOT);
            return vehicleClass.contains("ship") || vehicleClass.contains("boat") ? MobilityState.SHIP : MobilityState.MOUNTED;
        }
        if (state.shouldMount()) return MobilityState.MOUNTING;
        return MobilityState.FOOT;
    }
}
