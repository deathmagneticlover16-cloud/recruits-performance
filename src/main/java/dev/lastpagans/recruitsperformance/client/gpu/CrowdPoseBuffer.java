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
        FloatBuffer data = MemoryUtil.memAllocFloat(required);
        try {
            for (PoseData pose : poses) data.put(pose.values());
            while (data.position() < required) data.put(0.0F);
            data.flip();
            glBindBuffer(GL_TEXTURE_BUFFER, buffer);
            if (required > capacityFloats) {
                capacityFloats = Math.max(required, Integer.highestOneBit(required - 1) << 1);
                glBufferData(GL_TEXTURE_BUFFER, (long) capacityFloats * Float.BYTES, GL_STREAM_DRAW);
            }
            glBufferSubData(GL_TEXTURE_BUFFER, 0, data);
            glBindTexture(GL_TEXTURE_BUFFER, texture);
            glTexBuffer(GL_TEXTURE_BUFFER, GL_RGB32F, buffer);
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    public void bind() { glBindTexture(GL_TEXTURE_BUFFER, texture); }

    public long bytes() { return (long) capacityFloats * Float.BYTES; }

    @Override
    public void close() {
        glDeleteTextures(texture);
        glDeleteBuffers(buffer);
    }
}
