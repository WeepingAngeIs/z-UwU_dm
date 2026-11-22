package dev.uwu.dialoguecheck;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import dev.uwu.dimension.Keeper;
import dev.uwu.dimension.KeeperRenderer;
import dev.uwu.dimension.KeeperSpeech;
import dev.uwu.dimension.ReefLife;
import dev.uwu.dimension.client.KeeperSpeechBubbles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Real packaged client, regular-chat delivery and native world-rendered speech evidence. */
@Mod("uwu_dialogue_check")
public final class DialogueClientCheck {
    private enum Stage { LOAD, READY, PETAL, PETAL_CAMERA, PETAL_FRAME, DUSK_READY, DUSK,
        DUSK_CAMERA, DUSK_FRAME, TALK_READY, TALK, TALK_CAMERA, TALK_FRAME,
        REPLY_READY, REPLY, REPLY_CAMERA, REPLY_FRAME, EXPIRE, REFRESH, SWITCH,
        SWITCHED, REFERENCE, CLEANUP, WELCOME, DONE }
    private static final String MODE = System.getProperty("uwu.check.dialogueMode", "without-reference");
    private static final boolean REFERENCE = MODE.equals("with-reference");
    private static final String PLAYER_CONTROL = "UwU reference player control";
    private static final Path OUTPUT = Path.of(System.getProperty("uwu.check.evidence"));
    private static final ServerSpeechProbe SERVER = new ServerSpeechProbe();
    private static final List<Chat> CHAT = new ArrayList<>();
    private static final List<KeeperSpeech.Cue> CUES = new ArrayList<>();
    private static final Map<UUID, Integer> FRAME_DRAWS = new HashMap<>();
    private static final Map<UUID, Integer> NATIVE_FRAME_DRAWS = new HashMap<>();
    private static final Map<UUID, Integer> NATIVE_FRAME_STREAK = new HashMap<>();
    private static final Map<String, Object> FRAMES = new LinkedHashMap<>();
    private static final Map<UUID, Integer> DRAWN_FRAMES = new HashMap<>();
    private static Stage stage = Stage.LOAD;
    private static CompletableFuture<Void> serverWork;
    private static boolean opening, failed;
    private static long stageStarted = System.nanoTime();
    private static int ticks;
    private static Component expected;
    private static UUID speaking;
    private static KeeperSpeech.Cue lastCue;
    private static String capture, choice;
    private static Throwable renderFailure;

    public DialogueClientCheck() {
        CandidateJarProbe.verify();
        require(ModList.get().isLoaded("chatbubbles") == REFERENCE, "Reference-mod mode does not match actual loaded mods");
        NeoForge.EVENT_BUS.addListener(DialogueClientCheck::tick);
        NeoForge.EVENT_BUS.addListener(DialogueClientCheck::frame);
        NeoForge.EVENT_BUS.addListener(DialogueClientCheck::nativeKeeper);
        NeoForge.EVENT_BUS.addListener(DialogueClientCheck::chat);
        if (WelcomeCheck.enabled()) WelcomeCheck.register();
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        try {
            if (failed || stage == Stage.DONE) { client.stop(); return; }
            if (!opening && client.screen instanceof TitleScreen title) {
                opening = true;
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 720);
                client.createWorldOpenFlows().createFreshLevel("UwUDialogueCheck",
                        new LevelSettings("Disposable UwU dialogue check", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(152045L, false, false), WorldPresets::createNormalWorldDimensions, title);
            } else if (opening && client.player == null && client.screen != null) {
                for (var child : List.copyOf(client.screen.children())) {
                    if (child instanceof Button button && (button.getMessage().getString().equals("Proceed")
                            || button.getMessage().getString().equals("I know what I'm doing!"))) { button.onPress(); break; }
                }
            }
            if (client.player == null || client.level == null) return;
            if (renderFailure != null) throw new AssertionError("Render evidence failed", renderFailure);
            require(System.nanoTime() - stageStarted < (stage == Stage.WELCOME ? 420_000_000_000L : 180_000_000_000L), "Timed out stage=" + stage);
            if (serverWork != null) {
                if (!serverWork.isDone()) return;
                serverWork.join(); serverWork = null;
            }
            ++ticks;
            switch (stage) {
                case LOAD -> {
                    if (client.player.tickCount < 70 || client.screen != null) return;
                    client.options.setCameraType(CameraType.FIRST_PERSON);
                    action(client, SERVER::setup); next(Stage.READY);
                }
                case READY -> {
                    if (!positioned(client) || !(client.level.getEntity(SERVER.petalId) instanceof Keeper)
                            || !(client.level.getEntity(SERVER.duskId) instanceof Keeper)
                            || !sceneReady(client, SERVER.petalUuid)) return;
                    begin(SERVER.petalGreeting, SERVER.petalUuid);
                    action(client, owner -> SERVER.interact(owner, false)); next(Stage.PETAL);
                }
                case PETAL -> {
                    if (!received(client)) return;
                    closeMenu(client); action(client, owner -> SERVER.camera(owner, false)); next(Stage.PETAL_CAMERA);
                }
                case PETAL_CAMERA -> { if (readyToCapture(client)) { capture = "petal-speech"; next(Stage.PETAL_FRAME); } }
                case PETAL_FRAME -> {
                    if (!FRAMES.containsKey("petal-speech")) return;
                    checkReferenceNpc(client);
                    action(client, SERVER::positionDusk); next(Stage.DUSK_READY);
                }
                case DUSK_READY -> {
                    if (!positioned(client) || !sceneReady(client, SERVER.duskUuid)) return;
                    begin(SERVER.duskGreeting, SERVER.duskUuid);
                    action(client, owner -> SERVER.interact(owner, true)); next(Stage.DUSK);
                }
                case DUSK -> {
                    if (!received(client)) return;
                    closeMenu(client); action(client, owner -> SERVER.camera(owner, true)); next(Stage.DUSK_CAMERA);
                }
                case DUSK_CAMERA -> { if (readyToCapture(client)) { capture = "dusk-speech"; next(Stage.DUSK_FRAME); } }
                case DUSK_FRAME -> {
                    if (!FRAMES.containsKey("dusk-speech")) return;
                    checkReferenceNpc(client);
                    action(client, SERVER::positionForConversation); next(Stage.TALK_READY);
                }
                case TALK_READY -> {
                    if (!positioned(client) || !sceneReady(client, SERVER.duskUuid)) return;
                    begin(SERVER.opening, SERVER.duskUuid);
                    action(client, owner -> SERVER.conversation(owner, "sakuraambient talk", false)); next(Stage.TALK);
                }
                case TALK -> {
                    if (!received(client) || choice == null) return;
                    require(client.font.split(expected, 220).size() > 1, "Long existing dialogue did not exercise wrapping");
                    action(client, owner -> SERVER.camera(owner, true)); next(Stage.TALK_CAMERA);
                }
                case TALK_CAMERA -> { if (readyToCapture(client)) { capture = "dusk-conversation"; next(Stage.TALK_FRAME); } }
                case TALK_FRAME -> {
                    if (!FRAMES.containsKey("dusk-conversation")) return;
                    checkReferenceNpc(client);
                    action(client, SERVER::positionForConversation); next(Stage.REPLY_READY);
                }
                case REPLY_READY -> {
                    if (!positioned(client) || !sceneReady(client, SERVER.duskUuid)) return;
                    begin(SERVER.reply, SERVER.duskUuid);
                    action(client, owner -> SERVER.conversation(owner, choice.substring(1), true)); next(Stage.REPLY);
                }
                case REPLY -> {
                    if (!received(client)) return;
                    action(client, owner -> SERVER.camera(owner, true)); next(Stage.REPLY_CAMERA);
                }
                case REPLY_CAMERA -> { if (readyToCapture(client)) { capture = "dusk-reply"; next(Stage.REPLY_FRAME); } }
                case REPLY_FRAME -> {
                    if (!FRAMES.containsKey("dusk-reply")) return;
                    System.out.println("KEEPER SPEECH RECIPIENTS PASS native Petal/Dusk greetings and scene opening/reply; near, inside, edge80, far, vertical-far and other-dimension packets");
                    next(Stage.EXPIRE);
                }
                case EXPIRE -> {
                    if (client.level.getGameTime() - lastCue.startTick() < KeeperSpeech.DURATION_TICKS + 4) return;
                    require(!speech().containsKey(SERVER.duskUuid), "Expired speech is still cached");
                    rejectStale(client);
                    checkReferenceNpc(client);
                    capture = "expired"; next(Stage.REFRESH);
                }
                case REFRESH -> {
                    if (!FRAMES.containsKey("expired")) return;
                    begin(SERVER.steps, SERVER.duskUuid);
                    action(client, SERVER::vanillaAndRefresh); next(Stage.SWITCH);
                }
                case SWITCH -> {
                    if (!received(client)) return;
                    action(client, SERVER::leaveDimension); next(Stage.SWITCHED);
                }
                case SWITCHED -> {
                    if (!client.level.dimension().equals(Level.OVERWORLD) || ticks < 8) return;
                    require(speech().isEmpty(), "Old-dimension speech survived an actual level switch");
                    KeeperSpeechBubbles.receive(lastCue);
                    require(speech().isEmpty(), "An old-dimension cue was accepted after teleport");
                    System.out.println("KEEPER SPEECH CLIENT PASS regular chat and exact bubble lines, repeat suppression, native expiry, stale identity/dimension guards and vanilla control");
                    if (REFERENCE) client.getConnection().sendChat(PLAYER_CONTROL);
                    next(Stage.REFERENCE);
                }
                case REFERENCE -> {
                    if (REFERENCE && referenceMessages(client.player).stream().filter(PLAYER_CONTROL::equals).count() != 1) return;
                    checkReferenceNpc(client);
                    System.out.println("KEEPER SPEECH COMPATIBILITY PASS mode=" + MODE
                            + (REFERENCE ? " exact reference loaded, native player-chat control received, no reference NPC bubbles" : " no reference JAR or dependency"));
                    action(client, SERVER::cleanup); next(Stage.CLEANUP);
                }
                case CLEANUP -> {
                    writeReport(client);
                    System.out.println("KEEPER SPEECH RENDER PASS native Keeper model and overhead bubble observed together; five actual screenshots mode=" + MODE);
                    if (WelcomeCheck.enabled()) {
                        action(client, WelcomeCheck::start);
                        next(Stage.WELCOME);
                    } else next(Stage.DONE);
                }
                case WELCOME -> {
                    if (WelcomeCheck.problem != null) throw new AssertionError("Guardian visit", WelcomeCheck.problem);
                    if (WelcomeCheck.done) next(Stage.DONE);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failed = true;
            System.err.println("KEEPER SPEECH CHECK FAILED stage=" + stage + " mode=" + MODE);
            failure.printStackTrace();
            client.stop();
        }
    }

    private static void begin(Component line, UUID uuid) { expected = line; speaking = uuid; DRAWN_FRAMES.remove(uuid); }

    private static boolean positioned(Minecraft client) {
        return client.level.dimension().equals(ReefLife.REACH) && SERVER.targetPosition != null
                && client.player.distanceToSqr(SERVER.targetPosition) < .02 && ticks >= 8;
    }

    private static boolean received(Minecraft client) throws Exception {
        var nativeChat = CHAT.stream().filter(c -> c.text.equals(expected.getString())).toList();
        var nativeCues = CUES.stream().filter(c -> c.speaker().equals(speaking) && c.line().equals(expected)).toList();
        require(nativeChat.size() <= 1 && nativeCues.size() <= 1, "Duplicate real chat or speech payload: " + expected.getString());
        if (nativeChat.isEmpty() || nativeCues.isEmpty() || !speech().containsKey(speaking)) return false;
        require(!nativeChat.getFirst().overlay, "NPC speech arrived in the action bar");
        lastCue = speech().get(speaking);
        require(lastCue.line().equals(expected), "Bubble text differs from actual regular chat");
        require(chatHistoryCount(client, expected.getString()) == 1, "Regular chat history does not contain exactly one NPC line");
        return true;
    }

    private static boolean readyToCapture(Minecraft client) throws Exception {
        return positioned(client) && sceneReady(client, speaking) && speech().containsKey(speaking)
                && DRAWN_FRAMES.getOrDefault(speaking, 0) >= 4;
    }

    private static boolean sceneReady(Minecraft client, UUID speaker) {
        if (client.screen != null || NATIVE_FRAME_STREAK.getOrDefault(speaker, 0) < 4) return false;
        int id = speaker.equals(SERVER.petalUuid) ? SERVER.petalId : SERVER.duskId;
        return client.level.getEntity(id) instanceof Keeper keeper && keeper.getUUID().equals(speaker)
                && client.level.getBlockState(keeper.blockPosition().below()).is(Blocks.SMOOTH_QUARTZ)
                && client.level.getBlockState(client.player.blockPosition().below()).is(Blocks.SMOOTH_QUARTZ);
    }

    private static void closeMenu(Minecraft client) {
        client.player.closeContainer();
        client.setScreen(null);
    }

    private static void rejectStale(Minecraft client) throws Exception {
        var cue = lastCue;
        long now = client.level.getGameTime();
        KeeperSpeechBubbles.receive(new KeeperSpeech.Cue(cue.dimension(), cue.entityId(), UUID.randomUUID(), now, cue.dusk(), cue.line()));
        KeeperSpeechBubbles.receive(new KeeperSpeech.Cue(ResourceLocation.withDefaultNamespace("the_nether"), cue.entityId(), cue.speaker(), now, cue.dusk(), cue.line()));
        KeeperSpeechBubbles.receive(new KeeperSpeech.Cue(cue.dimension(), Integer.MAX_VALUE, cue.speaker(), now, cue.dusk(), cue.line()));
        KeeperSpeechBubbles.receive(new KeeperSpeech.Cue(cue.dimension(), cue.entityId(), cue.speaker(), now, !cue.dusk(), cue.line()));
        KeeperSpeechBubbles.receive(new KeeperSpeech.Cue(cue.dimension(), cue.entityId(), cue.speaker(), now - KeeperSpeech.DURATION_TICKS, cue.dusk(), cue.line()));
        require(!speech().containsKey(cue.speaker()), "Stale identity/faction/expired cue created speech");
    }

    private static void chat(ClientChatReceivedEvent.System event) {
        CHAT.add(new Chat(event.getMessage().getString(), event.isOverlay()));
        var click = event.getMessage().getStyle().getClickEvent();
        if (click != null && click.getValue().startsWith("/sakuraambient choose ") && click.getValue().endsWith(" lamps")) choice = click.getValue();
    }

    public static void receivedCue(KeeperSpeech.Cue cue) { CUES.add(cue); }
    public static void drawn(KeeperSpeech.Cue cue) { FRAME_DRAWS.merge(cue.speaker(), 1, Integer::sum); }

    private static void nativeKeeper(RenderLivingEvent.Post<?, ?> event) {
        // Tracked entities and speech cues can arrive before their terrain section
        // has compiled. Observe the real native renderer before starting the TTL.
        if (event.getEntity() instanceof Keeper keeper && event.getRenderer() instanceof KeeperRenderer
                && (keeper.getUUID().equals(SERVER.petalUuid) || keeper.getUUID().equals(SERVER.duskUuid)))
            NATIVE_FRAME_DRAWS.merge(keeper.getUUID(), 1, Integer::sum);
    }

    private static void frame(RenderFrameEvent.Post event) {
        try {
            if (failed || stage == Stage.DONE) return;
            for (var entry : FRAME_DRAWS.entrySet()) {
                require(entry.getValue() == 1, "Duplicate built-in bubble draw in one frame: " + entry);
                DRAWN_FRAMES.merge(entry.getKey(), 1, Integer::sum);
            }
            var observed = new HashSet<>(NATIVE_FRAME_STREAK.keySet());
            observed.addAll(NATIVE_FRAME_DRAWS.keySet());
            for (UUID uuid : observed) {
                int draws = NATIVE_FRAME_DRAWS.getOrDefault(uuid, 0);
                require(draws <= 1, "Duplicate native Keeper draw in one frame: " + uuid);
                if (draws == 1) NATIVE_FRAME_STREAK.merge(uuid, 1, Integer::sum);
                else NATIVE_FRAME_STREAK.remove(uuid);
            }
            if (capture == null) return;
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.screen != null) return;
            boolean expired = capture.equals("expired");
            require(expired ? FRAME_DRAWS.getOrDefault(speaking, 0) == 0 : FRAME_DRAWS.getOrDefault(speaking, 0) == 1,
                    "Evidence frame has the wrong bubble count for " + capture);
            require(sceneReady(client, speaking) && NATIVE_FRAME_DRAWS.getOrDefault(speaking, 0) == 1,
                    "Evidence frame does not show the native speaking Keeper and loaded platform: " + capture);
            Files.createDirectories(OUTPUT);
            try (NativeImage pixels = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                require(pixels.getWidth() >= 800 && pixels.getHeight() >= 450, "Evidence resolution is too small");
                pixels.writeToFile(OUTPUT.resolve(capture + ".png"));
                var entry = new LinkedHashMap<String, Object>();
                entry.put("width", pixels.getWidth()); entry.put("height", pixels.getHeight());
                entry.put("dimension", client.level.dimension().location().toString());
                entry.put("line", expected.getString()); entry.put("speaker", speaking.toString());
                entry.put("bubble_draws", FRAME_DRAWS.getOrDefault(speaking, 0));
                entry.put("native_keeper_draws", NATIVE_FRAME_DRAWS.getOrDefault(speaking, 0));
                entry.put("native_keeper_consecutive_frames", NATIVE_FRAME_STREAK.getOrDefault(speaking, 0));
                entry.put("native_renderer", KeeperRenderer.class.getName());
                entry.put("client_platform_present", true);
                entry.put("gui_scale", client.getWindow().getGuiScale());
                entry.put("camera", client.gameRenderer.getMainCamera().getPosition().toString());
                entry.put("wrapped_lines", client.font.split(expected, 220).size());
                FRAMES.put(capture, entry);
            }
            capture = null;
        } catch (Throwable failure) { renderFailure = failure; }
        finally { FRAME_DRAWS.clear(); NATIVE_FRAME_DRAWS.clear(); }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, KeeperSpeech.Cue> speech() throws Exception {
        var field = KeeperSpeechBubbles.class.getDeclaredField("SPEECH"); field.setAccessible(true);
        return (Map<UUID, KeeperSpeech.Cue>) field.get(null);
    }

    private static long chatHistoryCount(Minecraft client, String expectedLine) throws Exception {
        var chat = client.gui.getChat();
        var field = chat.getClass().getDeclaredField("allMessages"); field.setAccessible(true);
        long count = 0;
        for (Object message : (List<?>) field.get(chat)) {
            var method = message.getClass().getMethod("content");
            if (((Component) method.invoke(message)).getString().equals(expectedLine)) ++count;
        }
        return count;
    }

    private static void checkReferenceNpc(Minecraft client) throws Exception {
        if (!REFERENCE) return;
        for (int id : new int[]{SERVER.petalId, SERVER.duskId}) {
            Entity npc = client.level.getEntity(id);
            if (npc instanceof Keeper) require(referenceMessages(npc).isEmpty(), "Reference mod also created an NPC bubble");
        }
        var spoken = Set.of(SERVER.petalGreeting.getString(), SERVER.duskGreeting.getString(), SERVER.opening.getString(), SERVER.reply.getString(), SERVER.steps.getString());
        for (String message : referenceMessages(client.player)) require(!spoken.contains(message), "NPC line duplicated onto a player bubble");
    }

    @SuppressWarnings("unchecked")
    static List<String> referenceMessages(Entity entity) throws Exception {
        var supplier = (Supplier<?>) Class.forName("dev.jvitor.chatbubbles.data.ModAttachments").getField("BUBBLES").get(null);
        var type = (AttachmentType<Object>) supplier.get();
        if (!entity.hasData(type)) return List.of();
        Object attachment = entity.getData(type);
        var bubbles = (List<?>) attachment.getClass().getMethod("bubbles").invoke(attachment);
        var messages = new ArrayList<String>();
        for (Object bubble : bubbles) messages.add((String) bubble.getClass().getMethod("message").invoke(bubble));
        return messages;
    }

    private static void writeReport(Minecraft client) throws Exception {
        var report = new LinkedHashMap<String, Object>();
        report.put("source_sha", System.getenv("GITHUB_SHA")); report.put("candidate_sha256", System.getProperty("uwu.check.candidateSha256"));
        report.put("reference_mode", MODE); report.put("reference_loaded", ModList.get().isLoaded("chatbubbles"));
        report.put("recipient_packets", SERVER.receipts()); report.put("frames", FRAMES);
        report.put("regular_chat_lines", CHAT.stream().filter(c -> c.text.contains("Keeper:") || c.text.startsWith("Dusk Keeper:")).toList());
        report.put("boundary_recipient_kind", "Native server players with in-process recording packet listeners; one actual integrated client rendered the NPC speech");
        Files.createDirectories(OUTPUT);
        Files.writeString(OUTPUT.resolve("dialogue-evidence.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    }

    private static void action(Minecraft client, ServerAction action) {
        var server = client.getSingleplayerServer();
        UUID owner = client.player.getUUID();
        serverWork = new CompletableFuture<>();
        var completion = serverWork;
        server.execute(() -> {
            try { action.run(server.getPlayerList().getPlayer(owner)); completion.complete(null); }
            catch (Throwable failure) { completion.completeExceptionally(failure); }
        });
    }

    private static void next(Stage value) { stage = value; ticks = 0; stageStarted = System.nanoTime(); }
    private static void require(boolean condition, String problem) { if (!condition) throw new AssertionError(problem); }
    private record Chat(String text, boolean overlay) {}
    @FunctionalInterface private interface ServerAction { void run(ServerPlayer player) throws Exception; }
}
