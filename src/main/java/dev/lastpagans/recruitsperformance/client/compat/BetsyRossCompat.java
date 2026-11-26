package dev.lastpagans.recruitsperformance.client.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Resolves Betsy Ross' Immersive Paintings texture without requiring either mod at compile time. */
public final class BetsyRossCompat {
    private static final ResourceLocation DEFAULT_FLAG = ResourceLocation.tryParse(
            "betsyross:paintings/default_flag.png");
    private static final Access ACCESS = createAccess();

    private BetsyRossCompat() {}

    public static ResourceLocation standardTexture(ItemStack stack) {
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!"betsyross".equals(item.getNamespace())
                || (!"flag_standard".equals(item.getPath()) && !"banner_standard".equals(item.getPath()))) {
            return null;
        }
        if (ACCESS == null) {
            throw new IllegalStateException("Betsy Ross standard found without Immersive Paintings client API");
        }
        String encoded = stack.hasTag() ? stack.getTag().getString("FlagId") : "";
        ResourceLocation flag = ResourceLocation.tryParse(encoded);
        if (flag == null) flag = DEFAULT_FLAG;
        try {
            Object texture = ACCESS.getPaintingTexture.invoke(null, flag, ACCESS.fullType);
            Object location = ACCESS.textureIdentifier.get(texture);
            if (location instanceof ResourceLocation resource) return resource;
            throw new IllegalStateException("Immersive Paintings returned no standard texture for " + flag);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Could not resolve Betsy Ross standard texture for " + flag, failure);
        }
    }

    private static Access createAccess() {
        try {
            ClassLoader loader = BetsyRossCompat.class.getClassLoader();
            Class<?> manager = Class.forName(
                    "immersive_paintings.resources.ClientPaintingManager", false, loader);
            Class<?> type = Class.forName("immersive_paintings.resources.Painting$Type", false, loader);
            Class<?> texture = Class.forName("immersive_paintings.resources.Painting$Texture", false, loader);
            Method getter = manager.getMethod("getPaintingTexture", ResourceLocation.class, type);
            Field identifier = texture.getField("textureIdentifier");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object full = Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), "FULL");
            return new Access(getter, identifier, full);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException | RuntimeException absent) {
            return null;
        }
    }

    private record Access(Method getPaintingTexture, Field textureIdentifier, Object fullType) {}
}
