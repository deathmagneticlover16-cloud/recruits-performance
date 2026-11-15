# Verification

Run `./gradlew clean build contractTests`. The contract suite checks formation geometry, scheduler budgets, simulation tiers, mesh triangulation and draw-batch scaling through 2,000 soldiers.

For an in-game smoke test:

1. Install Recruits and this mod, start a Forge 1.20.1 client, and create a disposable world.
2. Spawn human and villager recruits with no equipment. Confirm they render once and remain depth-occluded by terrain.
3. Equip distinct vanilla and Epic Knights armor in every slot, plus swords, polearms, bows, crossbows and shields. Confirm each component follows the correct animated limb.
4. Change equipment while recruits are visible. Confirm only the changed cached component and batch counters change.
5. Exercise idle, walk, run, attack, block, bow, crossbow, hurt and death states, plus Recruits RTS formation movement.
6. Reload resources with F3+T and repeat the checks. GPU objects must be rebuilt without duplicate soldiers or stale textures.
7. Set `diagnostics=true`; verify 1,000 visible recruits report `visible=1000`, while GPU instances count their body/material/equipment components and draw submissions track unique mesh/material batches rather than entity count.
8. Verify human and villager biome textures, faction cloth and companion/assassin cloth match vanilla rendering; give one recruit a non-armor head item and confirm it deliberately stays on the vanilla fallback path.
9. Confirm the diagnostics GPU duration becomes non-zero after the first measured crowd frames and that it updates without visible stalls.
8. Set `enabled=false`; restart and confirm the original Recruits renderer is restored for A/B comparison.

Run the scenarios in [BENCHMARKING.md](BENCHMARKING.md) on the target RX 570. The cloud build environment can compile and validate CPU contracts but cannot verify real OpenGL driver behavior, visual equivalence, shader-pack interoperability or GPU frame time.
