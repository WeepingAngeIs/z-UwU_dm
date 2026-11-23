package dev.uwu.dimension.mixin;

import dev.uwu.dimension.TimePocket;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
abstract class TimePocketEntityRenderMixin {
    // The world keeps rendering between ticks. Pin the animation fraction as well as the tick counter.
    @ModifyVariable(method = "renderEntity", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float uwu$heldPose(float partial, Entity entity) {
        return TimePocket.suspended(entity) ? 1F : partial;
    }
}
