# Recruits Performance GPU renderer benchmark protocol

Use the same world, camera, render distance, resolution and equipment for both render paths. Disable shaders for the primary RX 570 comparison. Toggle `crowdRenderer.enabled` in `config/recruits-performance-client.toml`, restart the client, and capture at least 60 seconds after a 20-second warmup.

Run these visible-army scenarios: 300, 500, 1,000 and 2,000 stationary soldiers; 1,000 marching; 1,000 fighting; 1,000 with repeated loadouts; and 1,000 with varied Epic Knights loadouts.

Enable `crowdRenderer.diagnostics` and record average FPS, 1% low FPS, CPU and GPU frame time, displayed draw submissions, displayed GPU instances, cached meshes and approximate renderer VRAM. RenderDoc or Radeon GPU Profiler can validate that each displayed batch is one `glDrawElementsInstanced` call and can supply authoritative GPU timings and memory figures.

`./gradlew contractTests` runs the deterministic CPU batching harness at 300, 500, 1,000 and 2,000 soldiers. That harness verifies draw-count scaling; it is not a substitute for the in-game GPU benchmark.
