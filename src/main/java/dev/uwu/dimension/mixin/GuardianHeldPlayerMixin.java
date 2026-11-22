package dev.uwu.dimension.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dimension.GuardianWarningMotion;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draw the player's waist in the synchronized palms throughout the Petal flip. */
@Mixin(PlayerRenderer.class)
abstract class GuardianHeldPlayerMixin {
    @Inject(method="setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at=@At("HEAD"))
    private void uwu$turnWithPetalGuardian(AbstractClientPlayer player, PoseStack poses, float age, float yaw,
            float partial, float scale, CallbackInfo ci) {
        var guardian = GuardianWarningMotion.holderOnClient(player);
        if (guardian == null) return;
        var carry = GuardianWarningMotion.renderedCarry(guardian, partial);
        Vec3 offset = carry.waist().subtract(GuardianWarningMotion.renderPosition(player, partial));
        double waist = GuardianWarningMotion.PLAYER_GRIP_HEIGHT;
        poses.translate(offset.x/scale, offset.y/scale, offset.z/scale);
        double direction = Math.toRadians(carry.bearing());
        poses.mulPose(new Quaternionf().rotationAxis(carry.angle(),
                (float)-Math.cos(direction), 0F, (float)-Math.sin(direction)));
        poses.translate(0, -waist/scale, 0);
    }
}
