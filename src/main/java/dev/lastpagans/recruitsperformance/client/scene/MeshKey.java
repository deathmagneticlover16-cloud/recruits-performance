package dev.lastpagans.recruitsperformance.client.scene;

import net.minecraft.resources.ResourceLocation;

public record MeshKey(String geometry, ResourceLocation texture, int bone, Kind kind) {
    public enum Kind { BODY, ARMOR, MAIN_HAND, OFF_HAND, IMPOSTOR }
}
