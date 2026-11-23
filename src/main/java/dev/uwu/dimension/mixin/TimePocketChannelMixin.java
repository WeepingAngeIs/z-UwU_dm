package dev.uwu.dimension.mixin;

import com.mojang.blaze3d.audio.Channel;
import dev.uwu.dimension.client.TimePocketAcoustics.ChannelAir;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.EXTEfx;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** High frequencies lose more energy over distance. Preserve the channel's former EFX value. */
@Mixin(Channel.class)
abstract class TimePocketChannelMixin implements ChannelAir {
    @Shadow @Final private int source;
    @Unique private boolean uwu$pocketDamped;
    @Unique private float uwu$previousAir;

    @Override public void uwu$timePocketAir(boolean damped) {
        if (damped == uwu$pocketDamped || !ALC.getCapabilities().ALC_EXT_EFX) return;
        int property = EXTEfx.AL_AIR_ABSORPTION_FACTOR;
        float current = AL10.alGetSourcef(source, property);
        if (damped) {
            uwu$previousAir = current;
            AL10.alSourcef(source, property, 10F);
        } else if (current == 10F) {
            AL10.alSourcef(source, property, uwu$previousAir);
        }
        uwu$pocketDamped = damped;
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void uwu$restoreAir(CallbackInfo callback) { uwu$timePocketAir(false); }
}
