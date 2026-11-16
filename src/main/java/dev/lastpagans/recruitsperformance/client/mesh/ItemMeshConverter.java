package dev.lastpagans.recruitsperformance.client.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultedVertexConsumer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class ItemMeshConverter {
    private static final int MAX_CAPTURED_VERTICES = 32_768;
    private static final VertexConsumer DISCARD = new DiscardingVertexConsumer();

    private ItemMeshConverter() {}

    public static MeshData convert(LivingEntity entity, ItemStack stack, boolean leftHand, ModelPart arm) {
        MeshData.Builder builder = new MeshData.Builder();
        CaptureVertexConsumer consumer = new CaptureVertexConsumer(builder);
        PartPose bind = arm.getInitialPose();
        consumer.bone(leftHand ? HumanoidMeshConverter.LEFT_ARM : HumanoidMeshConverter.RIGHT_ARM);
        consumer.pivot(bind.x / 16.0F, bind.y / 16.0F, bind.z / 16.0F);
        boolean[] rejectedDynamicPass = {false};
        MultiBufferSource buffers = renderType -> {
            if (isDynamicForeignPass(renderType)) {
                rejectedDynamicPass[0] = true;
                return DISCARD;
            }
            return consumer;
        };
        PoseStack pose = new PoseStack();
        pose.translate(bind.x / 16.0F, bind.y / 16.0F, bind.z / 16.0F);
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate((leftHand ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                entity, stack,
                leftHand ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                leftHand, pose, buffers, entity.level(), 0x00F000F0, OverlayTexture.NO_OVERLAY, entity.getId());
        if (rejectedDynamicPass[0]) {
            throw new IllegalStateException("Dynamic Cruor item pass requires the source renderer");
        }
        MeshData data = builder.buildQuads();
        if (data.vertexCount() > MAX_CAPTURED_VERTICES) {
            throw new IllegalStateException("Item capture exceeded the geometry safety limit: " + data.vertexCount());
        }
        return data;
    }

    private static boolean isDynamicForeignPass(RenderType renderType) {
        String name = renderType.toString().toLowerCase(java.util.Locale.ROOT);
        return name.contains("cruor_gear") || name.contains("cruor_wound")
                || name.contains("cruor_armour_wound") || name.contains("cruor_layer_wound");
    }

    private static final class DiscardingVertexConsumer extends DefaultedVertexConsumer {
        @Override public VertexConsumer vertex(double x, double y, double z) { return this; }
        @Override public VertexConsumer color(int red, int green, int blue, int alpha) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() { }
    }
}
