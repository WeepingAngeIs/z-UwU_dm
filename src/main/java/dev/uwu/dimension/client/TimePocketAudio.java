package dev.uwu.dimension.client;

import dev.uwu.dimension.TimePocket;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** One listener, one active clock. The native audio buffer supplies the recorded cadence. */
final class TimePocketAudio {
    private static ClientLevel world;
    private static UUID insideCast;
    private static Cue opening, loop, ending;
    private static TimePocket.Field endingArea;

    static void update(Minecraft mc, TimePocket.Field field, boolean activation) {
        if (world != mc.level) reset(mc);
        world = mc.level;
        if (ending != null && (!inside(mc, endingArea) || !mc.getSoundManager().isActive(ending))) stopEnding(mc);
        if (!inside(mc, field)) {
            leave(mc);
            return;
        }
        if (!field.id().equals(insideCast)) {
            leave(mc);
            insideCast = field.id();
            TimePocketClient.setQuiet(mc, true);
            if (activation) opening = play(mc, TimePocket.OPENING.get(), false, field.until());
        }
        if (opening != null && mc.getSoundManager().isActive(opening)) return;
        opening = null;
        if (loop == null || !mc.getSoundManager().isActive(loop)) loop = play(mc, TimePocket.CLOCK_LOOP.get(), true, field.until());
    }

    static void thaw(Minecraft mc, TimePocket.Field field) {
        if (world != mc.level) reset(mc);
        world = mc.level;
        leave(mc);
        if (inside(mc, field)) {
            // A fast recast can begin while the preceding ending rings out. Keep at most one ending.
            stopEnding(mc);
            ending = play(mc, TimePocket.THAW.get(), false, -1);
            if (ending != null) endingArea = field;
        } else if (ending != null && !inside(mc, endingArea)) {
            stopEnding(mc);
        }
    }

    private static boolean inside(Minecraft mc, TimePocket.Field field) {
        return field != null && mc.level != null && mc.player != null && mc.player.level() == mc.level
                && field.at(mc.player.getX(), mc.player.getZ());
    }

    private static Cue play(Minecraft mc, SoundEvent event, boolean repeating, long deadline) {
        var manager = mc.getSoundManager();
        // Definitions and recordings come from Anya's private client resource pack.
        var definition = manager.getSoundEvent(event.getLocation());
        if (definition == null || definition.getWeight() == 0 || mc.options.getSoundSourceVolume(SoundSource.MASTER) == 0F) return null;
        var sound = new Cue(event, repeating, mc.level, deadline);
        manager.play(sound);
        return sound;
    }

    private static final class Cue extends SimpleSoundInstance implements TickableSoundInstance {
        private volatile boolean cancelled;
        private final ClientLevel level;
        private final long deadline;

        Cue(SoundEvent event, boolean repeating, ClientLevel level, long deadline) {
            super(event.getLocation(), SoundSource.MASTER, 1F, 1F, SoundInstance.createUnseededRandom(),
                    repeating, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
            this.level = level;
            this.deadline = deadline;
        }

        @Override public boolean isStopped() { return cancelled; }

        @Override public void tick() {
            // Soften the last game tick before a scheduled thaw; crossing or cancellation still stops immediately.
            if (deadline >= 0 && level.getGameTime() >= deadline - 1) volume = .02F;
        }
    }

    static boolean cancelled(SoundInstance sound) {
        return sound instanceof Cue cue && cue.cancelled;
    }

    private static void stop(Minecraft mc, Cue cue) {
        if (cue == null) return;
        // Buffer loading is asynchronous. Revoke the cue even if OpenAL has not attached it yet.
        cue.cancelled = true;
        mc.getSoundManager().stop(cue);
    }

    private static void leave(Minecraft mc) {
        stop(mc, opening);
        stop(mc, loop);
        opening = null;
        loop = null;
        insideCast = null;
        TimePocketClient.setQuiet(mc, false);
    }

    private static void stopEnding(Minecraft mc) {
        stop(mc, ending);
        ending = null;
        endingArea = null;
    }

    static void reset(Minecraft mc) {
        leave(mc);
        stopEnding(mc);
        world = null;
    }

    private TimePocketAudio() {}
}
