package dev.uwu.dimension.mixin;

import dev.uwu.dimension.client.TimePocketClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
abstract class TimePocketParticleMixin {
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
