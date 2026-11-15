package dev.lastpagans.recruitsperformance.client.diagnostics;

import java.util.Locale;

public final class CrowdDiagnostics {
    private static final CrowdDiagnostics INSTANCE = new CrowdDiagnostics();

    private volatile int supported;
    private volatile int visible;
    private volatile int batches;
    private volatile int gpuInstances;
    private volatile int drawSubmissions;
    private volatile int meshes;
    private volatile int equipmentVariants;
    private volatile int fallback;
    private volatile long gpuBytes;
    private volatile long cpuNanos;

    public static CrowdDiagnostics get() { return INSTANCE; }

    public void publish(int supported, int visible, int batches, int gpuInstances, int drawSubmissions,
                        int meshes, int equipmentVariants, int fallback, long gpuBytes, long cpuNanos) {
        this.supported = supported;
        this.visible = visible;
        this.batches = batches;
        this.gpuInstances = gpuInstances;
        this.drawSubmissions = drawSubmissions;
        this.meshes = meshes;
        this.equipmentVariants = equipmentVariants;
        this.fallback = fallback;
        this.gpuBytes = gpuBytes;
        this.cpuNanos = cpuNanos;
    }

    public String summary() {
        return String.format(Locale.ROOT,
                "virtual=%d visible=%d batches=%d instances=%d draws=%d meshes=%d equipment=%d vram=%.1fMiB submit=%.2fms fallback=%d",
                supported, visible, batches, gpuInstances, drawSubmissions, meshes, equipmentVariants,
                gpuBytes / 1048576.0, cpuNanos / 1_000_000.0, fallback);
    }

    public int visible() { return visible; }
    public int batches() { return batches; }
    public int drawSubmissions() { return drawSubmissions; }
    public long gpuBytes() { return gpuBytes; }
}
