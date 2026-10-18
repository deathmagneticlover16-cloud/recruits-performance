# Formation API — Alpha 2

The RTS mod owns formation selection, orders, routing, and movement. Recruits Performance supplies deterministic geometry, stable slot assignments, and per-recruit runtime intent.

## Generate geometry

```java
FormationRequest request = new FormationRequest(
    FormationShape.HOLLOW_CIRCLE,
    members.size(),
    0,      // files: 0 = automatic
    1.25,   // lateral spacing
    1.25,   // depth spacing
    0.0     // radius: 0 = automatic
);

List<FormationSlot> slots = RecruitsPerformanceApi.createFormationLayout(server, request);
```

`FormationShape` supports `LINE`, `COLUMN`, `RECTANGLE`, `SQUARE`, `HOLLOW_RECTANGLE`, `HOLLOW_SQUARE`, `CIRCLE`, `HOLLOW_CIRCLE`, and `WEDGE`.

## Preserve assignments

```java
Map<UUID, Integer> assignments = FormationAssignments.stable(
    members,
    previousAssignments,
    slots.size()
);
```

Valid unique previous assignments are retained. New or displaced members fill free slots in deterministic UUID order, preventing the whole formation from shuffling whenever one recruit disappears.

## Convert and submit slots

```java
FormationSlot slot = slots.get(assignments.get(recruitId));
Vec3 worldPosition = slot.toWorld(anchor, facingVector);

RecruitsPerformanceApi.registerRtsControl(server, recruitId, formationId);
RecruitsPerformanceApi.assignFormationSlot(
    server,
    recruitId,
    slot.index(),
    worldPosition,
    desiredVelocity
);
RecruitsPerformanceApi.heartbeat(server, recruitId, gameTime);
```

Continue sending heartbeats while RTS movement authority is active. If they stop, the watchdog returns the recruit to native authority instead of leaving it permanently stranded.

Geometry generation does not move entities by itself. This is intentional: the RTS FormationController remains the single tactical movement authority.
