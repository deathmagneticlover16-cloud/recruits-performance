package dev.lastpagans.recruitsperformance.client.scene;

import net.minecraft.world.entity.LivingEntity;

public enum CrowdAnimation {
    IDLE(0), HIT(7), DEATH(8);

    private final int shaderId;

    CrowdAnimation(int shaderId) {
        this.shaderId = shaderId;
    }

    public int shaderId() {
        return shaderId;
    }

    public static CrowdAnimation select(LivingEntity entity) {
        if (entity.isDeadOrDying()) return DEATH;
        if (entity.hurtTime > 0) return HIT;
        return IDLE;
    }
}
