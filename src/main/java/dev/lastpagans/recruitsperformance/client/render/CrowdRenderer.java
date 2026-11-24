package dev.lastpagans.recruitsperformance.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import dev.lastpagans.recruitsperformance.RecruitsPerformance;
import dev.lastpagans.recruitsperformance.client.compat.BetterCombatCompat;
import dev.lastpagans.recruitsperformance.client.compat.CruorBodyCompat;
import dev.lastpagans.recruitsperformance.client.compat.IcysHorsesCompat;
import dev.lastpagans.recruitsperformance.client.compat.ShaderPackCompat;
import dev.lastpagans.recruitsperformance.client.diagnostics.CrowdDiagnostics;
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
import dev.lastpagans.recruitsperformance.runtime.RecruitIntrospector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE2;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;

public final class CrowdRenderer {
    private static final CrowdRenderer INSTANCE = new CrowdRenderer();

    private final Map<MeshKey, Batch> batches = new LinkedHashMap<>();
    private final Set<Integer> submittedEntities = new HashSet<>();
    private final Set<Integer> fallbackEntities = new HashSet<>();
    private final Set<String> warnedConversions = new HashSet<>();
    private final List<PoseData> poses = new ArrayList<>();
    private final Map<LivingEntity, CachedPose> poseCache = new WeakHashMap<>();
    private final List<Nameplate> nameplates = new ArrayList<>();
    private final List<CruorWound> cruorWounds = new ArrayList<>();
    private MeshCache cache;
    private CrowdShader shader;
    private CrowdPoseBuffer poseBuffer;
    private GpuFrameTimer gpuTimer;
    private boolean failed;
    private int fallbacks;
    private FrameContext deferredShaderFrame;

    public static CrowdRenderer get() { return INSTANCE; }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean submit(LivingEntity entity, LivingEntityRenderer renderer, float partialTick, int packedLight) {
        if (!CrowdRendererConfig.enabled.get() || failed) return false;
        // Oculus renders living entities in more than one world/shadow pass. Once an entity is in
        // this frame's GPU batch, every repeated source-renderer pass must remain cancelled.
        if (submittedEntities.contains(entity.getId())) return true;
        if (fallbackEntities.contains(entity.getId())) return false;
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
            fallbackEntities.add(entity.getId());
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
            int poseIndex = CrowdRendererConfig.gpuAnimation.get()
                    ? poseIndex((HumanoidModel<LivingEntity>) model, entity, partialTick) : -1;
            InstanceData instance = InstanceData.from(entity, partialTick, packedLight, poseIndex, modelScale);
            add(body, instance.tinted(body.tint()));
            List<RecruitVisualLayers.VisualLayer> visualLayers = RecruitVisualLayers.layers(entity, texture);
            for (RecruitVisualLayers.VisualLayer visualLayer : visualLayers) {
                MeshHandle layer = cache.bodyLayer(model, visualLayer.texture(), visualLayer.kind(), fullDetail);
                if (layer == null) throw new IllegalStateException("Recruits visual layer produced no geometry: "
                        + visualLayer.texture());
                add(layer, instance.tinted(layer.tint()));
            }
            for (MeshHandle equipment : cache.equipment(entity, renderer, model, fullDetail, cruorDetail)) {
                add(equipment, instance.tinted(equipment.tint()));
            }
            if (cruorDetail) {
                Object woundLayer = CruorBodyCompat.woundLayer(renderer, entity);
                if (woundLayer != null) {
                    cruorWounds.add(new CruorWound(entity, (HumanoidModel<LivingEntity>) model,
                            woundLayer, partialTick, packedLight, instance,
                            visualLayers.stream().map(RecruitVisualLayers.VisualLayer::texture).toList()));
                }
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
            fallbackEntities.add(entity.getId());
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
            fallbackEntities.add(horse.getId());
            fallbacks++;
            return false;
        }
    }

    public void render(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                && stage != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        boolean shaderPack = ShaderPackCompat.isShaderPackActive();
        if (shaderPack) {
            if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL && !batches.isEmpty() && !failed) {
                // Oculus runs its final composite after Forge's AFTER_LEVEL stage. Drawing here
                // makes a valid instanced crowd briefly, then the composite overwrites it. Keep
                // the world matrices and submit to the main target immediately before the GUI.
                deferredShaderFrame = FrameContext.capture(event);
            }
            return;
        }
        if (stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES || batches.isEmpty() || failed) return;
        deferredShaderFrame = null;
        renderFrame(FrameContext.capture(event), false);
    }

    public void renderDeferredShaderCrowd() {
        FrameContext frame = deferredShaderFrame;
        if (frame == null || batches.isEmpty() || failed) return;
        deferredShaderFrame = null;
        renderFrame(frame, true);
    }

    private void renderFrame(FrameContext frame, boolean bindMainTarget) {
        long started = System.nanoTime();
        int drawCalls = 0;
        int instances = 0;
        boolean lightLayerEnabled = false;
        try {
            // Oculus' final pass leaves the completed scene in Minecraft's main target. Rebind it
            // explicitly because a shader pack is free to leave another framebuffer active.
            if (bindMainTarget) Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
            Vec3 camera = frame.camera();

            glActiveTexture(GL_TEXTURE1);
            poseBuffer.upload(poses);
            poseBuffer.bind();

            RenderSystem.enableDepthTest();
            // Body cloth, biome skins, armor dye overlays and Epic Knights decorations are
            // intentionally coplanar source-renderer layers. Minecraft renders those with
            // LEQUAL; inheriting LESS from an Oculus pass rejects every layer after the first.
            RenderSystem.depthFunc(GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            shader.uniforms(frame.projection(), frame.view(), camera.x, camera.y, camera.z,
                    CrowdRendererConfig.gpuAnimation.get(), poses.size());

            // Packed sky light is deliberately still 15 outdoors at midnight. Minecraft applies
            // day/night, weather, dimension ambient light and gamma through this live 16x16
            // lightmap; reducing the packed values to max(block, sky) made crowds full-bright.
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameRenderer.lightTexture().turnOnLightLayer();
            lightLayerEnabled = true;
            glActiveTexture(GL_TEXTURE2);
            glBindTexture(GL_TEXTURE_2D, RenderSystem.getShaderTexture(2));
            glActiveTexture(GL_TEXTURE0);
            gpuTimer.begin();
            List<Batch> orderedBatches = new ArrayList<>(batches.values());
            orderedBatches.sort(Comparator.comparingInt(
                    batch -> batch.handle.key().kind().renderOrder()));
            for (Batch batch : orderedBatches) {
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
            renderCompatibilityLayers(frame, bindMainTarget);
        } catch (Throwable renderFailure) {
            failed = true;
            RecruitsPerformance.LOGGER.error("Disabling Recruits crowd renderer after GPU failure", renderFailure);
        } finally {
            if (gpuTimer != null) gpuTimer.end();
            if (lightLayerEnabled) {
                Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer();
                glActiveTexture(GL_TEXTURE2);
                glBindTexture(GL_TEXTURE_2D, 0);
            }
            resetGpuState();
            RenderSystem.disableBlend();
            RenderSystem.depthFunc(GL_LEQUAL);
            RenderSystem.enableCull();
            long elapsed = System.nanoTime() - started;
            CrowdDiagnostics.get().publish(submittedEntities.size(), submittedEntities.size(), batches.size(),
                    instances, drawCalls, cache == null ? 0 : cache.size(),
                    cache == null ? 0 : cache.equipmentVariants(), fallbacks,
                    (cache == null ? 0 : cache.bytes()) + (poseBuffer == null ? 0 : poseBuffer.bytes()),
                    elapsed, gpuTimer == null ? 0 : gpuTimer.lastNanos());
            batches.clear();
            submittedEntities.clear();
            fallbackEntities.clear();
            poses.clear();
            nameplates.clear();
            cruorWounds.clear();
            deferredShaderFrame = null;
            fallbacks = 0;
        }
    }

    public void reload() {
        Runnable close = () -> {
            batches.clear();
            submittedEntities.clear();
            fallbackEntities.clear();
            poses.clear();
            poseCache.clear();
            nameplates.clear();
            cruorWounds.clear();
            CruorBodyCompat.clear();
            deferredShaderFrame = null;
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

    private int poseIndex(HumanoidModel<LivingEntity> model, LivingEntity entity, float partialTick) {
        CachedPose cached = poseCache.computeIfAbsent(entity, ignored -> new CachedPose());
        PoseData pose = cached.sample(model, entity, partialTick);
        int index = poses.size();
        poses.add(pose);
        return index;
    }

    private void removeEntity(int entityId) {
        batches.values().forEach(batch -> batch.instances.removeIf(instance -> instance.entityId() == entityId));
        cruorWounds.removeIf(wound -> wound.entity().getId() == entityId);
        submittedEntities.remove(entityId);
    }

    private void queueNameplate(LivingEntity entity, float partialTick, int packedLight, double distanceSquared) {
        if (!RecruitIntrospector.isRecruit(entity)) return;
        double range = entity.isDiscrete() ? 32.0D : 64.0D;
        if (Minecraft.getInstance().crosshairPickEntity != entity || distanceSquared > range * range) return;
        nameplates.add(new Nameplate(entity.getDisplayName(),
                net.minecraft.util.Mth.lerp(partialTick, entity.xOld, entity.getX()),
                net.minecraft.util.Mth.lerp(partialTick, entity.yOld, entity.getY()) + entity.getBbHeight() + 0.5D,
                net.minecraft.util.Mth.lerp(partialTick, entity.zOld, entity.getZ()),
                entity.isDiscrete(), packedLight));
    }

    private void renderNameplates(FrameContext frame) {
        if (nameplates.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        pose.mulPoseMatrix(new Matrix4f(frame.view()));
        Vec3 camera = frame.camera();
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

    private void renderCruorWounds(FrameContext frame) {
        if (cruorWounds.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Vec3 camera = frame.camera();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL_LEQUAL);
        for (CruorWound wound : cruorWounds) {
            try {
                // Recreate the same native humanoid pose used by this recruit's GPU matrices.
                // Cruor then owns wound placement, stacked-UV remapping, drying and texture upload.
                HumanoidPoseEvaluator.evaluate(wound.model(), wound.entity(), wound.partialTick());
                InstanceData instance = wound.instance();
                PoseStack pose = new PoseStack();
                pose.mulPoseMatrix(new Matrix4f(frame.view()));
                pose.translate(instance.x() - camera.x, instance.y() - camera.y,
                        instance.z() - camera.z);
                pose.mulPose(Axis.YP.rotation(instance.mountYaw()));
                pose.mulPose(Axis.XP.rotation(-instance.mountPitch()));
                pose.mulPose(Axis.ZP.rotation(instance.mountRoll()));
                pose.mulPose(Axis.YP.rotation(-instance.mountYaw()));
                pose.translate(0.0F, instance.mountAnchor(), 0.0F);
                pose.mulPose(Axis.YP.rotation(instance.yawRadians()));
                pose.scale(-instance.scale(), -instance.scale(), instance.scale());
                pose.translate(0.0F, -1.5F, 0.0F);
                for (ResourceLocation layerTexture : wound.materialLayers()) {
                    CruorBodyCompat.registerLayer(wound.model(), layerTexture, pose, buffers,
                            wound.entity(), wound.packedLight());
                }
                CruorBodyCompat.render(wound.layer(), pose, buffers, wound.packedLight(),
                        wound.entity(), wound.partialTick());
            } catch (Throwable failure) {
                String key = "cruor-body:" + wound.entity().getType();
                if (warnedConversions.add(key)) {
                    RecruitsPerformance.LOGGER.warn(
                            "Cruor body-wound overlay failed for {}; keeping the instanced recruit",
                            wound.entity().getType(), failure);
                }
            }
        }
        buffers.endBatch();
        resetGpuState();
    }

    private void renderCompatibilityLayers(FrameContext frame, boolean deferredShaderPass) {
        if (!deferredShaderPass) {
            renderCruorWounds(frame);
            renderNameplates(frame);
            return;
        }

        // The Oculus crowd pass is intentionally deferred until RenderGuiEvent.Pre, after its
        // final composite. At that point Minecraft has already installed the GUI projection and
        // model-view matrices. Our instancing shader receives the saved world matrices directly,
        // but Cruor and font RenderTypes use RenderSystem's globals. Temporarily restore the saved
        // world projection and an identity model-view (the submitted vertices already contain
        // frame.view), then put the GUI state back before returning to Forge's GUI pipeline.
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting previousSorting = RenderSystem.getVertexSorting();
        RenderSystem.getModelViewStack().pushPose();
        try {
            RenderSystem.getModelViewStack().last().pose().identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(frame.projection(), VertexSorting.DISTANCE_TO_ORIGIN);
            renderCruorWounds(frame);
            renderNameplates(frame);
        } finally {
            RenderSystem.getModelViewStack().popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, previousSorting);
        }
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

    private record CruorWound(LivingEntity entity, HumanoidModel<LivingEntity> model, Object layer,
                              float partialTick, int packedLight, InstanceData instance,
                              List<ResourceLocation> materialLayers) {}

    private record FrameContext(Matrix4f projection, Matrix4f view, Vec3 camera) {
        private static FrameContext capture(RenderLevelStageEvent event) {
            return new FrameContext(new Matrix4f(event.getProjectionMatrix()),
                    new Matrix4f(event.getPoseStack().last().pose()), event.getCamera().getPosition());
        }
    }

    private static final class CachedPose {
        private HumanoidModel<LivingEntity> model;
        private int tick = Integer.MIN_VALUE;
        private PoseData start;
        private PoseData end;
        private PoseData interpolated;

        private PoseData sample(HumanoidModel<LivingEntity> source, LivingEntity entity, float partialTick) {
            // Better Combat curves and item channels are already interpolated by Player Animation
            // Library. Sampling their actual render tick avoids inventing a second interpolation,
            // which visibly shears fast swings and held weapons.
            if (BetterCombatCompat.hasActiveCombatAnimation(entity)) {
                model = source;
                tick = entity.tickCount;
                PoseData exact = HumanoidPoseEvaluator.evaluate(source, entity, partialTick);
                start = exact;
                end = null;
                return exact;
            }
            if (model != source || tick != entity.tickCount) {
                boolean consecutive = model == source && end != null && entity.tickCount == tick + 1;
                model = source;
                tick = entity.tickCount;
                start = consecutive ? end : HumanoidPoseEvaluator.evaluate(source, entity, 0.0F);
                end = HumanoidPoseEvaluator.evaluate(source, entity, 1.0F);
                if (interpolated == null) {
                    interpolated = new PoseData(new float[PoseData.BONE_COUNT * PoseData.FLOATS_PER_BONE]);
                }
            }
            PoseData.interpolate(start, end, partialTick, interpolated);
            return interpolated;
        }
    }
}
