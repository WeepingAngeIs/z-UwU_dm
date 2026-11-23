package dev.uwu.dimension;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/** A visit belongs to the player, even if another resident Guardian sees the same arrival. */
final class GuardianGreetingGoal extends Goal {
    private static final String VISIT = "UwUDimensionGuardianVisit";
    private static final double SEARCH = 24, REACH = 3.5;
    private static final int APPROACH_LIMIT = 120;
    private record Claim(UUID guardian, long expires) {}
    // Logical server thread only. A cancelled or unloaded approach must never spend the daily visit.
    private static final Map<ServerPlayer, Claim> APPROACHES = new WeakHashMap<>();
    private final BlossomWarden guard;
    private ServerPlayer visitor;
    private long started, nextScan, nextPath;
    private boolean gaveAccent, departing;
    private UUID unreachable;
    private long retryAfter;
    private float bearing;

    GuardianGreetingGoal(BlossomWarden guard) {
        this.guard = guard;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private static long day(ServerPlayer player) {
        return Math.floorDiv(player.server.overworld().getDayTime(), 24000L);
    }

    private static boolean alreadyGreeted(ServerPlayer player) {
        var visit = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(VISIT);
        return visit.contains("Day", Tag.TAG_LONG) && visit.getLong("Day") >= day(player);
    }

    private GuardianGreeting remember(ServerPlayer player) {
        CompoundTag data = player.getPersistentData(), persistent = data.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag visit = persistent.getCompound(VISIT);
        var menu = guard.female() ? GuardianGreeting.DAINTY : GuardianGreeting.BULKY;
        String key = guard.female() ? "DaintyNext" : "BulkyNext";
        int first = Math.floorMod(player.getUUID().hashCode(), menu.length);
        int index = visit.contains(key, Tag.TAG_INT) ? Math.floorMod(visit.getInt(key), menu.length) : first;
        // The shared bow appears in both repertoires. Do not repeat it when switching villages.
        if (visit.contains("LastGesture", Tag.TAG_INT) && menu[index].ordinal() == visit.getInt("LastGesture"))
            index = (index + 1) % menu.length;
        visit.putInt(key, (index + 1) % menu.length);
        visit.putInt("LastGesture", menu[index].ordinal());
        visit.putLong("Day", day(player));
        persistent.put(VISIT, visit);
        data.put(Player.PERSISTED_NBT_TAG, persistent);
        return menu[index];
    }

    private boolean available(ServerPlayer player) {
        if (!eligible(player) || alreadyGreeted(player)
                || player.getUUID().equals(unreachable) && guard.level().getGameTime() < retryAfter) return false;
        Claim claim = APPROACHES.get(player);
        return claim == null || claim.expires <= guard.level().getGameTime()
                || !(player.serverLevel().getEntity(claim.guardian) instanceof BlossomWarden other) || !other.isAlive();
    }

    private boolean eligible(ServerPlayer player) {
        var home = guard.villageHome();
        return home != null && player.isAlive() && !player.isSpectator() && !player.isSleeping()
                && !player.isRemoved() && player.level() == guard.level()
                && guard.distanceToSqr(player) <= SEARCH * SEARCH
                && GuardianVillageHomes.inPrecinct(home, player.blockPosition());
    }

    private boolean ready() {
        return guard.villageHome() != null && !guard.isBaby() && guard.idlePose()
                && !guard.hasOffenders() && !guard.rescueSprinting() && guard.isAlive()
                && !guard.isPassenger() && !guard.isInWaterOrBubble();
    }

    @Override public boolean canUse() {
        if (guard.escortOwner!=null) return false;
        if (!ready() || !(guard.level() instanceof ServerLevel level) || level.getGameTime() < nextScan) return false;
        nextScan = level.getGameTime() + 10;
        visitor = level.players().stream().filter(this::available)
                .min(Comparator.comparingDouble(p -> guard.distanceToSqr(p))).orElse(null);
        return visitor != null;
    }

    @Override public void start() {
        started = guard.level().getGameTime();
        nextPath = started;
        guard.approachVisitor(true);
        gaveAccent = departing = false;
        APPROACHES.put(visitor, new Claim(guard.getUUID(), started + APPROACH_LIMIT + 1));
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public boolean canContinueToUse() {
        if (departing || visitor == null || !ready() || !eligible(visitor)) return false;
        if (guard.greeting() != GuardianGreeting.NONE) return true;
        var claim = APPROACHES.get(visitor);
        return claim != null && claim.guardian.equals(guard.getUUID()) && !alreadyGreeted(visitor)
                && guard.level().getGameTime() - started < APPROACH_LIMIT;
    }

    @Override public void tick() {
        long now = guard.level().getGameTime();
        var gesture = guard.greeting();
        if (gesture == GuardianGreeting.NONE) {
            guard.getLookControl().setLookAt(visitor, 30, 25);
            if (guard.distanceToSqr(visitor) > REACH * REACH || !guard.hasLineOfSight(visitor)) {
                if (now >= nextPath) {
                    guard.getNavigation().moveTo(visitor, 1.24);
                    nextPath = now + 9;
                }
                return;
            }
            if (alreadyGreeted(visitor)) { departing = true; return; }
            guard.getNavigation().stop();
            guard.approachVisitor(false);
            guard.beginGreeting(remember(visitor));
        }
        bearing = (float)(Mth.atan2(visitor.getZ() - guard.getZ(), visitor.getX() - guard.getX()) * Mth.RAD_TO_DEG) - 90F;
        guard.setYRot(bearing);
        guard.setYBodyRot(bearing);
        guard.setYHeadRot(bearing);
        guard.getLookControl().setLookAt(visitor, 30, 20);
        float elapsed = guard.greetingTicks(0);
        gesture = guard.greeting();
        if (!gaveAccent && elapsed >= 16) {
            gaveAccent = true;
            var level = visitor.serverLevel();
            if (gesture == GuardianGreeting.MEME_WINK)
                level.playSound(null, guard.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, .42F, 1.55F);
            if (gesture == GuardianGreeting.KISS) {
                var facing = GuardianWarningMotion.forward(bearing);
                level.sendParticles(ParticleTypes.HEART, guard.getX() + facing.x * .55,
                        guard.getEyeY() + .06, guard.getZ() + facing.z * .55, 1, 0, 0, 0, 0);
            }
        }
        if (elapsed >= gesture.duration) {
            guard.endGreeting();
            guard.walkAwayFrom(bearing);
            departing = true;
        }
    }

    @Override public void stop() {
        if (visitor != null) {
            if (guard.approachingVisitor() && guard.level().getGameTime() - started >= APPROACH_LIMIT) {
                unreachable = visitor.getUUID();
                retryAfter = guard.level().getGameTime() + 100;
            }
            var claim = APPROACHES.get(visitor);
            if (claim != null && claim.guardian.equals(guard.getUUID())) APPROACHES.remove(visitor);
        }
        visitor = null;
        guard.approachVisitor(false);
        guard.endGreeting();
        guard.getNavigation().stop();
        nextScan = guard.level().getGameTime() + 15;
    }
}
