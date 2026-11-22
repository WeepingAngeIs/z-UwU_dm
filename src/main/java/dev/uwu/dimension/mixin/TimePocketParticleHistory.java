package dev.uwu.dimension.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface TimePocketParticleHistory {
    @Accessor("xo") void uwu$previousX(double x);
    @Accessor("yo") void uwu$previousY(double y);
    @Accessor("zo") void uwu$previousZ(double z);
}
