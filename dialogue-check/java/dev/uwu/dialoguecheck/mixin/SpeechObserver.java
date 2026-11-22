package dev.uwu.dialoguecheck.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dialoguecheck.DialogueClientCheck;
import dev.uwu.dimension.Keeper;
import dev.uwu.dimension.KeeperSpeech;
import dev.uwu.dimension.client.KeeperSpeechBubbles;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes the packaged renderer without replacing any of its rendering. */
@Mixin(value = KeeperSpeechBubbles.class, remap = false)
abstract class SpeechObserver {
    @Inject(method = "receive", at = @At("TAIL"))
    private static void receive(KeeperSpeech.Cue cue, CallbackInfo ci) {
        DialogueClientCheck.receivedCue(cue);
    }

    @Inject(method = "draw", at = @At("TAIL"))
    private static void draw(Keeper keeper, KeeperSpeech.Cue cue, PoseStack poses,
            MultiBufferSource buffers, float partialTick, CallbackInfo ci) {
        DialogueClientCheck.drawn(cue);
    }
}
