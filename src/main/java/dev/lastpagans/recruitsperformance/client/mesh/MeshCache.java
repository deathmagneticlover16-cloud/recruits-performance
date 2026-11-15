package dev.lastpagans.recruitsperformance.client.mesh;

import dev.lastpagans.recruitsperformance.client.gpu.GpuMesh;
import dev.lastpagans.recruitsperformance.client.scene.MeshKey;
import dev.lastpagans.recruitsperformance.config.CrowdRendererConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MeshCache implements AutoCloseable {
    private static final ResourceLocation ITEM_ATLAS = TextureAtlas.LOCATION_BLOCKS;

    private final LinkedHashMap<String, GpuMesh> meshes = new LinkedHashMap<>(64, 0.75F, true);
    private final Set<String> equipmentMeshes = new java.util.HashSet<>();
    private HumanoidModel<LivingEntity> innerArmor;
    private HumanoidModel<LivingEntity> outerArmor;

    public MeshHandle body(HumanoidModel<?> model, ResourceLocation texture) {
        String geometry = model.getClass().getName();
        MeshKey key = new MeshKey(geometry, texture, -1, MeshKey.Kind.BODY);
        return handle(key, geometry, texture, 0xFFFFFFFF, false, () -> HumanoidMeshConverter.convert(model));
    }

    public MeshHandle bodyLayer(HumanoidModel<?> model, ResourceLocation texture) {
        return body(model, texture);
    }

    public List<MeshHandle> equipment(LivingEntity entity, HumanoidModel<?> bodyModel) {
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (!head.isEmpty() && !(head.getItem() instanceof ArmorItem)) {
            throw new IllegalStateException("Non-armor head item requires the vanilla custom-head layer: " +
                    BuiltInRegistries.ITEM.getKey(head.getItem()));
        }
        List<MeshHandle> result = new ArrayList<>(6);
        armor(entity, EquipmentSlot.HEAD, result);
        armor(entity, EquipmentSlot.CHEST, result);
        armor(entity, EquipmentSlot.LEGS, result);
        armor(entity, EquipmentSlot.FEET, result);
        boolean mainUsesLeftArm = entity.getMainArm() == HumanoidArm.LEFT;
        item(entity, entity.getMainHandItem(), mainUsesLeftArm, MeshKey.Kind.MAIN_HAND, bodyModel, result);
        item(entity, entity.getOffhandItem(), !mainUsesLeftArm, MeshKey.Kind.OFF_HAND, bodyModel, result);
        return result;
    }

    public int size() { return meshes.size(); }
    public int equipmentVariants() { return equipmentMeshes.size(); }
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
            equipmentMeshes.remove(entry.getKey());
            iterator.remove();
        }
    }

    @Override
    public void close() {
        meshes.values().forEach(GpuMesh::close);
        meshes.clear();
        equipmentMeshes.clear();
        innerArmor = null;
        outerArmor = null;
    }

    private void armor(LivingEntity entity, EquipmentSlot slot, List<MeshHandle> result) {
        ItemStack stack = entity.getItemBySlot(slot);
        if (!(stack.getItem() instanceof ArmorItem armor)) return;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        HumanoidModel<LivingEntity> fallback = defaultArmor(slot);
        HumanoidModel<?> model;
        try {
            model = IClientItemExtensions.of(stack).getHumanoidArmorModel(entity, stack, slot, fallback);
        } catch (Throwable failure) {
            throw new IllegalStateException("Armor model adapter failed for " + itemId, failure);
        }
        if (model == null) model = fallback;
        String baseTexture = armorTexture(armor, slot);
        String resolvedTexture = ForgeHooksClient.getArmorTexture(entity, stack, baseTexture, slot, null);
        ResourceLocation texture = new ResourceLocation(resolvedTexture);
        MeshKey key = new MeshKey(itemId + ":" + slot.getName() + ":" + model.getClass().getName(),
                texture, -1, MeshKey.Kind.ARMOR);
        int tint = stack.getItem() instanceof DyeableLeatherItem dyeable
                ? 0xFF000000 | dyeable.getColor(stack) : 0xFFFFFFFF;
        HumanoidModel<?> captured = model;
        MeshHandle handle = handle(key, key.geometry(), texture, tint, true, () -> captureArmor(captured, slot));
        if (handle == null) throw new IllegalStateException("Armor model produced no geometry: " + itemId);
        result.add(handle);
        if (stack.getItem() instanceof DyeableLeatherItem) {
            String overlayTexture = ForgeHooksClient.getArmorTexture(entity, stack, baseTexture, slot, "overlay");
            ResourceLocation overlay = new ResourceLocation(overlayTexture);
            MeshKey overlayKey = new MeshKey(key.geometry(), overlay, -1, MeshKey.Kind.ARMOR);
            MeshHandle overlayHandle = handle(overlayKey, key.geometry(), overlay, 0xFFFFFFFF, true,
                    () -> captureArmor(captured, slot));
            if (overlayHandle == null) throw new IllegalStateException("Armor overlay produced no geometry: " + itemId);
            result.add(overlayHandle);
        }
    }

    private void item(LivingEntity entity, ItemStack stack, boolean left, MeshKey.Kind kind,
                      HumanoidModel<?> bodyModel, List<MeshHandle> result) {
        if (stack.isEmpty()) return;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ResourceLocation texture = stack.getItem() instanceof ShieldItem ? Sheets.SHIELD_SHEET : ITEM_ATLAS;
        boolean using = entity.isUsingItem() && entity.getUseItem() == stack;
        boolean charged = stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack);
        int visualTag = stack.hasTag() ? stack.getTag().hashCode() : 0;
        String geometry = itemId + ":" + bodyModel.getClass().getName() + ":" +
                (left ? "left" : "right") + ":" + using + ":" + charged + ":" + visualTag;
        MeshKey key = new MeshKey(geometry, texture,
                left ? HumanoidMeshConverter.LEFT_ARM : HumanoidMeshConverter.RIGHT_ARM, kind);
        MeshHandle handle = handle(key, geometry, texture, 0xFFFFFFFF, true,
                () -> ItemMeshConverter.convert(entity, stack, left,
                        left ? bodyModel.leftArm : bodyModel.rightArm));
        if (handle == null) throw new IllegalStateException("Item model produced no geometry: " + itemId);
        result.add(handle);
    }

    private MeshHandle handle(MeshKey key, String geometry, ResourceLocation texture, int tint, boolean equipment,
                              MeshFactory factory) {
        GpuMesh mesh = meshes.get(geometry);
        if (mesh == null) {
            MeshData data = factory.create();
            if (data.isEmpty()) return null;
            mesh = new GpuMesh(data);
            meshes.put(geometry, mesh);
            if (equipment) equipmentMeshes.add(geometry);
        }
        return new MeshHandle(key, mesh, texture, tint, equipment);
    }

    @SuppressWarnings("unchecked")
    private HumanoidModel<LivingEntity> defaultArmor(EquipmentSlot slot) {
        if (innerArmor == null) {
            innerArmor = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
            outerArmor = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        }
        return slot == EquipmentSlot.LEGS ? innerArmor : outerArmor;
    }

    private static MeshData captureArmor(HumanoidModel<?> model, EquipmentSlot slot) {
        boolean[] visible = visibility(model);
        try {
            model.setAllVisible(false);
            switch (slot) {
                case HEAD -> { model.head.visible = true; model.hat.visible = true; }
                case CHEST -> { model.body.visible = true; model.leftArm.visible = true; model.rightArm.visible = true; }
                case LEGS -> { model.body.visible = true; model.leftLeg.visible = true; model.rightLeg.visible = true; }
                case FEET -> { model.leftLeg.visible = true; model.rightLeg.visible = true; }
                default -> { }
            }
            return HumanoidMeshConverter.convert(model);
        } finally {
            restoreVisibility(model, visible);
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

    private static String armorTexture(ArmorItem item, EquipmentSlot slot) {
        String material = item.getMaterial().getName();
        String namespace = "minecraft";
        String path = material;
        int separator = material.indexOf(':');
        if (separator >= 0) {
            namespace = material.substring(0, separator);
            path = material.substring(separator + 1);
        }
        return namespace + ":textures/models/armor/" + path + "_layer_" +
                (slot == EquipmentSlot.LEGS ? 2 : 1) + ".png";
    }

    @FunctionalInterface
    private interface MeshFactory { MeshData create(); }
}
