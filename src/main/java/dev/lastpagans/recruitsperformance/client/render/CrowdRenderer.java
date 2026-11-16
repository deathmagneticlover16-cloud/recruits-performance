package dev.lastpagans.recruitsperformance.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
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
        // These paths depend on source-renderer overlays, animation layers, render types, or labels.
        // Keeping them exact also prevents raw OpenGL output from corrupting shader-pack framebuffers.
        double sourceDistance = CrowdRendererConfig.sourceRendererDistance.get();
        if (distanceSquared <= sourceDistance * sourceDistance
                || entity.hurtTime > 0
                || BetterCombatCompat.hasActiveAttackAnimation(entity)
                || ShaderPackCompat.isShaderPackActive()) return false;
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
            MeshHandle body = cache.body(model, texture, fullDetail);
            if (body == null) return false;
            int poseIndex = -1;
            double exactDistance = CrowdRendererConfig.exactPoseDistance.get();
            if (CrowdRendererConfig.gpuAnimation.get() && distanceSquared <= exactDistance * exactDistance) {
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
            for (MeshHandle equipment : cache.equipment(entity, model, fullDetail)) {
                add(equipment, instance.tinted(equipment.tint()));
            }
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
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || batches.isEmpty() || failed) return;
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
            fallbacks = 0;
        }
    }

    public void reload() {
        Runnable close = () -> {
            batches.clear();
            submittedEntities.clear();
            poses.clear();
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
}
