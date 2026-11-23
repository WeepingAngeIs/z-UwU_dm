package dev.uwu.hotbarcheck.mixin;

import dev.uwu.hotbarcheck.HotbarProbe;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class NativeAttackObserver {
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void combined$attack(CallbackInfoReturnable<Boolean> callback) {
        HotbarProbe.nativeAttack();
    }
}
