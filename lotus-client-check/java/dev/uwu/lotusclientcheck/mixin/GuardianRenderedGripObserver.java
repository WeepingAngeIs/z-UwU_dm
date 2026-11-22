package dev.uwu.lotusclientcheck.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.lotusclientcheck.GuardianGripFrame;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Capture each real model after all native/pre-render transforms and animation, before its skin draw. */
@Mixin(LivingEntityRenderer.class)
abstract class GuardianRenderedGripObserver {
    @Inject(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;isBodyVisible(Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private void uwu$modelContact(LivingEntity entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light,CallbackInfo ci){
        if(!GuardianGripFrame.active())return;
        GuardianGripFrame.draw(entity,((LivingEntityRenderer<?,?>)(Object)this).getModel(),poses);
    }
}
