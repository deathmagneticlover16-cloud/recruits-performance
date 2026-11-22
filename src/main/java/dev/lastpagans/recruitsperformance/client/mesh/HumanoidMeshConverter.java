package dev.lastpagans.recruitsperformance.client.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.ArrayList;
import java.util.List;

public final class HumanoidMeshConverter {
    public static final int HEAD = 0;
    public static final int BODY = 1;
    public static final int RIGHT_ARM = 2;
    public static final int LEFT_ARM = 3;
    public static final int RIGHT_LEG = 4;
    public static final int LEFT_LEG = 5;
    public static final int RIGHT_ITEM = 6;
    public static final int LEFT_ITEM = 7;

    private HumanoidMeshConverter() {}

    public static MeshData convert(HumanoidModel<?> model) {
        return convert(model, true);
    }

    public static MeshData convert(HumanoidModel<?> model, boolean fullDetail) {
        return convert(model, model, fullDetail);
    }

    /** Captures a layer in its parent model's bind pose, matching HumanoidModel.copyPropertiesTo. */
    public static MeshData convertAttached(HumanoidModel<?> model, HumanoidModel<?> parent) {
        return convert(model, parent, true);
    }

    private static MeshData convert(HumanoidModel<?> model, HumanoidModel<?> bindModel, boolean fullDetail) {
        MeshData.Builder builder = new MeshData.Builder();
        CaptureVertexConsumer consumer = new CaptureVertexConsumer(builder);
        List<SavedPose> poses = save(model);
        try {
            loadBindPose(model, bindModel);
            capture(model.head, HEAD, consumer);
            // The hat part is the second skin/robe layer on Recruits humanoids. It is material
            // geometry, not an optional nametag-style detail, and dropping it breaks both skins
            // and armor-adjacent layers at the LOD boundary.
            capture(model.hat, HEAD, consumer);
            capture(model.body, BODY, consumer);
            capture(model.rightArm, RIGHT_ARM, consumer);
            capture(model.leftArm, LEFT_ARM, consumer);
            capture(model.rightLeg, RIGHT_LEG, consumer);
            capture(model.leftLeg, LEFT_LEG, consumer);
        } finally {
            poses.forEach(SavedPose::restore);
        }
        return builder.buildQuads();
    }

    private static void loadBindPose(HumanoidModel<?> model, HumanoidModel<?> bindModel) {
        loadBindPart(model.head, bindModel.head);
        loadBindPart(model.hat, bindModel.hat);
        loadBindPart(model.body, bindModel.body);
        loadBindPart(model.rightArm, bindModel.rightArm);
        loadBindPart(model.leftArm, bindModel.leftArm);
        loadBindPart(model.rightLeg, bindModel.rightLeg);
        loadBindPart(model.leftLeg, bindModel.leftLeg);
    }

    private static void loadBindPart(ModelPart target, ModelPart source) {
        target.loadPose(source.getInitialPose());
        // ModelPart scale is mutable animation state but is not represented by PartPose. Baking
        // the first entity's scale into a shared mesh makes later skin and armor pieces disappear
        // or distort. Keep the cached geometry unscaled; PoseData supplies live root scale.
        target.xScale = 1.0F;
        target.yScale = 1.0F;
        target.zScale = 1.0F;
    }

    private static void capture(ModelPart part, int bone, CaptureVertexConsumer consumer) {
        if (!part.visible) return;
        consumer.bone(bone);
        consumer.pivot(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
        part.render(new PoseStack(), consumer, 0x00F000F0, OverlayTexture.NO_OVERLAY);
    }

    private static List<SavedPose> save(HumanoidModel<?> model) {
        List<SavedPose> poses = new ArrayList<>(7);
        poses.add(SavedPose.capture(model.head));
        poses.add(SavedPose.capture(model.hat));
        poses.add(SavedPose.capture(model.body));
        poses.add(SavedPose.capture(model.rightArm));
        poses.add(SavedPose.capture(model.leftArm));
        poses.add(SavedPose.capture(model.rightLeg));
        poses.add(SavedPose.capture(model.leftLeg));
        return poses;
    }

    private record SavedPose(ModelPart part, PartPose pose, float xScale, float yScale, float zScale) {
        private static SavedPose capture(ModelPart part) {
            return new SavedPose(part, part.storePose(), part.xScale, part.yScale, part.zScale);
        }

        private void restore() {
            part.loadPose(pose);
            part.xScale = xScale;
            part.yScale = yScale;
            part.zScale = zScale;
        }
    }
}
