package dev.lastpagans.recruitsperformance.client.mesh;

import dev.lastpagans.recruitsperformance.RecruitsPerformance;
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

    private final LinkedHashMap<MeshKey, GpuMesh> meshes = new LinkedHashMap<>(64, 0.75F, true);
    private HumanoidModel<LivingEntity> innerArmor;
    private HumanoidModel<LivingEntity> outerArmor;
    private long geometryBytes;
    private int equipmentVariants;

    public MeshHandle body(HumanoidModel<?> model, ResourceLocation texture) {
        MeshKey key = new MeshKey(model.getClass().getName(), texture, -1, MeshKey.Kind.BODY);
        return handle(key, texture, 0xFFFFFFFF, false, () -> HumanoidMeshConverter.convert(model));
    }

    public List<MeshHandle> equipment(LivingEntity entity) {
        List<MeshHandle> result = new ArrayList<>(6);
        armor(entity, EquipmentSlot.HEAD, result);
        armor(entity, EquipmentSlot.CHEST, result);
        armor(entity, EquipmentSlot.LEGS, result);
        armor(entity, EquipmentSlot.FEET, result);
        item(entity, entity.getMainHandItem(), false, result);
        item(entity, entity.getOffhandItem(), true, result);
        return result;
    }

    public int size() { return meshes.size(); }
    public int equipmentVariants() { return equipmentVariants; }
    public long bytes() {
        long instanceBytes = meshes.values().stream().mapToLong(GpuMesh::bytes).sum();
        return Math.max(geometryBytes, instanceBytes);
    }

    public void trim(Set<MeshKey> active) {
        long budget = (long) CrowdRendererConfig.meshCacheMegabytes.get() * 1024L * 1024L;
        if (geometryBytes <= budget) return;
        var iterator = meshes.entrySet().iterator();
        while (geometryBytes > budget && iterator.hasNext()) {
            Map.Entry<MeshKey, GpuMesh> entry = iterator.next();
            if (active.contains(entry.getKey())) continue;
            geometryBytes -= entry.getValue().bytes();
            entry.getValue().close();
            iterator.remove();
        }
    }

    @Override
    public void close() {
        meshes.values().forEach(GpuMesh::close);
        meshes.clear();
        geometryBytes = 0L;
        equipmentVariants = 0;
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
        MeshHandle handle = handle(key, texture, tint, true, () -> captureArmor(captured, slot));
        if (handle == null) throw new IllegalStateException("Armor model produced no geometry: " + itemId);
        result.add(handle);
    }

    private void item(LivingEntity entity, ItemStack stack, boolean left, List<MeshHandle> result) {
        if (stack.isEmpty()) return;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        MeshKey.Kind kind = left ? MeshKey.Kind.OFF_HAND : MeshKey.Kind.MAIN_HAND;
        ResourceLocation texture = stack.getItem() instanceof ShieldItem ? Sheets.SHIELD_SHEET : ITEM_ATLAS;
        boolean using = entity.isUsingItem() && entity.getUseItem() == stack;
        boolean charged = stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack);
        int visualTag = stack.hasTag() ? stack.getTag().hashCode() : 0;
        MeshKey key = new MeshKey(itemId + ":" + using + ":" + charged + ":" + visualTag, texture,
                left ? HumanoidMeshConverter.LEFT_ARM : HumanoidMeshConverter.RIGHT_ARM, kind);
        MeshHandle handle = handle(key, texture, 0xFFFFFFFF, true,
                () -> ItemMeshConverter.convert(entity, stack, left));
        if (handle == null) throw new IllegalStateException("Item model produced no geometry: " + itemId);
        result.add(handle);
    }

    private MeshHandle handle(MeshKey key, ResourceLocation texture, int tint, boolean equipment,
                              MeshFactory factory) {
        GpuMesh mesh = meshes.get(key);
        if (mesh == null) {
            MeshData data = factory.create();
            if (data.isEmpty()) return null;
            mesh = new GpuMesh(data);
            meshes.put(key, mesh);
            geometryBytes += data.bytes();
            if (equipment) equipmentVariants++;
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
