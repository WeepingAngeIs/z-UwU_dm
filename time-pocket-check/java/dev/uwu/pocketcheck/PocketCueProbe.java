package dev.uwu.pocketcheck;

import dev.uwu.dimension.TimePocket;
import dev.uwu.pocketcheck.mixin.AudioSource;
import java.util.concurrent.atomic.AtomicInteger;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

/** All AL reads run on Minecraft's audio executor, in actual source-start callbacks. */
final class PocketCueProbe {
    private static final boolean EMBEDDED = Boolean.getBoolean("uwu.pocket.embeddedAudio");
    private static final Map<String, String> CUES = Map.of(
            "opening", "137a9585b99d8e6572a3e40c426a771651f9ea5adea292f35d4c9335755bf71e",
            "clock_loop", "f16f7e3657657645ad4e4fd4c4c67b084ccc116c399914b8710a91831de83f66",
            "thaw", "d630b4708a2c6958dfe0f68669cfb5233c65fff38f303c2a452216c7323728b0");
    private static final AtomicInteger openings = new AtomicInteger(), loops = new AtomicInteger(), endings = new AtomicInteger();
    private record Voice(int source, int buffer) {
        boolean playing() {
            return AL10.alIsSource(source) && AL10.alGetSourcei(source, AL10.AL_BUFFER) == buffer
                    && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
        }
    }
    private static Voice opening, clock, ending;
    private static volatile int firstLoops, longSamples;
    private static long firstSampleTime;
    private static float firstOffset, period;
    static volatile Throwable failure;

    static void requirePack(Minecraft mc) throws Exception {
        require(EMBEDDED, "Recorded cue assertions must be enabled for this candidate");
        for (var entry : CUES.entrySet()) {
            var key = ResourceLocation.fromNamespaceAndPath("uwudimension", "sounds/uwuzi/" + entry.getKey() + ".ogg");
            var resource = mc.getResourceManager().getResource(key).orElseThrow();
            try (var input = resource.open()) {
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
                require(hash.equals(entry.getValue()), "Native resource differs from approved cue: " + entry.getKey());
            }
        }
        System.out.println("UWUZI EMBEDDED CUE RESOURCES PASS " + CUES);
    }

    static void event(PlaySoundSourceEvent event, String marker) {
        try {
            var name = event.getSound().getLocation();
            boolean start = name.equals(TimePocket.OPENING.get().getLocation());
            boolean loop = name.equals(TimePocket.CLOCK_LOOP.get().getLocation());
            boolean thaw = name.equals(TimePocket.THAW.get().getLocation());
            if (start || loop || thaw) {
                require(EMBEDDED, "Recorded cue assertions are disabled");
                int source = ((AudioSource) event.getChannel()).pocketCheck$source();
                int buffer = AL10.alGetSourcei(source, AL10.AL_BUFFER);
                if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) return;
                Voice voice = new Voice(source, buffer);
                require(AL10.alGetSourcef(source, AL10.AL_PITCH) == 1F && event.getSound().isRelative(), "Recorded pitch or listener locality changed");
                require((AL10.alGetSourcei(source, AL10.AL_LOOPING) != 0) == loop, "Incorrect native loop flag");
                if (loop) {
                    int rate = AL10.alGetBufferi(buffer, AL10.AL_FREQUENCY);
                    int channels = AL10.alGetBufferi(buffer, AL10.AL_CHANNELS);
                    int bits = AL10.alGetBufferi(buffer, AL10.AL_BITS);
                    period = AL10.alGetBufferi(buffer, AL10.AL_SIZE) / (float)(rate * channels * (bits / 8));
                    require(rate == 44100 && Math.abs(period - 1.256009F) < .002F, "Different approved clock buffer");
                    clock = voice;
                    loops.incrementAndGet();
                } else if (start) {
                    opening = voice;
                    openings.incrementAndGet();
                } else {
                    ending = voice;
                    endings.incrementAndGet();
                }
            }
            if (!EMBEDDED || marker == null) return;
            if (marker.startsWith("long-clock-")) sampleLongLoop();
            if (marker.equals("outside-field") || marker.equals("after") || marker.equals("after-short") || marker.equals("gallery-cleanup")) {
                require(!playing(clock) && !playing(opening), "Clock or opening leaked at " + marker);
                if (!marker.equals("after")) require(!playing(ending), "Ending leaked at " + marker);
            }
        } catch (Throwable ex) { failure = ex; }
    }

    private static void sampleLongLoop() {
        require(playing(clock) && loops.get() == firstLoops + 1 && openings.get() == 2, "Long freeze restarted or lost a cue");
        long now = System.nanoTime();
        float offset = AL10.alGetSourcef(clock.source(), AL11.AL_SEC_OFFSET);
        if (longSamples++ == 0) {
            firstSampleTime = now;
            firstOffset = offset;
        } else {
            double elapsed = (now - firstSampleTime) / 1e9;
            double error = Math.IEEEremainder((offset - firstOffset) - elapsed, period);
            require(elapsed > period && Math.abs(error) < .18, "Loop drifted against the real audio clock");
        }
    }

    static void firstThaw() {
        if (!EMBEDDED) return;
        firstLoops = loops.get();
        require(openings.get() == 1 && endings.get() == 1 && firstLoops >= 1 && firstLoops <= 2,
                "Repeated activation, a second opening on re-entry, or a missing first thaw");
    }

    static void outsideThaw() {
        if (EMBEDDED) require(longSamples == 3 && openings.get() == 3 && endings.get() == 2
                && loops.get() == firstLoops + 1, "Short freeze played an ending outside, or long clock repeated an onset");
    }

    static String summary() {
        if (!EMBEDDED) return "Recorded cue assertions are disabled.";
        require(openings.get() == 4 && endings.get() == 3, "Cancellation or immediate reactivation duplicated a cue");
        return "Native opening / loop / ending starts " + openings + " / " + loops + " / " + endings
                + ", clock seconds " + period + ", fixed-pitch samples during 20-second hold " + longSamples;
    }

    private static boolean playing(Voice voice) { return voice != null && voice.playing(); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private PocketCueProbe() {}
}

