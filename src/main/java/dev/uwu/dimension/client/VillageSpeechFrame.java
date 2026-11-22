package dev.uwu.dimension.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.uwu.dimension.BlossomWarden;
import dev.uwu.dimension.GuardianGreeting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** World-space NPC lettering with a shared pixel-art frame. Only the trims are textured. */
public final class VillageSpeechFrame {
    enum Style {
        LOTUS(0xA75472, 0xFFDAE8), LEAF(0x577B58, 0xE6F5D9),
        DUSK(0x695478, 0xEDE2F8), PLAIN(0x74717A, 0xFFF9F1);
        final int border, paper;
        Style(int border, int paper) { this.border = border; this.paper = paper; }
    }

    private static final ResourceLocation TRIM = ResourceLocation.fromNamespaceAndPath("uwudimension", "textures/gui/village_speech_trim.png");
    private static final Component WELCOME = Component.translatable("message.uwudimension.guardian_welcome");
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    public static void welcome(BlossomWarden guard, float partial, PoseStack poses, MultiBufferSource buffers) {
        var mc = Minecraft.getInstance();
        GuardianGreeting gesture = guard.greeting();
        if (gesture == GuardianGreeting.NONE || mc.player == null || mc.options.hideGui || !guard.isAlive()
                || guard.isInvisibleTo(mc.player) || mc.player.distanceToSqr(guard) > 32 * 32) return;
        float age = guard.greetingTicks(partial);
        if (age < 0 || age >= gesture.duration) return;
        Style style = !guard.female() ? Style.LEAF : guard.duskGuardian() ? Style.PLAIN : Style.LOTUS;
        poses.pushPose();
        // The bouncing bulky Guardian can lift by 0.34 blocks. Keep the pointer above his cap.
        poses.translate(0, guard.getBbHeight() + (guard.female() ? .38 : .46) * guard.getScale(), 0);
        draw(WELCOME, style, Mth.clamp((gesture.duration - age) / 4F, 0, 1), poses, buffers);
        poses.popPose();
    }

    static void draw(Component text, Style style, float fade, PoseStack poses, MultiBufferSource buffers) {
        if (fade < .02F) return;
        var mc = Minecraft.getInstance();
        var font = mc.font;
        var lines = font.split(text, 220);
        int width = lines.stream().mapToInt(font::width).max().orElse(1);
        int height = lines.size() * 10 + 12;
        float left = -width / 2F - 7, right = width / 2F + 7;
        float bottom = -7, top = bottom - height;
        int opacity = Math.round(255 * fade) << 24;
        poses.mulPose(mc.gameRenderer.getMainCamera().rotation());
        poses.scale(.025F, -.025F, .025F);

        var panel = buffers.getBuffer(RenderType.textBackground());
        int border = opacity | style.border, paper = opacity | style.paper;
        rounded(panel, poses, left - 1, top - 1, right + 1, bottom + 1, border);
        rectangle(panel, poses, -3, bottom, 3, -4, border);
        rectangle(panel, poses, -2, -4, 2, -2, border);
        rectangle(panel, poses, -1, -2, 1, 0, border);
        poses.translate(0, 0, .045);
        rounded(panel, poses, left, top, right, bottom, paper);
        rectangle(panel, poses, -2, bottom - 1, 2, -5, paper);
        rectangle(panel, poses, -1, -5, 1, -4, paper);

        poses.translate(0, 0, .06);
        if (style != Style.PLAIN) {
            var trim = buffers.getBuffer(RenderType.text(TRIM));
            int white = opacity | 0xFFFFFF;
            switch (style) {
                case LOTUS -> {
                    sprite(trim, poses, left - 5, top - 6, 0, 0, 13, 9, false, white);
                    sprite(trim, poses, right - 8, bottom - 6, 0, 0, 13, 9, false, white);
                    sprite(trim, poses, right - 3, top - 2, 20, 17, 6, 5, true, white);
                }
                case LEAF -> {
                    sprite(trim, poses, left - 4, top - 2, 16, 0, 7, 13, false, white);
                    sprite(trim, poses, right - 3, bottom - 5, 16, 0, 7, 13, true, white);
                }
                case DUSK -> {
                    sprite(trim, poses, left - 3, top - 4, 0, 16, 7, 8, false, white);
                    sprite(trim, poses, right - 2, bottom - 3, 10, 16, 5, 5, false, white);
                }
                default -> { }
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            font.drawInBatch(line, -font.width(line) / 2F, top + 6 + i * 10, opacity | 0x111014,
                    false, poses.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LIGHT);
        }
    }

    private static void rounded(VertexConsumer out, PoseStack p, float l, float t, float r, float b, int color) {
        rectangle(out, p, l + 2, t, r - 2, b, color);
        rectangle(out, p, l, t + 2, l + 2, b - 2, color);
        rectangle(out, p, r - 2, t + 2, r, b - 2, color);
    }

    private static void rectangle(VertexConsumer out, PoseStack p, float l, float t, float r, float b, int color) {
        var m = p.last().pose();
        out.addVertex(m, l, b, 0).setColor(color).setLight(LIGHT);
        out.addVertex(m, r, b, 0).setColor(color).setLight(LIGHT);
        out.addVertex(m, r, t, 0).setColor(color).setLight(LIGHT);
        out.addVertex(m, l, t, 0).setColor(color).setLight(LIGHT);
    }

    private static void sprite(VertexConsumer out, PoseStack p, float x, float y, int u, int v,
                               int w, int h, boolean mirror, int color) {
        float u0 = (mirror ? u + w : u) / 32F, u1 = (mirror ? u : u + w) / 32F;
        float v0 = v / 32F, v1 = (v + h) / 32F;
        var m = p.last().pose();
        out.addVertex(m, x, y+h, 0).setColor(color).setUv(u0,v1).setLight(LIGHT);
        out.addVertex(m, x+w, y+h, 0).setColor(color).setUv(u1,v1).setLight(LIGHT);
        out.addVertex(m, x+w, y, 0).setColor(color).setUv(u1,v0).setLight(LIGHT);
        out.addVertex(m, x, y, 0).setColor(color).setUv(u0,v0).setLight(LIGHT);
    }

    private VillageSpeechFrame() {}
}
