package dev.uwu.lotusclientcheck;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Captures the real HUD following receipt and removal of an authentic charm-view payload. */
final class CupidCharmOverlayProbe {
    private final Consumer<Consumer<ServerPlayer>> server;
    private final Constructor<?> packet;
    private final Constructor<?> control;
    private final Field strength;
    private final Field age;
    private final Path evidence = Path.of(System.getProperty("uwu.check.evidence"));
    private final int start;
    private int phase;
    private int lastSteeringTick, steeringSent, steeringReceived, lastAge;
    private double lastStop;

    CupidCharmOverlayProbe(Minecraft client, Consumer<Consumer<ServerPlayer>> server) throws Exception {
        this.server = server;
        packet = Class.forName("dev.uwu.dimension.CupidMinions$CharmView").getDeclaredConstructor(int.class);
        packet.setAccessible(true);
        control = Class.forName("dev.uwu.dimension.CupidMinions$Control")
                .getDeclaredConstructor(boolean.class, double.class, double.class, double.class, double.class);
        control.setAccessible(true);
        strength = Class.forName("dev.uwu.dimension.CupidCharmOverlay").getDeclaredField("strength");
        strength.setAccessible(true);
        age = strength.getDeclaringClass().getDeclaredField("age");
        age.setAccessible(true);
        Files.createDirectories(evidence);
        client.options.setCameraType(CameraType.FIRST_PERSON);
        client.player.setXRot(-22);
        start = client.player.tickCount;
    }

    boolean frame(Minecraft client) throws Exception {
        int tick = client.player.tickCount - start;
        float alpha = strength.getFloat(null);
        if (phase == 0 && tick >= 8) {
            require(alpha == 0, "unaffected player received overlay");
            capture(client, "01-unaffected");
            send(120);
            phase++;
        } else if (phase == 1 && tick >= 26) {
            require(alpha > .99F, "targeted packet did not fade the overlay in");
            capture(client, "02-charmed");
            lastAge = age.getInt(null);
            phase++;
        } else if (phase == 2 && tick < 52 && tick >= lastSteeringTick + 2) {
            require(alpha > .99F, "steering update interrupted the glow");
            int currentAge = age.getInt(null);
            require(currentAge > lastAge, "steering update restarted the rising hearts");
            lastAge = currentAge;
            var state = client.player.getPersistentData().getCompound("uwudimension.cupid_control");
            double stop = state.getDouble("Stop");
            if (stop > lastStop) steeringReceived++;
            lastStop = stop;
            lastSteeringTick = tick;
            double nextStop = 4 + ++steeringSent / 100D;
            server.accept(player -> {
                var toward = player.position().add(player.getLookAngle().scale(2)).add(0, -.4, 0);
                sendControl(player, true, toward.x, toward.y, toward.z, nextStop);
            });
        } else if (phase == 2 && tick >= 52) {
            require(alpha > .99F, "charm overlay did not stay active");
            require(steeringReceived >= 6, "too few steering updates reached the real client: " + steeringReceived);
            capture(client, "03-drift");
            server.accept(player -> sendControl(player, false, 0, 0, 0, 0));
            send(0);
            phase++;
        } else if (phase == 3 && tick >= 61) {
            require(alpha > .15F && alpha < .85F, "release did not fade the overlay");
            capture(client, "04-fading");
            phase++;
        } else if (phase == 4 && tick >= 81) {
            require(alpha == 0, "released player still has an overlay");
            require(!client.player.getPersistentData().contains("uwudimension.cupid_control"),
                    "steering did not clear with the overlay");
            capture(client, "05-released");
            System.out.println("CUPID CHARM OVERLAY PASS targeted native packet, continuous animation through "
                    + steeringReceived + " steering updates, fade and clear");
            return true;
        }
        return false;
    }

    private void sendControl(ServerPlayer player, boolean active, double x, double y, double z, double stop) {
        try {
            PacketDistributor.sendToPlayer(player, (CustomPacketPayload) control.newInstance(active, x, y, z, stop));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private void send(int ticks) throws ReflectiveOperationException {
        var view = (CustomPacketPayload) packet.newInstance(ticks);
        server.accept(player -> PacketDistributor.sendToPlayer(player, view));
    }

    private void capture(Minecraft client, String name) throws Exception {
        try (var pixels = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            pixels.writeToFile(evidence.resolve(name + ".png"));
        }
    }

    private static void require(boolean ok, String failure) {
        if (!ok) throw new AssertionError(failure);
    }
}
