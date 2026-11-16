package dev.lastpagans.recruitsperformance.client.mesh;

import dev.lastpagans.recruitsperformance.client.gpu.GpuMesh;
import dev.lastpagans.recruitsperformance.client.scene.MeshKey;
import net.minecraft.resources.ResourceLocation;

public record MeshHandle(MeshKey key, GpuMesh mesh, ResourceLocation texture, int tint,
                         boolean equipment, boolean cull) {}
