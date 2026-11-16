package dev.lastpagans.recruitsperformance.client.scene;

import dev.lastpagans.recruitsperformance.client.compat.BetterCombatCompat;
import dev.lastpagans.recruitsperformance.client.compat.PlayerAnimationCompat;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class HumanoidPoseEvaluator {
    private static final float TWO_PI = (float) (Math.PI * 2.0D);

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
        model.prepareMobModel(entity, limbSwing, limbAmount, partialTick);
        model.setupAnim(entity, limbSwing, limbAmount, age, relativeHeadYaw, headPitch);
        PlayerAnimationCompat.apply(entity, model, partialTick);
        return PoseData.capture(model,
                PlayerAnimationCompat.itemTransform(entity, false, partialTick),
                PlayerAnimationCompat.itemTransform(entity, true, partialTick));
    }

    public static PoseKey cacheKey(HumanoidModel<?> model, LivingEntity entity, float partialTick) {
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
        float relativeHeadYaw = Mth.wrapDegrees(headYaw - bodyYaw);
        float walkPosition = entity.walkAnimation.position(partialTick);
        float walkSpeed = entity.walkAnimation.speed(partialTick);
        boolean playerAnimation = PlayerAnimationCompat.isActive(entity, partialTick);
        return new PoseKey(
                System.identityHashCode(model),
                entity.getType().toString(),
                quantizeCycle(walkPosition * 0.6662F, 64),
                quantize(walkSpeed, 32.0F),
                quantize(relativeHeadYaw, 0.25F),
                quantize(entity.getViewXRot(partialTick), 0.25F),
                quantize(entity.getAttackAnim(partialTick), 32.0F),
                quantizeCycle((entity.tickCount + partialTick) * 0.09F, 64),
                stateBits(entity, playerAnimation),
                itemKey(entity.getMainHandItem()),
                itemKey(entity.getOffhandItem()),
                entity.getUseItemRemainingTicks(),
                quantize(entity.getSwimAmount(partialTick), 32.0F),
                playerAnimation ? quantize(BetterCombatCompat.animationPhase(entity, partialTick), 16.0F) : 0,
                playerAnimation ? BetterCombatCompat.combo(entity) : 0);
    }

    private static int stateBits(LivingEntity entity, boolean playerAnimation) {
        int state = 0;
        if (entity.isPassenger()) state |= 1;
        if (entity.isBaby()) state |= 1 << 1;
        if (entity.isCrouching()) state |= 1 << 2;
        if (entity.isSprinting()) state |= 1 << 3;
        if (entity.isUsingItem()) state |= 1 << 4;
        if (entity.swinging) state |= 1 << 5;
        if (entity.isDeadOrDying()) state |= 1 << 6;
        if (entity.hurtTime > 0) state |= 1 << 7;
        if (playerAnimation) state |= 1 << 8;
        if (entity.isFallFlying()) state |= 1 << 9;
        if (entity.isVisuallySwimming()) state |= 1 << 10;
        if (entity.isAutoSpinAttack()) state |= 1 << 11;
        return state;
    }

    private static int itemKey(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).hashCode();
    }

    private static int quantize(float value, float scale) {
        return Math.round(value * scale);
    }

    private static int quantizeCycle(float value, int buckets) {
        float cycle = value % TWO_PI;
        if (cycle < 0.0F) cycle += TWO_PI;
        return Mth.floor(cycle / TWO_PI * buckets) % buckets;
    }

    public record PoseKey(int model, String entityType, int walkPhase, int walkSpeed,
                          int headYaw, int headPitch, int attack, int age, int state,
                          int mainHand, int offHand, int useTime, int swim,
                          int playerAnimationPhase, int combo) {}
}
