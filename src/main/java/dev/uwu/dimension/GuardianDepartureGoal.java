package dev.uwu.dimension;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Make a short, navigable exit after a warning, then give the regular home patrol control. */
final class GuardianDepartureGoal extends Goal {
    private final BlossomWarden guard;
    private Vec3 away = Vec3.ZERO;
    private long until;
    private Path route;

    GuardianDepartureGoal(BlossomWarden guard) {
        this.guard = guard;
        setFlags(EnumSet.of(Flag.LOOK, Flag.MOVE));
    }

    void afterToss(float bearing) {
        away = GuardianWarningMotion.forward(bearing).scale(-1);
        until = guard.level().getGameTime() + 55;
    }

    @Override public boolean canUse() {
        if (until == 0) return false;
        if (guard.hasOffenders() || guard.level().getGameTime() >= until) {
            until = 0;
            return false;
        }
        for (float angle : new float[]{0, .65F, -.65F, 1.15F, -1.15F}) {
            Vec3 end = guard.position().add(away.yRot(angle).scale(5));
            BlockPos dest = BlockPos.containing(end);
            if (!guard.level().hasChunkAt(dest) || !guard.level().getWorldBorder().isWithinBounds(dest)) continue;
            Path path = guard.getNavigation().createPath(dest, 1);
            if (path != null && path.canReach() && path.getNodeCount() >= 2) {
                route = path;
                return true;
            }
        }
        until = 0;
        return false;
    }

    @Override public void start() { guard.getNavigation().moveTo(route, .9); }

    @Override public boolean canContinueToUse() {
        return !guard.hasOffenders() && !guard.getNavigation().isDone() && guard.level().getGameTime() < until;
    }

    @Override public void tick() {
        if (route != null && !route.isDone()) {
            Vec3 ahead = route.getNextEntityPos(guard);
            guard.getLookControl().setLookAt(ahead.x, guard.getEyeY(), ahead.z);
        }
    }

    @Override public void stop() {
        if (route != null && guard.getNavigation().getPath() == route) guard.getNavigation().stop();
        route = null;
        until = 0;
    }
}
