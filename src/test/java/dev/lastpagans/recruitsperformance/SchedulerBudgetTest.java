package dev.lastpagans.recruitsperformance;

import dev.lastpagans.recruitsperformance.scheduler.*;
import dev.lastpagans.recruitsperformance.telemetry.Metric;
import dev.lastpagans.recruitsperformance.telemetry.PerformanceMetrics;

import java.util.ArrayList;
import java.util.List;

public final class SchedulerBudgetTest {
    public static void main(String[] args) {
        PerformanceMetrics metrics = new PerformanceMetrics();
        RecruitAIScheduler scheduler = new RecruitAIScheduler(metrics,
                priority -> 64,
                type -> type == WorkType.DORMANT_UPDATE ? 32 : Integer.MAX_VALUE);
        List<String> order = new ArrayList<>();

        for (int i = 0; i < 40; i++) scheduler.schedule(work(0, WorkPriority.LOW, WorkType.DORMANT_UPDATE, () -> order.add("d")));
        scheduler.schedule(work(0, WorkPriority.LOW, WorkType.BRAIN_UPDATE, () -> order.add("brain")));
        scheduler.schedule(work(0, WorkPriority.CRITICAL, WorkType.BRAIN_UPDATE, () -> order.add("critical")));

        scheduler.tick(0);

        require(!order.isEmpty() && order.get(0).equals("critical"), "critical work did not run first");
        require(order.stream().filter("d"::equals).count() == 32, "dormant type budget was not enforced");
        require(order.contains("brain"), "exhausted dormant budget incorrectly blocked another work type");
        require(scheduler.queueSize() == 8, "unexpected deferred queue size: " + scheduler.queueSize());
        require(metrics.get(Metric.SCHEDULER_BUDGET_OVERRUNS) > 0, "budget overrun was not recorded");

        System.out.println("SchedulerBudgetTest passed");
    }

    private static ScheduledWork work(long due, WorkPriority priority, WorkType type, Runnable action) {
        return new ScheduledWork() {
            @Override public long dueGameTime() { return due; }
            @Override public WorkPriority priority() { return priority; }
            @Override public WorkType type() { return type; }
            @Override public void run(long gameTime) { action.run(); }
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
