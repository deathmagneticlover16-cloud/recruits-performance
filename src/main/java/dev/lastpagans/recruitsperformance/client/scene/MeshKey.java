package dev.lastpagans.recruitsperformance.client.scene;

import net.minecraft.resources.ResourceLocation;

public record MeshKey(String geometry, ResourceLocation texture, int bone, Kind kind) {
    public enum Kind { BODY, ARMOR, DECORATION, MAIN_HAND, OFF_HAND, HORSE_BODY, HORSE_TACK, IMPOSTOR }
}
