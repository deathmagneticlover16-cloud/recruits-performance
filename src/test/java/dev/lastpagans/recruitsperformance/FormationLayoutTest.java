package dev.lastpagans.recruitsperformance;

import dev.lastpagans.recruitsperformance.api.formation.*;

import java.util.*;

public final class FormationLayoutTest {
    public static void main(String[] args) {
        hollowCircleIsActuallyCircular();
        filledCircleIsNotAPile();
        squareHasOneStableGrid();
        assignmentsRemainStable();
        System.out.println("FormationLayoutTest passed");
    }

    private static void hollowCircleIsActuallyCircular() {
        List<FormationSlot> slots = FormationLayouts.generate(FormationRequest.of(FormationShape.HOLLOW_CIRCLE, 80));
        double radius = Math.hypot(slots.get(0).lateral(), slots.get(0).longitudinal());
        require(slots.size() == 80, "wrong hollow circle size");
        require(slots.stream().allMatch(slot -> Math.abs(Math.hypot(slot.lateral(), slot.longitudinal()) - radius) < 1.0E-7),
                "hollow circle has non-circular slots");
    }

    private static void filledCircleIsNotAPile() {
        List<FormationSlot> slots = FormationLayouts.generate(FormationRequest.of(FormationShape.CIRCLE, 100));
        long unique = slots.stream().map(slot -> String.format(Locale.ROOT, "%.5f:%.5f", slot.lateral(), slot.longitudinal())).distinct().count();
        double maxRadius = slots.stream().mapToDouble(slot -> Math.hypot(slot.lateral(), slot.longitudinal())).max().orElse(0.0);
        require(unique == 100, "filled circle contains duplicate/piled slots");
        require(maxRadius > 5.0, "filled circle did not spread units across an area");
    }

    private static void squareHasOneStableGrid() {
        List<FormationSlot> slots = FormationLayouts.generate(FormationRequest.of(FormationShape.SQUARE, 100));
        require(slots.size() == 100, "wrong square size");
        require(slots.stream().map(FormationSlot::rank).distinct().count() == 10, "square did not form ten coherent ranks");
        require(slots.stream().map(FormationSlot::file).distinct().count() == 10, "square did not form ten coherent files");
    }

    private static void assignmentsRemainStable() {
        UUID a = new UUID(0, 1);
        UUID b = new UUID(0, 2);
        UUID c = new UUID(0, 3);
        Map<UUID, Integer> previous = Map.of(a, 7, b, 2);
        Map<UUID, Integer> assigned = FormationAssignments.stable(List.of(c, a, b), previous, 10);
        require(assigned.get(a) == 7 && assigned.get(b) == 2, "valid assignments were not preserved");
        require(new HashSet<>(assigned.values()).size() == 3, "duplicate stable assignment");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
