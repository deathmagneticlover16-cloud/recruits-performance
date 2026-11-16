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
        MeshData.Builder builder = new MeshData.Builder();
        CaptureVertexConsumer consumer = new CaptureVertexConsumer(builder);
        List<SavedPose> poses = save(model);
        try {
            poses.forEach(saved -> saved.part.loadPose(saved.part.getInitialPose()));
            capture(model.head, HEAD, consumer);
            if (fullDetail) capture(model.hat, HEAD, consumer);
            capture(model.body, BODY, consumer);
            capture(model.rightArm, RIGHT_ARM, consumer);
            capture(model.leftArm, LEFT_ARM, consumer);
            capture(model.rightLeg, RIGHT_LEG, consumer);
            capture(model.leftLeg, LEFT_LEG, consumer);
        } finally {
            poses.forEach(saved -> saved.part.loadPose(saved.pose));
        }
        return builder.buildQuads();
    }

    private static void capture(ModelPart part, int bone, CaptureVertexConsumer consumer) {
        if (!part.visible) return;
        consumer.bone(bone);
        consumer.pivot(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
        part.render(new PoseStack(), consumer, 0x00F000F0, OverlayTexture.NO_OVERLAY);
    }

    private static List<SavedPose> save(HumanoidModel<?> model) {
        List<SavedPose> poses = new ArrayList<>(7);
        poses.add(new SavedPose(model.head, model.head.storePose()));
        poses.add(new SavedPose(model.hat, model.hat.storePose()));
        poses.add(new SavedPose(model.body, model.body.storePose()));
        poses.add(new SavedPose(model.rightArm, model.rightArm.storePose()));
        poses.add(new SavedPose(model.leftArm, model.leftArm.storePose()));
        poses.add(new SavedPose(model.rightLeg, model.rightLeg.storePose()));
        poses.add(new SavedPose(model.leftLeg, model.leftLeg.storePose()));
        return poses;
    }

    private record SavedPose(ModelPart part, PartPose pose) {}
}
