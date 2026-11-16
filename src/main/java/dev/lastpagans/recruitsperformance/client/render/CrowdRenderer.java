package dev.lastpagans.recruitsperformance.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lastpagans.recruitsperformance.RecruitsPerformance;
import dev.lastpagans.recruitsperformance.client.diagnostics.CrowdDiagnostics;
import dev.lastpagans.recruitsperformance.client.compat.BetterCombatCompat;
import dev.lastpagans.recruitsperformance.client.compat.IcysHorsesCompat;
import dev.lastpagans.recruitsperformance.client.compat.ShaderPackCompat;
import dev.lastpagans.recruitsperformance.client.gpu.CrowdShader;
import dev.lastpagans.recruitsperformance.client.gpu.CrowdPoseBuffer;
import dev.lastpagans.recruitsperformance.client.gpu.GpuFrameTimer;
import dev.lastpagans.recruitsperformance.client.mesh.MeshCache;
import dev.lastpagans.recruitsperformance.client.mesh.MeshHandle;
import dev.lastpagans.recruitsperformance.client.mesh.HorseModelAdapter;
import dev.lastpagans.recruitsperformance.client.scene.InstanceData;
import dev.lastpagans.recruitsperformance.client.scene.HumanoidPoseEvaluator;
import dev.lastpagans.recruitsperformance.client.scene.MeshKey;
import dev.lastpagans.recruitsperformance.client.scene.PoseData;
import dev.lastpagans.recruitsperformance.client.scene.RecruitVisualLayers;
import dev.lastpagans.recruitsperformance.config.CrowdRendererConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;

public final class CrowdRenderer {
    private static final CrowdRenderer INSTANCE = new CrowdRenderer();

    private final Map<MeshKey, Batch> batches = new HashMap<>();
    private final Set<Integer> submittedEntities = new HashSet<>();
    private final Set<String> warnedConversions = new HashSet<>();
    private final List<PoseData> poses = new ArrayList<>();
    private final List<Nameplate> nameplates = new ArrayList<>();
    private MeshCache cache;
    private CrowdShader shader;
    private CrowdPoseBuffer poseBuffer;
    private GpuFrameTimer gpuTimer;
    private boolean failed;
    private int fallbacks;

    public static CrowdRenderer get() { return INSTANCE; }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean submit(LivingEntity entity, LivingEntityRenderer renderer, float partialTick, int packedLight) {
        if (!CrowdRendererConfig.enabled.get() || failed || submittedEntities.contains(entity.getId())) return false;
        if (entity.isInvisible() || entity.isCurrentlyGlowing()) return false;
        if (submittedEntities.size() >= CrowdRendererConfig.maximumInstances.get()) return false;
        Minecraft minecraft = Minecraft.getInstance();
        double distanceSquared = minecraft.gameRenderer.getMainCamera().getPosition().distanceToSqr(entity.position());
        if (distanceSquared > CrowdRendererConfig.renderDistance.get() * CrowdRendererConfig.renderDistance.get()) return false;
        Object sourceModel = renderer.getModel();
        if (IcysHorsesCompat.isMountedRecruitHorse(entity) && HorseModelAdapter.supports(sourceModel)) {
            if (!CrowdRendererConfig.gpuAnimation.get()) return false;
            if (!initialize()) return false;
            return submitHorse(entity, renderer, sourceModel, partialTick, packedLight, distanceSquared);
        }
        if (!(sourceModel instanceof HumanoidModel<?> model)) {
            fallbacks++;
            return false;
        }
        if (!initialize()) return false;

        try {
            ResourceLocation texture = renderer.getTextureLocation(entity);
            float modelScale = RecruitVisualLayers.modelScale(texture);
            double lodDistance = CrowdRendererConfig.geometryLodDistance.get();
            boolean fullDetail = distanceSquared <= lodDistance * lodDistance;
            double cruorDistance = CrowdRendererConfig.cruorStainDistance.get();
            boolean cruorDetail = distanceSquared <= cruorDistance * cruorDistance;
            MeshHandle body = cache.body(model, texture, fullDetail);
            if (body == null) return false;
            int poseIndex = -1;
            double exactDistance = CrowdRendererConfig.exactPoseDistance.get();
            if (CrowdRendererConfig.gpuAnimation.get()
                    && !BetterCombatCompat.hasActiveAttackAnimation(entity)
                    && distanceSquared <= exactDistance * exactDistance) {
                HumanoidModel<LivingEntity> typedModel = (HumanoidModel<LivingEntity>) model;
                poseIndex = poses.size();
                poses.add(HumanoidPoseEvaluator.evaluate(typedModel, entity, partialTick));
            }
            InstanceData instance = InstanceData.from(entity, partialTick, packedLight, poseIndex, modelScale);
            add(body, instance.tinted(body.tint()));
            for (ResourceLocation layerTexture : RecruitVisualLayers.textures(entity, texture)) {
                MeshHandle layer = cache.bodyLayer(model, layerTexture, fullDetail);
                if (layer == null) throw new IllegalStateException("Recruits visual layer produced no geometry: " + layerTexture);
                add(layer, instance.tinted(layer.tint()));
            }
            for (MeshHandle equipment : cache.equipment(entity, model, fullDetail, cruorDetail)) {
                add(equipment, instance.tinted(equipment.tint()));
            }
            queueNameplate(entity, partialTick, packedLight, distanceSquared);
            submittedEntities.add(entity.getId());
            return true;
        } catch (Throwable conversionFailure) {
            String key = entity.getType().toString() + ":" + conversionFailure.getMessage();
            if (warnedConversions.add(key)) {
                RecruitsPerformance.LOGGER.warn("Crowd conversion failed; preserving vanilla rendering for {}",
                        entity.getType(), conversionFailure);
            }
            removeEntity(entity.getId());
            fallbacks++;
            return false;
        }
    }

    private boolean submitHorse(LivingEntity horse, LivingEntityRenderer<?, ?> renderer, Object model,
                                float partialTick, int packedLight, double distanceSquared) {
        try {
            @SuppressWarnings("unchecked") LivingEntityRenderer<LivingEntity, ?> typedRenderer =
                    (LivingEntityRenderer<LivingEntity, ?>) renderer;
            ResourceLocation texture = typedRenderer.getTextureLocation(horse);
            double lodDistance = CrowdRendererConfig.geometryLodDistance.get();
            boolean fullDetail = distanceSquared <= lodDistance * lodDistance;
            MeshHandle body = cache.horse(model, texture, 0xFFFFFFFF, MeshKey.Kind.HORSE_BODY, fullDetail);
            if (body == null) return false;
            int poseIndex = poses.size();
            poses.add(HorseModelAdapter.evaluate(model, horse, partialTick));
            InstanceData instance = InstanceData.from(horse, partialTick, packedLight, poseIndex, 1.0F);
            add(body, instance);
            for (IcysHorsesCompat.Tack tack : IcysHorsesCompat.tacks(renderer, horse)) {
                MeshHandle handle = cache.horse(tack.model(), tack.texture(), tack.tint(),
                        MeshKey.Kind.HORSE_TACK, fullDetail);
                if (handle == null) throw new IllegalStateException("Icy horse tack produced no geometry");
                add(handle, instance.tinted(handle.tint()));
            }
            queueNameplate(horse, partialTick, packedLight, distanceSquared);
            submittedEntities.add(horse.getId());
            return true;
        } catch (Throwable conversionFailure) {
            String key = horse.getType() + ":" + conversionFailure.getMessage();
            if (warnedConversions.add(key)) {
                RecruitsPerformance.LOGGER.warn("Mounted Icy horse conversion failed; preserving vanilla rendering for {}",
                        horse.getType(), conversionFailure);
            }
            removeEntity(horse.getId());
            fallbacks++;
            return false;
        }
    }

    public void render(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                && stage != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        RenderLevelStageEvent.Stage target = ShaderPackCompat.isShaderPackActive()
                ? RenderLevelStageEvent.Stage.AFTER_LEVEL : RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        if (stage != target || batches.isEmpty() || failed) return;
        long started = System.nanoTime();
        int drawCalls = 0;
        int instances = 0;
        try {
            var camera = event.getCamera().getPosition();
            Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
            Matrix4f view = new Matrix4f(event.getPoseStack().last().pose());

            glActiveTexture(GL_TEXTURE1);
            poseBuffer.upload(poses);
            poseBuffer.bind();

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            shader.uniforms(projection, view, camera.x, camera.y, camera.z,
                    CrowdRendererConfig.gpuAnimation.get(), poses.size());

            glActiveTexture(GL_TEXTURE0);
            gpuTimer.begin();
            for (Batch batch : batches.values()) {
                if (batch.instances.isEmpty()) continue;
                if (batch.handle.cull()) RenderSystem.enableCull(); else RenderSystem.disableCull();
                batch.handle.mesh().upload(batch.instances);
                int texture = Minecraft.getInstance().getTextureManager().getTexture(batch.handle.texture()).getId();
                glBindTexture(GL_TEXTURE_2D, texture);
                batch.handle.mesh().draw(batch.instances.size());
                drawCalls++;
                instances += batch.instances.size();
            }
            cache.trim(batches.keySet());
            resetGpuState();
            renderNameplates(event);
        } catch (Throwable renderFailure) {
            failed = true;
            RecruitsPerformance.LOGGER.error("Disabling Recruits crowd renderer after GPU failure", renderFailure);
        } finally {
            if (gpuTimer != null) gpuTimer.end();
            resetGpuState();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            long elapsed = System.nanoTime() - started;
            CrowdDiagnostics.get().publish(submittedEntities.size(), submittedEntities.size(), batches.size(),
                    instances, drawCalls, cache == null ? 0 : cache.size(),
                    cache == null ? 0 : cache.equipmentVariants(), fallbacks,
                    (cache == null ? 0 : cache.bytes()) + (poseBuffer == null ? 0 : poseBuffer.bytes()),
                    elapsed, gpuTimer == null ? 0 : gpuTimer.lastNanos());
            batches.clear();
            submittedEntities.clear();
            poses.clear();
            nameplates.clear();
            fallbacks = 0;
        }
    }

    public void reload() {
        Runnable close = () -> {
            batches.clear();
            submittedEntities.clear();
            poses.clear();
            nameplates.clear();
            if (cache != null) cache.close();
            if (shader != null) shader.close();
            if (poseBuffer != null) poseBuffer.close();
            if (gpuTimer != null) gpuTimer.close();
            cache = null;
            shader = null;
            poseBuffer = null;
            gpuTimer = null;
            failed = false;
        };
        if (RenderSystem.isOnRenderThread()) close.run(); else RenderSystem.recordRenderCall(close::run);
    }

    private boolean initialize() {
        if (shader != null && cache != null && poseBuffer != null && gpuTimer != null) return true;
        try {
            shader = new CrowdShader();
            cache = new MeshCache();
            poseBuffer = new CrowdPoseBuffer();
            gpuTimer = new GpuFrameTimer();
            return true;
        } catch (Throwable initializationFailure) {
            if (poseBuffer != null) poseBuffer.close();
            if (cache != null) cache.close();
            if (shader != null) shader.close();
            if (gpuTimer != null) gpuTimer.close();
            poseBuffer = null;
            cache = null;
            shader = null;
            gpuTimer = null;
            failed = true;
            RecruitsPerformance.LOGGER.error("Crowd renderer initialization failed", initializationFailure);
            return false;
        }
    }

    private void add(MeshHandle handle, InstanceData instance) {
        batches.computeIfAbsent(handle.key(), ignored -> new Batch(handle)).instances.add(instance);
    }

    private void removeEntity(int entityId) {
        batches.values().forEach(batch -> batch.instances.removeIf(instance -> instance.entityId() == entityId));
        submittedEntities.remove(entityId);
    }

    private void queueNameplate(LivingEntity entity, float partialTick, int packedLight, double distanceSquared) {
        double range = entity.isDiscrete() ? 32.0D : 64.0D;
        if ((!entity.shouldShowName() && !entity.hasCustomName()) || distanceSquared > range * range) return;
        nameplates.add(new Nameplate(entity.getDisplayName(),
                net.minecraft.util.Mth.lerp(partialTick, entity.xOld, entity.getX()),
                net.minecraft.util.Mth.lerp(partialTick, entity.yOld, entity.getY()) + entity.getBbHeight() + 0.5D,
                net.minecraft.util.Mth.lerp(partialTick, entity.zOld, entity.getZ()),
                entity.isDiscrete(), packedLight));
    }

    private void renderNameplates(RenderLevelStageEvent event) {
        if (nameplates.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        for (Nameplate label : nameplates) {
            pose.pushPose();
            pose.translate(label.x - camera.x, label.y - camera.y, label.z - camera.z);
            pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            pose.scale(-0.025F, -0.025F, 0.025F);
            float x = -font.width(label.text) / 2.0F;
            int background = (int) (minecraft.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
            Font.DisplayMode firstPass = label.discrete ? Font.DisplayMode.NORMAL : Font.DisplayMode.SEE_THROUGH;
            font.drawInBatch(label.text, x, 0.0F, 0x20FFFFFF, false, pose.last().pose(),
                    buffers, firstPass, background, label.packedLight);
            if (!label.discrete) {
                font.drawInBatch(label.text, x, 0.0F, 0xFFFFFFFF, false, pose.last().pose(),
                        buffers, Font.DisplayMode.NORMAL, 0, label.packedLight);
            }
            pose.popPose();
        }
        buffers.endBatch();
    }

    private static void resetGpuState() {
        glActiveTexture(GL_TEXTURE0);
        glBindVertexArray(0);
        glUseProgram(0);
    }

    private static final class Batch {
        private final MeshHandle handle;
        private final List<InstanceData> instances = new ArrayList<>();

        private Batch(MeshHandle handle) { this.handle = handle; }
    }

    private record Nameplate(Component text, double x, double y, double z,
                             boolean discrete, int packedLight) {}
}
