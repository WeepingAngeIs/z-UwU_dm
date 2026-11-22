package dev.uwu.dimension;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Poses use the six shipped pivots. Model-only lifts keep visitors and navigation grounded. */
final class GuardianGreetingMotion {
    private static float ease(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float beat(float tick, float start, float peak, float end) {
        return tick <= peak ? ease((tick-start)/(peak-start)) : 1-ease((tick-peak)/(end-peak));
    }

    static void body(GuardianGreeting g, float t, PoseStack pose, float scale) {
        switch (g) {
            case TWIRL -> {
                float sway = beat(t, 1, 10, 21) - beat(t, 21, 32, 42);
                pose.translate(.095 * sway * scale, .055 * Math.abs(sway) * scale, 0);
                pose.mulPose(Axis.YP.rotationDegrees(42 * sway));
                pose.mulPose(Axis.ZP.rotationDegrees(5 * sway));
            }
            case SPIN_BOW -> {
                pose.mulPose(Axis.YP.rotationDegrees(360 * ease((t - 2) / 24)));
                float bow = beat(t, 27, 35, 47);
                pose.translate(0, scale, 0);
                pose.mulPose(Axis.XP.rotationDegrees(-20 * bow));
                pose.translate(0, -scale, 0);
            }
            case JACKS -> {
                float lift = beat(t, 3, 11, 19) + beat(t, 21, 29, 37);
                pose.translate(0, .34 * scale * lift, 0);
            }
            default -> { }
        }
    }

    static void limbs(GuardianGreeting g, float t, ModelPart head, ModelPart left, ModelPart right,
                      ModelPart footL, ModelPart footR) {
        switch (g) {
            case WAVE -> {
                float up = ease(t / 7) * (1 - ease((t - 28) / 8));
                right.zRot += (2.18F + .22F * Mth.sin(t * .63F)) * up;
                right.xRot -= .40F * up;
                head.zRot += .09F * up;
            }
            case JACKS -> {
                float out = beat(t, 3, 11, 19) + beat(t, 21, 29, 37);
                left.zRot -= 2.05F * out;
                right.zRot += 2.05F * out;
                footL.zRot -= .38F * out;
                footR.zRot += .38F * out;
            }
            case SPIN_BOW -> {
                float open = beat(t, 1, 14, 28), bow = beat(t, 27, 35, 47);
                left.zRot -= .64F * open;
                right.zRot += .64F * open;
                right.xRot -= .86F * bow;
                head.xRot += .30F * bow;
            }
            case MEME_WINK, WINK -> {
                float tilt = beat(t, 2, 15, 31);
                head.zRot += (g == GuardianGreeting.MEME_WINK ? .29F : .16F) * tilt;
                head.yRot -= .13F * tilt;
                right.zRot += .50F * tilt;
                right.xRot -= (g == GuardianGreeting.MEME_WINK ? .70F : .32F) * tilt;
            }
            case TWIRL -> {
                float a = beat(t, 1, 10, 21), b = beat(t, 21, 32, 42);
                left.zRot -= .78F * a + .22F * b;
                right.zRot += .78F * b + .22F * a;
                footL.xRot -= .28F * a;
                footR.xRot -= .28F * b;
                head.zRot += .13F * (a - b);
            }
            case KISS -> {
                float kiss = beat(t, 1, 12, 34), send = beat(t, 12, 22, 38);
                right.xRot -= 1.34F * kiss + .70F * send;
                right.zRot += .24F * send;
                head.xRot -= .12F * send;
                head.zRot += .14F * kiss;
            }
            default -> { }
        }
    }

    private GuardianGreetingMotion() {}
}
