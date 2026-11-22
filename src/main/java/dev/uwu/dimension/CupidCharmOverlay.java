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
        glow(graphics, width, height, visibility);
        hearts(graphics, width, height, age + partial, visibility);
    }

    private static void glow(GuiGraphics graphics, int width, int height, float opacity) {
        graphics.fill(0, 0, width, height, color(0xf3add4, opacity * .36F));
        int reach = Math.min(width, height) * 48 / 100;
        int rings = Math.min(80, reach);
        for (int ring = 0; ring < rings; ring++) {
            int outer = ring * reach / rings;
            int inner = (ring + 1) * reach / rings;
            float t = (ring + .5F) / rings;
            float light = 1F - ease(t);
            int green = Math.round(Mth.lerp(light, 159, 223));
            int blue = Math.round(Mth.lerp(light, 204, 243));
            int pink = (255 << 16) | (green << 8) | blue;
            int tint = color(pink, opacity * light * .98F);
            // Non-overlapping strips give each corner the same opacity as its adjacent edges.
            graphics.fill(outer, outer, width - outer, inner, tint);
            graphics.fill(outer, height - inner, width - outer, height - outer, tint);
            graphics.fill(outer, inner, inner, height - inner, tint);
            graphics.fill(width - inner, inner, width - outer, height - inner, tint);
        }
    }

    private static void hearts(GuiGraphics graphics, int width, int height, float time, float opacity) {
        float scale = Math.max(.5F, Math.min(width, height) / 240F);
        for (int i = 0; i < 32; i++) {
            boolean near = i >= 20;
            int lane = near ? i - 20 : i;
            int lanes = near ? 12 : 20;
            float period = (near ? 132F : 210F) + (i % 5) * 10F;
            float life = ((time + i * 47) % period) / period;
            float fade = ease(Math.min(1F, Math.min(life, 1F - life) * 6F));
            int size = Math.max(9, Math.round(((near ? 17 : 9) + (i % 4) * 4) * scale));
            float column = (lane + .5F) / lanes;
            float drift = scale * (near ? 10F : 5F) * Mth.sin(life * Mth.TWO_PI + i);
            float x = width * column + drift - size / 2F;
            float y = Mth.lerp(life, height + size * .5F, -size);
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(x, y, 0);
            heart(graphics, 0, 0, size, opacity * fade * (near ? 1F : .64F), near);
            pose.popPose();
        }
    }

    private static void heart(GuiGraphics graphics, int x, int y, int size, float opacity, boolean near) {
        for (int row = 0; row < LEFT.length; row++) {
            int y0 = y + row * size / 11;
            int y1 = y + (row + 1) * size / 11;
            if (row < 2) {
                band(graphics, x, y0, y1, size, LEFT[row], 3, row, opacity, near);
                band(graphics, x, y0, y1, size, 7, RIGHT[row], row, opacity, near);
            } else {
                band(graphics, x, y0, y1, size, LEFT[row], RIGHT[row], row, opacity, near);
            }
        }
    }

    private static void band(GuiGraphics graphics, int x, int y0, int y1, int size, int left, int right,
            int row, float opacity, boolean near) {
        int x0 = x + left * size / 11;
        int x1 = x + (right + 1) * size / 11;
        int pink = near ? (row < 3 ? 0xffeef8 : row < 6 ? 0xffc7e4 : 0xfba0d1)
                : (row < 5 ? 0xe887bf : 0xe178b0);
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, color(0xffc1dd, opacity * .16F));
        graphics.fill(x0, y0, x1, y1, color(pink, opacity * .7F));
    }

    private static int color(int rgb, float opacity) {
        return Math.round(opacity * 255) << 24 | rgb;
    }

    private static float ease(float t) {
        return t * t * (3F - 2F * t);
    }

    private CupidCharmOverlay() {}
}
