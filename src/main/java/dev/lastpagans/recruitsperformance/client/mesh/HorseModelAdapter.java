package dev.lastpagans.recruitsperformance.client.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

import dev.lastpagans.recruitsperformance.client.scene.PoseData;

import java.lang.reflect.Field;

/** Reflection-only adapter so Icy's Better Horses remains an optional dependency. */
public final class HorseModelAdapter {
    private static final String MODEL_CLASS = "icy.betterhorses.net.client.render.BhHorseModel";
    private static final String[] PART_NAMES = {
            "rootPart", "body", "neck", "head", "snout", "leftEar", "rightEar", "mane",
            "maneTip", "tail", "frontLeftLeg", "frontRightLeg", "backLeftLeg", "backRightLeg",
            "leftRein", "rightRein"
    };
    private static final int[] PARENT = {-1, 0, 1, 2, 3, 3, 3, 2, 3, 1, 0, 0, 0, 0, 4, 4};

    private HorseModelAdapter() {}

    public static boolean supports(Object model) {
        for (Class<?> type = model.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(MODEL_CLASS)) return true;
        }
        return false;
    }

    public static MeshData convert(Object model) {
        ModelPart[] parts = parts(model);
        PartPose[] saved = save(parts);
        boolean[] visibility = visibility(parts);
        MeshData.Builder builder = new MeshData.Builder();
        CaptureVertexConsumer consumer = new CaptureVertexConsumer(builder);
        try {
            reset(parts);
            Matrix4f[] bind = globals(parts, true);
            for (int bone = 0; bone < parts.length; bone++) {
                ModelPart part = parts[bone];
                if (!part.visible) continue;
                boolean[] hidden = hideStrategicChildren(parts, bone);
                try {
                    PoseStack pose = new PoseStack();
                    applyAncestors(parts, bone, pose);
                    consumer.bone(bone);
                    consumer.pivot(bind[bone].m30(), bind[bone].m31(), bind[bone].m32());
                    part.render(pose, consumer, 0x00F000F0, OverlayTexture.NO_OVERLAY);
                } finally {
                    restoreStrategicChildren(parts, bone, hidden);
                }
            }
        } finally {
            restore(parts, saved, visibility);
        }
        return builder.buildQuads();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static PoseData evaluate(Object model, LivingEntity entity, float partialTick) {
        EntityModel typed = (EntityModel) model;
        typed.attackTime = entity.getAttackAnim(partialTick);
        typed.riding = entity.isPassenger();
        typed.young = entity.isBaby();
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
        float relativeHeadYaw = Mth.wrapDegrees(headYaw - bodyYaw);
        float limbAmount = entity.isAlive() ? Math.min(entity.walkAnimation.speed(partialTick), 1.0F) : 0.0F;
        float limbSwing = entity.isAlive() ? entity.walkAnimation.position(partialTick) : 0.0F;
        if (typed.young) limbSwing *= 3.0F;
        typed.prepareMobModel(entity, limbSwing, limbAmount, partialTick);
        typed.setupAnim(entity, limbSwing, limbAmount, entity.tickCount + partialTick,
                relativeHeadYaw, entity.getViewXRot(partialTick));

        ModelPart[] parts = parts(model);
        Matrix4f[] bind = globals(parts, true);
        Matrix4f[] current = globals(parts, false);
        float[] values = new float[PoseData.BONE_COUNT * PoseData.FLOATS_PER_BONE];
        for (int bone = 0; bone < parts.length; bone++) {
            Matrix4f skin = new Matrix4f(current[bone]).mul(new Matrix4f(bind[bone]).invert());
            PoseData.putMatrix(values, bone, skin);
        }
        return new PoseData(values);
    }

    private static ModelPart[] parts(Object model) {
        ModelPart[] result = new ModelPart[PART_NAMES.length];
        try {
            Class<?> base = model.getClass();
            while (base != null && !base.getName().equals(MODEL_CLASS)) base = base.getSuperclass();
            if (base == null) throw new IllegalArgumentException("Not an Icy's Better Horses model: " + model.getClass());
            for (int index = 0; index < PART_NAMES.length; index++) {
                Field field = base.getDeclaredField(PART_NAMES[index]);
                field.setAccessible(true);
                result[index] = (ModelPart) field.get(model);
            }
            return result;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Icy's Better Horses 2.1.6 model contract changed", failure);
        }
    }

    private static Matrix4f[] globals(ModelPart[] parts, boolean initial) {
        Matrix4f[] result = new Matrix4f[parts.length];
        for (int index = 0; index < parts.length; index++) {
            Matrix4f local = initial ? PoseData.local(parts[index].getInitialPose()) : PoseData.local(parts[index]);
            result[index] = PARENT[index] < 0 ? local : new Matrix4f(result[PARENT[index]]).mul(local);
        }
        return result;
    }

    private static void applyAncestors(ModelPart[] parts, int bone, PoseStack pose) {
        int depth = 0;
        int cursor = PARENT[bone];
        int[] path = new int[PART_NAMES.length];
        while (cursor >= 0) {
            path[depth++] = cursor;
            cursor = PARENT[cursor];
        }
        for (int index = depth - 1; index >= 0; index--) parts[path[index]].translateAndRotate(pose);
    }

    private static boolean[] hideStrategicChildren(ModelPart[] parts, int bone) {
        boolean[] result = new boolean[parts.length];
        for (int index = 0; index < parts.length; index++) {
            if (PARENT[index] == bone) {
                result[index] = parts[index].visible;
                parts[index].visible = false;
            }
        }
        return result;
    }

    private static void restoreStrategicChildren(ModelPart[] parts, int bone, boolean[] visible) {
        for (int index = 0; index < parts.length; index++) {
            if (PARENT[index] == bone) parts[index].visible = visible[index];
        }
    }

    private static PartPose[] save(ModelPart[] parts) {
        PartPose[] poses = new PartPose[parts.length];
        for (int index = 0; index < parts.length; index++) poses[index] = parts[index].storePose();
        return poses;
    }

    private static boolean[] visibility(ModelPart[] parts) {
        boolean[] result = new boolean[parts.length];
        for (int index = 0; index < parts.length; index++) result[index] = parts[index].visible;
        return result;
    }

    private static void reset(ModelPart[] parts) {
        for (ModelPart part : parts) part.loadPose(part.getInitialPose());
    }

    private static void restore(ModelPart[] parts, PartPose[] poses, boolean[] visibility) {
        for (int index = 0; index < parts.length; index++) {
            parts[index].loadPose(poses[index]);
            parts[index].visible = visibility[index];
        }
    }
}
