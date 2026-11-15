package dev.lastpagans.recruitsperformance.client;

import dev.lastpagans.recruitsperformance.client.mesh.MeshData;

import java.util.Arrays;

public final class MeshDataTest {
    public static void main(String[] args) {
        MeshData.Builder builder = new MeshData.Builder();
        builder.vertex(0, 0, 0, 0, 0, 0, 1, 0, 2, 1, 1, 1, 1, 0, 0, 0);
        builder.vertex(1, 0, 0, 1, 0, 0, 1, 0, 2, 1, 1, 1, 1, 0, 0, 0);
        builder.vertex(1, 1, 0, 1, 1, 0, 1, 0, 2, 1, 1, 1, 1, 0, 0, 0);
        builder.vertex(0, 1, 0, 0, 1, 0, 1, 0, 2, 1, 1, 1, 1, 0, 0, 0);
        MeshData mesh = builder.buildQuads();
        require(mesh.vertexCount() == 4, "quad did not retain four shared vertices");
        require(Arrays.equals(mesh.indices(), new int[]{0, 1, 2, 2, 3, 0}), "quad triangulation is incorrect");
        require(mesh.vertices()[8] == 2.0F, "bone index was not packed");
        System.out.println("MeshDataTest passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
