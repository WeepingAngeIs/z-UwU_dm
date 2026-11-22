package dev.uwu.dimension;

import net.minecraft.world.phys.Vec3;

/** The renderer and the server move the Guardian and its carried player around the same pivot. */
public final class GuardianWarningMotion {
    public static final double PIVOT_Y = 1.12;

    private static double flipProgress(float elapsedTicks) {
        return Math.clamp(elapsedTicks / BlossomWarden.WARNING_FLIP_TICKS, 0F, 1F);
    }

    public static float flipAngle(float elapsedTicks) {
        double t = flipProgress(elapsedTicks);
        return (float)(Math.PI * 2 * t * t * (3 - 2 * t));
    }

    public static float flipLift(float elapsedTicks) {
        return (float)(1.25 * Math.sin(Math.PI * flipProgress(elapsedTicks)));
    }

    public static float headShake(float elapsedTicks) {
        double sweep = Math.clamp((elapsedTicks - 4.0) / 36, 0, 1);
        return (float)(34 * Math.sin(sweep * Math.PI * 4));
    }

    static Vec3 forward(float bearing) {
        double yaw = Math.toRadians(bearing);
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    static Vec3 grip(BlossomWarden guard, float bearing, float flipTicks, double raise) {
        // Anchor the player's lower ribs between the two palms; a juvenile's rig alone shrinks.
        double scale = guard.getAgeScale() * guard.getScale();
        double forward = guard.female() ? .48 : .94;
        double handY = (guard.female() ? 1.08 : 1.39) - (1 - raise) * .22;
        double height = handY - PIVOT_Y;
        double theta = flipAngle(flipTicks);
        double forwardOffset = forward * Math.cos(theta) - height * Math.sin(theta);
        double handHeight = PIVOT_Y + flipLift(flipTicks) + height * Math.cos(theta) + forward * Math.sin(theta);
        return guard.position().add(forward(bearing).scale(forwardOffset * scale))
                .add(0, Math.max(.03, handHeight * scale - .58), 0);
    }

    private GuardianWarningMotion() {}
}
