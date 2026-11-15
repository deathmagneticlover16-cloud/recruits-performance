# Verification

Run `./gradlew clean build contractTests`. The contract suite checks formation geometry, scheduler budgets, simulation tiers, mesh triangulation and draw-batch scaling through 2,000 soldiers.

For an in-game smoke test:

1. Install Recruits and this mod, start a Forge 1.20.1 client, and create a disposable world.
2. Spawn human and villager recruits with no equipment. Confirm they render once and remain depth-occluded by terrain.
3. Equip distinct vanilla and Epic Knights armor in every slot, plus swords, polearms, bows, crossbows and shields. Confirm each component follows the correct animated limb.
4. Change equipment while recruits are visible. Confirm only the changed cached component and batch counters change.
5. Exercise idle, walk, run, attack, block, bow, crossbow, hurt and death states, plus Recruits RTS formation movement.
6. Reload resources with F3+T and repeat the checks. GPU objects must be rebuilt without duplicate soldiers or stale textures.
7. Set `diagnostics=true`; verify 1,000 visible recruits produce 1,000 body instances while draw submissions track unique mesh/material components rather than entity count.
8. Set `enabled=false`; restart and confirm the original Recruits renderer is restored for A/B comparison.

Run the scenarios in [BENCHMARKING.md](BENCHMARKING.md) on the target RX 570. The cloud build environment can compile and validate CPU contracts but cannot verify real OpenGL driver behavior, visual equivalence, shader-pack interoperability or GPU frame time.
