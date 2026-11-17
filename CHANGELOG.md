# 1.0.0 Alpha 7

- Restricted the hover nameplate queue to Villager Recruits entities and removed mounted horses from the label path.
- Removed shared quantized pose keys and duplicate Player Animator application; each recruit now keeps stable tick-endpoint poses that are interpolated per render frame while remaining GPU-instanced.
- Expanded the pose buffer to 17 bones and preserved Mob Player Animator's whole-body and held-item channels, including Recruits Serious Animations and Better Mob Combat animation stacks, without colliding with Icy's 16 horse-part slots.
- Read Icy's Better Horses 2.1.6 native `BhRiderMotion` and `BhRiderSeat` outputs so instanced riders inherit the horse renderer's saddle translation, pitch, roll and Recruits Serious Animations seat anchor.
- Replaced guessed Epic Knights decoration layers with the live registered `ArmorDecorationLayer`, including the models and textures contributed by Epic Knights Addon, Dark Ages, Slavic Armory and Antique Legacy, and matched their no-cull render state so one-sided plates and cloth are not discarded.
- Kept the Oculus 1.8.0 path instanced and reduced animation sampling/reflection work from every rendered frame to stable per-entity tick endpoints.
- Bumped the mod to `1.0.0-alpha.7`.

# 1.0.0 Alpha 6

- Removed every hand-authored walk, run, attack, block, bow, crossbow and Better Combat transform from the crowd shader.
- Added a bounded shared pose palette generated from Minecraft's active humanoid model, including its native locomotion and item-use poses, at every instanced distance.
- Added an optional Player Animation Library bridge that applies the active Recruits/Better Combat animation stack and its `leftItem`/`rightItem` weapon channels to dedicated GPU bones.
- Kept armor, Epic Knights decorations, dyes, overlays and surcoats on the same source-model pose palette, and stopped dropping decoration layers at the geometry LOD boundary.
- Changed GPU nameplates to queue only for the entity currently under the crosshair.
- Removed automatic Performance-mod brain observation and rescheduling so native Recruits goals, navigation, move controls, movement attributes and entity ticks remain the only movement authority.
- Removed the obsolete five-block exact-pose configuration and bumped the mod to `1.0.0-alpha.6`.

# 1.0.0 Alpha 5

- Removed the 72-block source-renderer zone; supported recruits remain GPU-instanced at every distance.
- Reduced per-recruit exact pose evaluation from 96 blocks to a hard five-block maximum; all farther animation uses shared GPU states.
- Kept hurt frames in the instanced path and replaced the exaggerated multiplier with a restrained GPU hurt overlay.
- Cached Better Mob Combat state once per entity tick and added a shared GPU attack phase instead of falling back to vanilla rendering.
- Kept instancing enabled with Iris/Oculus and moved the crowd pass to `AFTER_LEVEL` while a shader pack is active so it is drawn after shader-pack composition.
- Replaced Cruor's per-recruit map scan with one cached snapshot per client tick.
- Split Cruor stains into their own dynamic-texture GPU pass, kept the base weapon instanced, and distance-culled unique stain passes beyond 16 blocks while retaining normal depth-buffer occlusion.
- Restored GPU-pass nameplates for named recruits without retaining the source renderer.

# 1.0.0 Alpha 4

- Restored the source renderer inside 72 blocks so vanilla nameplates, smooth Player Animator/Recruits SPA locomotion, complete armor layers and exact mount transforms render normally up close.
- Restored vanilla hurt overlays by retaining the source renderer for every active hurt frame instead of applying an exaggerated full-model red tint.
- Added active Better Mob Combat attack detection so attack animation layers are never replaced by the generic crowd animation.
- Added Iris/Oculus shader-pack detection and a correctness fallback that prevents raw crowd shaders from writing invalid data into shader-pack framebuffers.
- Fixed Cruor's package-private gear-cache reflection and rejected Cruor dynamic render types at the item-capture boundary, with an additional captured-geometry safety limit.
- Restored the Recruits SPA Icy horse seat anchor and moved crowd interception ahead of paired render-event transform hooks.
- Added a real distant Icy horse geometry LOD while retaining body and tack materials.
- Removed the superseded custom nameplate path now that the exact source nameplate renderer owns the full visible label range.

# 1.0.0 Alpha 3

- Fixed the confirmed AMD `atio6axx.dll` access violation during `glShaderSource` on Radeon RX 570.
- Replaced LWJGL's non-NUL-terminated `CharSequence` submission with an explicitly owned, NUL-terminated UTF-8 allocation, 64 zeroed guard bytes and a `NULL` lengths pointer.
- Added render-thread/OpenGL 3.3 context validation and a minimal vertex/fragment compile-link preflight before the crowd shaders.
- Persisted and logged each exact GLSL source, type, UTF-8 byte length and SHA-256 digest before its native submission.
- Added driver source round-trip validation, required-uniform validation and complete shader/program cleanup on recoverable initialization failures.

# 1.0.0 Alpha 2

- Replaced the six-Euler pose palette with sixteen 3x4 skin matrices for exact close-range hierarchy animation.
- Added Icy's Better Horses 2.1.6 mounted-recruit instancing for body and tack models, with correctness fallbacks for dynamic GeckoLib stabilizers, banners and optional horns.
- Added Epic Knights and addon `ArmorDecoration` model layers, dyes, overlays, surcoats and heraldry material passes.
- Restored ordinary entity ground shadows, source-style progressive death rotation and world nameplates.
- Added a long-range geometry LOD while retaining body and armor silhouettes.
- Added correctness fallbacks for glowing entities, foil/glint equipment, custom heads and Cruor's transient bloodied-gear material pass.
- Fixed Cruor weapon blood captures being merged into the block atlas and displaying unrelated atlas sprites.
- Removed the target-memory controller, pursuit navigation, configuration, commands, metrics and dormant runtime state.

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
