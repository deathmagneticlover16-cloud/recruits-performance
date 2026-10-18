package dev.lastpagans.recruitsperformance.api.formation;

import net.minecraft.world.phys.Vec3;

/** Local coordinates use lateral right/left and longitudinal forward/back. */
public record FormationSlot(int index, double lateral, double longitudinal, int rank, int file) {
    public Vec3 toWorld(Vec3 anchor, Vec3 facing) {
        Vec3 forward = new Vec3(facing.x, 0.0, facing.z);
        if (forward.lengthSqr() < 1.0E-8) forward = new Vec3(0.0, 0.0, 1.0);
        else forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        return anchor.add(right.scale(lateral)).add(forward.scale(longitudinal));
    }
}
