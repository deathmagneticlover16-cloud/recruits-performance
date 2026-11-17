package dev.lastpagans.recruitsperformance.client.scene;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public record PoseData(float[] values) {
    public static final int BONE_COUNT = 17;
    public static final int FLOATS_PER_BONE = 12;
    public static final int TEXELS_PER_BONE = 4;

    public static PoseData capture(HumanoidModel<?> model) {
        return capture(model, new Matrix4f(), new Matrix4f(), new Matrix4f());
    }

    public static PoseData capture(HumanoidModel<?> model, Matrix4f rightItem, Matrix4f leftItem) {
        return capture(model, rightItem, leftItem, new Matrix4f());
    }

    public static PoseData capture(HumanoidModel<?> model, Matrix4f rightItem, Matrix4f leftItem,
                                   Matrix4f bodyTransform) {
        float[] values = new float[BONE_COUNT * FLOATS_PER_BONE];
        for (int bone = 0; bone < BONE_COUNT; bone++) putIdentity(values, bone);
        put(values, 0, model.head);
        put(values, 1, model.body);
        put(values, 2, model.rightArm);
        put(values, 3, model.leftArm);
        put(values, 4, model.rightLeg);
        put(values, 5, model.leftLeg);
        putItem(values, 6, model.rightArm, rightItem);
        putItem(values, 7, model.leftArm, leftItem);
        putMatrix(values, 16, bodyTransform);
        return new PoseData(values);
    }

    public static void interpolate(PoseData start, PoseData end, float partialTick, PoseData destination) {
        float delta = Mth.clamp(partialTick, 0.0F, 1.0F);
        float[] from = start.values;
        float[] to = end.values;
        float[] result = destination.values;
        if (from.length != to.length || result.length != from.length) {
            throw new IllegalArgumentException("Pose interpolation buffers have different lengths");
        }
        for (int index = 0; index < result.length; index++) {
            result[index] = Mth.lerp(delta, from[index], to[index]);
        }
    }

    private static void put(float[] values, int bone, ModelPart part) {
        putMatrix(values, bone, skin(part, new Matrix4f(), new Matrix4f()));
    }

    private static void putItem(float[] values, int bone, ModelPart arm, Matrix4f itemTransform) {
        Matrix4f bind = local(arm.getInitialPose());
        Matrix4f current = local(arm);
        putMatrix(values, bone, current.mul(itemTransform).mul(bind.invert()));
    }

    public static void putMatrix(float[] values, int bone, Matrix4f matrix) {
        int offset = bone * FLOATS_PER_BONE;
        values[offset] = matrix.m00(); values[offset + 1] = matrix.m01(); values[offset + 2] = matrix.m02();
        values[offset + 3] = matrix.m10(); values[offset + 4] = matrix.m11(); values[offset + 5] = matrix.m12();
        values[offset + 6] = matrix.m20(); values[offset + 7] = matrix.m21(); values[offset + 8] = matrix.m22();
        values[offset + 9] = matrix.m30(); values[offset + 10] = matrix.m31(); values[offset + 11] = matrix.m32();
    }

    public static Matrix4f local(PartPose pose) {
        return local(pose.x, pose.y, pose.z, pose.xRot, pose.yRot, pose.zRot);
    }

    public static Matrix4f local(ModelPart part) {
        return local(part.x, part.y, part.z, part.xRot, part.yRot, part.zRot);
    }

    private static Matrix4f local(float x, float y, float z, float xRot, float yRot, float zRot) {
        return new Matrix4f().translation(x / 16.0F, y / 16.0F, z / 16.0F)
                .rotateZ(zRot).rotateY(yRot).rotateX(xRot);
    }

    private static Matrix4f skin(ModelPart part, Matrix4f bindParent, Matrix4f currentParent) {
        Matrix4f bind = new Matrix4f(bindParent).mul(local(part.getInitialPose()));
        Matrix4f current = new Matrix4f(currentParent).mul(local(part));
        return current.mul(new Matrix4f(bind).invert());
    }

    private static void putIdentity(float[] values, int bone) {
        putMatrix(values, bone, new Matrix4f());
    }
}
