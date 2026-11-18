package dev.lastpagans.recruitsperformance.client.scene;

import net.minecraft.resources.ResourceLocation;

public record MeshKey(String geometry, ResourceLocation texture, int bone, Kind kind) {
    /**
     * The order mirrors the source renderers. It is explicit because material batches shared by
     * different recruits are not naturally encountered in a reliable layer order.
     */
    public enum Kind {
        BODY(0),
        HORSE_BODY(0),
        IMPOSTOR(0),
        ARMOR(10),
        ARMOR_OVERLAY(11),
        HORSE_TACK(12),
        BODY_TEAM(20),
        BODY_BIOME(21),
        BODY_COMPANION(22),
        MAIN_HAND(40),
        OFF_HAND(40),
        DECORATION(50),
        DECORATION_OVERLAY(51);

        private final int renderOrder;

        Kind(int renderOrder) {
            this.renderOrder = renderOrder;
        }

        public int renderOrder() {
            return renderOrder;
        }
    }
}
