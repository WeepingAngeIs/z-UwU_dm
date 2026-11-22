package dev.uwu.dimension.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dimension.Keeper;
import dev.uwu.dimension.KeeperSpeech;
import dev.uwu.dimension.People;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Built-in NPC bubbles; no player-chat interception or optional-mod dependency. */
@EventBusSubscriber(modid = "uwudimension", value = Dist.CLIENT)
public final class KeeperSpeechBubbles {
    private static final Map<UUID, KeeperSpeech.Cue> SPEECH = new HashMap<>();
    private static ClientLevel level;

    public static void receive(KeeperSpeech.Cue cue) {
        Minecraft client = Minecraft.getInstance();
        synchronizeLevel(client.level);
        if (level == null || client.player == null || !level.dimension().location().equals(cue.dimension())) return;
        long age = level.getGameTime() - cue.startTick();
        if (age < -20 || age >= KeeperSpeech.DURATION_TICKS || cue.line().getString().isBlank()
                || cue.line().getString().length() > KeeperSpeech.MAX_TEXT_LENGTH) return;
        if (!(level.getEntity(cue.entityId()) instanceof Keeper keeper) || !matches(keeper, cue)
                || client.player.distanceToSqr(keeper) > KeeperSpeech.RANGE * KeeperSpeech.RANGE) return;
        var previous = SPEECH.get(cue.speaker());
        if (previous != null && previous.startTick() > cue.startTick()) return;
        SPEECH.put(cue.speaker(), new KeeperSpeech.Cue(cue.dimension(), cue.entityId(), cue.speaker(),
                cue.startTick(), cue.dusk(), cue.line().copy()));
    }

    private static void synchronizeLevel(ClientLevel current) {
        if (level != current) {
            SPEECH.clear();
            level = current;
        }
    }

    private static boolean matches(Keeper keeper, KeeperSpeech.Cue cue) {
        return !keeper.isRemoved() && keeper.isAlive() && keeper.getUUID().equals(cue.speaker())
                && (keeper.getType() == People.DUSK_KEEPER.get()) == cue.dusk() && keeper.level() == level;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        synchronizeLevel(client.level);
        if (level == null || client.player == null) {
            SPEECH.clear();
            return;
        }
        long now = level.getGameTime();
        SPEECH.values().removeIf(cue -> now - cue.startTick() >= KeeperSpeech.DURATION_TICKS
                || now - cue.startTick() < -20 || !(level.getEntity(cue.entityId()) instanceof Keeper keeper)
                || !matches(keeper, cue) || client.player.distanceToSqr(keeper) > KeeperSpeech.RANGE * KeeperSpeech.RANGE);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SPEECH.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        if (level == null || client.level != level || client.player == null || client.options.hideGui) return;
        var buffers = client.renderBuffers().bufferSource();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (var cue : SPEECH.values()) {
            if (!(level.getEntity(cue.entityId()) instanceof Keeper keeper) || !matches(keeper, cue)
                    || keeper.isInvisibleTo(client.player)) continue;
            long age = level.getGameTime() - cue.startTick();
            if (age < 0 || age >= KeeperSpeech.DURATION_TICKS) continue;
            if (client.player.distanceToSqr(keeper) > KeeperSpeech.RANGE * KeeperSpeech.RANGE
                    || !event.getFrustum().isVisible(keeper.getBoundingBox().inflate(4, 5, 4))) continue;
            draw(keeper, cue, event.getPoseStack(), buffers, partialTick);
        }
        buffers.endBatch();
    }

    private static void draw(Keeper keeper, KeeperSpeech.Cue cue, PoseStack poses,
                             MultiBufferSource buffers, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        var camera = client.gameRenderer.getMainCamera();
        var eye = camera.getPosition();
        float remaining = KeeperSpeech.DURATION_TICKS - (level.getGameTime() - cue.startTick() + partialTick);
        poses.pushPose();
        poses.translate(Mth.lerp(partialTick, keeper.xo, keeper.getX()) - eye.x,
                Mth.lerp(partialTick, keeper.yo, keeper.getY()) + keeper.getBbHeight() + .55 - eye.y,
                Mth.lerp(partialTick, keeper.zo, keeper.getZ()) - eye.z);
        VillageSpeechFrame.draw(cue.line(), cue.dusk() ? VillageSpeechFrame.Style.DUSK : VillageSpeechFrame.Style.LOTUS,
                Mth.clamp(remaining / 20, 0, 1), poses, buffers);
        poses.popPose();
    }

    private KeeperSpeechBubbles() {}
}
