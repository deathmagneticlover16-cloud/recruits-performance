package dev.lastpagans.recruitsperformance.client.gpu;

import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL33C.GL_TIME_ELAPSED;
import static org.lwjgl.opengl.GL33C.glGetQueryObjecti64;

/** Double-buffered timer queries expose GPU duration without synchronously waiting for the driver. */
public final class GpuFrameTimer implements AutoCloseable {
    private final int[] queries = {glGenQueries(), glGenQueries()};
    private final boolean[] pending = new boolean[2];
    private int next;
    private int active = -1;
    private long lastNanos;

    public void begin() {
        poll(next ^ 1);
        poll(next);
        if (pending[next]) return;
        active = next;
        glBeginQuery(GL_TIME_ELAPSED, queries[active]);
    }

    public void end() {
        if (active < 0) return;
        glEndQuery(GL_TIME_ELAPSED);
        pending[active] = true;
        next = active ^ 1;
        active = -1;
    }

    public long lastNanos() { return lastNanos; }

    @Override
    public void close() {
        end();
        glDeleteQueries(queries[0]);
        glDeleteQueries(queries[1]);
    }

    private void poll(int index) {
        if (!pending[index] || glGetQueryObjecti(queries[index], GL_QUERY_RESULT_AVAILABLE) == 0) return;
        lastNanos = glGetQueryObjecti64(queries[index], GL_QUERY_RESULT);
        pending[index] = false;
    }
}
