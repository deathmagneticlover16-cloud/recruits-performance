package dev.lastpagans.recruitsperformance.client.gpu;

import dev.lastpagans.recruitsperformance.client.scene.PoseData;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL11C.glGenTextures;
import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL30C.GL_RGB32F;
import static org.lwjgl.opengl.GL31C.GL_TEXTURE_BUFFER;
import static org.lwjgl.opengl.GL31C.glTexBuffer;

public final class CrowdPoseBuffer implements AutoCloseable {
    private final int buffer = glGenBuffers();
    private final int texture = glGenTextures();
    private int capacityFloats;
    private FloatBuffer staging;

    public CrowdPoseBuffer() {
        capacityFloats = 3;
        glBindBuffer(GL_TEXTURE_BUFFER, buffer);
        glBufferData(GL_TEXTURE_BUFFER, (long) capacityFloats * Float.BYTES, GL_STREAM_DRAW);
        glBindTexture(GL_TEXTURE_BUFFER, texture);
        glTexBuffer(GL_TEXTURE_BUFFER, GL_RGB32F, buffer);
        glBindTexture(GL_TEXTURE_BUFFER, 0);
    }

    public void upload(List<PoseData> poses) {
        int required = Math.max(3, poses.size() * PoseData.BONE_COUNT * PoseData.FLOATS_PER_BONE);
        if (staging == null || staging.capacity() < required) {
            if (staging != null) MemoryUtil.memFree(staging);
            staging = MemoryUtil.memAllocFloat(Math.max(required, Integer.highestOneBit(required - 1) << 1));
        }
        staging.clear();
        for (PoseData pose : poses) staging.put(pose.values());
        while (staging.position() < required) staging.put(0.0F);
        staging.flip();
        glBindBuffer(GL_TEXTURE_BUFFER, buffer);
        if (required > capacityFloats) {
            capacityFloats = Math.max(required, Integer.highestOneBit(required - 1) << 1);
        }
        glBufferData(GL_TEXTURE_BUFFER, (long) capacityFloats * Float.BYTES, GL_STREAM_DRAW);
        glBufferSubData(GL_TEXTURE_BUFFER, 0, staging);
        glBindTexture(GL_TEXTURE_BUFFER, texture);
        glTexBuffer(GL_TEXTURE_BUFFER, GL_RGB32F, buffer);
    }

    public void bind() { glBindTexture(GL_TEXTURE_BUFFER, texture); }

    public long bytes() { return (long) capacityFloats * Float.BYTES; }

    @Override
    public void close() {
        if (staging != null) MemoryUtil.memFree(staging);
        glDeleteTextures(texture);
        glDeleteBuffers(buffer);
    }
}
