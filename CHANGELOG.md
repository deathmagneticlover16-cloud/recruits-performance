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
