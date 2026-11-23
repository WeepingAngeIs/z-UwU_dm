package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.uwu.dimension.SharedAbilityHotbar;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, priority = 1100)
abstract class SharedAbilityInputMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void uwu$abilitySelection(CallbackInfo callback) {
        Minecraft client = (Minecraft)(Object)this;
        SharedAbilityHotbar.handleHotbar(client);
        SharedAbilityHotbar.handleAttack(client);
    }

    @WrapOperation(method = "handleKeybinds",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;continueAttack(Z)V"))
    private void uwu$continueOrdinaryAttack(Minecraft client, boolean attack, Operation<Void> vanilla) {
        if (!SharedAbilityHotbar.suppressOrdinaryAttack(client)) {
            vanilla.call(client, attack);
            return;
        }
        // Better Combat's hold handler reads the binding itself rather than this call's flag.
        var key = client.options.keyAttack;
        boolean wasDown = key.isDown();
        key.setDown(false);
        try {
            vanilla.call(client, false);
        } finally {
            key.setDown(wasDown);
        }
    }
}
