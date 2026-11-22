# Verification

Run `./gradlew clean build contractTests`. The contract suite checks formation geometry, scheduler budgets, simulation tiers, mesh triangulation and draw-batch scaling through 2,000 soldiers.

For an in-game smoke test:

Before visual testing on AMD, inspect `logs/recruits-performance-shaders`. Startup must log a successful minimal GLSL preflight followed by the crowd vertex and fragment source digests. On the affected RX 570, confirm initialization passes `glShaderSource`, shader compilation and program linking without creating a new `hs_err_pid*.log`.

1. Install Recruits and this mod, start a Forge 1.20.1 client, and create a disposable world.
2. Spawn human and villager recruits with no equipment. Confirm they render once and remain depth-occluded by terrain.
3. Equip distinct vanilla and all supplied Epic Knights addon armor/decorations in every slot on both human and villager recruits, plus swords, polearms, bows, crossbows and shields. Confirm each component follows the correct animated limb; verify two-sided plates, dyed overlays and patterned surcoats.
4. Change equipment while recruits are visible. Confirm only the changed cached component and batch counters change.
5. Exercise idle, walk, run, attack, block, bow, crossbow, hurt and death states, plus Recruits RTS formation movement. With the supplied Better Combat, Better Mob Combat, Mob Player Animator and Serious Animations builds installed, confirm Better Combat's real holding, swing, body and held-item channels remain authoritative without the Serious Animations fallback swing leaking through, doubled poses, twitching or quantized snapping.
6. Reload resources with F3+T and repeat the checks. GPU objects must be rebuilt without duplicate soldiers or stale textures.
7. Set `diagnostics=true`; verify 1,000 visible recruits report `visible=1000`, while GPU instances count their body/material/equipment components and draw submissions track unique mesh/material batches rather than entity count.
8. Verify human and villager biome textures, faction cloth and companion/assassin cloth match vanilla rendering from every camera angle; give one recruit a non-armor head item and confirm it deliberately stays on the vanilla fallback path.
9. Mount recruits on adult and foal Icy's Better Horses 2.1.6 breeds. Verify coat, saddle, carpet, armor, neck plates and chest tack stay synchronized; verify riders follow Icy's native saddle translation, pitch, roll and Serious Animations seat anchor while the horse moves. Equipped stabilizers/banners must use the original renderer.
10. With Cruor 0.1.3 hotfix installed, blood a recruit's held weapon. Confirm its separate stain pass never displays unrelated atlas sprites and is omitted beyond the configured stain distance while the base weapon remains instanced.
11. Verify a nameplate appears only while the crosshair is over a Recruits unit; point at its horse and unrelated living entities and confirm no GPU nameplate is queued. Also verify ground shadows, enchanted glint fallback, glowing outlines and source death presentation.
12. Order recruits across open ground and around obstacles. Confirm native pathfinding and native movement speed remain unchanged with Recruits Performance installed.
13. Test with Oculus 1.8.0 both without and with the target shader pack, including daylight, fog, rain, shadowed scenes and the HUD hidden with F1. Confirm recruits remain visible after Oculus' final composite, diagnostics still report one submitted recruit per entity despite Oculus entity/shadow passes, no vanilla armor layer reappears, and instanced draw calls remain active in both modes; compare CPU/GPU frame time at equal recruit counts.
14. Confirm the diagnostics GPU duration becomes non-zero after the first measured crowd frames and that it updates without visible stalls.
15. Set `enabled=false`; restart and confirm the original Recruits renderer is restored for A/B comparison.

Run the scenarios in [BENCHMARKING.md](BENCHMARKING.md) on the target RX 570. The cloud build environment can compile and validate CPU contracts but cannot verify real OpenGL driver behavior, visual equivalence, shader-pack interoperability or GPU frame time.
