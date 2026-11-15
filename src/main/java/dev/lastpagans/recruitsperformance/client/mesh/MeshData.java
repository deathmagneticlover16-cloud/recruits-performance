package dev.lastpagans.recruitsperformance.client.mesh;

import java.util.ArrayList;
import java.util.List;

public final class MeshData {
    public static final int FLOATS_PER_VERTEX = 13;

    private final float[] vertices;
    private final int[] indices;

    public MeshData(float[] vertices, int[] indices) {
        this.vertices = vertices;
        this.indices = indices;
    }

    public float[] vertices() { return vertices; }
    public int[] indices() { return indices; }
    public int vertexCount() { return vertices.length / FLOATS_PER_VERTEX; }
    public long bytes() { return (long) vertices.length * Float.BYTES + (long) indices.length * Integer.BYTES; }
    public boolean isEmpty() { return indices.length == 0; }

    public static final class Builder {
        private final List<Vertex> vertices = new ArrayList<>();

        public void vertex(float x, float y, float z, float u, float v,
                           float nx, float ny, float nz, int bone,
                           float red, float green, float blue, float alpha) {
            vertices.add(new Vertex(x, y, z, u, v, nx, ny, nz, bone, red, green, blue, alpha));
        }

        public MeshData buildQuads() {
            int quads = vertices.size() / 4;
            float[] packed = new float[quads * 4 * FLOATS_PER_VERTEX];
            int[] indices = new int[quads * 6];
            for (int i = 0; i < quads * 4; i++) {
                Vertex vertex = vertices.get(i);
                int base = i * FLOATS_PER_VERTEX;
                packed[base] = vertex.x;
                packed[base + 1] = vertex.y;
                packed[base + 2] = vertex.z;
                packed[base + 3] = vertex.u;
                packed[base + 4] = vertex.v;
                packed[base + 5] = vertex.nx;
                packed[base + 6] = vertex.ny;
                packed[base + 7] = vertex.nz;
                packed[base + 8] = vertex.bone;
                packed[base + 9] = vertex.red;
                packed[base + 10] = vertex.green;
                packed[base + 11] = vertex.blue;
                packed[base + 12] = vertex.alpha;
            }
            for (int quad = 0; quad < quads; quad++) {
                int vertex = quad * 4;
                int index = quad * 6;
                indices[index] = vertex;
                indices[index + 1] = vertex + 1;
                indices[index + 2] = vertex + 2;
                indices[index + 3] = vertex + 2;
                indices[index + 4] = vertex + 3;
                indices[index + 5] = vertex;
            }
            return new MeshData(packed, indices);
        }
    }

    private record Vertex(float x, float y, float z, float u, float v,
                          float nx, float ny, float nz, int bone,
                          float red, float green, float blue, float alpha) {}
}
