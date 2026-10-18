# Recruits Performance 0.4.0 Alpha 2

Target: Minecraft 1.20.1, Forge 47.4.10, Villager Recruits 1.15.2.

Alpha 2 begins active behaviour control while keeping every invasive feature independently configurable.

Implemented:

- event-maintained recruit runtime registry;
- transient runtime brain and dirty state;
- global priority scheduler with per-work-type budgets;
- corrected HOT/WARM/COLD/DORMANT classification;
- nearby idle armies no longer remain permanently HOT merely because a player is present;
- urgent wake leases for damage, commands, target changes, fire, and explosions;
- bounded target memory after line-of-sight loss;
- last-known-position pursuit for eligible native orders;
- target expiry, range limits, reacquisition, and telemetry;
- deterministic formation geometry API;
- genuine filled circles, hollow circles, rectangles, squares, hollow variants, lines, columns, and wedges;
- stable formation slot assignment helpers;
- RTS formation slot/position/velocity integration API;
- movement-authority state and RTS heartbeat watchdog;
- `/recruitsperf` diagnostics and feature flags.

Target memory deliberately does not pull HOLD, FOLLOW, MOVE, REST, UPKEEP, or RTS-controlled recruits away from their orders. The RTS mod remains authoritative for tactical formation geometry and movement; this mod supplies reusable geometry and runtime primitives.

Still reserved for later measured phases:

- broad native goal suppression;
- formation slot steering execution;
- shared battlefield perception replacement;
- route-corridor navigation and recovery paths;
- collision suppression;
- rendering, animation, and network LOD.

## Commands

- `/recruitsperf stats`
- `/recruitsperf resetstats`
- `/recruitsperf dump`
- `/recruitsperf scheduler`
- `/recruitsperf ai`
- `/recruitsperf targetmemory`
- `/recruitsperf navigation`
- `/recruitsperf rendering`
- `/recruitsperf features`

Commands require permission level 2. Configuration is generated at `config/recruitsperformance-common.toml`.

RTS integration examples are in `FORMATION_API.md`.
