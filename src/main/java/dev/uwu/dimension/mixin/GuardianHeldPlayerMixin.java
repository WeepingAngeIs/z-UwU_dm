package dev.uwu.dimension.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dimension.GuardianWarningMotion;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native player movement and the camera stay authoritative. The visible carried body shares its holder's hand clock. */
@Mixin(PlayerRenderer.class)
abstract class GuardianHeldPlayerMixin {
    @Inject(method="setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at=@At("HEAD"))
    private void uwu$turnWithPetalGuardian(AbstractClientPlayer player, PoseStack poses, float age, float yaw,
            float partial, float scale, CallbackInfo ci) {
        var guardian = GuardianWarningMotion.holderOnClient(player);
        if (guardian == null) return;
        float bearing = Mth.rotLerp(partial, guardian.yBodyRotO, guardian.yBodyRot);
        float elapsed = guardian.warningPhaseTicks(partial);
        Vec3 feet = GuardianWarningMotion.grip(guardian, bearing, elapsed, 1)
                .add(uwu$renderPosition(guardian, partial).subtract(guardian.position()));
        Vec3 offset = feet.subtract(uwu$renderPosition(player, partial));
        double waist = GuardianWarningMotion.PLAYER_GRIP_HEIGHT;
        poses.translate(offset.x/scale, (offset.y+waist)/scale, offset.z/scale);
        double direction = Math.toRadians(bearing);
        poses.mulPose(new Quaternionf().rotationAxis(GuardianWarningMotion.flipAngle(elapsed),
                (float)-Math.cos(direction), 0F, (float)-Math.sin(direction)));
        poses.translate(0, -waist/scale, 0);
    }

    @Unique private static Vec3 uwu$renderPosition(Entity entity, float partial) {
        return new Vec3(Mth.lerp((double)partial, entity.xOld, entity.getX()),
                Mth.lerp((double)partial, entity.yOld, entity.getY()),
                Mth.lerp((double)partial, entity.zOld, entity.getZ()));
    }
}
