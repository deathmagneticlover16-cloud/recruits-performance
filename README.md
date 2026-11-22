# Recruits Performance

Recruits Performance is a Forge 1.20.1 client renderer and server performance runtime for Villager Recruits. Supported recruit visuals bypass `LivingEntityRenderer` after their visual state has been extracted and are drawn as shared GPU mesh instances at every distance.

The renderer converts the active Recruits humanoid `ModelPart` hierarchy into one reusable body mesh. Recruits biome, faction-cloth and companion-cloth materials reuse that same geometry as separate instanced batches. Armor is resolved through Forge's complete `ForgeHooksClient#getArmorModel` path, including the generic replacement-model property transfer used by Epic Knights 10.15 and compatible addons. Standard and custom humanoid armor therefore use the same automatic converter instead of an item whitelist, with every selected model-part subtree retained and dyed base/overlay materials sharing one converted geometry allocation. Main-hand and off-hand baked item geometry is captured once and attached to the animated arm bones. Dynamic equipment changes select a different cached component without rebuilding unrelated soldiers.

Every body and equipment vertex carries a compact bone identifier. Exact GPU poses are uploaded as 3x4 skin matrices to a shared 17-bone pose buffer, with a dedicated whole-body animation slot. Body, armor and held-item instances reference one pose per entity, so they stay aligned without duplicating pose data per component. Geometry LODs omit secondary soldier and horse detail at long range while retaining their core silhouettes and material identity. Instances sharing a mesh and texture are submitted with `glDrawElementsInstanced`.

Each recruit's native model and Mob Player Animator stack is sampled at stable tick endpoints and interpolated for the current render frame. Active Better Mob Combat poses are sampled at the real render partial tick so its fast attack curves and held-item channels are not interpolated a second time. While any pose sample is taken, Better Combat and the Recruits renderer's native item pose remain authoritative over Recruits Serious Animations' overlapping fallback action/swing layer. Every supported distance remains in the instanced path.

## Requirements

- Minecraft 1.20.1
- Forge 47.4.10 or newer in the 47.x line
- Java 17
- Villager Recruits 1.15.2 or newer compatible 1.20.1 build
- OpenGL 3.3 or newer; OpenGL 4.6 hardware is recommended

The compatibility baseline is Recruits 1.15.2, Recruits Serious Animations 0.13.3, Mob Player Animator 1.3.3, Serious Player Animations 1.1.1, Better Mob Combat Recruits Fix 1.3.1, Better Combat 1.8.6, Icy's Better Horses 2.1.6, Oculus 1.8.0, Last Pagans Assorted Fixes 1.2.3, Recruits RTS Camera 0.24.5-server.1, the supplied Epic Knights Addon 2.6, Dark Ages 1.1, Slavic Armory 2.1 and Antique Legacy 1.13 builds, plus 7 Years War Uniforms 1.4.2, Victorian Uniforms 1.0.0 and SYW USA 1.0.0. Integrations remain reflection-only so those client visual mods are not hard server dependencies.

## Build

```text
./gradlew clean build
```

The distributable JAR is written to `build/libs/recruits-performance-1.20.1-1.0.0-alpha.13.jar`. The checked-in wrapper verifies Gradle 8.5 with its official SHA-256 checksum.

## Install

Place the built JAR in the client and server `mods` directories alongside Recruits. The GPU renderer is client-side; observational scheduling and the formation API remain available on both sides. The obsolete target-memory controller and its navigation commands have been removed.

Client settings are generated at `config/recruits-performance-client.toml`. The renderer is enabled by default. Set `diagnostics=true` to display virtual instance, batch, draw, cache, VRAM-estimate, CPU submission and asynchronous GPU timer counters.

## Rendering behavior

- Vanilla Recruits rendering is cancelled only after the complete body and equipped loadout has converted successfully.
- Supported recruits stay instanced at every distance; there is no blanket near-field source-renderer zone.
- Hurt flashes and Better Mob Combat attacks use GPU overlay and animation states without leaving the instanced path.
- When Iris/Oculus reports an active shader pack, the world matrices are captured at `AFTER_LEVEL` and the instanced crowd is submitted to Minecraft's main target after Oculus' final composite but before the HUD; repeated Oculus entity and shadow passes remain cancelled after the entity's single GPU submission.
- If a foreign model cannot produce reusable geometry, that entity keeps its original renderer and the fallback is reported. Equipment is never silently omitted.
- The supported Recruits humanoid material adapter preserves the source renderer's deterministic team, biome and companion layer order, no-cull state and `LEQUAL` coplanar depth contract; unknown Recruits body formats and custom-head items deliberately retain vanilla rendering.
- Body, helmet, chest, leg, boot, main-hand and off-hand geometry are cached separately, so mixed loadouts retain component batching.
- Captured source-model bone rotations, Mob Player Animator whole-body motion, moving pivots and item channels are shared by body, armor and both held-item components through one pose index per entity.
- Armor uses the inner/outer models installed on the live Recruits renderer. Epic Knights `ArmorDecoration` geometry, dye overlays and surcoat heraldry are obtained from its live registered layer and attached in the recruit model's bind pose, including models registered by installed addons.
- Generated Forge armor hooks that return a newly baked model on every query are cached by their item, slot, entity, parent-model and stack variant. This preserves the custom uniforms from 7 Years War, Victorian Uniforms and SYW USA without turning their transient Java object identities into unbounded GPU meshes.
- Mounted Icy's Better Horses 2.1.6 entities retain all 16 native horse-part transforms plus reusable body/tack meshes when carrying a recruit. Riders consume Icy's published saddle translation, pitch and roll instead of a hand-authored mount animation. Stabilizers, banners and optional horn layers retain the source renderer.
- Cruor-bloodied weapons retain a shared base mesh plus a correctly textured dynamic stain pass. Wounded living recruits keep their instanced body and equipment while Cruor's registered wound layer performs its own hit placement, stacked-UV remapping, drying and texture upload after the crowd draw. Recruits cloth/biome material textures are registered with Cruor before wound settlement, and unique Cruor detail is omitted beyond its configured distance.
- Only Villager Recruits units under the crosshair use the instanced nameplate queue; mounts and unrelated entities never enter it.
- Resource reload destroys all GPU objects and lazily rebuilds them from the newly baked models.
- Shader initialization writes the exact minimal and crowd GLSL sources to `logs/recruits-performance-shaders`, records their types, byte lengths and SHA-256 digests, and uses guarded NUL-terminated native buffers for AMD driver safety.
- Minecraft's depth buffer, camera matrices, fog, alpha cutout and sampled block/sky light remain active for the crowd pass.
- Recruits RTS movement hints and ordinary entity combat/use state select GPU animation states without changing server-authoritative gameplay.

The observational AI scheduler, simulation tiers and deterministic formation API remain in the project. Their server commands continue under `/recruitsperf`.

See [BENCHMARKING.md](BENCHMARKING.md) for the reproducible 300–2,000-soldier protocol and [TESTING.md](TESTING.md) for verification steps.

## Known limits

Animated geometry supplied only through an unsupported procedural renderer keeps its original renderer and increments the fallback counter. Camera-frustum acceptance remains Minecraft's conservative entity culling; this release does not add compute/indirect occlusion culling or impostors. Distant Horizons renders terrain rather than living entities, so soldier and horse LOD selection remains in Recruits Performance while using the extended terrain view around them. Shader-pack visual equivalence and RX 570 performance still require an actual modded client/GPU run; the cloud source environment cannot emulate those results.
