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
    private final Field strength;
    private final Path evidence = Path.of(System.getProperty("uwu.check.evidence"));
    private final int start;
    private int phase;

    CupidCharmOverlayProbe(Minecraft client, Consumer<Consumer<ServerPlayer>> server) throws Exception {
        this.server = server;
        packet = Class.forName("dev.uwu.dimension.CupidMinions$CharmView").getDeclaredConstructor(int.class);
        packet.setAccessible(true);
        strength = Class.forName("dev.uwu.dimension.CupidCharmOverlay").getDeclaredField("strength");
        strength.setAccessible(true);
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
            phase++;
        } else if (phase == 2 && tick >= 52) {
            require(alpha > .99F, "charm overlay did not stay active");
            capture(client, "03-drift");
            send(0);
            phase++;
        } else if (phase == 3 && tick >= 61) {
            require(alpha > .15F && alpha < .85F, "release did not fade the overlay");
            capture(client, "04-fading");
            phase++;
        } else if (phase == 4 && tick >= 81) {
            require(alpha == 0, "released player still has an overlay");
            capture(client, "05-released");
            System.out.println("CUPID CHARM OVERLAY PASS targeted native packet, steady state, fade and clear");
            return true;
        }
        return false;
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
