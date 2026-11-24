package dev.uwu.weaponcheck.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface PairedMouse {
    @Invoker("onPress")
    void hotbarCheck$press(long window, int button, int action, int modifiers);

    @Invoker("onScroll")
    void hotbarCheck$scroll(long window, double horizontal, double vertical);
}
