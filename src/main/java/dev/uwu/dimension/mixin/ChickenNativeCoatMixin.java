package dev.uwu.dimension.mixin;

import dev.uwu.dimension.ChickenCoatArt;
import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Chicken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChickenRenderer.class)
abstract class ChickenNativeCoatMixin {
    @Inject(method = "getTextureLocation(Lnet/minecraft/world/entity/animal/Chicken;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"), cancellable = true)
    private void uwu$coat(Chicken mob, CallbackInfoReturnable<ResourceLocation> result) {
        var texture = ChickenCoatArt.texture(mob);
        if (texture != null) result.setReturnValue(texture);
    }
}
