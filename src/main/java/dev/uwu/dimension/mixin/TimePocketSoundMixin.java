package dev.uwu.dimension.mixin;

import dev.uwu.dimension.client.TimePocketClient;
import dev.uwu.dimension.client.TimePocketAcoustics.ChannelAir;
import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
abstract class TimePocketSoundMixin {
    @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Inject(method="updateCategoryVolume",at=@At("RETURN"))
    private void uwu$refreshAir(SoundSource source, float volume, CallbackInfo callback) {
        if (source == SoundSource.MASTER) return;
        instanceToChannel.forEach((sound, handle) -> {
            if (TimePocketClient.ordinary(sound.getSource())) {
                boolean damped = TimePocketClient.gain(sound.getSource()) < 1;
                handle.execute(channel -> ((ChannelAir) channel).uwu$timePocketAir(damped));
            }
        });
    }

    @Inject(method="calculateVolume(FLnet/minecraft/sounds/SoundSource;)F",at=@At("RETURN"),cancellable=true)
    private void uwu$duck(float volume,SoundSource source,CallbackInfoReturnable<Float> ci) {
        float gain=TimePocketClient.gain(source);
        if(gain!=1)ci.setReturnValue(ci.getReturnValueF()*gain);
    }
}
