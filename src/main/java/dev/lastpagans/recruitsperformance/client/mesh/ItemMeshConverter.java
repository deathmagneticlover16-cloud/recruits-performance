package dev.lastpagans.recruitsperformance.client.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class ItemMeshConverter {
    private ItemMeshConverter() {}

    public static MeshData convert(LivingEntity entity, ItemStack stack, boolean leftHand, ModelPart arm) {
        MeshData.Builder builder = new MeshData.Builder();
        CaptureVertexConsumer consumer = new CaptureVertexConsumer(builder);
        PartPose bind = arm.getInitialPose();
        consumer.bone(leftHand ? HumanoidMeshConverter.LEFT_ARM : HumanoidMeshConverter.RIGHT_ARM);
        consumer.pivot(bind.x / 16.0F, bind.y / 16.0F, bind.z / 16.0F);
        MultiBufferSource buffers = renderType -> consumer;
        PoseStack pose = new PoseStack();
        pose.translate(bind.x / 16.0F, bind.y / 16.0F, bind.z / 16.0F);
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate((leftHand ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                entity, stack,
                leftHand ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                leftHand, pose, buffers, entity.level(), 0x00F000F0, OverlayTexture.NO_OVERLAY, entity.getId());
        return builder.buildQuads();
    }
}
