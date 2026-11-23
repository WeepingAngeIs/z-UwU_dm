package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.uwu.dimension.client.TimePocketClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
abstract class TimePocketParticleMixin {
    @WrapOperation(method="render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/particle/Particle;render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V"))
    private void uwu$heldParticleFrame(Particle p, VertexConsumer out, Camera camera, float partial, Operation<Void> render) {
        var field=TimePocketClient.field(Minecraft.getInstance().level);
        if(field!=null) {
            var pos=p.getPos();
            if(field.at(pos.x,pos.z)) partial=1F;
        }
        render.call(p,out,camera,partial);
    }

    @Inject(method="tickParticle",at=@At("HEAD"),cancellable=true)
    private void uwu$holdParticle(Particle p,CallbackInfo ci) {
        var f=TimePocketClient.field(Minecraft.getInstance().level);
        if (f==null) return;
        var at=p.getPos();
        if(f.at(at.x,at.z)) {
            var previous=(TimePocketParticleHistory)p;
            previous.uwu$previousX(at.x); previous.uwu$previousY(at.y); previous.uwu$previousZ(at.z);
            ci.cancel();
        }
    }
}
