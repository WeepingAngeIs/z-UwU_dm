package dev.uwu.lotusclientcheck.mixin;

import dev.uwu.dimension.RevivalBlossomClient;
import dev.uwu.lotusclientcheck.BlossomFollowProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RevivalBlossomClient.class, remap = false)
abstract class BlossomDrawObserver {
    @Inject(method = "drawFrame", at = @At("TAIL"))
    private static void observeDrawnFrame(CallbackInfo ci) {
        BlossomFollowProbe.submitted();
    }
}
