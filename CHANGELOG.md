# 1.0.0 Alpha 1

- Replaced supported Recruits living-entity rendering with a dedicated instanced crowd pass.
- Added automatic humanoid body and Forge armor-model conversion, including Epic Knights model formats.
- Added reusable baked-item conversion for weapons and shields.
- Matched vanilla's third-person hand transform while retaining the active model's real arm pivot for converted weapons and shields.
- Added shared GPU geometry, per-instance buffers, material batching and resource-reload disposal.
- Added GPU skeletal animation states for movement, combat, ranged weapons, damage and death.
- Added indexed GPU pose palettes that preserve active humanoid rotations, moving pivots and renderer-selected arm poses across body and equipment batches.
- Added instanced Recruits biome, faction-cloth and companion-cloth layers while sharing one body geometry allocation across material variants.
- Preserved the Recruits villager renderer's 0.9375 model scale as compact per-instance state.
- Matched Minecraft's living-model X/Y inversion and 180-degree facing transform, including the equivalent captured-bone rotation signs.
- Added a configurable exact-pose near field with shared procedural GPU animation beyond it, and reuse one extracted instance record across all material/equipment components.
- Added depth, fog, lighting and camera integration.
- Added client configuration, diagnostics, cache accounting and a 300–2,000-soldier batching harness.
- Added non-blocking GPU timer queries to the diagnostics overlay alongside CPU crowd-pass timing.
- Reused native instance staging buffers and orphaned shared instance VBO storage between material draws to avoid allocation churn and driver synchronization stalls.
- Added a checksummed Gradle wrapper and reproducible build instructions.

# 0.4.0-alpha.2

- Fixed nearby idle recruits being permanently classified HOT.
- Added urgent wake leases and simulation-tier transition telemetry.
- Added bounded target memory and last-known-position pursuit after LOS loss.
- Added validity, timeout, range, order-safety, reacquisition, and kill-switch rules for target memory.
- Added deterministic formation layout and stable slot-assignment APIs for RTS addons.
- Added real circle, hollow circle, square, hollow square, rectangle, line, column, and wedge layouts.
- Added target-memory diagnostics and Alpha 2 tests.

# 0.3.0-alpha.1

- Rebuilt the addon around the new runtime-layer architecture.
- Added Phase 0 instrumentation.
- Added Phase 1 runtime registry and observational brain state.
- Added Phase 2 priority scheduler and configurable budgets.
- Added RTS integration API scaffolding and movement authority state.
- Added damage and projectile wake handling.
- Added diagnostic commands and independent future subsystem flags.
- Preserved native Villager Recruits behaviour; no goals or navigation are replaced yet.
