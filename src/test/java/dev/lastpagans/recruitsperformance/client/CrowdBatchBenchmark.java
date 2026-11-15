package dev.lastpagans.recruitsperformance.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** CPU-side reproducibility harness. In-game GPU results still require the target hardware. */
public final class CrowdBatchBenchmark {
    private static final int[] COUNTS = {300, 500, 1_000, 2_000};
    private static final int WARMUPS = 20;
    private static final int RUNS = 100;

    public static void main(String[] args) {
        for (int count : COUNTS) benchmark(count, false);
        for (int count : COUNTS) benchmark(count, true);
        System.out.println("CrowdBatchBenchmark passed");
    }

    private static void benchmark(int count, boolean variedEquipment) {
        List<Visual> scene = scene(count, variedEquipment);
        for (int i = 0; i < WARMUPS; i++) batch(scene);
        long start = System.nanoTime();
        Map<Integer, Integer> batches = Map.of();
        for (int i = 0; i < RUNS; i++) batches = batch(scene);
        long nanos = System.nanoTime() - start;
        int maximumExpectedDraws = variedEquipment ? 96 : 8;
        require(batches.size() <= maximumExpectedDraws,
                "draw submissions scaled with soldier count: " + batches.size());
        double averageMicros = nanos / 1_000.0 / RUNS;
        System.out.printf("crowd=%d varied=%s batches=%d cpuBatchUs=%.2f%n",
                count, variedEquipment, batches.size(), averageMicros);
    }

    private static List<Visual> scene(int count, boolean varied) {
        List<Visual> result = new ArrayList<>(count * 6);
        for (int soldier = 0; soldier < count; soldier++) {
            int loadout = varied ? soldier % 24 : 0;
            result.add(new Visual(0));
            result.add(new Visual(1 + loadout % 8));
            result.add(new Visual(16 + loadout % 12));
            result.add(new Visual(32 + loadout % 6));
            result.add(new Visual(48 + loadout % 24));
            result.add(new Visual(80 + loadout % 16));
        }
        return result;
    }

    private static Map<Integer, Integer> batch(List<Visual> scene) {
        Map<Integer, Integer> batches = new HashMap<>();
        for (Visual visual : scene) batches.merge(visual.mesh, 1, Integer::sum);
        return batches;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Visual(int mesh) {}
}
