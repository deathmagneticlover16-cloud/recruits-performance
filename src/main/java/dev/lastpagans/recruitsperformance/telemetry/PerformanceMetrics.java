package dev.lastpagans.recruitsperformance.telemetry;

import dev.lastpagans.recruitsperformance.runtime.RecruitRuntimeRegistry;
import dev.lastpagans.recruitsperformance.scheduler.RecruitAIScheduler;
import dev.lastpagans.recruitsperformance.runtime.state.SimulationTier;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public final class PerformanceMetrics {
    private final EnumMap<Metric, LongAdder> counters = new EnumMap<>(Metric.class);

    public PerformanceMetrics() {
        for (Metric metric : Metric.values()) counters.put(metric, new LongAdder());
    }

    public void increment(Metric metric) { counters.get(metric).increment(); }
    public void add(Metric metric, long value) { counters.get(metric).add(value); }
    public long get(Metric metric) { return counters.get(metric).sum(); }

    public void reset() {
        counters.values().forEach(LongAdder::reset);
    }

    public Map<Metric, Long> snapshot() {
        EnumMap<Metric, Long> result = new EnumMap<>(Metric.class);
        counters.forEach((key, value) -> result.put(key, value.sum()));
        return result;
    }

    public String compact(RecruitRuntimeRegistry registry, RecruitAIScheduler scheduler) {
        long updates = get(Metric.BRAIN_UPDATES);
        double avgMicros = updates == 0 ? 0.0 : get(Metric.BRAIN_NANOS) / 1000.0 / updates;
        return "loaded=" + registry.size()
                + " hot=" + registry.countTier(SimulationTier.HOT)
                + " warm=" + registry.countTier(SimulationTier.WARM)
                + " cold=" + registry.countTier(SimulationTier.COLD)
                + " dormant=" + registry.countTier(SimulationTier.DORMANT)
                + " queue=" + scheduler.queueSize()
                + " brainUpdates=" + updates
                + " avgBrainUs=" + String.format(java.util.Locale.ROOT, "%.2f", avgMicros)
                + " overruns=" + get(Metric.SCHEDULER_BUDGET_OVERRUNS);
    }
}

