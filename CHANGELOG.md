# 1.0.0 Alpha 17

- Added a recruit-only loaded-entity sweep so troops skipped by vanilla's short entity-render decision still enter the existing GPU-instanced batches. The sweep is capped by both the crowd configuration and Distant Horizons 3.3.3's active chunk distance, reuses Minecraft frustum testing, and honors Embeddium 0.3.31 entity occlusion inside Embeddium's own render-section radius without treating its undefined far field as occluded.
- Restricted `RenderLivingEvent` interception to the active world-entity pass, preventing recruit GUI previews from being canceled, submitted into the world batch and projected onto their world entity.
- Resampled Betsy Ross standard cloth from Betsy Ross's own renderer once per visual frame, retaining its configured native wave geometry instead of freezing the first captured frame or adding a replacement curve.
- Preserved live VAO/VBO bindings around mesh creation and animated geometry updates, and flushed Forge's shared entity buffer before raw instanced draws. This prevents equipment changes and item-pickup frames from invalidating Corpsebound's queued ragdoll geometry.
- Bumped the mod to `1.0.0-alpha.17`.

# 1.0.0 Alpha 16

- Fixed the reported Java compilation failure by removing the obsolete `equipment` field from `MeshHandle` and its stale constructor argument after diagnostics removal.
- Split Betsy Ross flag and banner standard capture into a block-atlas pole pass and an Immersive Paintings dynamic-texture cloth pass, preventing the selected painting UVs from sampling the entire Minecraft atlas.
- Preserved the complete Recruits Serious Animations 0.13.7 action layer outside active Better Mob Combat attacks, restoring eating, drinking, blocking, bow, crossbow and other specialty actions while retaining Better Combat authority during real attacks.
- Corrected the Oculus/Forge ordering assumption: Oculus completes its final composite in `LevelRenderer`'s return injection before Forge posts `AFTER_LEVEL`. The instanced crowd now draws directly at that post-composite stage while world depth remains intact instead of being postponed into GUI rendering.
- Bumped the mod to `1.0.0-alpha.16`.

# 1.0.0 Alpha 15

- Removed the unused Formation API, formation geometry/assignment helpers, RTS movement-authority state and its documentation; Recruits Performance no longer advertises a tactical interface that has no production consumer.
- Removed client diagnostics configuration, HUD counters, GPU timer queries, server telemetry, `/recruitsperf` commands and periodic diagnostic logging.
- Removed the inert server scheduler, simulation-tier registry and event listeners after verifying that production code never queued or executed their advertised observation work. Native Recruits remains the sole authority for AI, navigation, movement and entity synchronization.
- Marked the mod as client-optional with `displayTest="IGNORE_SERVER_VERSION"` and a client-side Recruits dependency, so dedicated servers no longer need to install Recruits Performance.
- Documented why camera-, resource-, animation- and GPU-dependent crowd work cannot be profitably shifted to the server without a high-bandwidth pose-streaming protocol.
- Bumped the mod to `1.0.0-alpha.15`.

# 1.0.0 Alpha 14

- Replaced the crowd shader's `max(blockLight, skyLight)` approximation with Minecraft's live 16×16 lightmap texture, fixing recruits that remained full-bright outdoors at night.
- Removed the hard-coded 12% light floor and retained separate packed block/sky coordinates through the instance stream so time of day, weather, dimension ambient light, gamma and nearby light sources use Minecraft's current lightmap.
- Bound the lightmap explicitly on texture unit 2 for the custom instanced shader and restored Minecraft's light-layer state after every crowd pass, including the deferred Oculus path.
- Bumped the mod to `1.0.0-alpha.14`.

# 1.0.0 Alpha 13

- Restored Cruor wounds on living recruits without restoring the full vanilla entity renderer: supported bodies and equipment remain GPU-instanced while only Cruor's wound overlay is replayed after the crowd draw.
- Kept Cruor's own `WoundLayer`, wound painter, shared-UV remapper, drying state and dynamic textures authoritative through an optional reflection bridge; no Cruor or Assorted Fixes implementation was copied into Recruits Performance.
- Registered Recruits team, biome and companion material textures with Cruor before it settles pending wounds so its skin mask includes the material layers actually visible on the recruit.
- Reconstructed the crowd instance's camera, body-yaw, rider anchor and model-scale transforms for Cruor's overlay, and retained normal depth testing so armor occludes body wounds.
- Limited the per-entity wound overlay to the existing configurable Cruor detail distance and kept failures isolated to the wound pass instead of disabling GPU instancing.
- Restored the saved world projection while replaying Cruor wounds after Oculus' final composite, then restored the GUI projection before returning to Forge, so dynamic wound RenderTypes do not inherit GUI matrices.
- Added semantic armor-geometry caching for 7 Years War Uniforms 1.4.2, Victorian Uniforms 1.0.0 and SYW USA 1.0.0. Their generated Forge hooks rebuild multiple model trees per query; identical uniforms now resolve and upload once per entity/model/stack variant instead of once per recruit per frame.
- Bumped the mod to `1.0.0-alpha.13`.

# 1.0.0 Alpha 12

- Fixed dyeable armor overlays by constructing Minecraft's `_overlay` texture path before calling Forge's replacement hook, restoring Epic Knights straps, buckles, plates and other non-dyeable material details on recruits.
- Verified the failure against `magistuarmory:gambeson_chestplate` on Recruits Performance `1.0.0-alpha.11`: the GPU pass was binding the dyeable base texture for both armor passes while player and armor-stand rendering used distinct base and overlay textures.
- Applied the correction to every `DyeableLeatherItem` armor set rather than adding an Epic Knights item whitelist.
- Bumped the mod to `1.0.0-alpha.12`.

# 1.0.0 Alpha 11

- Deferred the Oculus crowd draw until after the shader pipeline's final composite and explicitly rebound Minecraft's main framebuffer, preventing the final shader pass from overwriting every instanced recruit.
- Added a render-tick fallback for hidden-HUD play so the post-composite crowd submission is not dependent on a GUI event.
- Corrected Better Combat held-item matrix order to match Player Animation Library's injection point after Minecraft's fixed hand transform, keeping idle and swing weapons attached to the hand.
- Captured body and armor meshes at unit root scale and moved live `ModelPart` scaling into each GPU pose, preventing the first animated recruit from baking collapsed or distorted skin and armor pieces into the shared mesh cache.
- Configured both the Forge fallback and the resolved Epic Knights replacement with the active recruit model and equipment-slot visibility, including generic armor hooks that do not copy their own model properties.
- Bumped the mod to `1.0.0-alpha.11`.

# 1.0.0 Alpha 10

- Replaced the direct `IClientItemExtensions#getHumanoidArmorModel` shortcut with Forge's complete `ForgeHooksClient#getArmorModel` pipeline, including the generic replacement-model property transfer used by Epic Knights and its addons.
- Matched `HumanoidArmorLayer` setup by copying the active Recruits parent model into the renderer's real inner/outer armor model before resolving an item-specific replacement.
- Kept armor slot masking confined to the seven humanoid roots so item-specific child geometry is preserved exactly while the equipped slot is captured.
- Added per-model debug diagnostics containing the item, slot, resolved model class, captured vertex count and triangle count.
- Bumped the mod to `1.0.0-alpha.10`.

# 1.0.0 Alpha 9

- Made Better Mob Combat and the Recruits renderer's native item poses authoritative over Recruits Serious Animations' overlapping fallback action/swing layer while sampling GPU poses; the foreign layer is restored immediately after each sample.
- Sampled active Better Combat curves and `leftItem`/`rightItem` weapon channels at the real render partial tick instead of linearly blending a second pair of matrices over them.
- Matched Minecraft's `LEQUAL` depth contract for coplanar body cloth, biome skins, armor dye overlays, Epic Knights decorations and heraldry, preventing later layers from disappearing based on stale Oculus/OpenGL state.
- Retained the humanoid outer-skin/hat geometry at the geometry LOD boundary rather than dropping a real recruit material layer.
- Bumped the mod to `1.0.0-alpha.9`.

# 1.0.0 Alpha 8

- Fixed the Oculus multi-pass cancellation bug: once a recruit is submitted to the frame's instanced batch, every repeated shader/shadow source-renderer pass remains cancelled instead of restoring vanilla body and armor rendering.
- Cached per-frame conversion failures so an unsupported entity is not reconverted once for every Oculus pass.
- Added deterministic source-renderer layer ordering for base bodies, armor, dye overlays, Recruits team/biome/companion materials, held items, Epic Knights decorations and heraldry.
- Matched the no-cull state used by Recruits body/material layers so reverse-wound and two-sided skin geometry is retained.
- Resolved the actual inner and outer models from each Recruits `HumanoidArmorLayer` rather than substituting player armor models.
- Captured armor and Epic Knights addon decorations in the parent recruit model's bind pose, matching the source renderer's `copyPropertiesTo` attachment contract.
- Bumped the mod to `1.0.0-alpha.8`.

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
