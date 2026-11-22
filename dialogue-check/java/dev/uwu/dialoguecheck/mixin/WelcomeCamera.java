package dev.uwu.dialoguecheck.mixin;

import dev.uwu.dialoguecheck.WelcomeCheck;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
abstract class WelcomeCamera {
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Inject(method="setup", at=@At("TAIL"))
    private void angle(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partial, CallbackInfo ci) {
        var view = WelcomeCheck.camera(partial);
        if (view != null) {
            setPosition(view.x(), view.y(), view.z());
            setRotation(view.yaw(), view.pitch());
        }
    }
}
