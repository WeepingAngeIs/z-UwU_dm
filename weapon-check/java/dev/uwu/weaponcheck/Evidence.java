package dev.uwu.weaponcheck;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

final class Evidence {
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    static final Path OUTPUT = Path.of(System.getProperty("uwu.check.evidence"));
    static final Path CONTROL = Path.of(System.getProperty("uwu.check.control"));
    static final boolean BASELINE = Boolean.getBoolean("uwu.check.baseline");
    static final boolean OBSERVER = System.getProperty("uwu.check.role").equals("observer");

    static void initialize() throws Exception {
        Files.createDirectories(OUTPUT);
        Files.createDirectories(CONTROL);
        json("identity.json", Map.of("role", OBSERVER ? "observer" : "host", "baseline", BASELINE,
                "jar", System.getProperty("uwu.check.candidateJar"),
                "sha256", System.getProperty("uwu.check.candidateSha256")));
    }

    static void json(String name, Object value) throws Exception {
        Files.writeString(OUTPUT.resolve(name), JSON.toJson(value) + "\n");
    }

    static void line(String name, String value) throws Exception {
        Files.writeString(OUTPUT.resolve(name), value + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    static void signal(String name, Object value) throws Exception {
        Path temp = CONTROL.resolve(name + ".tmp");
        Files.writeString(temp, JSON.toJson(value));
        Files.move(temp, CONTROL.resolve(name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    static <T> T read(String name, Class<T> type) throws Exception {
        Path file = CONTROL.resolve(name);
        return Files.exists(file) ? JSON.fromJson(Files.readString(file), type) : null;
    }

    static void screenshot(String name) throws Exception {
        require(name.matches("[a-zA-Z0-9_-]+"), "Unsafe screenshot name");
        Minecraft client = Minecraft.getInstance();
        try (NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(OUTPUT.resolve(name + ".png"));
        }
        var camera = client.gameRenderer.getMainCamera();
        line("frames.tsv", name + "\t" + client.player.tickCount + "\t" + client.options.getCameraType()
                + "\t" + camera.getPosition() + "\t" + camera.getYRot() + "\t" + camera.getXRot());
    }

    static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
