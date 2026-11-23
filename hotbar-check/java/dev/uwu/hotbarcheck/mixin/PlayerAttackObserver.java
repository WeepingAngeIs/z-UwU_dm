package dev.uwu.hotbarcheck.mixin;

import dev.uwu.hotbarcheck.HotbarProbe;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
abstract class PlayerAttackObserver {
    @Inject(method = "attack", at = @At("HEAD"))
    private void combined$serverMelee(Entity target, CallbackInfo callback) {
        HotbarProbe.serverMelee((Player)(Object)this);
    }
}
