package dev.uwu.dimension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Direct player hits are remembered by this Guardian, independently of Keeper rescue reports. */
@EventBusSubscriber(modid = "uwudimension")
public final class GuardianWarnings {
    private static final int FORGET_AFTER = 30 * 20;
    private static final int SAME_IMPACT = 10;
    private static final String LANDING = "uwudimension:guardian_warning_landing";
    static final int FIRST = 1, SECOND = 2;
    private record Notice(long lastHit, long until, int completed, int pending) {}
    private final Map<UUID, Notice> notices = new HashMap<>();

    boolean accept(UUID id, long now, boolean alreadyOnDuty, boolean petal) {
        Notice old = notices.get(id);
        // A hit during approach, shake, wink or hold cannot cancel or promote an active warning.
        if (alreadyOnDuty || old != null && (old.pending != 0 || now >= old.lastHit && now - old.lastHit < SAME_IMPACT)) return false;
        int completed = old == null || now >= old.until ? 0 : old.completed;
        int next = completed == 0 ? FIRST : petal && completed == FIRST ? SECOND : 0;
        notices.put(id, new Notice(now, now + FORGET_AFTER, completed, next));
        return true;
    }

    int kind(UUID id) {
        Notice notice = notices.get(id);
        return notice == null ? 0 : notice.pending;
    }

    boolean pending(UUID id) { return kind(id) != 0; }

    void delivered(UUID id, long now) {
        Notice notice = notices.get(id);
        if (notice != null && notice.pending != 0)
            notices.put(id, new Notice(notice.lastHit, now + FORGET_AFTER, notice.pending, 0));
    }

    void abandon(UUID id) {
        Notice notice = notices.get(id);
        if (notice == null || notice.pending == 0) return;
        if (notice.completed == 0) notices.remove(id);
        else notices.put(id, new Notice(notice.lastHit, notice.until, notice.completed, 0));
    }

    void prune(long now) {
        notices.values().removeIf(n -> n.pending == 0 && now >= n.until);
    }

    void save(CompoundTag tag, long now) {
        prune(now);
        ListTag entries = new ListTag();
        notices.forEach((id, notice) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putLong("Hit", notice.lastHit);
            entry.putLong("Until", notice.until);
            entry.putBoolean("Pending", notice.pending != 0);
            entry.putInt("Completed", notice.completed);
            entry.putInt("Kind", notice.pending);
            entries.add(entry);
        });
        if (!entries.isEmpty()) tag.put("PlayerWarnings", entries);
    }

    void load(CompoundTag tag, java.util.Collection<UUID> queue, long now) {
        notices.clear();
        ListTag entries = tag.getList("PlayerWarnings", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID("Player")) continue;
            UUID id = entry.getUUID("Player");
            boolean pending = entry.getBoolean("Pending");
            int completed = entry.contains("Completed") ? Math.clamp(entry.getInt("Completed"), 0, SECOND) : pending ? 0 : FIRST;
            int kind = pending ? entry.contains("Kind") ? Math.clamp(entry.getInt("Kind"), FIRST, SECOND) : FIRST : 0;
            long until = entry.getLong("Until");
            if (pending ? queue.contains(id) : until > now)
                notices.put(id, new Notice(entry.getLong("Hit"), until, completed, kind));
        }
    }

    static void toss(ServerPlayer player, double originalFeetY, int kind, float bearing, boolean left) {
        CompoundTag landing = new CompoundTag();
        long now = player.level().getGameTime();
        landing.putString("Dimension", player.level().dimension().location().toString());
        landing.putLong("Launched", now);
        landing.putDouble("Floor", originalFeetY);
        player.getPersistentData().put(LANDING, landing);
        player.fallDistance = 0;
        player.setOnGround(false);
        if (kind == SECOND) {
            // In still air this travels about twenty blocks horizontally before returning to level ground.
            var horizontal = GuardianWarningMotion.forward(bearing).yRot(left ? .95F : -.95F).scale(2.18);
            player.setDeltaMovement(horizontal.x, .94, horizontal.z);
        } else {
            player.setDeltaMovement(0, 1.39, 0);
        }
        player.hurtMarked = true; // ServerEntity sends the velocity to the player and tracking clients.
    }

    private static boolean current(ServerPlayer player, CompoundTag landing) {
        long age = player.level().getGameTime() - landing.getLong("Launched");
        return age >= 0 && age < 100 && landing.getString("Dimension").equals(player.level().dimension().location().toString());
    }

    @SubscribeEvent
    public static void land(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var data = player.getPersistentData();
        if (!data.contains(LANDING, Tag.TAG_COMPOUND)) return;
        var landing = data.getCompound(LANDING);
        if (current(player, landing)) {
            float naturalDrop = (float)Math.max(0, landing.getDouble("Floor") - player.getY());
            float discount = Math.clamp(event.getDistance() - naturalDrop, 0F, 10.5F);
            event.setDistance(event.getDistance() - discount);
        }
    }

    @SubscribeEvent
    public static void clearLanding(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var data = player.getPersistentData();
        if (!data.contains(LANDING, Tag.TAG_COMPOUND)) return;
        var landing = data.getCompound(LANDING);
        if (!current(player, landing)) {
            data.remove(LANDING);
            return;
        }
        // Acknowledgments from the grip can still report ground after release. Retire
        // protection only after the descent ends and the actual landing packets settle.
        double y = player.getY();
        if (landing.contains("LastY") && y < landing.getDouble("LastY") - .02
                && player.level().getGameTime() - landing.getLong("Launched") > 4)
            landing.putBoolean("Descending", true);
        landing.putDouble("LastY", y);
        int grounded = player.onGround() && landing.getBoolean("Descending") ? landing.getInt("Grounded") + 1 : 0;
        if (grounded >= 4) data.remove(LANDING);
        else landing.putInt("Grounded", grounded);
    }
}
