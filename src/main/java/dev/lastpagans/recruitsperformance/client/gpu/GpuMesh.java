package dev.lastpagans.recruitsperformance.client.gpu;

import dev.lastpagans.recruitsperformance.client.mesh.MeshData;
import dev.lastpagans.recruitsperformance.client.scene.InstanceData;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL31C.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL33C.glVertexAttribDivisor;

public final class GpuMesh implements AutoCloseable {
    private static final int INSTANCE_FLOATS = 14;
    private static final int INSTANCE_STRIDE = INSTANCE_FLOATS * Float.BYTES;

    private final int vao;
    private final int vertexBuffer;
    private final int indexBuffer;
    private final int instanceBuffer;
    private final int indexCount;
    private final long geometryBytes;
    private int instanceCapacity;
    private FloatBuffer staging;

    public GpuMesh(MeshData data) {
        this.indexCount = data.indices().length;
        this.geometryBytes = data.bytes();
        vao = glGenVertexArrays();
        vertexBuffer = glGenBuffers();
        indexBuffer = glGenBuffers();
        instanceBuffer = glGenBuffers();

        glBindVertexArray(vao);
        FloatBuffer vertices = MemoryUtil.memAllocFloat(data.vertices().length);
        IntBuffer indices = MemoryUtil.memAllocInt(data.indices().length);
        try {
            vertices.put(data.vertices()).flip();
            indices.put(data.indices()).flip();
            glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
            glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(vertices);
            MemoryUtil.memFree(indices);
        }

        int vertexStride = MeshData.FLOATS_PER_VERTEX * Float.BYTES;
        attribute(0, 3, vertexStride, 0);
        attribute(1, 2, vertexStride, 3 * Float.BYTES);
        attribute(2, 3, vertexStride, 5 * Float.BYTES);
        attribute(3, 1, vertexStride, 8 * Float.BYTES);
        attribute(4, 4, vertexStride, 9 * Float.BYTES);
        attribute(11, 3, vertexStride, 13 * Float.BYTES);

        glBindBuffer(GL_ARRAY_BUFFER, instanceBuffer);
        instanceAttribute(5, 3, 0);
        instanceAttribute(6, 1, 3 * Float.BYTES);
        instanceAttribute(7, 1, 4 * Float.BYTES);
        instanceAttribute(8, 1, 5 * Float.BYTES);
        instanceAttribute(9, 4, 6 * Float.BYTES);
        instanceAttribute(10, 2, 10 * Float.BYTES);
        instanceAttribute(12, 1, 12 * Float.BYTES);
        instanceAttribute(13, 1, 13 * Float.BYTES);
        glBindVertexArray(0);
    }

    public void upload(List<InstanceData> instances) {
        if (instances.isEmpty()) return;
        int requiredFloats = instances.size() * INSTANCE_FLOATS;
        if (staging == null || staging.capacity() < requiredFloats) {
            if (staging != null) MemoryUtil.memFree(staging);
            int capacity = Math.max(INSTANCE_FLOATS,
                    Integer.highestOneBit(requiredFloats - 1) << 1);
            staging = MemoryUtil.memAllocFloat(capacity);
        }
        staging.clear();
        for (InstanceData instance : instances) {
            staging.put(instance.x()).put(instance.y()).put(instance.z()).put(instance.yawRadians());
            staging.put(instance.animationPhase()).put(instance.animationId());
            staging.put(instance.red()).put(instance.green()).put(instance.blue()).put(instance.alpha());
            staging.put(instance.blockLight()).put(instance.skyLight());
            staging.put(instance.poseIndex());
            staging.put(instance.scale());
        }
        staging.flip();
        glBindBuffer(GL_ARRAY_BUFFER, instanceBuffer);
        if (instances.size() > instanceCapacity) {
            instanceCapacity = Math.max(1, Integer.highestOneBit(instances.size() - 1) << 1);
        }
        // Orphaning avoids waiting when shared geometry is redrawn immediately with another material batch.
        glBufferData(GL_ARRAY_BUFFER, (long) instanceCapacity * INSTANCE_STRIDE, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, staging);
    }

    public void draw(int instanceCount) {
        glBindVertexArray(vao);
        glDrawElementsInstanced(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L, instanceCount);
    }

    public long bytes() {
        return geometryBytes + (long) instanceCapacity * INSTANCE_STRIDE;
    }

    @Override
    public void close() {
        if (staging != null) MemoryUtil.memFree(staging);
        glDeleteBuffers(instanceBuffer);
        glDeleteBuffers(indexBuffer);
        glDeleteBuffers(vertexBuffer);
        glDeleteVertexArrays(vao);
    }

    private static void attribute(int index, int size, int stride, long offset) {
        glEnableVertexAttribArray(index);
        glVertexAttribPointer(index, size, GL_FLOAT, false, stride, offset);
    }

    private static void instanceAttribute(int index, int size, long offset) {
        attribute(index, size, INSTANCE_STRIDE, offset);
        glVertexAttribDivisor(index, 1);
    }
}
