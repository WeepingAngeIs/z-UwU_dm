package dev.uwu.dialoguecheck.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dialoguecheck.WelcomeCheck;
import dev.uwu.dimension.BlossomWarden;
import dev.uwu.dimension.client.VillageSpeechFrame;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VillageSpeechFrame.class, remap = false)
abstract class WelcomeObserver {
    @Inject(method="welcome", at=@At("HEAD"))
    private static void speaker(BlossomWarden entity, float partial, PoseStack poses, MultiBufferSource buffer, CallbackInfo ci) {
        WelcomeCheck.speaker(entity);
    }
    @Inject(method="draw", at=@At("TAIL"))
    private static void label(Component text, @Coerce Object style, float fade, PoseStack poses, MultiBufferSource buffer, CallbackInfo ci) {
        WelcomeCheck.draw(text, style.toString(), fade);
    }
    @Inject(method="welcome", at=@At("RETURN"))
    private static void clear(CallbackInfo ci) { WelcomeCheck.speaker(null); }
}
