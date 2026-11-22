package dev.uwu.dimension;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** The bow victim's first-person charm effect, drawn underneath the HUD. */
@EventBusSubscriber(modid = "uwudimension", value = Dist.CLIENT)
public final class CupidCharmOverlay {
    private static final int[] LEFT = {1, 0, 0, 0, 1, 2, 3, 4, 5};
    private static final int[] RIGHT = {9, 10, 10, 10, 9, 8, 7, 6, 5};
    private static LocalPlayer owner;
    private static ClientLevel world;
    private static float previous, strength;
    private static int age;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != owner || mc.level != world) {
            owner = mc.player;
            world = mc.level;
            previous = strength = age = 0;
        }
        previous = strength;
        boolean charmed = owner != null && world != null && owner.isAlive() && !owner.isSpectator()
                && owner.getPersistentData().getLong(CupidMinions.VIEW_UNTIL) > world.getGameTime();
        strength = Mth.clamp(strength + (charmed ? 1F / 12 : -1F / 20), 0F, 1F);
        if (strength > 0 || previous > 0) age++;
        else age = 0;
    }

    @SubscribeEvent
    public static void draw(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player != owner || mc.level != world || !owner.isAlive() || owner.isSpectator()
                || mc.getCameraEntity() != owner || !mc.options.getCameraType().isFirstPerson() || mc.options.hideGui) return;
        float partial = Mth.clamp(event.getPartialTick().getGameTimeDeltaPartialTick(false), 0F, 1F);
        float visibility = ease(Mth.lerp(partial, previous, strength));
        if (visibility <= 0F) return;
        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        int top = 48, bottom = height - 56;
        if (width < 150 || bottom - top < 28) return;
        for (int i = 0; i < 8; i++) {
            float period = 110 + (i % 4) * 13;
            float life = ((age + partial + i * 29) % period) / period;
            float fade = ease(Math.min(1F, Math.min(life, 1F - life) * 5F));
            int size = 11 + (i % 4) * 3;
            int inset = 8 + (i % 3) * 8 + Math.round(3 * Mth.sin(life * Mth.TWO_PI + i));
            int x = i % 2 == 0 ? inset : width - inset - size;
            int y = Math.round(Mth.lerp(life, bottom - size, top));
            heart(graphics, x, y, size, visibility * fade);
        }
    }

    private static void heart(GuiGraphics graphics, int x, int y, int size, float opacity) {
        for (int row = 0; row < LEFT.length; row++) {
            int y0 = y + row * size / 11;
            int y1 = y + (row + 1) * size / 11;
            if (row < 2) {
                band(graphics, x, y0, y1, size, LEFT[row], 3, row, opacity);
                band(graphics, x, y0, y1, size, 7, RIGHT[row], row, opacity);
            } else {
                band(graphics, x, y0, y1, size, LEFT[row], RIGHT[row], row, opacity);
            }
        }
    }

    private static void band(GuiGraphics graphics, int x, int y0, int y1, int size, int left, int right,
            int row, float opacity) {
        int x0 = x + left * size / 11;
        int x1 = x + (right + 1) * size / 11;
        int pink = row < 3 ? 0xffdae9 : row < 6 ? 0xffbfd9 : 0xfba9cf;
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, color(0xffc1dd, opacity * .11F));
        graphics.fill(x0, y0, x1, y1, color(pink, opacity * .5F));
    }

    private static int color(int rgb, float opacity) {
        return Math.round(opacity * 255) << 24 | rgb;
    }

    private static float ease(float t) {
        return t * t * (3F - 2F * t);
    }

    private CupidCharmOverlay() {}
}
