package dev.lastpagans.recruitsperformance.client.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class ItemMeshConverter {
    private ItemMeshConverter() {}

    public static MeshData convert(LivingEntity entity, ItemStack stack, boolean leftHand) {
        MeshData.Builder builder = new MeshData.Builder();
        CaptureVertexConsumer consumer = new CaptureVertexConsumer(builder);
        consumer.bone(leftHand ? HumanoidMeshConverter.LEFT_ARM : HumanoidMeshConverter.RIGHT_ARM);
        consumer.pivot(leftHand ? 5.0F / 16.0F : -5.0F / 16.0F, 2.0F / 16.0F, 0.0F);
        MultiBufferSource buffers = renderType -> consumer;
        PoseStack pose = new PoseStack();
        pose.translate(leftHand ? 0.34 : -0.34, 0.38, 0.0);
        pose.scale(0.65F, 0.65F, 0.65F);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                entity, stack,
                leftHand ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                leftHand, pose, buffers, entity.level(), 0x00F000F0, OverlayTexture.NO_OVERLAY, entity.getId());
        return builder.buildQuads();
    }
}
