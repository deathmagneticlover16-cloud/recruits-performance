# Verification

Run `./gradlew clean build contractTests`. The contract suite checks formation geometry, scheduler budgets, simulation tiers, mesh triangulation and draw-batch scaling through 2,000 soldiers.

For an in-game smoke test:

Before visual testing on AMD, inspect `logs/recruits-performance-shaders`. Startup must log a successful minimal GLSL preflight followed by the crowd vertex and fragment source digests. On the affected RX 570, confirm initialization passes `glShaderSource`, shader compilation and program linking without creating a new `hs_err_pid*.log`.

1. Install Recruits and this mod, start a Forge 1.20.1 client, and create a disposable world.
2. Spawn human and villager recruits with no equipment. Confirm they render once and remain depth-occluded by terrain.
3. Equip distinct vanilla and all supplied Epic Knights addon armor/decorations in every slot, plus swords, polearms, bows, crossbows and shields. Confirm each component follows the correct animated limb; verify dyed overlays and patterned surcoats.
4. Change equipment while recruits are visible. Confirm only the changed cached component and batch counters change.
5. Exercise idle, walk, run, attack, block, bow, crossbow, hurt and death states, plus Recruits RTS formation movement.
6. Reload resources with F3+T and repeat the checks. GPU objects must be rebuilt without duplicate soldiers or stale textures.
7. Set `diagnostics=true`; verify 1,000 visible recruits report `visible=1000`, while GPU instances count their body/material/equipment components and draw submissions track unique mesh/material batches rather than entity count.
8. Verify human and villager biome textures, faction cloth and companion/assassin cloth match vanilla rendering; give one recruit a non-armor head item and confirm it deliberately stays on the vanilla fallback path.
9. Mount recruits on adult and foal Icy's Better Horses 2.1.6 breeds. Verify coat, saddle, carpet, armor, neck plates and chest tack stay synchronized; equipped stabilizers/banners must use the original renderer.
10. With Cruor 0.1.3 hotfix installed, blood a recruit's held weapon. Confirm it remains on Cruor's source renderer and never displays unrelated atlas sprites; unbloodied copies should return to instancing.
11. Verify custom names, crouching/discrete name distance, ground shadows, enchanted glint fallback, glowing outlines and source death presentation.
12. Test with Oculus both without and with the target shader pack, including daylight, fog, rain and shadowed scenes.
13. Confirm the diagnostics GPU duration becomes non-zero after the first measured crowd frames and that it updates without visible stalls.
14. Set `enabled=false`; restart and confirm the original Recruits renderer is restored for A/B comparison.

Run the scenarios in [BENCHMARKING.md](BENCHMARKING.md) on the target RX 570. The cloud build environment can compile and validate CPU contracts but cannot verify real OpenGL driver behavior, visual equivalence, shader-pack interoperability or GPU frame time.
