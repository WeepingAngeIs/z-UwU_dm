package dev.uwu.weaponcheck.mixin;

import dev.uwu.weaponcheck.PairedTrial;

import net.bettercombat.logic.AnimatedHand;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractClientPlayer.class, priority = 850)
abstract class PairedAnimation {
    @Dynamic("Better Combat adds this method to client players.")
    @Inject(method = "playAttackAnimation(Ljava/lang/String;Lnet/bettercombat/logic/AnimatedHand;FF)V",
            at = @At("HEAD"), require = 0, remap = false)
    private void weaponCheck$animation(String name, AnimatedHand hand, float length, float upswing, CallbackInfo ci) {
        PairedTrial.animation((AbstractClientPlayer)(Object)this, name);
    }
}
