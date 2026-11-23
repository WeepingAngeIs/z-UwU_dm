package dev.uwu.hotbarcheck.mixin;

import dev.uwu.dimension.DawnpetalAbilities;
import dev.uwu.dimension.DawnpetalAbility;
import dev.uwu.hotbarcheck.HotbarProbe;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes the real production request; never substitutes a packet or ability call. */
@Mixin(value = DawnpetalAbilities.class, remap = false)
abstract class AbilityNetworkObserver {
    @Inject(method = "send", at = @At("HEAD"))
    private static void combined$sent(Player player, long sequence, UUID weapon,
            DawnpetalAbility ability, CallbackInfo callback) {
        HotbarProbe.sentUse(ability.index);
    }

    @Inject(method = "receive", at = @At("HEAD"))
    private static void combined$received(CallbackInfo callback) {
        HotbarProbe.receivedUse();
    }
}
