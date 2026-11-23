package dev.uwu.dimension.mixin;

import dev.uwu.dimension.client.TimePocketClient;
import net.minecraft.client.particle.TrackingEmitter;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TrackingEmitter.class)
abstract class TimePocketEmitterMixin {
    @Shadow @Final private Entity entity;

    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void uwu$pauseEmission(CallbackInfo ci) {
        var field=TimePocketClient.field(entity.level());
        if(field!=null && field.at(entity.getX(),entity.getZ()))ci.cancel();
    }
}
