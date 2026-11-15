package dev.lastpagans.recruitsperformance.client.scene;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public record InstanceData(
        float x, float y, float z,
        float yawRadians,
        float animationPhase,
        float animationId,
        float red, float green, float blue, float alpha,
        float blockLight, float skyLight,
        int entityId
) {
    public static InstanceData from(LivingEntity entity, float partialTick, int packedLight, int tint) {
        float x = (float) Mth.lerp(partialTick, entity.xOld, entity.getX());
        float y = (float) Mth.lerp(partialTick, entity.yOld, entity.getY());
        float z = (float) Mth.lerp(partialTick, entity.zOld, entity.getZ());
        float yaw = -Mth.lerp(partialTick, entity.yBodyRotO, entity.yBodyRot) * Mth.DEG_TO_RAD;
        CrowdAnimation animation = CrowdAnimation.select(entity);
        float phase = entity.tickCount + partialTick + (entity.getId() * 0.6180339F % 1.0F) * 20.0F;
        float red = ((tint >> 16) & 255) / 255.0F;
        float green = ((tint >> 8) & 255) / 255.0F;
        float blue = (tint & 255) / 255.0F;
        if (entity.hurtTime > 0) {
            green *= 0.35F;
            blue *= 0.35F;
        }
        return new InstanceData(
                x, y, z, yaw, phase, animation.shaderId(),
                red, green, blue,
                ((tint >>> 24) & 255) / 255.0F,
                (packedLight & 0xFFFF) / 240.0F,
                ((packedLight >>> 16) & 0xFFFF) / 240.0F,
                entity.getId());
    }
}
