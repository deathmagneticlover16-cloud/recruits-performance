package dev.lastpagans.recruitsperformance.runtime;

import dev.lastpagans.recruitsperformance.api.MovementAuthority;
import dev.lastpagans.recruitsperformance.runtime.state.SimulationTier;
import dev.lastpagans.recruitsperformance.telemetry.Metric;
import dev.lastpagans.recruitsperformance.telemetry.PerformanceMetrics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.*;

public final class RecruitRuntimeRegistry {
    private final Map<UUID, RecruitRuntimeBrain> byId = new LinkedHashMap<>();
    private final Map<ResourceKey<Level>, Set<UUID>> byDimension = new HashMap<>();
    private final Map<UUID, Set<UUID>> byGroup = new HashMap<>();
    private final Map<UUID, Set<UUID>> byFormation = new HashMap<>();
    private final Map<UUID, UUID> indexedGroup = new HashMap<>();
    private final Map<UUID, UUID> indexedFormation = new HashMap<>();
    private final PerformanceMetrics metrics;

    public RecruitRuntimeRegistry(PerformanceMetrics metrics) { this.metrics = metrics; }

    public RecruitRuntimeBrain register(LivingEntity recruit, long gameTime) {
        RecruitRuntimeBrain existing = byId.get(recruit.getUUID());
        if (existing != null) {
            existing.attach(recruit);
            return existing;
        }
        RecruitRuntimeBrain brain = new RecruitRuntimeBrain(recruit, gameTime);
        byId.put(brain.id(), brain);
        byDimension.computeIfAbsent(brain.dimension(), ignored -> new LinkedHashSet<>()).add(brain.id());
        metrics.increment(Metric.RECRUIT_LOADS);
        return brain;
    }

    public void unregister(UUID recruitId, boolean died) {
        RecruitRuntimeBrain brain = byId.remove(recruitId);
        if (brain == null) return;
        brain.detach();
        removeFrom(byDimension, brain.dimension(), recruitId);
        removeFrom(byGroup, indexedGroup.remove(recruitId), recruitId);
        removeFrom(byFormation, indexedFormation.remove(recruitId), recruitId);
        metrics.increment(died ? Metric.RECRUIT_DEATHS : Metric.RECRUIT_UNLOADS);
    }

    public void refreshIndexes(RecruitRuntimeBrain brain) {
        refresh(byGroup, indexedGroup, brain.id(), brain.groupId());
        refresh(byFormation, indexedFormation, brain.id(), brain.formationId());
    }

    public Optional<RecruitRuntimeBrain> get(UUID id) { return Optional.ofNullable(byId.get(id)); }
    public Collection<RecruitRuntimeBrain> all() { return Collections.unmodifiableCollection(byId.values()); }
    public int size() { return byId.size(); }
    public int countTier(SimulationTier tier) { return (int) byId.values().stream().filter(b -> b.simulationTier() == tier).count(); }
    public int countAuthority(MovementAuthority authority) { return (int) byId.values().stream().filter(b -> b.movementAuthority() == authority).count(); }
    public Set<UUID> byGroup(UUID id) { return immutable(byGroup.get(id)); }
    public Set<UUID> byFormation(UUID id) { return immutable(byFormation.get(id)); }
    public Set<UUID> byDimension(ResourceKey<Level> id) { return immutable(byDimension.get(id)); }

    public int audit() {
        List<UUID> stale = byId.values().stream().filter(brain -> !brain.loaded()).map(RecruitRuntimeBrain::id).toList();
        stale.forEach(id -> unregister(id, false));
        metrics.increment(Metric.REGISTRY_AUDITS);
        metrics.add(Metric.REGISTRY_STALE_REMOVALS, stale.size());
        return stale.size();
    }

    public void clear() {
        byId.values().forEach(RecruitRuntimeBrain::detach);
        byId.clear();
        byDimension.clear();
        byGroup.clear();
        byFormation.clear();
        indexedGroup.clear();
        indexedFormation.clear();
    }

    private static <K> void refresh(Map<K, Set<UUID>> index, Map<UUID, K> reverse, UUID recruitId, K current) {
        K previous = reverse.get(recruitId);
        if (Objects.equals(previous, current)) return;
        removeFrom(index, previous, recruitId);
        if (current == null) reverse.remove(recruitId);
        else {
            reverse.put(recruitId, current);
            index.computeIfAbsent(current, ignored -> new LinkedHashSet<>()).add(recruitId);
        }
    }

    private static <K> void removeFrom(Map<K, Set<UUID>> index, K key, UUID recruitId) {
        if (key == null) return;
        Set<UUID> values = index.get(key);
        if (values == null) return;
        values.remove(recruitId);
        if (values.isEmpty()) index.remove(key);
    }

    private static Set<UUID> immutable(Set<UUID> ids) {
        return ids == null ? Set.of() : Collections.unmodifiableSet(ids);
    }
}

