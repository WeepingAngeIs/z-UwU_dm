package dev.uwu.lotusclientcheck.mixin;

import dev.uwu.lotusclientcheck.GuardianCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The test's second pass films the native attacking player with a separate camera location. */
@Mixin(Camera.class)
abstract class GuardianObserverCamera {
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Inject(method="setup",at=@At("TAIL"))
    private void uwu$observer(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partial, CallbackInfo ci) {
        if (!detached || !GuardianCamera.observer()) return;
        var view = GuardianCamera.observerView(partial);
        if (view != null) {
            setPosition(view.eye().x, view.eye().y, view.eye().z);
            setRotation(view.yaw(), view.pitch());
        }
    }
}
