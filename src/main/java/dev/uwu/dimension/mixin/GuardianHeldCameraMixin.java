package dev.uwu.dimension.mixin;

import dev.uwu.dimension.GuardianWarningMotion;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
abstract class GuardianHeldCameraMixin {
    @Shadow public abstract Vec3 getPosition();
    @Shadow protected abstract void setPosition(double x, double y, double z);

    @Inject(method="setup", at=@At("TAIL"))
    private void uwu$followCarriedEyes(BlockGetter level, Entity subject, boolean detached, boolean mirrored,
            float partial, CallbackInfo ci) {
        if (detached || !(subject instanceof LocalPlayer player) || player != Minecraft.getInstance().player) return;
        var guardian = GuardianWarningMotion.holderOnClient(player);
        if (guardian == null || guardian.female() || guardian.duskGuardian()) return;

        Vec3 start = getPosition();
        double eyeHeight = start.y - player.getPosition(partial).y;
        Vec3 desired = GuardianWarningMotion.renderedCarry(guardian, partial).eye(eyeHeight);
        Vec3 eye = uwu$clipEye(level, player, start, desired);
        // Only the origin follows the carried body. Vanilla keeps the player's look direction and horizon.
        setPosition(eye.x, eye.y, eye.z);
    }

    @Unique private static Vec3 uwu$clipEye(BlockGetter level, LocalPlayer player, Vec3 start, Vec3 end) {
        Vec3 travel = end.subtract(start);
        double length = travel.length();
        if (length < .0001) return start;

        // Sweep a small camera volume to avoid peeking around a wall or through a low ceiling.
        double clearance = length;
        for (int corner = 0; corner < 9; corner++) {
            Vec3 offset = corner == 8 ? Vec3.ZERO : new Vec3(
                    (corner & 1) == 0 ? -.1 : .1, (corner & 2) == 0 ? -.1 : .1, (corner & 4) == 0 ? -.1 : .1);
            Vec3 from = start.add(offset);
            var hit = level.clip(new ClipContext(from, end.add(offset), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS)
                clearance = Math.min(clearance, Math.max(0, from.distanceTo(hit.getLocation()) - .035));
        }
        return start.add(travel.scale(clearance / length));
    }
}
