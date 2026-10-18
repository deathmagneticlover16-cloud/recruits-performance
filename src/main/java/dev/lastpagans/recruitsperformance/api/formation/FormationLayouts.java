package dev.lastpagans.recruitsperformance.api.formation;

import java.util.ArrayList;
import java.util.List;

/** Deterministic geometry only. Movement and tactical ownership remain with the RTS controller. */
public final class FormationLayouts {
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    private FormationLayouts() {}

    public static List<FormationSlot> generate(FormationRequest request) {
        if (request.unitCount() == 0) return List.of();
        List<FormationSlot> result = switch (request.shape()) {
            case LINE -> rectangular(request, request.unitCount());
            case COLUMN -> rectangular(request, 1);
            case RECTANGLE -> rectangular(request, automaticFiles(request));
            case SQUARE -> rectangular(request, (int) Math.ceil(Math.sqrt(request.unitCount())));
            case HOLLOW_RECTANGLE -> hollowRectangle(request, request.files() > 1 ? request.files() : automaticFiles(request), false);
            case HOLLOW_SQUARE -> hollowRectangle(request, 0, true);
            case CIRCLE -> filledCircle(request);
            case HOLLOW_CIRCLE -> hollowCircle(request);
            case WEDGE -> wedge(request);
        };
        return List.copyOf(result);
    }

    private static int automaticFiles(FormationRequest request) {
        return request.files() > 0 ? request.files() : Math.max(1, (int) Math.ceil(Math.sqrt(request.unitCount())));
    }

    private static List<FormationSlot> rectangular(FormationRequest request, int files) {
        files = Math.max(1, files);
        int ranks = (int) Math.ceil(request.unitCount() / (double) files);
        List<FormationSlot> slots = new ArrayList<>(request.unitCount());
        for (int index = 0; index < request.unitCount(); index++) {
            int rank = index / files;
            int file = index % files;
            int inRank = Math.min(files, request.unitCount() - rank * files);
            double lateral = (file - (inRank - 1) / 2.0) * request.lateralSpacing();
            double longitudinal = ((ranks - 1) / 2.0 - rank) * request.depthSpacing();
            slots.add(new FormationSlot(index, lateral, longitudinal, rank, file));
        }
        return slots;
    }

    private static List<FormationSlot> hollowRectangle(FormationRequest request, int requestedWidth, boolean square) {
        int count = request.unitCount();
        if (count == 1) return List.of(new FormationSlot(0, 0.0, 0.0, 0, 0));
        int width;
        int height;
        if (square) {
            width = height = Math.max(2, (int) Math.ceil((count + 4) / 4.0));
        } else {
            width = Math.max(2, requestedWidth);
            height = 2;
            while (perimeterCapacity(width, height) < count) height++;
        }

        List<double[]> perimeter = new ArrayList<>(perimeterCapacity(width, height));
        for (int x = 0; x < width; x++) perimeter.add(point(x, 0, width, height, request));
        for (int z = 1; z < height; z++) perimeter.add(point(width - 1, z, width, height, request));
        for (int x = width - 2; x >= 0; x--) perimeter.add(point(x, height - 1, width, height, request));
        for (int z = height - 2; z > 0; z--) perimeter.add(point(0, z, width, height, request));

        List<FormationSlot> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int candidate = (int) Math.floor(i * perimeter.size() / (double) count);
            double[] point = perimeter.get(candidate);
            slots.add(new FormationSlot(i, point[0], point[1], -1, -1));
        }
        return slots;
    }

    private static List<FormationSlot> filledCircle(FormationRequest request) {
        int count = request.unitCount();
        double radius = request.radius() > 0.0 ? request.radius()
                : request.lateralSpacing() * Math.sqrt(Math.max(1, count - 1) / Math.PI);
        List<FormationSlot> slots = new ArrayList<>(count);
        slots.add(new FormationSlot(0, 0.0, 0.0, 0, 0));
        for (int i = 1; i < count; i++) {
            double r = radius * Math.sqrt(i / (double) Math.max(1, count - 1));
            double angle = i * GOLDEN_ANGLE;
            slots.add(new FormationSlot(i, Math.cos(angle) * r, Math.sin(angle) * r, -1, -1));
        }
        return slots;
    }

    private static List<FormationSlot> hollowCircle(FormationRequest request) {
        int count = request.unitCount();
        if (count == 1) return List.of(new FormationSlot(0, 0.0, 0.0, 0, 0));
        double radius = request.radius() > 0.0 ? request.radius()
                : Math.max(request.lateralSpacing(), count * request.lateralSpacing() / (Math.PI * 2.0));
        List<FormationSlot> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = Math.PI / 2.0 - i * Math.PI * 2.0 / count;
            slots.add(new FormationSlot(i, Math.cos(angle) * radius, Math.sin(angle) * radius, -1, -1));
        }
        return slots;
    }

    private static List<FormationSlot> wedge(FormationRequest request) {
        List<FormationSlot> slots = new ArrayList<>(request.unitCount());
        int index = 0;
        int rank = 0;
        while (index < request.unitCount()) {
            int capacity = rank * 2 + 1;
            int inRank = Math.min(capacity, request.unitCount() - index);
            for (int file = 0; file < inRank; file++) {
                double lateral = (file - (inRank - 1) / 2.0) * request.lateralSpacing();
                double longitudinal = -rank * request.depthSpacing();
                slots.add(new FormationSlot(index++, lateral, longitudinal, rank, file));
            }
            rank++;
        }
        return slots;
    }

    private static int perimeterCapacity(int width, int height) {
        return height <= 1 ? width : width * 2 + Math.max(0, height - 2) * 2;
    }

    private static double[] point(int x, int z, int width, int height, FormationRequest request) {
        return new double[]{(x - (width - 1) / 2.0) * request.lateralSpacing(),
                ((height - 1) / 2.0 - z) * request.depthSpacing()};
    }
}
