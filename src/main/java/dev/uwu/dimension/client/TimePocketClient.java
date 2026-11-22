package dev.uwu.dimension.client;

import dev.uwu.dimension.TimePocket;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="uwudimension",value=Dist.CLIENT)
public final class TimePocketClient {
    private static final Map<ResourceLocation, TimePocket.Field> FIELDS = new HashMap<>();
    private static volatile boolean quiet;
    private static long nextBeat;
    private static boolean tock;
    private static SoundInstance lastBeat;

    public static void receive(TimePocket.State message) {
        // A login or dimension packet can arrive before its new ClientLevel is installed.
        if (message.field().until() < 0) FIELDS.remove(message.dimension());
        else FIELDS.put(message.dimension(), message.field());
        // New sounds can start between receiving the field and the next client tick.
        updateListener(Minecraft.getInstance());
    }

    public static TimePocket.Field field(Level level) {
        if (level == null) return null;
        var current = FIELDS.get(level.dimension().location());
        return current != null && level.getGameTime() < current.until() ? current : null;
    }

    @SubscribeEvent public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        FIELDS.clear();
        setQuiet(Minecraft.getInstance(), false);
        TimePocketNegative.release();
    }

    public static boolean ordinary(SoundSource channel) {
        return channel != SoundSource.MASTER && channel != SoundSource.VOICE;
    }

    public static float gain(SoundSource channel) {
        return quiet && ordinary(channel) ? .20F : 1F;
    }

    private static void updateListener(Minecraft mc) {
        var current = field(mc.level);
        setQuiet(mc, current != null && mc.player != null && current.at(mc.player.getX(), mc.player.getZ()));
    }

    private static void setQuiet(Minecraft mc, boolean inside) {
        if (quiet == inside) return;
        quiet = inside;
        // Minecraft refreshes all live channels for a non-master volume update.
        mc.getSoundManager().updateSourceVolume(SoundSource.AMBIENT, mc.options.getSoundSourceVolume(SoundSource.AMBIENT));
        if (lastBeat != null) {
            mc.getSoundManager().stop(lastBeat);
            lastBeat = null;
        }
        nextBeat = 0;
        tock = false;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        var f=field(mc.level);
        updateListener(mc);
        if (quiet && mc.level.getGameTime() >= nextBeat) {
            lastBeat=SimpleSoundInstance.forUI((tock?TimePocket.TOCK:TimePocket.TICK).get(),1F,.7F);
            mc.getSoundManager().play(lastBeat);
            tock=!tock;
            nextBeat=mc.level.getGameTime()+10;
        }
        if (f==null || !f.negative()) TimePocketNegative.release();
    }

    @SubscribeEvent public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
        var mc=Minecraft.getInstance();
        if (!event.isAttack() || mc.player==null || mc.screen!=null || !mc.player.getMainHandItem().is(TimePocket.CLOCK.get()))return;
        event.setCanceled(true); event.setSwingHand(false);
        PacketDistributor.sendToServer(new TimePocket.Fire());
    }

    @EventBusSubscriber(modid="uwudimension",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Models {
        @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(TimePocket.SHOT.get(),ctx->new ThrownItemRenderer<>(ctx,2.4F,true));
        }
    }
    private TimePocketClient() {}
}
