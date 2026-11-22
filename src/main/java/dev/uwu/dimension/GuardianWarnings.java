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
    private record Notice(long lastHit, long until, boolean pending) {}
    private final Map<UUID, Notice> notices = new HashMap<>();

    /** False for repeat callbacks in the same hurt-resistance interval. */
    boolean accept(UUID id, long now, boolean alreadyOnDuty) {
        Notice old = notices.get(id);
        if (old != null && now >= old.lastHit && now - old.lastHit < SAME_IMPACT) return false;
        boolean first = !alreadyOnDuty && (old == null || (!old.pending && now >= old.until));
        notices.put(id, new Notice(now, now + FORGET_AFTER, first));
        return true;
    }

    boolean pending(UUID id) {
        Notice notice = notices.get(id);
        return notice != null && notice.pending;
    }

    void delivered(UUID id, long now) {
        Notice notice = notices.get(id);
        if (notice != null) notices.put(id, new Notice(notice.lastHit, now + FORGET_AFTER, false));
    }

    void abandon(UUID id) {
        if (pending(id)) notices.remove(id);
    }

    void prune(long now) {
        notices.values().removeIf(n -> !n.pending && now >= n.until);
    }

    void save(CompoundTag tag, long now) {
        prune(now);
        ListTag entries = new ListTag();
        notices.forEach((id, notice) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putLong("Hit", notice.lastHit);
            entry.putLong("Until", notice.until);
            entry.putBoolean("Pending", notice.pending);
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
            long until = entry.getLong("Until");
            if (pending ? queue.contains(id) : until > now)
                notices.put(id, new Notice(entry.getLong("Hit"), until, pending));
        }
    }

    static void toss(ServerPlayer player, double originalFeetY) {
        CompoundTag landing = new CompoundTag();
        long now = player.level().getGameTime();
        landing.putString("Dimension", player.level().dimension().location().toString());
        landing.putLong("Launched", now);
        landing.putDouble("Floor", originalFeetY);
        player.getPersistentData().put(LANDING, landing);
        player.fallDistance = 0;
        player.setOnGround(false);
        // The grip raises the player about two blocks. Vanilla gravity supplies the remaining eight.
        player.setDeltaMovement(0, 1.20, 0);
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
        data.remove(LANDING);
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
        if (!current(player, landing) || player.onGround() && player.level().getGameTime() - landing.getLong("Launched") > 2)
            data.remove(LANDING);
    }
}
