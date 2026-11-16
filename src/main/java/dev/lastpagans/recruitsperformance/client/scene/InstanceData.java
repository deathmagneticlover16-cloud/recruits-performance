package dev.lastpagans.recruitsperformance.client.scene;

import dev.lastpagans.recruitsperformance.client.compat.IcysHorsesCompat;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public record InstanceData(
        float x, float y, float z,
        float yawRadians,
        float animationPhase,
        float animationId,
        float red, float green, float blue, float alpha,
        float blockLight, float skyLight,
        float poseIndex,
        float scale,
        int entityId
) {
    public static InstanceData from(LivingEntity entity, float partialTick, int packedLight,
                                    int poseIndex, float scale) {
        float x = (float) Mth.lerp(partialTick, entity.xOld, entity.getX());
        float y = (float) Mth.lerp(partialTick, entity.yOld, entity.getY());
        if (IcysHorsesCompat.isRecruitRidingIcysHorse(entity)) {
            // Recruits SPA uses the same seat correction in IcysHorseRiderAnchor.
            y -= 0.45F;
        }
        float z = (float) Mth.lerp(partialTick, entity.zOld, entity.getZ());
        float yaw = Mth.PI - Mth.lerp(partialTick, entity.yBodyRotO, entity.yBodyRot) * Mth.DEG_TO_RAD;
        CrowdAnimation animation = CrowdAnimation.select(entity);
        float phase;
        if (animation == CrowdAnimation.DEATH) {
            phase = entity.deathTime + partialTick;
        } else {
            phase = entity.tickCount + partialTick + (entity.getId() * 0.6180339F % 1.0F) * 20.0F;
        }
        return new InstanceData(
                x, y, z, yaw, phase, animation.shaderId(),
                1.0F, 1.0F, 1.0F,
                1.0F,
                (packedLight & 0xFFFF) / 240.0F,
                ((packedLight >>> 16) & 0xFFFF) / 240.0F,
                poseIndex,
                scale,
                entity.getId());
    }

    public InstanceData tinted(int tint) {
        return new InstanceData(x, y, z, yawRadians, animationPhase, animationId,
                red * ((tint >> 16) & 255) / 255.0F,
                green * ((tint >> 8) & 255) / 255.0F,
                blue * (tint & 255) / 255.0F,
                alpha * ((tint >>> 24) & 255) / 255.0F,
                blockLight, skyLight, poseIndex, scale, entityId);
    }
}
