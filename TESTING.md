# Alpha 2 test protocol

Use a disposable world or backup. Run `/recruitsperf resetstats` before each scenario and capture `/recruitsperf dump` afterward.

## Simulation tiers

1. Place 100 idle recruits beside the player.
2. Wait ten seconds.
3. Confirm they settle into WARM rather than remaining HOT.
4. Move more than 192 blocks away while keeping chunks loaded and confirm stationary safe recruits become DORMANT.
5. Damage one recruit and confirm it immediately becomes HOT, then cools after the configured urgent wake lease.

## Target memory

1. Let a melee recruit acquire a valid enemy.
2. Break LOS with a short wall or corner.
3. Confirm the recruit keeps combat intent and investigates the last-known position.
4. Restore LOS within five seconds and confirm pursuit resumes.
5. Keep LOS blocked beyond five seconds and confirm the target is released.
6. Repeat with HOLD and FOLLOW orders; recruits must retain awareness without abandoning those orders.
7. Repeat with bowmen, crossbowmen, and mounted recruits.
8. Run `/recruitsperf targetmemory` and verify starts, retentions, reacquisitions, expiries, and pursuit commands change sensibly.

## Formation API

The API itself is covered by deterministic tests. Once the RTS mod adopts it, visually test 25, 100, and 500 units in square, hollow square, circle, hollow circle, line, column, rectangle, and wedge layouts. Slot assignments should remain stable when units are added or removed.

## Performance baseline

Repeat the Alpha 1 measurements at 20, 100, 200, and 500 idle recruits. Then test 100-vs-100 combat. Record MSPT, tier counts, scheduler queue size, target-memory counts, and any behavioural regression.
