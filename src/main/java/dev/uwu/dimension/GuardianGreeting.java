package dev.uwu.dimension;

/** Each welcome is a single short performance, timed in server ticks. */
public enum GuardianGreeting {
    NONE(0), WAVE(37), JACKS(42), SPIN_BOW(48), MEME_WINK(34),
    TWIRL(43), KISS(40), WINK(34);

    public final int duration;

    GuardianGreeting(int duration) { this.duration = duration; }

    static final GuardianGreeting[] BULKY = {WAVE, JACKS, SPIN_BOW, WAVE, MEME_WINK, SPIN_BOW};
    static final GuardianGreeting[] DAINTY = {TWIRL, SPIN_BOW, KISS, TWIRL, WINK, SPIN_BOW};
    private static final GuardianGreeting[] VALUES = values();

    static GuardianGreeting byId(int id) { return id >= 0 && id < VALUES.length ? VALUES[id] : NONE; }

    public boolean winking(float ticks) {
        return (this == MEME_WINK || this == WINK) && ticks >= 12 && ticks < 23;
    }
}
