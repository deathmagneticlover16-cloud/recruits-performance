package dev.lastpagans.recruitsperformance.client.mesh;

import dev.lastpagans.recruitsperformance.RecruitsPerformance;
import dev.lastpagans.recruitsperformance.client.compat.CruorCompat;
import dev.lastpagans.recruitsperformance.client.compat.EpicKnightsCompat;
import dev.lastpagans.recruitsperformance.client.compat.HumanoidArmorCompat;
import dev.lastpagans.recruitsperformance.client.gpu.GpuMesh;
import dev.lastpagans.recruitsperformance.client.scene.MeshKey;
import dev.lastpagans.recruitsperformance.config.CrowdRendererConfig;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraftforge.client.ForgeHooksClient;
import com.mojang.datafixers.util.Pair;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MeshCache implements AutoCloseable {
    private static final ResourceLocation ITEM_ATLAS = TextureAtlas.LOCATION_BLOCKS;

    private final LinkedHashMap<String, GpuMesh> meshes = new LinkedHashMap<>(64, 0.75F, true);
    private final Map<String, String> armorGeometries = new java.util.HashMap<>();

    public MeshHandle body(HumanoidModel<?> model, ResourceLocation texture, boolean fullDetail) {
        String geometry = model.getClass().getName() + (fullDetail ? ":full" : ":lod");
        MeshKey key = new MeshKey(geometry, texture, -1, MeshKey.Kind.BODY);
        return handle(key, geometry, texture, 0xFFFFFFFF,
                () -> HumanoidMeshConverter.convert(model, fullDetail));
    }

    public MeshHandle bodyLayer(HumanoidModel<?> model, ResourceLocation texture, MeshKey.Kind kind,
                                boolean fullDetail) {
        String geometry = model.getClass().getName() + (fullDetail ? ":full" : ":lod");
        MeshKey key = new MeshKey(geometry, texture, -1, kind);
        return handle(key, geometry, texture, 0xFFFFFFFF,
                () -> HumanoidMeshConverter.convert(model, fullDetail));
    }

    public MeshHandle horse(Object model, ResourceLocation texture, int tint, MeshKey.Kind kind,
                            boolean fullDetail) {
        String geometry = "icy-horse:" + kind + ":" + model.getClass().getName() + ":"
                + Integer.toHexString(System.identityHashCode(model)) + (fullDetail ? ":full" : ":lod");
        MeshKey key = new MeshKey(geometry, texture, -1, kind);
        return handle(key, geometry, texture, tint,
                () -> HorseModelAdapter.convert(model, fullDetail));
    }

    public List<MeshHandle> equipment(LivingEntity entity, LivingEntityRenderer<?, ?> renderer,
                                      HumanoidModel<?> bodyModel, boolean fullDetail, boolean cruorDetail) {
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (!head.isEmpty() && !(head.getItem() instanceof ArmorItem)) {
            throw new IllegalStateException("Non-armor head item requires the vanilla custom-head layer: " +
                    BuiltInRegistries.ITEM.getKey(head.getItem()));
        }
        List<MeshHandle> result = new ArrayList<>(6);
        EpicKnightsCompat.Layer epicLayer = EpicKnightsCompat.layer(renderer);
        armor(entity, renderer, bodyModel, EquipmentSlot.HEAD, epicLayer, result);
        armor(entity, renderer, bodyModel, EquipmentSlot.CHEST, epicLayer, result);
        armor(entity, renderer, bodyModel, EquipmentSlot.LEGS, epicLayer, result);
        armor(entity, renderer, bodyModel, EquipmentSlot.FEET, epicLayer, result);
        if (!fullDetail) return result;
        boolean mainUsesLeftArm = entity.getMainArm() == HumanoidArm.LEFT;
        item(entity, entity.getMainHandItem(), mainUsesLeftArm, MeshKey.Kind.MAIN_HAND,
                bodyModel, cruorDetail, result);
        item(entity, entity.getOffhandItem(), !mainUsesLeftArm, MeshKey.Kind.OFF_HAND,
                bodyModel, cruorDetail, result);
        return result;
    }

    public long bytes() {
        return meshes.values().stream().mapToLong(GpuMesh::bytes).sum();
    }

    public void trim(Set<MeshKey> active) {
        long budget = (long) CrowdRendererConfig.meshCacheMegabytes.get() * 1024L * 1024L;
        long currentBytes = bytes();
        if (currentBytes <= budget) return;
        Set<String> activeGeometry = new java.util.HashSet<>();
        active.forEach(key -> activeGeometry.add(key.geometry()));
        var iterator = meshes.entrySet().iterator();
        while (currentBytes > budget && iterator.hasNext()) {
            Map.Entry<String, GpuMesh> entry = iterator.next();
            if (activeGeometry.contains(entry.getKey())) continue;
            currentBytes -= entry.getValue().bytes();
            entry.getValue().close();
            iterator.remove();
        }
    }

    @Override
    public void close() {
        meshes.values().forEach(GpuMesh::close);
        meshes.clear();
        armorGeometries.clear();
    }

    private void armor(LivingEntity entity, LivingEntityRenderer<?, ?> renderer,
                       HumanoidModel<?> bodyModel, EquipmentSlot slot,
                       EpicKnightsCompat.Layer epicLayer, List<MeshHandle> result) {
        ItemStack stack = entity.getItemBySlot(slot);
        if (!(stack.getItem() instanceof ArmorItem armor)) return;
        if (stack.hasFoil()) throw new IllegalStateException("Foil armor requires the vanilla glint pass");
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        int visualTag = stack.hasTag() ? stack.getTag().hashCode() : 0;
        String armorVariant = itemId + ":" + slot.getName() + ":entity:"
                + entity.getType() + ":parent:" + bodyModel.getClass().getName() + ":tag:" + visualTag;
        String geometry = armorGeometries.get(armorVariant);
        if (geometry != null && !meshes.containsKey(geometry)) {
            // The LRU budget may evict an inactive mesh while its semantic lookup remains.
            armorGeometries.remove(armorVariant);
            geometry = null;
        }
        HumanoidModel<?> model = null;
        if (geometry == null) {
            HumanoidModel<?> fallback = HumanoidArmorCompat.model(renderer, slot);
            Model resolved;
            try {
                // Forge's generic hook expects its original model to be fully configured and copies
                // those properties into an item replacement such as Epic Knights' persistent model.
                ForgeHooksClient.copyModelProperties(bodyModel, fallback);
                setSlotVisibility(fallback, slot);
                resolved = ForgeHooksClient.getArmorModel(entity, stack, slot, fallback);
            } catch (Throwable failure) {
                throw new IllegalStateException("Armor model adapter failed for " + itemId, failure);
            }
            if (!(resolved instanceof HumanoidModel<?> humanoid)) {
                throw new IllegalStateException("Armor model is not reusable humanoid geometry: "
                        + itemId + " (" + (resolved == null ? "null" : resolved.getClass().getName()) + ")");
            }
            model = humanoid;
            // Reapply this explicitly as well: generic hooks copy it for standard replacements, while
            // custom generic-model hooks are responsible for their own transfer and may omit it.
            ForgeHooksClient.copyModelProperties(bodyModel, model);
            setSlotVisibility(model, slot);
            // Several MCreator armor packs (including 7 Years War, Victorian Uniforms and SYW USA)
            // allocate a new HumanoidModel and freshly baked ModelParts every time this hook is
            // queried. Cache the resolved semantic geometry, not the transient object identity.
            geometry = armorVariant + ":model:" + model.getClass().getName();
        }
        String baseTexture = armorTexture(armor, slot, null);
        String resolvedTexture = ForgeHooksClient.getArmorTexture(entity, stack, baseTexture, slot, null);
        ResourceLocation texture = new ResourceLocation(resolvedTexture);
        MeshKey key = new MeshKey(geometry,
                texture, -1, MeshKey.Kind.ARMOR);
        int tint = stack.getItem() instanceof DyeableLeatherItem dyeable
                ? 0xFF000000 | dyeable.getColor(stack) : 0xFFFFFFFF;
        HumanoidModel<?> captured = model;
        MeshHandle handle = handle(key, key.geometry(), texture, tint,
                () -> {
                    if (captured == null) {
                        throw new IllegalStateException("Missing cached armor geometry: " + itemId);
                    }
                    return captureArmor(captured, bodyModel, slot, itemId);
                });
        if (handle == null) throw new IllegalStateException("Armor model produced no geometry: " + itemId);
        armorGeometries.putIfAbsent(armorVariant, geometry);
        result.add(handle);
        if (stack.getItem() instanceof DyeableLeatherItem) {
            // HumanoidArmorLayer constructs the suffixed default path before invoking Forge's
            // item hook. ForgeHooksClient only lets an item replace the supplied path; it does
            // not append "_overlay" itself. Passing baseTexture here bound the dyeable texture
            // twice and discarded every non-dyeable strap, buckle and plate detail.
            String overlayDefault = armorTexture(armor, slot, "overlay");
            String overlayTexture = ForgeHooksClient.getArmorTexture(
                    entity, stack, overlayDefault, slot, "overlay");
            ResourceLocation overlay = new ResourceLocation(overlayTexture);
            MeshKey overlayKey = new MeshKey(key.geometry(), overlay, -1, MeshKey.Kind.ARMOR_OVERLAY);
            MeshHandle overlayHandle = handle(overlayKey, key.geometry(), overlay, 0xFFFFFFFF,
                    () -> captureArmor(captured, bodyModel, slot, itemId));
            if (overlayHandle == null) throw new IllegalStateException("Armor overlay produced no geometry: " + itemId);
            result.add(overlayHandle);
        }
        decorations(stack, epicLayer, bodyModel, result);
    }

    private void item(LivingEntity entity, ItemStack stack, boolean left, MeshKey.Kind kind,
                      HumanoidModel<?> bodyModel, boolean cruorDetail, List<MeshHandle> result) {
        if (stack.isEmpty()) return;
        if (stack.hasFoil()) throw new IllegalStateException("Foil item requires the vanilla glint pass");
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ResourceLocation texture = stack.getItem() instanceof ShieldItem ? Sheets.SHIELD_SHEET : ITEM_ATLAS;
        boolean using = entity.isUsingItem() && entity.getUseItem() == stack;
        boolean charged = stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack);
        int visualTag = stack.hasTag() ? stack.getTag().hashCode() : 0;
        String geometry = itemId + ":" + bodyModel.getClass().getName() + ":" +
                (left ? "left" : "right") + ":" + using + ":" + charged + ":" + visualTag;
        MeshKey key = new MeshKey(geometry, texture,
                left ? HumanoidMeshConverter.LEFT_ITEM : HumanoidMeshConverter.RIGHT_ITEM, kind);
        MeshHandle handle = handle(key, geometry, texture, 0xFFFFFFFF,
                () -> ItemMeshConverter.convertBase(entity, stack, left,
                        left ? bodyModel.leftArm : bodyModel.rightArm));
        if (handle == null) throw new IllegalStateException("Item model produced no geometry: " + itemId);
        result.add(handle);
        if (!cruorDetail) return;
        CruorCompat.Stain stain = CruorCompat.stain(entity, stack);
        if (stain == null) return;
        MeshData captured = null;
        if (stain.texture() == null) {
            captured = ItemMeshConverter.convertCruor(entity, stack, left,
                    left ? bodyModel.leftArm : bodyModel.rightArm);
            stain = CruorCompat.refreshStain(entity, stack);
        }
        if (stain == null || stain.texture() == null) return;
        String stainGeometry = geometry + ":cruor:" + entity.getId();
        MeshKey stainKey = new MeshKey(stainGeometry, stain.texture(),
                left ? HumanoidMeshConverter.LEFT_ITEM : HumanoidMeshConverter.RIGHT_ITEM, kind);
        MeshData precomputed = captured;
        MeshHandle stainHandle = handle(stainKey, stainGeometry, stain.texture(), 0xFFFFFFFF,
                () -> precomputed == null
                        ? ItemMeshConverter.convertCruor(entity, stack, left,
                        left ? bodyModel.leftArm : bodyModel.rightArm)
                        : precomputed);
        if (stainHandle != null) result.add(stainHandle);
    }

    private MeshHandle handle(MeshKey key, String geometry, ResourceLocation texture, int tint,
                              MeshFactory factory) {
        GpuMesh mesh = meshes.get(geometry);
        if (mesh == null) {
            MeshData data = factory.create();
            if (data.isEmpty()) return null;
            mesh = new GpuMesh(data);
            meshes.put(geometry, mesh);
        }
        boolean cull = key.kind() == MeshKey.Kind.MAIN_HAND
                || key.kind() == MeshKey.Kind.OFF_HAND
                || key.kind() == MeshKey.Kind.HORSE_BODY
                || key.kind() == MeshKey.Kind.IMPOSTOR;
        return new MeshHandle(key, mesh, texture, tint, equipment, cull);
    }

    private void decorations(ItemStack stack, EpicKnightsCompat.Layer epicLayer,
                             HumanoidModel<?> bodyModel, List<MeshHandle> result) {
        CompoundTag decorationRoot = stack.getTagElement("ArmorDecoration");
        if (decorationRoot != null) {
            if (epicLayer == null) {
                throw new IllegalStateException("Epic Knights decoration data exists without its renderer layer");
            }
            ListTag items = decorationRoot.getList("Items", Tag.TAG_COMPOUND);
            for (int index = 0; index < items.size(); index++) {
                CompoundTag entry = items.getCompound(index);
                ResourceLocation location = decorationLocation(entry.getString("name"));
                addDecoration(epicLayer, bodyModel, location, entry.getBoolean("dyeable"),
                        entry.getInt("color"), result);
            }
        }
        if (stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == EquipmentSlot.CHEST
                && net.minecraft.world.item.BlockItem.getBlockEntityData(stack) != null) {
            if (epicLayer == null) {
                throw new IllegalStateException("Epic Knights surcoat data exists without its renderer layer");
            }
            addSurcoat(epicLayer, bodyModel, result);
            List<Pair<Holder<BannerPattern>, DyeColor>> patterns = BannerBlockEntity.createPatterns(
                    ShieldItem.getColor(stack), BannerBlockEntity.getItemPatterns(stack));
            for (Pair<Holder<BannerPattern>, DyeColor> pattern : patterns) {
                pattern.getFirst().unwrapKey().ifPresent(key ->
                        addSurcoatPattern(epicLayer, bodyModel, key.location(), pattern.getSecond(), result));
            }
        }
    }

    private void addSurcoat(EpicKnightsCompat.Layer layer, HumanoidModel<?> bodyModel,
                            List<MeshHandle> result) {
        HumanoidModel<?> model = EpicKnightsCompat.coatModel(layer);
        String geometry = "epic-surcoat:" + model.getClass().getName() + ":"
                + Integer.toHexString(System.identityHashCode(model));
        ResourceLocation texture = EpicKnightsCompat.baseTexture(layer);
        MeshKey key = new MeshKey(geometry, texture, -1, MeshKey.Kind.DECORATION);
        MeshHandle handle = handle(key, geometry, texture, 0xFFFFFFFF,
                () -> HumanoidMeshConverter.convertAttached(model, bodyModel));
        if (handle == null) throw new IllegalStateException("Epic Knights surcoat model produced no geometry");
        result.add(handle);
    }

    private void addSurcoatPattern(EpicKnightsCompat.Layer layer, HumanoidModel<?> bodyModel,
                                   ResourceLocation pattern, DyeColor color, List<MeshHandle> result) {
        HumanoidModel<?> model = EpicKnightsCompat.coatModel(layer);
        String geometry = "epic-surcoat:" + model.getClass().getName() + ":"
                + Integer.toHexString(System.identityHashCode(model));
        ResourceLocation texture = EpicKnightsCompat.patternTexture(layer, pattern);
        MeshKey key = new MeshKey(geometry, texture, -1, MeshKey.Kind.DECORATION_OVERLAY);
        float[] rgb = color.getTextureDiffuseColors();
        int tint = 0xFF000000 | ((int) (rgb[0] * 255.0F) << 16)
                | ((int) (rgb[1] * 255.0F) << 8) | (int) (rgb[2] * 255.0F);
        MeshHandle patternHandle = handle(key, geometry, texture, tint,
                () -> { throw new IllegalStateException("Missing shared Epic Knights surcoat geometry"); });
        result.add(patternHandle);
    }

    private void addDecoration(EpicKnightsCompat.Layer layer, HumanoidModel<?> bodyModel,
                               ResourceLocation location, boolean dyeable, int color,
                               List<MeshHandle> result) {
        HumanoidModel<?> model = EpicKnightsCompat.decorationModel(layer, location);
        String geometry = "epic-decoration:" + location + ":" + model.getClass().getName() + ":"
                + Integer.toHexString(System.identityHashCode(model));
        ResourceLocation texture = EpicKnightsCompat.texture(layer, location, false);
        MeshKey key = new MeshKey(geometry, texture, -1, MeshKey.Kind.DECORATION);
        int tint = dyeable ? 0xFF000000 | color : 0xFFFFFFFF;
        MeshHandle base = handle(key, geometry, texture, tint,
                () -> HumanoidMeshConverter.convertAttached(model, bodyModel));
        if (base == null) throw new IllegalStateException("Epic Knights decoration produced no geometry: " + location);
        result.add(base);
        if (dyeable) {
            ResourceLocation overlay = EpicKnightsCompat.texture(layer, location, true);
            MeshKey overlayKey = new MeshKey(geometry, overlay, -1, MeshKey.Kind.DECORATION_OVERLAY);
            MeshHandle overlayHandle = handle(overlayKey, geometry, overlay, 0xFFFFFFFF,
                    () -> { throw new IllegalStateException("Missing shared Epic Knights decoration geometry: " + location); });
            result.add(overlayHandle);
        }
    }

    private static ResourceLocation decorationLocation(String name) {
        ResourceLocation parsed = new ResourceLocation(name);
        return parsed.getNamespace().equals("minecraft")
                ? new ResourceLocation("magistuarmory", parsed.getPath()) : parsed;
    }

    private static MeshData captureArmor(HumanoidModel<?> model, HumanoidModel<?> bodyModel,
                                         EquipmentSlot slot, ResourceLocation itemId) {
        boolean[] visible = visibility(model);
        try {
            // Change only the seven HumanoidModel roots, matching HumanoidArmorLayer's slot mask.
            // The visibility state of custom children below those roots must remain untouched.
            setSlotVisibility(model, slot);
            MeshData data = HumanoidMeshConverter.convertAttached(model, bodyModel);
            RecruitsPerformance.LOGGER.debug(
                    "Captured armor mesh item={} slot={} model={} vertices={} triangles={}",
                    itemId, slot.getName(), model.getClass().getName(), data.vertexCount(),
                    data.indices().length / 3);
            return data;
        } finally {
            restoreVisibility(model, visible);
        }
    }

    private static void setSlotVisibility(HumanoidModel<?> model, EquipmentSlot slot) {
        model.head.visible = false;
        model.hat.visible = false;
        model.body.visible = false;
        model.rightArm.visible = false;
        model.leftArm.visible = false;
        model.rightLeg.visible = false;
        model.leftLeg.visible = false;
        switch (slot) {
            case HEAD -> { model.head.visible = true; model.hat.visible = true; }
            case CHEST -> { model.body.visible = true; model.leftArm.visible = true; model.rightArm.visible = true; }
            case LEGS -> { model.body.visible = true; model.leftLeg.visible = true; model.rightLeg.visible = true; }
            case FEET -> { model.leftLeg.visible = true; model.rightLeg.visible = true; }
            default -> { }
        }
    }

    private static boolean[] visibility(HumanoidModel<?> model) {
        return new boolean[]{model.head.visible, model.hat.visible, model.body.visible,
                model.rightArm.visible, model.leftArm.visible, model.rightLeg.visible, model.leftLeg.visible};
    }

    private static void restoreVisibility(HumanoidModel<?> model, boolean[] v) {
        model.head.visible = v[0]; model.hat.visible = v[1]; model.body.visible = v[2];
        model.rightArm.visible = v[3]; model.leftArm.visible = v[4];
        model.rightLeg.visible = v[5]; model.leftLeg.visible = v[6];
    }

    private static String armorTexture(ArmorItem item, EquipmentSlot slot, String layer) {
        String material = item.getMaterial().getName();
        String namespace = "minecraft";
        String path = material;
        int separator = material.indexOf(':');
        if (separator >= 0) {
            namespace = material.substring(0, separator);
            path = material.substring(separator + 1);
        }
        String suffix = layer == null ? "" : "_" + layer;
        return namespace + ":textures/models/armor/" + path + "_layer_" +
                (slot == EquipmentSlot.LEGS ? 2 : 1) + suffix + ".png";
    }

    @FunctionalInterface
    private interface MeshFactory { MeshData create(); }
}
