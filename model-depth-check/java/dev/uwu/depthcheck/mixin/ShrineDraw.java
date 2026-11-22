package dev.uwu.depthcheck.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.depthcheck.DepthClient;
import dev.uwu.dimension.SeatedCupidStatueVisual;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SeatedCupidStatueVisual.class)
abstract class ShrineDraw {
    @Inject(method="draw", at=@At("RETURN"), remap=false)
    private static void depth$draw(PoseStack pose, MultiBufferSource buffers, int light, float yaw, CallbackInfoReturnable<Boolean> result) {
        if (Boolean.TRUE.equals(result.getReturnValue())) DepthClient.shrineDrawn();
    }
}
