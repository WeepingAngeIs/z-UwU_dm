package dev.uwu.hotbarcheck.mixin;

import dev.uwu.hotbarcheck.CombinedVisualProbe;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves only the static third-person review camera; never transforms a held model. */
@Mixin(Camera.class)
abstract class CombinedReviewCamera {
    @Shadow protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "setup", at = @At("TAIL"))
    private void combinedCheck$closeReview(BlockGetter level, Entity entity, boolean detached,
            boolean mirrored, float partial, CallbackInfo callback) {
        double distance = CombinedVisualProbe.closeReviewCameraDistance();
        if (distance <= 0 || !detached || entity != Minecraft.getInstance().player) return;
        Vec3 eye = entity.getEyePosition(partial);
        Vec3 fromEye = ((Camera) (Object) this).getPosition().subtract(eye);
        if (fromEye.lengthSqr() < 1.0E-8) return;
        Vec3 position = eye.add(fromEye.normalize().scale(distance));
        setPosition(position.x, position.y, position.z);
    }
}
