package dev.lastpagans.recruitsperformance.client.mesh;

import com.mojang.blaze3d.vertex.DefaultedVertexConsumer;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class CaptureVertexConsumer extends DefaultedVertexConsumer {
    private final MeshData.Builder builder;
    private int bone;
    private float x;
    private float y;
    private float z;
    private float u;
    private float v;
    private float nx;
    private float ny = 1.0F;
    private float nz;
    private float red = 1.0F;
    private float green = 1.0F;
    private float blue = 1.0F;
    private float alpha = 1.0F;
    private float pivotX;
    private float pivotY;
    private float pivotZ;

    public CaptureVertexConsumer(MeshData.Builder builder) {
        this.builder = builder;
    }

    public void bone(int bone) {
        this.bone = bone;
    }

    public void pivot(float x, float y, float z) {
        this.pivotX = x;
        this.pivotY = y;
        this.pivotZ = z;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        this.x = (float) x;
        this.y = (float) y;
        this.z = (float) z;
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        this.red = red / 255.0F;
        this.green = green / 255.0F;
        this.blue = blue / 255.0F;
        this.alpha = alpha / 255.0F;
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        this.u = u;
        this.v = v;
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        this.nx = x;
        this.ny = y;
        this.nz = z;
        return this;
    }

    @Override
    public void endVertex() {
        builder.vertex(x, y, z, u, v, nx, ny, nz, bone,
                red, green, blue, alpha, pivotX, pivotY, pivotZ);
    }
}
