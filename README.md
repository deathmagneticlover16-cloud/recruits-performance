# Recruits Performance

Recruits Performance is a Forge 1.20.1 client renderer and server performance runtime for Villager Recruits. Supported recruit visuals bypass `LivingEntityRenderer` after their visual state has been extracted and are drawn as shared GPU mesh instances.

The renderer converts the active Recruits humanoid `ModelPart` hierarchy into one reusable body mesh. Recruits biome, faction-cloth and companion-cloth materials reuse that same geometry as separate instanced batches. Armor is resolved through Forge's `IClientItemExtensions#getHumanoidArmorModel` hook, which is the model-format API used by Epic Knights 10.15 and compatible addons. Standard and custom humanoid armor therefore use the same automatic converter instead of an item whitelist, with dyed base and overlay materials sharing one converted geometry allocation. Main-hand and off-hand baked item geometry is captured once and attached to the animated arm bones. Dynamic equipment changes select a different cached component without rebuilding unrelated soldiers.

Every body and equipment vertex carries a compact bone identifier. In the configurable near field, the renderer evaluates the active source model and uploads 3x4 skin matrices to a shared 16-bone GPU pose buffer. Body, armor and held-item instances reference that pose by index, so they stay aligned without duplicating pose data per component. Farther soldiers use shared procedural walking, running, attack, block, bow, crossbow, hit and death states in the vertex shader. A geometry LOD keeps the armored core silhouette while omitting secondary body, decoration and held-item geometry at long range. Instances sharing a mesh and texture are submitted with `glDrawElementsInstanced`.

## Requirements

- Minecraft 1.20.1
- Forge 47.4.10 or newer in the 47.x line
- Java 17
- Villager Recruits 1.15.2 or newer compatible 1.20.1 build
- OpenGL 3.3 or newer; OpenGL 4.6 hardware is recommended

Epic Knights and Recruits RTS are optional integrations. Their conventional humanoid armor and item models are discovered through Forge and Minecraft model APIs without a compile-time dependency.

## Build

```text
./gradlew clean build
```

The distributable JAR is written to `build/libs/recruits-performance-1.20.1-1.0.0-alpha.3.jar`. The checked-in wrapper verifies Gradle 8.5 with its official SHA-256 checksum.

## Install

Place the built JAR in the client and server `mods` directories alongside Recruits. The GPU renderer is client-side; observational scheduling and the formation API remain available on both sides. The obsolete target-memory controller and its navigation commands have been removed.

Client settings are generated at `config/recruits-performance-client.toml`. The renderer is enabled by default. Set `diagnostics=true` to display virtual instance, batch, draw, cache, VRAM-estimate, CPU submission and asynchronous GPU timer counters.

## Rendering behavior

- Vanilla Recruits rendering is cancelled only after the complete body and equipped loadout has converted successfully.
- If a foreign model cannot produce reusable geometry, that entity keeps its original renderer and the fallback is reported. Equipment is never silently omitted.
- The supported Recruits humanoid material adapter preserves biome, faction and companion overlays; unknown Recruits body formats and custom-head items deliberately retain vanilla rendering.
- Body, helmet, chest, leg, boot, main-hand and off-hand geometry are cached separately, so mixed loadouts retain component batching.
- Captured source-model bone rotations and moving pivots are shared by body, armor and both held-item components through one pose index per entity.
- Epic Knights `ArmorDecoration` geometry, dye overlays and surcoat heraldry are resolved from the registered model layers and material paths used by the source mod and its addons.
- Mounted Icy's Better Horses 2.1.6 entities use a 16-part exact pose palette plus reusable body/tack meshes when carrying a recruit. Stabilizers, banners and optional horn layers retain the source renderer.
- Cruor-bloodied weapons, foil/glint equipment, glowing entities, custom heads and unsupported dynamic layers deliberately retain their source renderer instead of folding incompatible material passes into the block atlas.
- Converted names are rendered in the world pass using Minecraft's font, background-opacity, packed-light and discrete-name distance behavior.
- Resource reload destroys all GPU objects and lazily rebuilds them from the newly baked models.
- Shader initialization writes the exact minimal and crowd GLSL sources to `logs/recruits-performance-shaders`, records their types, byte lengths and SHA-256 digests, and uses guarded NUL-terminated native buffers for AMD driver safety.
- Minecraft's depth buffer, camera matrices, fog, alpha cutout and sampled block/sky light remain active for the crowd pass.
- Recruits RTS movement hints and ordinary entity combat/use state select GPU animation states without changing server-authoritative gameplay.

The observational AI scheduler, simulation tiers and deterministic formation API remain in the project. Their server commands continue under `/recruitsperf`.

See [BENCHMARKING.md](BENCHMARKING.md) for the reproducible 300–2,000-soldier protocol and [TESTING.md](TESTING.md) for verification steps.

## Known limits

Animated geometry supplied only through an unsupported procedural renderer keeps its original renderer and increments the fallback counter. Camera-frustum acceptance remains Minecraft's conservative entity culling; this release does not add compute/indirect occlusion culling or impostors. Near-field animation preserves the evaluated source model; far-field humanoids use the listed procedural states rather than arbitrary third-party animation graphs. The crowd pass preserves the active framebuffer, camera, depth, fog and render state, but Oculus shader-pack visual equivalence and RX 570 performance still require an actual modded client/GPU run; the cloud source environment cannot emulate those results.
