package dev.uwu.pocketcheck.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.pocketcheck.PocketClientProbe;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
abstract class EntityPose {
    @Inject(method="render",at=@At("HEAD"))
    private void pocketCheck$pose(Entity entity,double x,double y,double z,float yaw,float partial,PoseStack pose,
            MultiBufferSource buffers,int light,CallbackInfo callback) {
        PocketClientProbe.pose(entity,partial);
    }
}

