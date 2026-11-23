package dev.uwu.hotbarcheck;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dimension.CupidBowVisualConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.NeoForge;

/** Native world/hand/hotbar screenshots from the sealed candidate JAR. No replacement models. */
public final class CombinedVisualProbe {
    private static final Path OUT = Path.of(System.getProperty("uwu.check.evidence", "../combined-visual-evidence"));
    private static final String SMOOTH = "uwudimension:smooth_cupid";
    private static final List<String> MATERIALS = List.of("iron", "gold", "diamond", "netherite", "runic");
    private static final List<String> SHAPES = List.of("katana", "rapier", "sai", "twinblade");
    private record Look(String id, boolean detailed) {
        boolean bow() { return id.equals("cupid_bow"); }
        String label() { return bow() ? (detailed ? "cupid-detailed" : "cupid-original") : id; }
        ItemStack stack() {
            var key = ResourceLocation.fromNamespaceAndPath("uwudimension", id);
            require(BuiltInRegistries.ITEM.containsKey(key), "Unregistered visual target " + key);
            var result = new ItemStack(BuiltInRegistries.ITEM.get(key));
            require(!result.isEmpty(), "Empty visual target " + key);
            if (detailed) CustomData.update(DataComponents.CUSTOM_DATA, result, nbt -> nbt.putBoolean(SMOOTH, true));
            return result;
        }
    }
    private enum Pose {
        REST("", -1, -1), DRAW0("_draw0", 0, 8), DRAW1("_draw1", 8, 15),
        DEEP("_deep", 15, 20), DRAW2("_draw2", 20, Integer.MAX_VALUE);
        final String suffix;
        final int minimum, limit;
        Pose(String suffix, int minimum, int limit) { this.suffix = suffix; this.minimum = minimum; this.limit = limit; }
        boolean accepts(Minecraft client) {
            var player = client.player;
            return this == REST ? !player.isUsingItem() : player.isUsingItem()
                    && player.getTicksUsingItem() >= minimum && player.getTicksUsingItem() < limit;
        }
    }
    private record View(String label, CameraType camera, InteractionHand hand, float orbit, int fov) {}
    private record Trial(Look look, View view, Pose pose) {
        String label() { return look.label() + "-" + view.label + "-" + pose.name().toLowerCase(); }
    }
    private static final List<View> VIEWS = List.of(
            new View("first-main", CameraType.FIRST_PERSON, InteractionHand.MAIN_HAND, 0, 70),
            new View("third-front", CameraType.THIRD_PERSON_FRONT, InteractionHand.MAIN_HAND, 0, 70),
            new View("third-side", CameraType.THIRD_PERSON_FRONT, InteractionHand.MAIN_HAND, 90, 70),
            new View("third-rear", CameraType.THIRD_PERSON_BACK, InteractionHand.MAIN_HAND, 0, 70),
            new View("third-front-nudge", CameraType.THIRD_PERSON_FRONT, InteractionHand.MAIN_HAND, 3, 70),
            new View("third-front-close", CameraType.THIRD_PERSON_FRONT, InteractionHand.MAIN_HAND, 0, 70),
            new View("third-side-close", CameraType.THIRD_PERSON_FRONT, InteractionHand.MAIN_HAND, 90, 70),
            new View("third-rear-close", CameraType.THIRD_PERSON_BACK, InteractionHand.MAIN_HAND, 0, 70),
            new View("third-front-close-nudge", CameraType.THIRD_PERSON_FRONT, InteractionHand.MAIN_HAND, 3, 70),
            new View("first-offhand", CameraType.FIRST_PERSON, InteractionHand.OFF_HAND, 0, 70),
            new View("third-front-offhand", CameraType.THIRD_PERSON_FRONT, InteractionHand.OFF_HAND, 0, 70),
            // Append these to retain the existing first-person/bow-pose view indices.
            new View("third-side-nudge", CameraType.THIRD_PERSON_FRONT, InteractionHand.MAIN_HAND, 93, 70),
            new View("third-rear-nudge", CameraType.THIRD_PERSON_BACK, InteractionHand.MAIN_HAND, 3, 70),
            new View("third-side-close-nudge", CameraType.THIRD_PERSON_FRONT, InteractionHand.MAIN_HAND, 93, 70),
            new View("third-rear-close-nudge", CameraType.THIRD_PERSON_BACK, InteractionHand.MAIN_HAND, 3, 70));
    private static final List<Look> LOOKS = looks();
    private static final List<Trial> TRIALS = trials();
    private static final List<Map<String, Object>> FRAMES = new ArrayList<>();
    private static final List<Map<String, Object>> EXCEPTIONS = new ArrayList<>();
    private static final Map<String, Object> MODELS = new LinkedHashMap<>();
    private static boolean opening, prepared, done, active, equipped, drawing, saved, motionStarted;
    private static int index, requestedAt, settledAt, retries, frameNumber, requestedFrame;
    private static long startedNanos;
    private static volatile int serverAck = -1;
    private static volatile Throwable serverFailure;
    private static ItemStack expected;

    private CombinedVisualProbe() {}

    public static void start(Minecraft client) {
        require(!active, "Visual probe started twice");
        active = true;
        startedNanos = System.nanoTime();
        NeoForge.EVENT_BUS.addListener(CombinedVisualProbe::tick);
        NeoForge.EVENT_BUS.addListener(CombinedVisualProbe::frame);
        NeoForge.EVENT_BUS.addListener(CombinedVisualProbe::camera);
        System.out.println("COMBINED VISUAL START looks=" + LOOKS.size() + " expected_native_screenshots=" + TRIALS.size());
    }

    private static List<Look> looks() {
        var result = new ArrayList<Look>();
        for (String material : MATERIALS) for (String shape : SHAPES)
            result.add(new Look("counter_" + material + "_" + shape, false));
        result.add(new Look("cupid_bow", false));
        result.add(new Look("cupid_bow", true));
        require(result.size() == 22 && result.stream().map(Look::label).distinct().count() == 22,
                "Visual roster must contain the exact 20 chakrams and two bow looks");
        return List.copyOf(result);
    }

    private static List<Trial> trials() {
        var result = new ArrayList<Trial>();
        for (Look look : LOOKS) {
            for (View view : VIEWS) result.add(new Trial(look, view, Pose.REST));
            if (look.bow()) for (View view : List.of(VIEWS.get(0), VIEWS.get(1), VIEWS.get(9), VIEWS.get(10)))
                for (Pose pose : List.of(Pose.DRAW0, Pose.DRAW1, Pose.DEEP, Pose.DRAW2))
                    result.add(new Trial(look, view, pose));
        }
        return List.copyOf(result);
    }

    private static void tick(ClientTickEvent.Post event) {
        if (!active || done) return;
        var client = Minecraft.getInstance();
        try {
            require(System.nanoTime() - startedNanos < 1_800_000_000_000L, "Visual client exceeded 30 minutes");
            if (serverFailure != null) throw new AssertionError("Visual fixture server action failed", serverFailure);
            if (!opening && client.screen instanceof TitleScreen title) {
                opening = true;
                client.createWorldOpenFlows().createFreshLevel("CombinedVisualCheck",
                        new LevelSettings("Combined native visual check", GameType.SURVIVAL, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(152045L, false, false), WorldPresets::createNormalWorldDimensions, title);
            } else if (opening && client.player == null && client.screen != null) {
                for (var widget : List.copyOf(client.screen.children())) if (widget instanceof Button button
                        && (button.getMessage().getString().equals("Proceed")
                        || button.getMessage().getString().equals("I know what I'm doing!"))) {
                    button.onPress();
                    break;
                }
            }
            if (client.player == null || client.level == null || client.gameMode == null || client.screen != null) return;
            if (!prepared) {
                if (client.player.tickCount < 40) return;
                prepared = true;
                client.setWindowActive(true);
                client.options.hideGui = false;
                client.options.guiScale().set(3);
                client.options.renderDistance().set(2);
                client.options.bobView().set(false);
                client.options.fovEffectScale().set(0D);
                client.resizeDisplay();
                CupidBowVisualConfig.HELD_SCALE.set(1D);
                server(client, actor -> {
                    var level = actor.serverLevel();
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, actor.server);
                    level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, actor.server);
                    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, actor.server);
                    level.setDayTime(6000);
                    level.setWeatherParameters(Integer.MAX_VALUE, 0, false, false);
                    for (int x = -14; x <= 14; x++) for (int z = -14; z <= 14; z++)
                        level.setBlockAndUpdate(new BlockPos(x, 220, z),
                                (Math.abs(x) == 14 || Math.abs(z) == 14 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.SMOOTH_QUARTZ).defaultBlockState());
                    actor.setInvulnerable(true);
                    actor.setHealth(actor.getMaxHealth());
                    actor.getFoodData().setFoodLevel(20);
                });
                begin(client);
                return;
            }
            if (index == TRIALS.size()) {
                if (!motionStarted) {
                    motionStarted = true;
                    CombinedChakramMotion.start(client, OUT);
                }
                if (CombinedChakramMotion.tick(client)) finish(client);
                return;
            }
            Trial trial = TRIALS.get(index);
            var player = client.player;
            require(player.tickCount - requestedAt < 260, "Visual trial timed out " + trial.label()
                    + " ack=" + serverAck + " expected=" + expected + " held=" + player.getItemInHand(trial.view.hand)
                    + " storedOffhand=" + player.getInventory().getItem(40)
                    + " useTicks=" + player.getTicksUsingItem());
            if (saved) { release(client); index++; if (index < TRIALS.size()) begin(client); return; }
            // Better Combat intentionally returns EMPTY from the effective OFF_HAND
            // accessor when the offhand weapon itself is two-handed. Synchronize the
            // actual inventory first, then report that policy without bypassing it.
            ItemStack stored = trial.view.hand == InteractionHand.OFF_HAND
                    ? player.getInventory().getItem(40) : player.getMainHandItem();
            if (serverAck != index || !ItemStack.isSameItemSameComponents(stored, expected)) return;
            if (trial.view.hand == InteractionHand.OFF_HAND && player.getItemInHand(trial.view.hand).isEmpty()
                    && isTwoHanded(stored)) {
                var reason = "The shipped Better Combat two-handed profile intentionally masks this offhand item";
                EXCEPTIONS.add(Map.of("item", "uwudimension:" + trial.look.id, "look", trial.look.label(),
                        "view", trial.view.label, "pose", trial.pose.name(), "reason", reason,
                        "storedSlot40", stored.toString(), "captured", false));
                System.out.println("COMBINED VISUAL INTENTIONAL EXCEPTION " + trial.label() + ": " + reason);
                saved = true;
                writeManifest(false, null);
                return;
            }
            if (!ItemStack.isSameItemSameComponents(player.getItemInHand(trial.view.hand), expected)) return;
            if (!equipped) { equipped = true; settledAt = player.tickCount; }
            if (player.tickCount - settledAt < 18 || player.getAttackStrengthScale(0) < .99F) return;
            if (!MODELS.containsKey(trial.look.label())) inspectModels(client, trial.look, expected);
            if (trial.pose == Pose.REST) return; // The post-render callback captures this live view.
            if (!drawing) {
                if (player.getCooldowns().isOnCooldown(expected.getItem())) return;
                client.options.keyUse.setDown(true);
                require(client.gameMode.useItem(player, trial.view.hand).consumesAction(), "Bow draw did not start " + trial.label());
                require(player.isUsingItem() && player.getUsedItemHand() == trial.view.hand, "Wrong bow hand " + trial.label());
                drawing = true;
            } else if (player.getTicksUsingItem() >= trial.pose.limit) {
                // A software-rendered client can run several game ticks before one frame. Retry
                // the native draw rather than label a later-pose screenshot as an earlier pose.
                require(++retries <= 8, "No rendered frame in requested bow pose " + trial.label());
                release(client);
                equipped = false;
                serverAck = -1;
                equip(client, trial);
                requestedFrame = frameNumber;
            }
        } catch (Throwable failure) { fail(client, failure); }
    }

    private static void begin(Minecraft client) {
        Trial trial = TRIALS.get(index);
        expected = trial.look.stack();
        equipped = drawing = saved = false;
        retries = 0;
        requestedAt = client.player.tickCount;
        requestedFrame = frameNumber;
        serverAck = -1;
        client.options.setCameraType(trial.view.camera);
        // Retain the native hotbar in first-person views, but do not cover the
        // third-person weapon and grip with health, ability or item-name overlays.
        client.options.hideGui = trial.view.camera != CameraType.FIRST_PERSON;
        client.options.fov().set(trial.view.fov);
        equip(client, trial);
    }

    private static void equip(Minecraft client, Trial trial) {
        int expectedIndex = index;
        var stack = expected.copy();
        server(client, actor -> {
            actor.stopUsingItem();
            actor.getInventory().clearContent();
            actor.getInventory().selected = 0;
            actor.setItemInHand(trial.view.hand, stack);
            actor.getInventory().setItem(8, new ItemStack(Items.ARROW, 64));
            actor.getCooldowns().removeCooldown(stack.getItem());
            actor.teleportTo(actor.serverLevel(), .5, 221, .5, Set.of(), 0, 0);
            actor.setYRot(0); actor.setXRot(0); actor.setYHeadRot(0); actor.setYBodyRot(0);
            actor.containerMenu.broadcastChanges();
            serverAck = expectedIndex;
        });
    }

    private static void release(Minecraft client) {
        client.options.keyUse.setDown(false);
        if (client.player != null && client.player.isUsingItem() && client.gameMode != null)
            client.gameMode.releaseUsingItem(client.player);
        drawing = false;
    }

    private static void camera(ViewportEvent.ComputeCameraAngles event) {
        if (!active || done || motionStarted || index >= TRIALS.size()) return;
        Trial trial = TRIALS.get(index);
        // NeoForge 1.21.1 Camera.setup posts this event before its setPosition and
        // detached-camera move. This changes the actual orbit position as well as
        // its yaw; the post-render callback also verifies alignment to the subject.
        if (trial.view.camera != CameraType.FIRST_PERSON
                && event.getCamera().getEntity() == Minecraft.getInstance().player)
            event.setYaw(event.getYaw() + trial.view.orbit);
    }

    /** QA-only camera framing; zero leaves native first-person, motion and gameplay untouched. */
    public static double closeReviewCameraDistance() {
        if (!active || !prepared || done || motionStarted || index >= TRIALS.size()) return 0;
        View view = TRIALS.get(index).view;
        return view.camera != CameraType.FIRST_PERSON && view.label.contains("-close") ? 2.75 : 0;
    }

    private static void frame(RenderFrameEvent.Post event) {
        frameNumber++;
        if (!active || done || !prepared) return;
        if (motionStarted) {
            try { CombinedChakramMotion.frame(Minecraft.getInstance()); }
            catch (Throwable failure) { fail(Minecraft.getInstance(), failure); }
            return;
        }
        if (!equipped || saved || index >= TRIALS.size()) return;
        var client = Minecraft.getInstance();
        try {
            Trial trial = TRIALS.get(index);
            if (client.player == null || client.screen != null || client.player.tickCount - settledAt < 18
                    || frameNumber - requestedFrame < 2 || client.player.getAttackStrengthScale(0) < .99F
                    || !trial.pose.accepts(client) || trial.pose != Pose.REST && !drawing) return;
            ItemStack held = client.player.getItemInHand(trial.view.hand);
            if (!ItemStack.isSameItemSameComponents(held, expected)) return;
            var model = client.getItemRenderer().getModel(held, client.level, client.player, 31);
            String particle = model.getParticleIcon().contents().name().toString();
            require(!particle.contains("missingno"), "Missing model while rendering " + trial.label());
            if (trial.look.bow()) {
                String wanted = "uwudimension:item/" + (trial.look.detailed ? "smooth_" : "") + "cupid_bow" + trial.pose.suffix;
                require(particle.equals(wanted), "Native bow override selected " + particle + " expected " + wanted);
            }
            require(client.options.getCameraType() == trial.view.camera, "Unexpected camera type " + trial.label());
            var camera = client.gameRenderer.getMainCamera();
            double subjectAlignment = 1, subjectDistance = 0;
            if (trial.view.camera != CameraType.FIRST_PERSON) {
                Vec3 toSubject = client.player.getEyePosition(camera.getPartialTickTime()).subtract(camera.getPosition());
                subjectDistance = toSubject.length();
                subjectAlignment = toSubject.normalize().dot(Vec3.directionFromRotation(camera.getXRot(), camera.getYRot()));
                double expectedDistance = trial.view.label.contains("-close") ? 2.75 : 4;
                require(Math.abs(subjectDistance - expectedDistance) < .02 && subjectAlignment > .999,
                        "Detached camera is not looking at its subject " + trial.label()
                                + " distance=" + subjectDistance + " expected=" + expectedDistance + " alignment=" + subjectAlignment);
            }
            if (trial.view.camera != CameraType.FIRST_PERSON) {
                float facingOffset = trial.view.camera == CameraType.THIRD_PERSON_FRONT ? 180 : 0;
                double actual = Math.IEEEremainder(camera.getYRot()
                        - client.player.getViewYRot(camera.getPartialTickTime()) - facingOffset, 360);
                require(Math.abs(actual - trial.view.orbit) < .02, "Native camera failed requested orbit " + trial.label() + " actual=" + actual);
            }
            Files.createDirectories(OUT.resolve("screenshots"));
            String name = String.format("%03d-%s.png", index, trial.label());
            try (NativeImage pixels = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                require(pixels.getWidth() == 1280 && pixels.getHeight() == 720,
                        "Native comparison requires actual 1280x720 pixels");
                pixels.writeToFile(OUT.resolve("screenshots").resolve(name));
            }
            var record = new LinkedHashMap<String, Object>();
            record.put("file", "screenshots/" + name);
            record.put("item", "uwudimension:" + trial.look.id);
            record.put("look", trial.look.label());
            record.put("name", held.getHoverName().getString());
            record.put("view", trial.view.label);
            record.put("hand", trial.view.hand.name());
            record.put("pose", trial.pose.name());
            record.put("useTicks", client.player.getTicksUsingItem());
            record.put("particle", particle);
            record.put("fov", client.options.fov().get());
            record.put("camera", client.options.getCameraType().name());
            record.put("cameraPosition", camera.getPosition().toString());
            record.put("cameraYaw", camera.getYRot());
            record.put("cameraPitch", camera.getXRot());
            record.put("cameraToSubjectDistance", subjectDistance);
            record.put("cameraToSubjectAlignment", subjectAlignment);
            record.put("closeCameraObserver", closeReviewCameraDistance() > 0);
            record.put("guiScale", client.getWindow().getGuiScale());
            record.put("drawRetries", retries);
            FRAMES.add(record);
            saved = true;
            writeManifest(false, null);
            System.out.println("COMBINED VISUAL FRAME " + name + " useTicks=" + client.player.getTicksUsingItem() + " model=" + particle);
        } catch (Throwable failure) { fail(client, failure); }
    }

    private static void inspectModels(Minecraft client, Look look, ItemStack stack) {
        var model = client.getItemRenderer().getModel(stack, client.level, client.player, 31);
        var contexts = new LinkedHashMap<String, Object>();
        for (ItemDisplayContext context : List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
            var poses = new PoseStack();
            boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            var selected = ClientHooks.handleCameraTransforms(poses, model, context, left);
            var quads = new ArrayList<BakedQuad>();
            for (var pass : selected.getRenderPasses(stack, false)) {
                quads.addAll(pass.getQuads(null, null, RandomSource.create(31), ModelData.EMPTY, null));
                for (Direction face : Direction.values())
                    quads.addAll(pass.getQuads(null, face, RandomSource.create(31), ModelData.EMPTY, null));
            }
            require(!quads.isEmpty(), "Empty baked geometry " + look.label() + " " + context);
            Set<String> sprites = new LinkedHashSet<>();
            for (BakedQuad quad : quads) sprites.add(quad.getSprite().contents().name().toString());
            require(sprites.stream().noneMatch(s -> s.contains("missingno")), "Missing texture " + look.label() + " " + context + " " + sprites);
            contexts.put(context.name(), Map.of("quadCount", quads.size(), "sprites", sprites,
                    "transform", poses.last().pose().toString()));
        }
        MODELS.put(look.label(), contexts);
    }

    private static void finish(Minecraft client) throws Exception {
        require(FRAMES.size() + EXCEPTIONS.size() == TRIALS.size() && MODELS.size() == 22,
                "Incomplete visual coverage frames=" + FRAMES.size() + " exceptions=" + EXCEPTIONS.size()
                        + "/" + TRIALS.size() + " looks=" + MODELS.size());
        for (Look look : LOOKS) for (String requiredView : List.of("first-main", "third-front", "third-side", "third-rear",
                "third-front-nudge", "third-side-nudge", "third-rear-nudge", "third-front-close", "third-side-close",
                "third-rear-close", "third-front-close-nudge", "third-side-close-nudge", "third-rear-close-nudge"))
            require(FRAMES.stream().anyMatch(frame -> look.label().equals(frame.get("look"))
                            && requiredView.equals(frame.get("view")) && "REST".equals(frame.get("pose"))),
                    "Required main-hand view was not captured: " + look.label() + " " + requiredView);
        require(motionStarted && CombinedChakramMotion.successful(),
                "Native motion contains unverified trials; inspect motion/motion-results.json");
        writeManifest(true, null);
        done = true;
        System.out.println("COMBINED VISUAL CAPTURES PASS native_views=" + FRAMES.size()
                + " intentional_offhand_exceptions=" + EXCEPTIONS.size()
                + " chakrams=20 bow_looks=2 all_supported_bow_draw_poses=true missing_models=0 missing_textures=0"
                + " native_motion=" + CombinedChakramMotion.summary());
        System.out.println("COMBINED VISUAL REVIEW REQUIRED inspect retained pixels for grip, orientation, bow curvature, clipping and flicker; capture completion is not an artistic acceptance verdict");
        client.stop();
    }

    private static void writeManifest(boolean complete, Throwable failure) throws Exception {
        Files.createDirectories(OUT);
        var document = new LinkedHashMap<String, Object>();
        document.put("complete", complete);
        document.put("expectedLooks", 22);
        document.put("expectedFrames", TRIALS.size());
        document.put("nativeScreenshotFrames", FRAMES);
        document.put("intentionalOffhandExceptions", EXCEPTIONS);
        document.put("bakedModels", MODELS);
        document.put("motionEvidence", "motion/motion-results.json");
        document.put("motionSummary", CombinedChakramMotion.summary());
        document.put("captureMethod", "Screenshot.takeScreenshot on the unmodified Minecraft main render target, after the native world, held-item and HUD render");
        document.put("closeViewMethod", "QA-only Camera.setup tail observer moves the static third-person camera along its native eye-to-camera line to 2.75 blocks; normal views retain the native 4 blocks. Both distances use FOV 70. First-person, motion, models and gameplay are untouched");
        document.put("cameraNudges", "Front, side and rear each retain an actual +3 degree camera orbit at both physical distances, 4 and 2.75 blocks");
        document.put("weatherFixture", "Daylight and weather cycles disabled; clear midday retained for all stills and native motion");
        document.put("remainingManualChecks", List.of("all twenty chakram silhouettes and grip against the approved art",
                "both Cupid Bow hotbar curves point upper left at rest and in every draw pose",
                "visible clipping/intersections and flicker between the small native camera nudges",
                "first-person crop in the retained native attack, throw, catch and return frames",
                "unrecorded poses and continuous flicker outside the retained native frame samples"));
        if (failure != null) document.put("failure", failure.toString());
        Files.writeString(OUT.resolve("visual-captures.json"), new GsonBuilder().setPrettyPrinting().create().toJson(document));
    }

    private static void fail(Minecraft client, Throwable failure) {
        if (done) return;
        done = true;
        release(client);
        CombinedChakramMotion.abort(client, failure);
        System.err.println("COMBINED VISUAL FAILED " + failure);
        failure.printStackTrace();
        try { writeManifest(false, failure); } catch (Throwable secondary) { secondary.printStackTrace(); }
        client.stop();
    }

    private static void server(Minecraft client, Consumer<ServerPlayer> action) {
        var server = client.getSingleplayerServer();
        require(server != null && client.player != null, "Missing actual integrated server");
        var uuid = client.player.getUUID();
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(uuid);
                require(player != null, "Missing actual server player");
                action.accept(player);
            } catch (Throwable failure) { serverFailure = failure; }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static boolean isTwoHanded(ItemStack stack) {
        try {
            Object attributes = Class.forName("net.bettercombat.logic.WeaponRegistry")
                    .getMethod("getAttributes", ItemStack.class).invoke(null, stack);
            return attributes != null && (boolean) attributes.getClass().getMethod("isTwoHanded").invoke(attributes);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect the actual Better Combat offhand profile", failure);
        }
    }
}
