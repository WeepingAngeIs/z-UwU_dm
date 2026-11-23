package dev.uwu.hotbarcheck.mixin;

import dev.uwu.hotbarcheck.HotbarProbe;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
abstract class MiningObserver {
    @Inject(method = {"startDestroyBlock", "continueDestroyBlock"}, at = @At("HEAD"))
    private void combined$mine(CallbackInfoReturnable<Boolean> callback) {
        HotbarProbe.nativeMining();
    }
}
