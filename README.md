# Recruits Performance

Recruits Performance is a Forge 1.20.1 client renderer and server performance runtime for Villager Recruits. Supported recruit visuals bypass `LivingEntityRenderer` after their visual state has been extracted and are drawn as shared GPU mesh instances.

The renderer converts the active Recruits humanoid `ModelPart` hierarchy into one reusable body mesh. Recruits biome, faction-cloth and companion-cloth materials reuse that same geometry as separate instanced batches. Armor is resolved through Forge's `IClientItemExtensions#getHumanoidArmorModel` hook, which is the model-format API used by Epic Knights 10.15 and compatible addons. Standard and custom humanoid armor therefore use the same automatic converter instead of an item whitelist, with dyed base and overlay materials sharing one converted geometry allocation. Main-hand and off-hand baked item geometry is captured once and attached to the animated arm bones. Dynamic equipment changes select a different cached component without rebuilding unrelated soldiers.

Every body and equipment vertex carries a compact bone identifier and the source model part's real bind pivot. In the configurable near field, the renderer evaluates the active `HumanoidModel` pose—including renderer-selected arm poses—and uploads its six bone rotations and pivots to a shared GPU pose buffer. Body, armor and held-item instances reference that pose by index, so they stay aligned without duplicating pose data per component. Farther soldiers use shared procedural walking, running, attack, block, bow, crossbow, hit and death states entirely in the vertex shader, avoiding per-soldier model animation work across wide battlefield views. Instances sharing a mesh and texture are uploaded together and submitted with `glDrawElementsInstanced`; the CPU does not resubmit humanoid vertices for every soldier.

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

The distributable JAR is written to `build/libs/recruits-performance-1.20.1-1.0.0-alpha.1.jar`. The checked-in wrapper verifies Gradle 8.5 with its official SHA-256 checksum.

## Install

Place the built JAR in the client and server `mods` directories alongside Recruits. The GPU renderer is client-side, while the existing scheduling, target-memory and formation APIs remain available on both sides.

Client settings are generated at `config/recruits-performance-client.toml`. The renderer is enabled by default. Set `diagnostics=true` to display virtual instance, batch, draw, cache, VRAM-estimate, CPU submission and asynchronous GPU timer counters.

## Rendering behavior

- Vanilla Recruits rendering is cancelled only after the complete body and equipped loadout has converted successfully.
- If a foreign model cannot produce reusable geometry, that entity keeps its original renderer and the fallback is reported. Equipment is never silently omitted.
- The supported Recruits humanoid material adapter preserves biome, faction and companion overlays; unknown Recruits body formats and custom-head items deliberately retain vanilla rendering.
- Body, helmet, chest, leg, boot, main-hand and off-hand geometry are cached separately, so mixed loadouts retain component batching.
- Captured source-model bone rotations and moving pivots are shared by body, armor and both held-item components through one pose index per entity.
- Resource reload destroys all GPU objects and lazily rebuilds them from the newly baked models.
- Minecraft's depth buffer, camera matrices, fog, alpha cutout and sampled block/sky light remain active for the crowd pass.
- Recruits RTS movement hints and ordinary entity combat/use state select GPU animation states without changing server-authoritative gameplay.

The previous AI scheduler, simulation tiers, bounded target memory and deterministic formation API remain in the project. Their server commands continue under `/recruitsperf`.

See [BENCHMARKING.md](BENCHMARKING.md) for the reproducible 300–2,000-soldier protocol and [TESTING.md](TESTING.md) for verification steps.

## Known limits

Animated geometry supplied only through a procedural renderer that never emits Forge `HumanoidModel`, `ModelPart`, or baked-item vertices cannot be converted generically. Those entities keep their original renderer and increment the fallback counter. The current engine relies on Minecraft's pre-render entity frustum acceptance, full-detail instanced geometry and ordinary instanced draw calls; it does not yet ship compute/indirect occlusion culling, decimated meshes or animated impostors. Near-field animation preserves the evaluated source `HumanoidModel`; far-field animation uses the listed procedural states rather than arbitrary third-party animation graphs. Shader-pack correctness, in-game visual equivalence and RX 570 performance require an actual modded client/GPU run; the cloud source environment cannot emulate those results.
