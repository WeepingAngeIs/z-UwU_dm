package dev.uwu.dimension;

import dev.uwu.dimension.client.KeeperSpeechBubbles;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** One server-authored utterance, shared by regular nearby chat and its NPC bubble. */
public final class KeeperSpeech {
    public static final double RANGE = 80.0;
    public static final int DURATION_TICKS = 240;
    public static final int MAX_TEXT_LENGTH = 512;
    private static final int REPEAT_COOLDOWN_TICKS = 40;
    // Transient state only: no resident NBT or saved-world migration is involved.
    private static final Map<Keeper, LastLine> LAST_LINES = Collections.synchronizedMap(new WeakHashMap<>());

    private record LastLine(Component line, long startedAt) {}

    public record Cue(ResourceLocation dimension, int entityId, UUID speaker, long startTick,
                      boolean dusk, Component line) implements CustomPacketPayload {
        public static final Type<Cue> TYPE = new Type<>(UwUDimension.id("keeper_speech"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Cue> CODEC = StreamCodec.of(
                (buffer, cue) -> {
                    buffer.writeResourceLocation(cue.dimension());
                    buffer.writeVarInt(cue.entityId());
                    buffer.writeUUID(cue.speaker());
                    buffer.writeVarLong(cue.startTick());
                    buffer.writeBoolean(cue.dusk());
                    ComponentSerialization.STREAM_CODEC.encode(buffer, cue.line());
                },
                buffer -> new Cue(buffer.readResourceLocation(), buffer.readVarInt(), buffer.readUUID(),
                        buffer.readVarLong(), buffer.readBoolean(), ComponentSerialization.STREAM_CODEC.decode(buffer)));

        @Override public Type<Cue> type() { return TYPE; }
    }

    static void register(IEventBus bus) {
        bus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1").playToClient(
                Cue.TYPE, Cue.CODEC, (cue, context) -> KeeperSpeechBubbles.receive(cue)));
    }

    /** Uses a spherical five-chunk (80-block) distance from the speaking keeper. */
    public static boolean say(Keeper speaker, Component line) {
        if (!(speaker.level() instanceof ServerLevel level) || !speaker.isAlive() || speaker.isRemoved()
                || (speaker.getType() != People.PETAL_KEEPER.get() && speaker.getType() != People.DUSK_KEEPER.get())) return false;
        String text = line.getString();
        if (text.isBlank() || text.length() > MAX_TEXT_LENGTH) return false;
        long now = level.getGameTime();
        LastLine previous = LAST_LINES.get(speaker);
        if (previous != null && previous.line().equals(line) && now >= previous.startedAt()
                && now - previous.startedAt() < REPEAT_COOLDOWN_TICKS) return false;

        var listeners = level.players().stream().filter(player -> !player.isRemoved()
                && player.level() == level && player.distanceToSqr(speaker) <= RANGE * RANGE).toList();
        if (listeners.isEmpty()) return false;
        Component spoken = line.copy();
        LAST_LINES.put(speaker, new LastLine(spoken, now));
        var cue = new Cue(level.dimension().location(), speaker.getId(), speaker.getUUID(), now, speaker.dusk(), spoken);
        for (var listener : listeners) {
            // System chat is the normal chat window, not an action bar or a forged
            // player message. Player-only chat-bubble mods do not receive a chat event.
            listener.sendSystemMessage(spoken);
            PacketDistributor.sendToPlayer(listener, cue);
        }
        return true;
    }

    private KeeperSpeech() {}
}
