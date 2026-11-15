package dev.lastpagans.recruitsperformance.client.scene;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

public enum CrowdAnimation {
    IDLE(0), WALK(1), RUN(2), ATTACK(3), BLOCK(4), BOW(5), CROSSBOW(6), HIT(7), DEATH(8);

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
        String hint = entity.getPersistentData().getString("RecruitsRTSAnimHint");
        if ("SPEAR_THROW".equals(hint)) return ATTACK;
        if ("ROUTING".equals(hint)) return RUN;
        if (entity.swinging) return ATTACK;
        ItemStack using = entity.getUseItem();
        if (!using.isEmpty() && entity.getUseItemRemainingTicks() > 0) {
            UseAnim use = using.getUseAnimation();
            if (use == UseAnim.BLOCK) return BLOCK;
            if (use == UseAnim.BOW) return BOW;
            if (use == UseAnim.CROSSBOW || using.getItem() instanceof CrossbowItem) return CROSSBOW;
        }
        String movement = entity.getPersistentData().getString("RecruitsRTSMovementState");
        if ("RUNNING".equals(movement)) return RUN;
        if ("WALKING".equals(movement)) return WALK;
        double horizontalSpeed = entity.getDeltaMovement().horizontalDistanceSqr();
        if (entity.isSprinting() || horizontalSpeed > 0.035) return RUN;
        if (horizontalSpeed > 0.0004) return WALK;
        return IDLE;
    }
}
