package dev.uwu.dimension;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** The renderer and the server move the Guardian and its carried player around the same pivot. */
public final class GuardianWarningMotion {
    public static final double PIVOT_Y = 1.12;
    public static final double PLAYER_GRIP_HEIGHT = .58;
    // Both methods touching this table run on the logical client's main thread.
    private static final Map<Player,WeakReference<BlossomWarden>> CLIENT_HOLDS = new WeakHashMap<>();

    static void trackOnClient(Player player, BlossomWarden guardian) {
        var last = CLIENT_HOLDS.get(player);
        if (last == null || last.get() != guardian) CLIENT_HOLDS.put(player, new WeakReference<>(guardian));
    }

    @Nullable public static BlossomWarden holderOnClient(Player player) {
        var tracked = CLIENT_HOLDS.get(player);
        if (tracked == null) return null;
        var guardian = tracked.get();
        if (guardian != null && !guardian.isRemoved() && guardian.level() == player.level()
                && guardian.holdsDuringFlip(player)) return guardian;
        CLIENT_HOLDS.remove(player);
        return null;
    }

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

    public static Vec3 grip(BlossomWarden guard, float bearing, float flipTicks, double raise) {
        // Anchor the player's lower ribs between the two palms; a juvenile's rig alone shrinks.
        double scale = guard.getAgeScale() * guard.getScale();
        double forward = guard.female() ? .48 : .94;
        double handY = (guard.female() ? 1.08 : 1.39) - (1 - raise) * .22;
        double height = handY - PIVOT_Y;
        double theta = flipAngle(flipTicks);
        double forwardOffset = forward * Math.cos(theta) - height * Math.sin(theta);
        double handHeight = PIVOT_Y + flipLift(flipTicks) + height * Math.cos(theta) + forward * Math.sin(theta);
        return guard.position().add(forward(bearing).scale(forwardOffset * scale))
                .add(0, Math.max(.03, handHeight * scale - PLAYER_GRIP_HEIGHT), 0);
    }

    /** The same interpolated hand pose drives the visible player's waist and carried eyes. */
    public record CarryPose(Vec3 waist, float bearing, float angle) {
        public Vec3 eye(double eyeHeight) {
            double aboveWaist = eyeHeight - PLAYER_GRIP_HEIGHT;
            return waist.add(forward(bearing).scale(-aboveWaist * Math.sin(angle)))
                    .add(0, aboveWaist * Math.cos(angle), 0);
        }
    }

    public static CarryPose renderedCarry(BlossomWarden guardian, float partial) {
        float bearing = Mth.rotLerp(partial, guardian.yBodyRotO, guardian.yBodyRot);
        float elapsed = guardian.warningPhaseTicks(partial);
        Vec3 waist = grip(guardian, bearing, elapsed, 1)
                .add(renderPosition(guardian, partial).subtract(guardian.position()))
                .add(0, PLAYER_GRIP_HEIGHT, 0);
        return new CarryPose(waist, bearing, flipAngle(elapsed));
    }

    public static Vec3 renderPosition(Entity entity, float partial) {
        return new Vec3(Mth.lerp((double)partial, entity.xOld, entity.getX()),
                Mth.lerp((double)partial, entity.yOld, entity.getY()),
                Mth.lerp((double)partial, entity.zOld, entity.getZ()));
    }

    private GuardianWarningMotion() {}
}
