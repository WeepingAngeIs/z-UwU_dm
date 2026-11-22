package dev.uwu.weaponcheck.mixin;

import dev.uwu.weaponcheck.BladeModelProbe;
import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only the fixture's paired GUI captures use a fixed native glint clock. */
@Mixin(RenderStateShard.class)
public abstract class GuiGlintClock {
    @Redirect(method = "setupGlintTexturing(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;getMillis()J"))
    private static long uwu$pairedGuiClock() { return BladeModelProbe.glintMillis(); }
}
