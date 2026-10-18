package dev.lastpagans.recruitsperformance.scheduler;

import dev.lastpagans.recruitsperformance.config.PerformanceConfig;
import dev.lastpagans.recruitsperformance.telemetry.Metric;
import dev.lastpagans.recruitsperformance.telemetry.PerformanceMetrics;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.PriorityQueue;
import java.util.function.ToIntFunction;

public final class RecruitAIScheduler {
    private record Entry(long sequence, ScheduledWork work) {}

    private final EnumMap<WorkPriority, EnumMap<WorkType, PriorityQueue<Entry>>> queues =
        new EnumMap<>(WorkPriority.class);
    private final PerformanceMetrics metrics;
    private final ToIntFunction<WorkPriority> priorityBudgets;
    private final ToIntFunction<WorkType> typeBudgets;
    private long sequence;

    public RecruitAIScheduler(PerformanceMetrics metrics) {
        this(metrics, RecruitAIScheduler::configuredPriorityBudget, RecruitAIScheduler::configuredTypeBudget);
    }

    public RecruitAIScheduler(PerformanceMetrics metrics, ToIntFunction<WorkPriority> priorityBudgets,
                              ToIntFunction<WorkType> typeBudgets) {
        this.metrics = metrics;
        this.priorityBudgets = priorityBudgets;
        this.typeBudgets = typeBudgets;
        Comparator<Entry> order = Comparator.comparingLong((Entry e) -> e.work.dueGameTime()).thenComparingLong(Entry::sequence);
        for (WorkPriority priority : WorkPriority.values()) {
            EnumMap<WorkType, PriorityQueue<Entry>> byType = new EnumMap<>(WorkType.class);
            for (WorkType type : WorkType.values()) byType.put(type, new PriorityQueue<>(order));
            queues.put(priority, byType);
        }
    }

    public void schedule(ScheduledWork work) {
        if (work == null) {
            metrics.increment(Metric.SCHEDULER_TASKS_REJECTED);
            return;
        }
        queues.get(work.priority()).get(work.type()).add(new Entry(sequence++, work));
    }

    public void tick(long gameTime) {
        EnumMap<WorkType, Integer> typeRemaining = new EnumMap<>(WorkType.class);
        for (WorkType type : WorkType.values()) typeRemaining.put(type, typeBudgets.applyAsInt(type));
        for (WorkPriority priority : WorkPriority.values()) {
            int priorityRemaining = priorityBudgets.applyAsInt(priority);
            EnumMap<WorkType, PriorityQueue<Entry>> byType = queues.get(priority);
            for (WorkType type : WorkType.values()) {
                PriorityQueue<Entry> queue = byType.get(type);
                int remaining = typeRemaining.getOrDefault(type, Integer.MAX_VALUE);
                while (priorityRemaining > 0 && remaining > 0 && !queue.isEmpty()
                    && queue.peek().work.dueGameTime() <= gameTime) {
                    Entry entry = queue.poll();
                    remaining--;
                    priorityRemaining--;
                    entry.work.run(gameTime);
                    metrics.increment(Metric.SCHEDULER_TASKS_RUN);
                }
                typeRemaining.put(type, remaining);
            }
            boolean dueWorkRemains = byType.values().stream()
                .anyMatch(queue -> !queue.isEmpty() && queue.peek().work.dueGameTime() <= gameTime);
            if (dueWorkRemains) {
                metrics.increment(Metric.SCHEDULER_BUDGET_OVERRUNS);
            }
        }
    }

    public int queueSize() {
        return queues.values().stream()
            .flatMap(byType -> byType.values().stream())
            .mapToInt(PriorityQueue::size)
            .sum();
    }

    public int queueSize(WorkPriority priority) {
        return queues.get(priority).values().stream().mapToInt(PriorityQueue::size).sum();
    }

    public void clear() {
        queues.values().forEach(byType -> byType.values().forEach(PriorityQueue::clear));
    }

    private static int configuredPriorityBudget(WorkPriority priority) {
        return switch (priority) {
            case CRITICAL -> PerformanceConfig.criticalBudget.get();
            case HIGH -> PerformanceConfig.highBudget.get();
            case MEDIUM -> PerformanceConfig.mediumBudget.get();
            case LOW -> PerformanceConfig.lowBudget.get();
        };
    }

    private static int configuredTypeBudget(WorkType type) {
        return switch (type) {
            case BRAIN_UPDATE -> Integer.MAX_VALUE;
            case PATH_REQUEST, INDIVIDUAL_RECOVERY_PATH -> PerformanceConfig.pathRequestBudget.get();
            case BROAD_PERCEPTION -> PerformanceConfig.perceptionBudget.get();
            case LINE_OF_SIGHT -> PerformanceConfig.lineOfSightBudget.get();
            case COLLISION_QUERY -> PerformanceConfig.collisionQueryBudget.get();
            case MAINTENANCE -> PerformanceConfig.maintenanceBudget.get();
            case DORMANT_UPDATE -> PerformanceConfig.dormantBudget.get();
        };
    }
}
