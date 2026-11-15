package dev.lastpagans.recruitsperformance.client.scene;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

public record PoseData(float[] values) {
    public static final int BONE_COUNT = 6;
    public static final int FLOATS_PER_BONE = 6;

    public static PoseData capture(HumanoidModel<?> model) {
        float[] values = new float[BONE_COUNT * FLOATS_PER_BONE];
        put(values, 0, model.head);
        put(values, 1, model.body);
        put(values, 2, model.rightArm);
        put(values, 3, model.leftArm);
        put(values, 4, model.rightLeg);
        put(values, 5, model.leftLeg);
        return new PoseData(values);
    }

    private static void put(float[] values, int bone, ModelPart part) {
        int offset = bone * FLOATS_PER_BONE;
        values[offset] = part.xRot;
        values[offset + 1] = part.yRot;
        values[offset + 2] = part.zRot;
        values[offset + 3] = part.x / 16.0F;
        values[offset + 4] = part.y / 16.0F;
        values[offset + 5] = part.z / 16.0F;
    }
}
