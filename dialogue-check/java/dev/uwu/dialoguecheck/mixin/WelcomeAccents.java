package dev.uwu.dialoguecheck.mixin;

import dev.uwu.dialoguecheck.WelcomeCheck;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class WelcomeAccents {
    @Inject(method="handleParticleEvent",at=@At("TAIL"))
    private void heart(ClientboundLevelParticlesPacket p, CallbackInfo ci) {
        if(p.getParticle().getType()==ParticleTypes.HEART && WelcomeCheck.near(p.getX(),p.getY(),p.getZ())) WelcomeCheck.heart();
    }
    @Inject(method="handleSoundEvent",at=@At("TAIL"))
    private void ding(ClientboundSoundPacket p, CallbackInfo ci) {
        if(p.getSound().value()==SoundEvents.EXPERIENCE_ORB_PICKUP && WelcomeCheck.near(p.getX(),p.getY(),p.getZ())) WelcomeCheck.ding();
    }
}
