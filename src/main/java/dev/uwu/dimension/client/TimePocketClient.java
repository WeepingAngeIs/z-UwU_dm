package dev.uwu.dimension.client;

import dev.uwu.dimension.TimePocket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
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
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="uwudimension",value=Dist.CLIENT)
public final class TimePocketClient {
    private static final Map<ResourceLocation, TimePocket.Field> FIELDS = new HashMap<>();
    private static final Map<ResourceLocation, UUID> LAST_THAW = new HashMap<>();
    private static volatile boolean quiet;

    public static void receive(TimePocket.State message) {
        var mc = Minecraft.getInstance();
        var dimension = message.dimension();
        var field = message.field();
        var previous = FIELDS.get(dimension);
        boolean here = mc.level != null && dimension.equals(mc.level.dimension().location());
        if (field == null) {
            FIELDS.remove(dimension);
            if (here) TimePocketAudio.update(mc, null, false);
            return;
        }
        if (field.id().equals(LAST_THAW.get(dimension))) return;
        if (message.change() == TimePocket.Change.THAW) {
            if (previous != null && previous.id().equals(field.id())) finish(mc, dimension, previous);
            return;
        }
        if (here && mc.level.getGameTime() >= field.until()) {
            if (previous != null && previous.id().equals(field.id())) finish(mc, dimension, previous);
            return;
        }
        // A snapshot can arrive before a login or dimension switch installs the new ClientLevel.
        FIELDS.put(dimension, field);
        boolean newCast = previous == null || !field.id().equals(previous.id());
        if (here) TimePocketAudio.update(mc, field, newCast && message.change() == TimePocket.Change.START);
    }

    private static void finish(Minecraft mc, ResourceLocation dimension, TimePocket.Field field) {
        FIELDS.remove(dimension);
        LAST_THAW.put(dimension, field.id());
        if (mc.level != null && dimension.equals(mc.level.dimension().location())) TimePocketAudio.thaw(mc, field);
    }

    public static TimePocket.Field field(Level level) {
        if (level == null) return null;
        var current = FIELDS.get(level.dimension().location());
        return current != null && level.getGameTime() < current.until() ? current : null;
    }

    @SubscribeEvent public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        FIELDS.clear();
        LAST_THAW.clear();
        TimePocketAudio.reset(Minecraft.getInstance());
        TimePocketNegative.release();
    }

    public static boolean ordinary(SoundSource channel) {
        return channel != SoundSource.MASTER && channel != SoundSource.VOICE;
    }

    public static float gain(SoundSource channel) {
        return quiet && ordinary(channel) ? .20F : 1F;
    }

    private static void updateListener(Minecraft mc) {
        if (mc.level == null) {
            TimePocketAudio.reset(mc);
            return;
        }
        var dimension = mc.level.dimension().location();
        var current = FIELDS.get(dimension);
        if (current != null && mc.level.getGameTime() >= current.until()) finish(mc, dimension, current);
        else TimePocketAudio.update(mc, current, false);
    }

    static void setQuiet(Minecraft mc, boolean inside) {
        if (quiet == inside) return;
        quiet = inside;
        // Minecraft refreshes all live channels for a non-master volume update.
        mc.getSoundManager().updateSourceVolume(SoundSource.AMBIENT, mc.options.getSoundSourceVolume(SoundSource.AMBIENT));
    }

    @SubscribeEvent public static void moved(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide && event.getEntity() == Minecraft.getInstance().player)
            updateListener(Minecraft.getInstance());
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        updateListener(mc);
        var f=field(mc.level);
        if (f==null || !f.negative()) TimePocketNegative.release();
    }

    @SubscribeEvent public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
        var mc=Minecraft.getInstance();
        if (!event.isAttack() || mc.player==null || mc.screen!=null || !(mc.player.getMainHandItem().getItem() instanceof TimePocket.Clock))return;
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
