package dev.lastpagans.recruitsperformance.runtime;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Avoids a hard compile-time dependency while still recognizing the real Recruits base class. */
public final class RecruitIntrospector {
    private static final String ABSTRACT_RECRUIT = "com.talhanation.recruits.entities.AbstractRecruitEntity";
    private RecruitIntrospector() {}

    public static boolean isRecruit(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        for (Class<?> type = entity.getClass(); type != null; type = type.getSuperclass()) {
            if (ABSTRACT_RECRUIT.equals(type.getName())) return true;
        }
        return false;
    }

}

