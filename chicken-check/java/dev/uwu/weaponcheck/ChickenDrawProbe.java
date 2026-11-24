package dev.uwu.weaponcheck;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.ModelResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="dev.uwu.dimension.ChickenCoatArt", remap=false)
abstract class ChickenDrawProbe {
    @Inject(method="draw",at=@At("HEAD"),remap=false)
    private static void record(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
            ModelResourceLocation id, CallbackInfo ci) {
        ChickenGallery.drawn(id);
    }
}
