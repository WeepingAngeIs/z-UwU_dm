package dev.uwu.lotusclientcheck.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dimension.BlossomWarden;
import dev.uwu.dimension.BlossomWardenRenderer;
import dev.uwu.lotusclientcheck.GuardianCamera;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measure the real rendered mesh's up vector before and after the renderer's body transform. */
@Mixin(value=BlossomWardenRenderer.class,remap=false)
abstract class GuardianFlipObserver {
    @Unique private Matrix4f uwu$incoming;
    @Inject(method="setupRotations(Ldev/uwu/dimension/BlossomWarden;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",at=@At("HEAD"))
    private void uwu$before(BlossomWarden guard, PoseStack stack, float age, float yaw, float partial,float scale,CallbackInfo ci) {
        if (GuardianCamera.recording(guard)) uwu$incoming=new Matrix4f(stack.last().pose());
    }
    @Inject(method="setupRotations(Ldev/uwu/dimension/BlossomWarden;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",at=@At("RETURN"))
    private void uwu$after(BlossomWarden guard, PoseStack stack, float age, float yaw, float partial,float scale,CallbackInfo ci) {
        if (uwu$incoming==null) return;
        Vector3f up=uwu$incoming.invert().mul(stack.last().pose()).transformDirection(new Vector3f(0,1,0)).normalize();
        GuardianCamera.observeUp(guard,up.y);
        uwu$incoming=null;
    }
}
