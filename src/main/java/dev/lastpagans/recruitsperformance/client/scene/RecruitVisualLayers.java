package dev.lastpagans.recruitsperformance.client.scene;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Converts the material-only layers used by the supported Recruits humanoid renderers. */
public final class RecruitVisualLayers {
    private static final String NAMESPACE = "recruits";
    private static final String COMPANION = "com.talhanation.recruits.entities.ICompanion";
    private static final String[] BIOMES = {
            "desert", "jungle", "plains", "savanna", "snowy_tundra", "swamp", "taiga"
    };
    private static final String[] COLORS = {
            "white", "black", "light_grey", "grey", "dark_grey", "light_blue", "blue", "dark_blue",
            "light_green", "green", "dark_green", "light_red", "red", "dark_red", "light_brown", "brown",
            "dark_brown", "light_cyan", "cyan", "dark_cyan", "yellow", "orange", "magenta", "purple", "gold"
    };
    private static final ConcurrentHashMap<Class<?>, Accessors> ACCESSORS = new ConcurrentHashMap<>();

    private RecruitVisualLayers() {}

    public static List<VisualLayer> layers(LivingEntity entity, ResourceLocation baseTexture) {
        String path = baseTexture.getPath();
        String family;
        if (path.contains("/human/")) family = "human";
        else if (path.contains("/villager/")) family = "villager";
        else throw new IllegalStateException("Unsupported Recruits body material: " + baseTexture);

        Accessors accessors = ACCESSORS.computeIfAbsent(entity.getClass(), Accessors::new);
        int biome = invokeIndex(accessors.biome, entity, BIOMES.length, "biome");
        List<VisualLayer> result = new ArrayList<>(3);
        // Recruits registers team/base cloth before its biome pass; preserve that exact order.
        if (entity.getTeam() == null) {
            result.add(new VisualLayer(texture("textures/entity/" + family + "/" + family
                    + "_base_cloth.png"), MeshKey.Kind.BODY_TEAM));
        } else {
            int color = invokeIndex(accessors.color, entity, COLORS.length, "team color");
            result.add(new VisualLayer(texture("textures/entity/" + family + "/" + family + "_team_"
                    + COLORS[color] + ".png"), MeshKey.Kind.BODY_TEAM));
        }
        result.add(new VisualLayer(texture("textures/entity/" + family + "/biome/" + family + "_"
                + BIOMES[biome] + ".png"), MeshKey.Kind.BODY_BIOME));
        if (accessors.companion) {
            result.add(new VisualLayer(texture("textures/entity/" + family + "/" + family
                    + "_assassin_cloth.png"), MeshKey.Kind.BODY_COMPANION));
        }
        return result;
    }

    public static float modelScale(ResourceLocation baseTexture) {
        return baseTexture.getPath().contains("/villager/") ? 0.9375F : 1.0F;
    }

    private static ResourceLocation texture(String path) {
        return new ResourceLocation(NAMESPACE, path);
    }

    private static int invokeIndex(Method method, LivingEntity entity, int bound, String description) {
        if (method == null) throw new IllegalStateException("Recruits renderer has no " + description + " accessor");
        try {
            Object value = method.invoke(entity);
            int index = value instanceof Number number ? number.intValue() : -1;
            if (index < 0 || index >= bound) throw new IllegalStateException("Invalid Recruits " + description + ": " + index);
            return index;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot read Recruits " + description, failure);
        }
    }

    private static boolean implementsInterface(Class<?> type, String name) {
        for (Class<?> candidate = type; candidate != null; candidate = candidate.getSuperclass()) {
            for (Class<?> implemented : candidate.getInterfaces()) {
                if (implemented.getName().equals(name) || implementsInterface(implemented, name)) return true;
            }
        }
        return false;
    }

    private record Accessors(Method biome, Method color, boolean companion) {
        private Accessors(Class<?> type) {
            this(find(type, "getBiome"), find(type, "getColor"), implementsInterface(type, COMPANION));
        }

        private static Method find(Class<?> type, String name) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }
    }

    public record VisualLayer(ResourceLocation texture, MeshKey.Kind kind) {}
}
