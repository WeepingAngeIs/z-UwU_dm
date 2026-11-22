package dev.uwu.dimension.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import net.neoforged.neoforge.client.event.sound.SoundEvent.SoundSourceEvent;

@EventBusSubscriber(modid = "uwudimension", value = Dist.CLIENT)
public final class TimePocketAcoustics {
    public interface ChannelAir {
        void uwu$timePocketAir(boolean damped);
    }

    @SubscribeEvent public static void played(PlaySoundSourceEvent event) { apply(event); }
    @SubscribeEvent public static void streamed(PlayStreamingSourceEvent event) { apply(event); }

    private static void apply(SoundSourceEvent event) {
        if (TimePocketClient.gain(event.getSound().getSource()) < 1) {
            ((ChannelAir) event.getChannel()).uwu$timePocketAir(true);
        }
    }

    private TimePocketAcoustics() {}
}
