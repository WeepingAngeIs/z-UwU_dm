package dev.uwu.weaponcheck;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Disposable hosted native client plus a separate TCP observer. Never loads a user world. */
@Mod("uwu_weapon_check")
public final class PairedFixture {
    enum Stage { LOAD, POSITION,  CONNECT, TRIALS, FINISH, DONE }
    static Stage stage = Stage.LOAD;
    static volatile Throwable serverFailure;
    private static boolean opening, observerReady, optionsVerified;
    private static int entered;
    private static long started;
    private static UUID owner;
    record ServerInfo(String owner, int port) {}

    public PairedFixture() {
        CandidateJarProbe.verify();
        NeoForge.EVENT_BUS.addListener(PairedTrial::serverTick);
        try { Evidence.initialize(); } catch (Exception failure) { throw new IllegalStateException(failure); }
        started = System.nanoTime();
        NeoForge.EVENT_BUS.addListener(PairedFixture::tick);
        NeoForge.EVENT_BUS.addListener(PairedFixture::frame);


    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (stage == Stage.DONE) { client.stop(); return; }
        try {
            Evidence.require(System.nanoTime() - started < 18L * 60 * 1_000_000_000L, "Weapon fixture exceeded its bounded deadline");
            if (Files.exists(Evidence.CONTROL.resolve("failure.json"))) throw new AssertionError("The other fixture process reported failure");
            if (serverFailure != null) throw new AssertionError("Disposable server action failed", serverFailure);
            if (!optionsVerified && client.screen instanceof TitleScreen) {
                client.options.fov().set(70);
                Evidence.require(client.options.fov().get() == 70, "Native fixture FOV did not resolve to 70 degrees");
                Evidence.require(client.options.simulationDistance().get() == 5, "Native fixture simulation distance did not resolve to five");
                Evidence.json("client-options.json", Map.of("fov_degrees", client.options.fov().get(),
                        "simulation_distance", client.options.simulationDistance().get(),
                        "render_distance", client.options.renderDistance().get(),
                        "temporary_directory", System.getProperty("java.io.tmpdir"),
                        "native_library_directory", System.getProperty("org.lwjgl.system.SharedLibraryExtractPath")));
                optionsVerified = true;
            }
            if (Evidence.OBSERVER) { observerTick(client); return; }
            if (!opening && client.screen instanceof TitleScreen title) {
                opening = true;
                client.createWorldOpenFlows().createFreshLevel("UwUWeaponViews",
                        new LevelSettings("UwU Weapon Views", GameType.SURVIVAL, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(152045L, false, false), WorldPresets::createNormalWorldDimensions, title);
                return;
            }
            acceptWorldWarning(client);
            if (client.player == null || client.level == null || client.screen != null) return;
            client.setWindowActive(true);
            var player = client.player;
            switch (stage) {
                case LOAD -> {
                    if (player.tickCount < 50) return;
                    for (String mod : List.of("bettercombat", "playeranimator"))
                        Evidence.require(ModList.get().isLoaded(mod), "Required animation integration missing: " + mod);
                    owner = player.getUUID();
                    server(client, actor -> {
                        var level = actor.serverLevel();
                        for (int x = -10; x <= 10; x++) for (int z = -10; z <= 28; z++)
                            level.setBlockAndUpdate(new BlockPos(x, 200, z), Blocks.SMOOTH_STONE.defaultBlockState());
                        level.setDayTime(6000);
                        level.setWeatherParameters(24000, 0, false, false);
                        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
                        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
                        actor.teleportTo(.5, 201, .5);
                        actor.getInventory().clearContent();
                        actor.fallDistance = 0;
                    });
                    enter(Stage.POSITION, client);
                }
                case POSITION -> {
                    if (Math.abs(player.getY() - 201) > .01 || player.tickCount - entered < 10) return;
                    poseOwner(client);
                    client.options.hideGui = false;
                    client.options.guiScale().set(3);
                    client.resizeDisplay();
                    client.options.setCameraType(CameraType.FIRST_PERSON);
                    var server = client.getSingleplayerServer();
                    Evidence.require(server != null, "No disposable integrated server");
                    server.setUsesAuthentication(false);
                    Evidence.require(server.publishServer(GameType.SURVIVAL, false, 25591), "Could not publish the disposable fixture");
                    Evidence.signal("server.json", new ServerInfo(owner.toString(), 25591));
                    System.out.println("UWU WEAPONS HOST READY native TCP port=25591 owner=" + owner);
                    enter(Stage.CONNECT, client);
                }
                case CONNECT -> {
                    Evidence.require(player.tickCount - entered < 1200, "Separate observer did not connect within 60 seconds");
                    if (!Files.exists(Evidence.CONTROL.resolve("observer-ready.json"))) return;
                    var server = client.getSingleplayerServer();
                    if (server.getPlayerList().getPlayerCount() != 2) return;
                    server.execute(() -> {
                        try {
                            for (ServerPlayer actor : server.getPlayerList().getPlayers()) {
                                actor.getInventory().clearContent();
                                if (!actor.getUUID().equals(owner)) actor.teleportTo(4.0, 201, .5);
                                else actor.teleportTo(.5, 201, .5);
                                actor.fallDistance = 0;
                                actor.containerMenu.broadcastChanges();
                            }
                        } catch (Throwable error) { serverFailure = error; }
                    });
                    Evidence.signal("observer-positioned.json", Map.of("owner", owner.toString(), "players", 2));
                    enter(Stage.TRIALS, client);
                }
                case TRIALS -> {
                    poseOwner(client);
                    client.mouseHandler.grabMouse();
                    if (PairedTrial.host(client)) {
                        Evidence.signal("host-done.json", Map.of("passed", true));
                        enter(Stage.FINISH, client);
                    }
                }
                case FINISH -> {
                    Evidence.require(player.tickCount - entered < 400, "Separate observer did not finish within 20 seconds");
                    if (!Files.exists(Evidence.CONTROL.resolve("observer-done.json"))) return;
                    System.out.println(Evidence.BASELINE ? "UWU WEAPONS BASELINE OBSERVED" : "UWU WEAPONS CLIENT PASS");
                    stage = Stage.DONE;
                }
                case DONE -> {}
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private static void observerTick(Minecraft client) throws Exception {
        if (!opening && client.screen instanceof TitleScreen title) {
            ServerInfo info = Evidence.read("server.json", ServerInfo.class);
            if (info == null) return;
            opening = true;
            owner = UUID.fromString(info.owner);
            String address = "127.0.0.1:" + info.port;
            ConnectScreen.startConnecting(title, client, ServerAddress.parseString(address),
                    new ServerData("Disposable UwU weapon observer", address, ServerData.Type.LAN), false, null);
            return;
        }
        acceptWorldWarning(client);
        if (client.player == null || client.level == null || client.screen != null || client.player.tickCount < 25) return;
        client.setWindowActive(true);
        if (!observerReady) {
            Evidence.require(client.getSingleplayerServer() == null, "Observer must be a separate client process");
            Evidence.require(client.getConnection() != null && !client.getConnection().getConnection().isMemoryConnection(),
                    "Observer connection is not native TCP");
            Evidence.require(!client.player.getUUID().equals(owner), "Observer and wielder identities collide");
            Evidence.signal("observer-ready.json", Map.of("uuid", client.player.getUUID().toString(),
                    "remote", client.getConnection().getConnection().getRemoteAddress().toString()));
            Evidence.json("network-identity.json", Map.of("owner", owner.toString(), "observer", client.player.getUUID().toString(),
                    "remote", client.getConnection().getConnection().getRemoteAddress().toString(), "memory_connection", false));
            observerReady = true;
            System.out.println("UWU WEAPONS OBSERVER TCP PASS distinct process and identity, native socket "
                    + client.getConnection().getConnection().getRemoteAddress());
        }
        if (!Files.exists(Evidence.CONTROL.resolve("observer-positioned.json")) || Math.abs(client.player.getX() - 4.0) > .2) return;
        client.options.setCameraType(CameraType.FIRST_PERSON);
        client.player.setYRot(90);
        client.player.yRotO = 90;
        client.player.setXRot(11);
        client.player.xRotO = 11;
        PairedTrial.observer(client, owner);
        if (Files.exists(Evidence.CONTROL.resolve("host-done.json"))) {
            Evidence.signal("observer-done.json", Map.of("passed", true));
            System.out.println(Evidence.BASELINE ? "UWU WEAPONS OBSERVER BASELINE OBSERVED" : "UWU WEAPONS OBSERVER PASS");
            stage = Stage.DONE;
        }
    }

    static void poseOwner(Minecraft client) {
        var p = client.player;
        p.setYRot(0); p.yRotO = 0; p.setXRot(0); p.xRotO = 0;
        p.yBodyRot = 0; p.yBodyRotO = 0; p.yHeadRot = 0; p.yHeadRotO = 0;
    }


    static void equip(Minecraft client, ItemStack source, String marker, InteractionHand hand) {
        ItemStack stack = source.copy();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("uwu.weapon_fixture", marker));
        server(client, actor -> {
            actor.getInventory().clearContent();
            actor.setItemInHand(hand, stack.copy());
            actor.getCooldowns().removeCooldown(stack.getItem());
            actor.containerMenu.broadcastChanges();
        });
    }

    static String marker(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("uwu.weapon_fixture");
    }

    static void server(Minecraft client, Consumer<ServerPlayer> action) {
        var server = client.getSingleplayerServer();
        UUID id = client.player.getUUID();
        server.execute(() -> {
            try { action.accept(server.getPlayerList().getPlayer(id)); }
            catch (Throwable failure) { serverFailure = failure; }
        });
    }

    private static void enter(Stage next, Minecraft client) { stage = next; entered = client.player.tickCount; }

    private static void acceptWorldWarning(Minecraft client) {
        if (opening && client.player == null && client.screen != null)
            for (var widget : List.copyOf(client.screen.children()))
                if (widget instanceof Button button && (button.getMessage().getString().equals("Proceed")
                        || button.getMessage().getString().equals("I know what I'm doing!"))) { button.onPress(); break; }
    }

    private static void frame(RenderFrameEvent.Post event) {
        var c = Minecraft.getInstance();
        if (c.player == null || c.level == null || c.screen != null) return;
        try { PairedTrial.frame(c, owner); }
        catch (Throwable failure) { fail(failure); }
    }
    static void fail(Throwable failure) {
        System.err.println("UWU WEAPONS CHECK FAILED role=" + (Evidence.OBSERVER ? "observer" : "host"));
        failure.printStackTrace();
        try { Evidence.signal("failure.json", Map.of("message", failure.toString())); } catch (Exception ignored) {}
        stage = Stage.DONE;
    }
}
