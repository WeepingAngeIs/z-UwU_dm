package dev.uwu.lotusclientcheck;

import dev.uwu.dimension.RevivalBlossomClient;
import dev.uwu.dimension.RevivalBlossoms;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/** Drives ordinary client movement and measures the shipped companion and its rendered lightmap. */
public final class BlossomFollowProbe {
    private enum Phase { ARRIVE, READY, WALK, RUN, TURN, STOP, RELOCATE, DISARM }

    private final Consumer<Consumer<ServerPlayer>> server;
    private final Method anchor;
    private final Method visible;
    private Phase phase = Phase.ARRIVE;
    private int phaseTicks;
    private volatile Vec3 start;
    private Vec3 previousOwner;
    private Vec3 previousBlossom;
    private Vec3 phaseOrigin;
    private Vec3 landing;
    private Vec3 stopBlossom;
    private BlockPos finalLightPosition;
    private float turnFrom;
    private double walkSpeed, sprintSpeed, walkLag, sprintLag, turnLag, maxTurnStep;
    private double stopLag, earlyStopStep, coastDistance, settledLag;
    private int sprintTicks, lightTicks, litTicks;
    private int dimmestPlayerLight = 15;
    private int dimmestBlossomLight = 15;
    private int disarmedTicks, framesBeforeDisarm;
    private static int submittedFrames;

    BlossomFollowProbe(Minecraft client, Consumer<Consumer<ServerPlayer>> server) throws ReflectiveOperationException {
        require(ModList.get().isLoaded("lambdynlights_runtime"), "LambDynamicLights runtime is required for this check");
        this.server = server;
        anchor = RevivalBlossomClient.class.getDeclaredMethod("anchor", Player.class, float.class);
        visible = Class.forName("dev.uwu.dimension.RevivalReady").getDeclaredMethod("visible", Player.class);
        anchor.setAccessible(true);
        visible.setAccessible(true);
        submittedFrames = 0;
        client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        server.accept(actor -> {
            BlockPos floor = actor.blockPosition().atY(200);
            var level = actor.serverLevel();
            for (int x = -5; x <= 16; x++) {
                for (int z = -5; z <= 24; z++) {
                    level.setBlock(floor.offset(x, 0, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            actor.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            actor.getFoodData().setFoodLevel(20);
            level.setDayTime(18000);
            level.setWeatherParameters(6000, 0, false, false);
            Vec3 pad = Vec3.atBottomCenterOf(floor.above());
            actor.teleportTo(pad.x, pad.y, pad.z);
            actor.fallDistance = 0;
            start = pad;
        });
    }

    boolean tick(Minecraft client) throws ReflectiveOperationException {
        var player = client.player;
        if (player == null || client.level == null || client.screen != null) return false;
        require(++phaseTicks <= 150, "Timed out during " + phase
                + " at " + player.position() + ", submitted blossom frames=" + submittedFrames);
        if (phase == Phase.ARRIVE) {
            if (start == null || player.distanceToSqr(start) > .04 || !player.onGround()) return false;
            player.setYRot(0);
            player.setXRot(0);
            require(light(client, player.getEyePosition()) == 0, "movement pad already has block light");
            server.accept(actor -> actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RevivalBlossoms.DIRK.get())));
            enter(Phase.READY);
            return false;
        }
        boolean ready = (boolean) visible.invoke(null, player);
        if (phase == Phase.DISARM) {
            if (ready) return false;
            if (++disarmedTicks == 6) framesBeforeDisarm = submittedFrames;
            if (disarmedTicks < 12) return false;
            int remainingLight = light(client, Vec3.atCenterOf(finalLightPosition));
            require(remainingLight == 0 && submittedFrames == framesBeforeDisarm,
                    "companion or light remained after unequipping");
            System.out.printf(Locale.ROOT, "BLOSSOM WALK RUN TURN STOP PASS: walk %.2f blocks/tick (%.2f blocks trail), "
                            + "sprint %.2f blocks/tick (%.2f blocks trail), sharp-turn trail %.2f blocks, "
                            + "coast %.2f blocks, settled error %.3f blocks, short teleport reset%n",
                    walkSpeed, walkLag, sprintSpeed, sprintLag, turnLag, coastDistance, settledLag);
            require(litTicks >= .95 * lightTicks && dimmestPlayerLight >= 4 && dimmestBlossomLight >= 8,
                    "moving light was weak too often: " + litTicks + "/" + lightTicks
                            + ", minimum by player=" + dimmestPlayerLight + ", by blossom=" + dimmestBlossomLight);
            System.out.printf(Locale.ROOT, "BLOSSOM RENDER AND LIGHT PASS: %d companion frames, "
                            + "%d/%d lit moving samples, minimum block light %d by player and %d by blossom, disarmed light %d%n",
                    submittedFrames, litTicks, lightTicks, dimmestPlayerLight, dimmestBlossomLight, remainingLight);
            return true;
        }
        if (phase == Phase.READY && !ready) return false;
        require(ready, "ready companion disappeared during " + phase);

        Vec3 owner = player.position();
        Vec3 goal = restingAnchor(player);
        Vec3 blossom = (Vec3) anchor.invoke(null, player, 1F);
        Vec3 lag = goal.subtract(blossom);
        double speed = previousOwner == null ? 0 : owner.subtract(previousOwner).horizontalDistance();
        double blossomStep = previousBlossom == null ? 0 : blossom.subtract(previousBlossom).horizontalDistance();
        previousOwner = owner;
        previousBlossom = blossom;
        require(blossom.subtract(owner).horizontalDistance() <= 3.50001 * player.getScale(),
                "companion moved beyond its travel radius");

        if (phase == Phase.WALK || phase == Phase.RUN || phase == Phase.TURN || phase == Phase.STOP) {
            lightTicks++;
            int ownerLight = light(client, player.getEyePosition());
            int blossomLight = light(client, blossom);
            dimmestPlayerLight = Math.min(dimmestPlayerLight, ownerLight);
            dimmestBlossomLight = Math.min(dimmestBlossomLight, blossomLight);
            if (blossomLight >= 9 && ownerLight >= 5) litTicks++;
        }
        switch (phase) {
            case READY -> {
                if (lag.length() > .025 || light(client, blossom) < 9 || submittedFrames < 3) return false;
                client.options.keyUp.setDown(true);
                enter(Phase.WALK);
            }
            case WALK -> {
                require(!player.isSprinting(), "ordinary walk unexpectedly sprinted");
                if (phaseTicks > 18) {
                    walkSpeed += speed / 6;
                    walkLag += lag.z / 6;
                }
                if (phaseTicks < 24) return false;
                require(walkSpeed > .15 && walkLag > 1, "walking did not build a visible trail: " + walkSpeed + ", " + walkLag);
                client.options.keySprint.setDown(true);
                enter(Phase.RUN);
            }
            case RUN -> {
                if (player.isSprinting()) sprintTicks++;
                if (phaseTicks > 22) {
                    sprintSpeed += speed / 6;
                    sprintLag += lag.z / 6;
                }
                if (phaseTicks < 28) return false;
                require(sprintTicks >= 12 && sprintSpeed > walkSpeed + .035 && sprintLag > walkLag + .25,
                        "sprint did not add trail: " + sprintTicks + " sprint ticks, speed " + sprintSpeed + ", lag " + sprintLag);
                phaseOrigin = owner;
                turnFrom = player.yBodyRot;
                player.setYRot(-90);
                enter(Phase.TURN);
            }
            case TURN -> {
                double degrees = Math.abs(Mth.wrapDegrees(player.yBodyRot - turnFrom));
                if (degrees >= 45 && turnLag == 0) turnLag = lag.horizontalDistance();
                maxTurnStep = Math.max(maxTurnStep, blossomStep);
                if (phaseTicks < 21) return false;
                require(degrees >= 70 && owner.x - phaseOrigin.x > 3 && turnLag > 1 && maxTurnStep < .55,
                        "turn failed: heading change=" + degrees + ", trail=" + turnLag + ", max blossom step=" + maxTurnStep);
                stopBlossom = blossom;
                stopLag = lag.horizontalDistance();
                release(client);
                enter(Phase.STOP);
            }
            case STOP -> {
                if (phaseTicks <= 3) earlyStopStep = Math.max(earlyStopStep, blossomStep);
                if (phaseTicks == 5) coastDistance = blossom.subtract(stopBlossom).horizontalDistance();
                if (phaseTicks == 12) require(blossomStep < earlyStopStep * .65, "companion did not slow after coasting");
                if (phaseTicks < 42) return false;
                settledLag = lag.length();
                require(stopLag > 1 && coastDistance > .45 && settledLag < .035 && speed < .02,
                        "stop failed: initial trail=" + stopLag + ", coast=" + coastDistance + ", settled error=" + settledLag);
                landing = owner.add(-2.75, 0, 0);
                server.accept(actor -> actor.teleportTo(landing.x, landing.y, landing.z));
                enter(Phase.RELOCATE);
            }
            case RELOCATE -> {
                if (owner.distanceToSqr(landing) > .04) return false;
                Vec3 interpolationStart = (Vec3) anchor.invoke(null, player, 0F);
                require(lag.length() < .12 && interpolationStart.distanceTo(blossom) < .12,
                        "short server teleport left a visible trail from the old position");
                finalLightPosition = BlockPos.containing(blossom);
                server.accept(actor -> actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
                enter(Phase.DISARM);
            }
            default -> throw new AssertionError(phase);
        }
        return false;
    }

    public static void submitted() {
        submittedFrames++;
    }

    static void release(Minecraft client) {
        client.options.keyUp.setDown(false);
        client.options.keySprint.setDown(false);
    }

    private void enter(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    private static int light(Minecraft client, Vec3 where) {
        return LightTexture.block(LevelRenderer.getLightColor(client.level, BlockPos.containing(where)));
    }

    private static Vec3 restingAnchor(Player player) {
        // Fixed Playtest 9 resting offset, used as the comparison for the moving result.
        double yaw = Math.toRadians(player.yBodyRot);
        double x = .55 * Math.sin(yaw) - 1.05 * Math.cos(yaw);
        double z = -.55 * Math.cos(yaw) - 1.05 * Math.sin(yaw);
        return player.position().add(x * player.getScale(), player.getBbHeight() * 1.15, z * player.getScale());
    }

    private static void require(boolean ok, String problem) {
        if (!ok) throw new AssertionError(problem);
    }
}
