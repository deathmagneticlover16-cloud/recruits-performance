package dev.lastpagans.recruitsperformance.api.formation;

import java.util.Objects;

/** files=0 and radius=0 select sensible automatic dimensions. */
public record FormationRequest(FormationShape shape, int unitCount, int files,
                               double lateralSpacing, double depthSpacing, double radius) {
    public FormationRequest {
        Objects.requireNonNull(shape, "shape");
        if (unitCount < 0) throw new IllegalArgumentException("unitCount must be non-negative");
        if (files < 0) throw new IllegalArgumentException("files must be non-negative");
        if (!(lateralSpacing > 0.0) || !(depthSpacing > 0.0)) {
            throw new IllegalArgumentException("spacing must be positive");
        }
        if (radius < 0.0) throw new IllegalArgumentException("radius must be non-negative");
    }

    public static FormationRequest of(FormationShape shape, int unitCount) {
        return new FormationRequest(shape, unitCount, 0, 1.25, 1.25, 0.0);
    }
}
