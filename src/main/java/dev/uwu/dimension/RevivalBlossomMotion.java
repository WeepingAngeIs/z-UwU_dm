package dev.uwu.dimension;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Client-tick companion motion, independent of the camera and render frame rate. */
final class RevivalBlossomMotion {
    private static final double HORIZONTAL_SPRING = .058;
    private static final double HORIZONTAL_DAMPING = .66;
    private static final double HORIZONTAL_RADIUS = 3.5;
    private static final double VERTICAL_SPRING = .14;
    private static final double VERTICAL_DAMPING = .68;
    private static final double VERTICAL_LAG = .16;
    static final double SNAP_DISTANCE_SQUARED = 16;

    private Vec3 previous;
    private Vec3 position;
    private Vec3 ownerPosition;
    private Vec3 velocity;
    private int sampledTick;
    private int relocationTicks;

    RevivalBlossomMotion(Vec3 anchor, Vec3 owner, int tick) {
        reset(anchor, owner, tick, 0);
    }

    void reset(Vec3 anchor, Vec3 owner, int tick, int interpolationTicks) {
        previous = position = anchor;
        ownerPosition = owner;
        velocity = Vec3.ZERO;
        sampledTick = tick;
        relocationTicks = interpolationTicks;
    }

    void follow(Vec3 anchor, Vec3 owner, int tick, float scale) {
        int elapsed = tick - sampledTick;
        if (elapsed < 0 || elapsed > 1 || owner.distanceToSqr(ownerPosition) >= SNAP_DISTANCE_SQUARED) {
            reset(anchor, owner, tick, 0);
            return;
        }
        if (elapsed == 0) return;
        sampledTick = tick;
        ownerPosition = owner;
        previous = position;
        if (relocationTicks > 0) {
            // Vanilla interpolates remote relocations. Follow that existing interpolation without adding lag.
            relocationTicks--;
            position = anchor;
            velocity = Vec3.ZERO;
            return;
        }

        Vec3 toAnchor = anchor.subtract(position);
        Vec3 proposed = position.add(
                (velocity.x + toAnchor.x * HORIZONTAL_SPRING) * HORIZONTAL_DAMPING,
                (velocity.y + toAnchor.y * VERTICAL_SPRING) * VERTICAL_DAMPING,
                (velocity.z + toAnchor.z * HORIZONTAL_SPRING) * HORIZONTAL_DAMPING);
        // Limit distance from the owner so the light stays useful through abrupt changes in the goal.
        Vec3 fromOwner = proposed.subtract(owner);
        double radius = HORIZONTAL_RADIUS * scale;
        double distance = fromOwner.horizontalDistance();
        double horizontalScale = distance > radius ? radius / distance : 1;
        double verticalLimit = VERTICAL_LAG * scale;
        position = new Vec3(owner.x + fromOwner.x * horizontalScale,
                Mth.clamp(proposed.y, anchor.y - verticalLimit, anchor.y + verticalLimit),
                owner.z + fromOwner.z * horizontalScale);
        velocity = position.subtract(previous);
        if (position.distanceToSqr(anchor) < 1e-6 && velocity.lengthSqr() < 1e-6) {
            position = anchor;
            velocity = Vec3.ZERO;
        }
    }

    Vec3 sample(float partialTick) {
        return previous.lerp(position, Mth.clamp(partialTick, 0, 1));
    }
}
