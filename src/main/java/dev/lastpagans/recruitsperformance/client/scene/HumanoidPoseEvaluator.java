package dev.lastpagans.recruitsperformance.client.scene;

import dev.lastpagans.recruitsperformance.client.compat.PlayerAnimationCompat;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public final class HumanoidPoseEvaluator {
    private HumanoidPoseEvaluator() {}

    public static PoseData evaluate(HumanoidModel<LivingEntity> model, LivingEntity entity, float partialTick) {
        model.attackTime = entity.getAttackAnim(partialTick);
        model.riding = entity.isPassenger();
        model.young = entity.isBaby();

        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
        float relativeHeadYaw = Mth.wrapDegrees(headYaw - bodyYaw);
        float headPitch = entity.getViewXRot(partialTick);
        float limbSwing = 0.0F;
        float limbAmount = 0.0F;
        if (!model.riding && entity.isAlive()) {
            limbAmount = entity.walkAnimation.speed(partialTick);
            limbSwing = entity.walkAnimation.position(partialTick);
            if (model.young) limbSwing *= 3.0F;
            limbAmount = Math.min(limbAmount, 1.0F);
        }
        float age = entity.tickCount + partialTick;
        PlayerAnimationCompat.prepare(entity, partialTick);
        model.prepareMobModel(entity, limbSwing, limbAmount, partialTick);
        model.setupAnim(entity, limbSwing, limbAmount, age, relativeHeadYaw, headPitch);
        return PoseData.capture(model,
                PlayerAnimationCompat.itemTransform(entity, false, partialTick),
                PlayerAnimationCompat.itemTransform(entity, true, partialTick),
                PlayerAnimationCompat.bodyTransform(entity, partialTick));
    }
}
