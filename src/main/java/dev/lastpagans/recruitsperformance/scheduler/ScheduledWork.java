package dev.lastpagans.recruitsperformance.scheduler;

public interface ScheduledWork {
    long dueGameTime();
    WorkPriority priority();
    WorkType type();
    void run(long gameTime);
}

