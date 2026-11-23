package dev.uwu.hotbarcheck;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import dev.uwu.dimension.RelicFlight;
import dev.uwu.dimension.SharedAbilityHotbar;
import dev.uwu.hotbarcheck.mixin.MouseCallbacks;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Observes native mouse-driven attacks and physical throws; never calls an ability or animation. */
final class CombinedChakramMotion {
    private static final String MARKER = "uwu_visual_motion_trial";
    private static final String BASE = "c1c0802be13d0cd5a01149ffa357018f7a863c5c";
    private static final int WALK_WARMUP_TICKS = 12;
    private static final int WALK_MIN_TICKS = 72;
    private static final int WALK_MAX_TICKS = 120;
    private static final double REQUIRED_BOB_PHASE_SPAN = 6.0; // Three complete sine periods, two dips each.
    private static final double MAX_RETAINED_PHASE_GAP = .25;
    private enum Action { ATTACK, USE }
    private enum Stage { EQUIP, REST, WALK, WALK_STOP, SELECT, ACTIVE, RETURN, DONE }
    private record Trial(String id, InteractionHand hand, Action action) {
        String label() { return id + "-" + (hand == InteractionHand.MAIN_HAND ? "main" : "offhand")
                + "-" + action.name().toLowerCase(); }
    }
    private record Combat(boolean active, float progress, int upswing, int combo, String clip, float vanilla) {}
    private record Bob(float walk, float oldWalk, float amount, float oldAmount, float partial,
                       double phase, double sway, double dip) {}
    private static final List<Trial> TRIALS = new ArrayList<>();
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final List<Map<String, Object>> FRAMES = new ArrayList<>();
    private static final Set<String> CAPTURED = new LinkedHashSet<>();
    private static final Set<String> FLIGHT_IDS = new LinkedHashSet<>();
    private static final Set<String> CLIPS = new LinkedHashSet<>();
    private static final List<Map<String, Object>> SAMPLES = new ArrayList<>();
    private static Path out;
    private static Method currentHand, swingProgress, upswingTicks, comboCount, attributes;
    private static Field walkDistanceField, oldWalkDistanceField, bobField, oldBobField;
    private static Stage stage;
    private static int index, begunAt, settledAt = -1, selectedAt, inputAt, returnAt = -1, lastFrameTick = -1;
    private static int firstFrame, activeFrames, verified, walkStartedAt, walkStoppedAt;
    private static int walkFrames, steadyWalkFrames, sequenceFrames, bestSequenceFrames, backstopZ = Integer.MIN_VALUE;
    private static double sequenceFirstPhase, sequenceLastPhase, sequenceSpan, bestSequenceSpan, maximumPhaseGap;
    private static boolean sequenceDipA, sequenceDipB, sequenceSwayLeft, sequenceSwayRight;
    private static boolean fullNativeWalkCaptured;
    private static Vec3 walkOrigin;
    private static double walkedDistance;
    private static boolean walkStoppedCaptured;
    private static boolean started, preCaptured, returnCaptured, actionSeen, emptySeen, flightSeen, returningSeen;
    private static boolean profiled;
    private static float maximumSwing;
    private static double maximumFlight;
    private static ItemStack expected;
    private static String token;
    private static volatile int serverAck = -1;
    private static volatile Throwable serverFailure;

    private CombinedChakramMotion() {}

    static void start(Minecraft client, Path evidence) throws Exception {
        require(!started, "Native motion probe started twice");
        started = true;
        out = evidence.resolve("motion");
        Class<?> api = Class.forName("net.bettercombat.api.MinecraftClient_BetterCombat");
        require(api.isInstance(client), "The shipped Better Combat client extension is absent");
        currentHand = api.getMethod("getCurrentAttackHand");
        swingProgress = api.getMethod("getSwingProgress");
        upswingTicks = api.getMethod("getUpswingTicks");
        comboCount = api.getMethod("getComboCount");
        attributes = Class.forName("net.bettercombat.logic.WeaponRegistry").getMethod("getAttributes", ItemStack.class);
        // Read the unmodified vanilla inputs; never write walking/bob fields or synthetic transforms.
        walkDistanceField = field(client.player.getClass(), "walkDist");
        oldWalkDistanceField = field(client.player.getClass(), "walkDistO");
        bobField = field(client.player.getClass(), "bob");
        oldBobField = field(client.player.getClass(), "oBob");
        for (String material : List.of("iron", "gold", "diamond", "netherite", "runic")) {
            for (String shape : List.of("katana", "rapier", "sai", "twinblade")) {
                String id = "counter_" + material + "_" + shape;
                TRIALS.add(new Trial(id, InteractionHand.MAIN_HAND, Action.ATTACK));
                TRIALS.add(new Trial(id, InteractionHand.MAIN_HAND, Action.USE));
                // The shipped katana/twinblade profiles intentionally mask offhand rendering.
                if (shape.equals("rapier") || shape.equals("sai"))
                    TRIALS.add(new Trial(id, InteractionHand.OFF_HAND, Action.USE));
            }
        }
        require(TRIALS.size() == 50, "Expected 20 attacks and 30 supported physical-use cases");
        server(client, actor -> {
            var level = actor.serverLevel();
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, actor.server);
            level.setWeatherParameters(1_000_000, 0, false, false);
            // A level, ordinary-block corridor gives W enough room for multiple complete bob cycles.
            // It is only a hosted fixture; movement and rendering remain native.
            for (int x = -4; x <= 4; x++) for (int z = -2; z <= 40; z++)
                level.setBlockAndUpdate(new BlockPos(x, 220, z), Blocks.SMOOTH_QUARTZ.defaultBlockState());
        });
        begin(client);
        write(false);
        System.out.println("COMBINED VISUAL MOTION START cases=50 attacks=20 physical_uses=30 supported_offhand=10");
    }

    private static void begin(Minecraft client) throws Exception {
        Trial trial = TRIALS.get(index);
        release(client);
        client.setWindowActive(true);
        client.mouseHandler.grabMouse();
        client.options.setCameraType(CameraType.FIRST_PERSON);
        client.options.fov().set(70);
        client.options.guiScale().set(3);
        client.options.hideGui = false;
        client.options.bobView().set(false);
        client.player.setMainArm(HumanoidArm.RIGHT);
        client.resizeDisplay();
        var id = ResourceLocation.fromNamespaceAndPath("uwudimension", trial.id);
        require(BuiltInRegistries.ITEM.containsKey(id), "Missing motion target " + id);
        expected = new ItemStack(BuiltInRegistries.ITEM.get(id));
        token = "combined-visual-motion-" + index;
        CustomData.update(DataComponents.CUSTOM_DATA, expected, nbt -> nbt.putString(MARKER, token));
        profiled = attributes.invoke(null, expected) != null;
        if (trial.action == Action.ATTACK)
            require(profiled != trial.id.endsWith("_sai"), "Unexpected native chakram melee profile " + trial.id);
        stage = Stage.EQUIP;
        begunAt = client.player.tickCount;
        settledAt = -1;
        returnAt = -1;
        inputAt = -1;
        walkOrigin = null;
        walkedDistance = 0;
        walkFrames = steadyWalkFrames = sequenceFrames = bestSequenceFrames = 0;
        sequenceFirstPhase = sequenceLastPhase = sequenceSpan = bestSequenceSpan = maximumPhaseGap = 0;
        sequenceDipA = sequenceDipB = sequenceSwayLeft = sequenceSwayRight = fullNativeWalkCaptured = false;
        walkStoppedCaptured = false;
        lastFrameTick = -1;
        activeFrames = 0;
        firstFrame = FRAMES.size();
        preCaptured = returnCaptured = actionSeen = emptySeen = flightSeen = returningSeen = false;
        maximumSwing = 0;
        maximumFlight = 0;
        CAPTURED.clear(); FLIGHT_IDS.clear(); CLIPS.clear(); SAMPLES.clear();
        serverAck = -1;
        int ordinal = index;
        var stack = expected.copy();
        server(client, actor -> {
            actor.stopUsingItem();
            actor.removeAllEffects();
            actor.setSprinting(false);
            actor.getFoodData().setFoodLevel(20);
            if (backstopZ != Integer.MIN_VALUE) {
                for (int x = -2; x <= 3; x++) for (int y = 221; y <= 225; y++)
                    actor.serverLevel().setBlockAndUpdate(new BlockPos(x, y, backstopZ), Blocks.AIR.defaultBlockState());
                backstopZ = Integer.MIN_VALUE;
            }
            // Cleanup belongs to the next fixture, never to the preceding trial's acceptance.
            for (var flight : actor.serverLevel().getEntitiesOfClass(RelicFlight.class,
                    actor.getBoundingBox().inflate(160), f -> f.getOwner() != null
                            && f.getOwner().getUUID().equals(actor.getUUID()))) flight.discard();
            actor.getInventory().clearContent();
            actor.getInventory().selected = 0;
            actor.setItemInHand(trial.hand, stack);
            actor.getCooldowns().removeCooldown(stack.getItem());
            actor.setMainArm(HumanoidArm.RIGHT);
            actor.teleportTo(actor.serverLevel(), .5, 221, .5, Set.of(), 0, 0);
            actor.setYRot(0); actor.setXRot(0); actor.setYHeadRot(0); actor.setYBodyRot(0);
            actor.containerMenu.broadcastChanges();
            serverAck = ordinal;
        });
    }

    static boolean tick(Minecraft client) throws Exception {
        if (stage == Stage.DONE) return true;
        if (serverFailure != null) throw new AssertionError("Native motion fixture server action failed", serverFailure);
        if (client.player == null || client.level == null || client.screen != null) return false;
        client.setWindowActive(true);
        Trial trial = TRIALS.get(index);
        var combat = combat(client);
        int tick = client.player.tickCount;
        if (tick - begunAt > 400) {
            end(client, "Timed out while observing native " + stage + "; no recovery was simulated");
            return stage == Stage.DONE;
        }
        if (stage == Stage.EQUIP) {
            ItemStack raw = trial.hand == InteractionHand.OFF_HAND
                    ? client.player.getInventory().getItem(40) : client.player.getMainHandItem();
            if (serverAck != index || !matches(raw) || !matches(client.player.getItemInHand(trial.hand))) return false;
            if (settledAt < 0) settledAt = tick;
            // Match the proven pinned-dependency fixture: no current native attack hand,
            // restored vanilla swing/cooldown. Do not assume a particular idle progress value.
            if (tick - settledAt < 30 || combat.active || combat.vanilla > .001F
                    || client.player.getAttackStrengthScale(0) < .99F) return false;
            stage = Stage.REST;
        } else if (stage == Stage.REST && preCaptured) {
            if (trial.action == Action.USE) {
                walkOrigin = client.player.position();
                walkStartedAt = tick;
                client.options.bobView().set(true);
                key(client, GLFW.GLFW_KEY_W, GLFW.GLFW_PRESS);
                stage = Stage.WALK;
            } else select(client);
        } else if (stage == Stage.WALK) {
            walkedDistance = Math.max(walkedDistance, client.player.position().distanceTo(walkOrigin));
            SAMPLES.add(sample(client, combat, flights(client)));
            if ((tick - walkStartedAt >= WALK_MIN_TICKS && fullNativeWalkCaptured)
                    || tick - walkStartedAt >= WALK_MAX_TICKS) {
                key(client, GLFW.GLFW_KEY_W, GLFW.GLFW_RELEASE);
                walkStoppedAt = tick;
                stage = Stage.WALK_STOP;
            }
        } else if (stage == Stage.WALK_STOP) {
            walkedDistance = Math.max(walkedDistance, client.player.position().distanceTo(walkOrigin));
            SAMPLES.add(sample(client, combat, flights(client)));
            if (walkStoppedCaptured) {
                client.options.bobView().set(false);
                select(client);
            }
        } else if (stage == Stage.SELECT && tick - selectedAt >= 3) {
            if (trial.hand == InteractionHand.MAIN_HAND)
                require(SharedAbilityHotbar.selectedCell() == 0, "Native number 1 did not select ordinary attack");
            inputAt = tick;
            stage = Stage.ACTIVE;
            int button = trial.action == Action.ATTACK ? GLFW.GLFW_MOUSE_BUTTON_LEFT : GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            mouse(client, button, GLFW.GLFW_PRESS);
            mouse(client, button, GLFW.GLFW_RELEASE);
            System.out.println("COMBINED VISUAL MOTION INPUT " + index + " " + trial.label() + " native_button=" + button);
        } else if (stage == Stage.ACTIVE) {
            var flights = flights(client);
            observe(client, combat, flights);
            SAMPLES.add(sample(client, combat, flights));
            int elapsed = tick - inputAt;
            if (trial.action == Action.ATTACK) {
                if (elapsed >= 48 && !combat.active && combat.vanilla < .001F
                        && client.player.getAttackStrengthScale(0) >= .99F && matches(client.player.getItemInHand(trial.hand)))
                    stage = Stage.RETURN;
            } else {
                if (flightSeen && emptySeen && flights.isEmpty() && matches(client.player.getItemInHand(trial.hand))) {
                    if (returnAt < 0) returnAt = tick;
                    if (elapsed >= 35 && tick - returnAt >= 18) stage = Stage.RETURN;
                }
            }
        } else if (stage == Stage.RETURN && returnCaptured) {
            String issue = null;
            if (trial.action == Action.ATTACK && (!actionSeen || activeFrames < 2))
                issue = "Native attack lacked two distinct rendered active phases; observed=" + actionSeen + " frames=" + activeFrames;
            if (trial.action == Action.USE && (!flightSeen || !emptySeen || FLIGHT_IDS.size() != 1
                    || activeFrames < 1 || !CAPTURED.contains("return-reequip")))
                issue = "Physical throw/catch coverage incomplete; flight=" + flightSeen + " empty=" + emptySeen
                        + " ids=" + FLIGHT_IDS.size() + " flightFrames=" + activeFrames;
            if (trial.action == Action.USE && client.player.getItemInHand(trial.hand).getCount() != 1)
                issue = "Physical return did not restore exactly one held stack";
            if (trial.action == Action.USE && (walkedDistance < 8 || !walkStoppedCaptured || !fullNativeWalkCaptured))
                issue = "Full native walking-cycle coverage incomplete; distance=" + walkedDistance
                        + " frames=" + walkFrames + " bestContinuousPhaseSpan=" + bestSequenceSpan
                        + " required=" + REQUIRED_BOB_PHASE_SPAN + " stop=" + walkStoppedCaptured;
            end(client, issue);
        }
        return stage == Stage.DONE;
    }

    static void frame(Minecraft client) throws Exception {
        if (!started || stage == Stage.DONE || client.player == null || client.level == null || client.screen != null) return;
        int tick = client.player.tickCount;
        if (tick == lastFrameTick) return;
        var combat = combat(client);
        var flights = flights(client);
        Trial trial = TRIALS.get(index);
        if (stage == Stage.REST && !preCaptured && matches(client.player.getItemInHand(trial.hand))) {
            capture(client, "pre", combat, flights);
            preCaptured = true;
        } else if (stage == Stage.WALK && client.player.position().distanceTo(walkOrigin) > .02) {
            require(client.options.bobView().get() && !combat.active && combat.vanilla < .001F
                            && flights.isEmpty() && matches(client.player.getItemInHand(trial.hand)),
                    "Walking capture must show the actual held item without an attack or flight");
            capture(client, String.format("walk-%03d", tick - walkStartedAt), combat, flights);
            observeWalkFrame(client);
        } else if (stage == Stage.WALK_STOP && tick - walkStoppedAt >= 10
                && client.player.getDeltaMovement().horizontalDistanceSqr() < .0001 && !walkStoppedCaptured) {
            capture(client, "walk-stop", combat, flights);
            walkStoppedCaptured = true;
        } else if (stage == Stage.ACTIVE) {
            observe(client, combat, flights);
            if (trial.action == Action.ATTACK) {
                float progress = profiled ? combat.progress : combat.vanilla;
                boolean nativeActive = profiled ? combat.active : combat.vanilla > .001F;
                String phase = progress < .34F ? "attack-early" : progress < .67F ? "attack-mid" : "attack-late";
                if (nativeActive && !CAPTURED.contains(phase)) {
                    capture(client, phase, combat, flights);
                    activeFrames++;
                }
            } else if (!flights.isEmpty()) {
                String phase = flights.stream().anyMatch(RelicFlight::returning) ? "use-returning"
                        : flights.stream().anyMatch(RelicFlight::landed) ? "use-landed" : "use-outbound";
                if (!CAPTURED.contains(phase)) {
                    capture(client, phase, combat, flights);
                    activeFrames++;
                }
            } else if (flightSeen && emptySeen && matches(client.player.getItemInHand(trial.hand))
                    && !CAPTURED.contains("return-reequip")) capture(client, "return-reequip", combat, flights);
        } else if (stage == Stage.RETURN && !returnCaptured && matches(client.player.getItemInHand(trial.hand))) {
            capture(client, trial.action == Action.ATTACK ? "attack-return" : "return-settled", combat, flights);
            returnCaptured = true;
        }
    }

    private static void observeWalkFrame(Minecraft client) {
        walkFrames++;
        if (client.player.tickCount - walkStartedAt < WALK_WARMUP_TICKS) return;
        Bob nativeBob = bob(client);
        require(!client.player.isSprinting() && client.player.onGround(),
                "Walking cycle must be ordinary grounded walking, without sprint or jump");
        require(nativeBob.amount > .05F, "Native walking bob amplitude never became active");
        steadyWalkFrames++;
        double gap = sequenceFrames == 0 ? 0 : Math.abs(nativeBob.phase - sequenceLastPhase);
        maximumPhaseGap = Math.max(maximumPhaseGap, gap);
        if (sequenceFrames == 0 || gap > MAX_RETAINED_PHASE_GAP) {
            sequenceFirstPhase = nativeBob.phase;
            sequenceFrames = 0;
            sequenceDipA = sequenceDipB = sequenceSwayLeft = sequenceSwayRight = false;
        }
        sequenceLastPhase = nativeBob.phase;
        sequenceFrames++;
        sequenceSpan = Math.abs(sequenceLastPhase - sequenceFirstPhase);
        sequenceDipA |= nativeBob.dip > .97;
        sequenceDipB |= nativeBob.dip < -.97;
        sequenceSwayLeft |= nativeBob.sway < -.97;
        sequenceSwayRight |= nativeBob.sway > .97;
        if (sequenceSpan > bestSequenceSpan) {
            bestSequenceSpan = sequenceSpan;
            bestSequenceFrames = sequenceFrames;
        }
        fullNativeWalkCaptured |= sequenceSpan >= REQUIRED_BOB_PHASE_SPAN
                && sequenceDipA && sequenceDipB && sequenceSwayLeft && sequenceSwayRight;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field result = current.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException absent) { /* The native field may belong to a superclass. */ }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Bob bob(Minecraft client) {
        try {
            float walk = walkDistanceField.getFloat(client.player);
            float oldWalk = oldWalkDistanceField.getFloat(client.player);
            float amount = bobField.getFloat(client.player);
            float oldAmount = oldBobField.getFloat(client.player);
            float partial = client.gameRenderer.getMainCamera().getPartialTickTime();
            double phase = -(walk + (walk - oldWalk) * partial);
            return new Bob(walk, oldWalk, amount, oldAmount, partial, phase,
                    Math.sin(phase * Math.PI), Math.cos(phase * Math.PI));
        } catch (IllegalAccessException failure) {
            throw new AssertionError("Could not read the native walking/bob observation fields", failure);
        }
    }

    private static void observe(Minecraft client, Combat combat, List<RelicFlight> flights) {
        Trial trial = TRIALS.get(index);
        if (trial.action == Action.ATTACK) {
            boolean moving = profiled ? combat.active : combat.vanilla > .001F;
            actionSeen |= moving;
            maximumSwing = Math.max(maximumSwing, profiled ? combat.progress : combat.vanilla);
            if (!combat.clip.isEmpty()) CLIPS.add(combat.clip);
        } else {
            emptySeen |= client.player.getItemInHand(trial.hand).isEmpty();
            for (var flight : flights) {
                flightSeen = true;
                FLIGHT_IDS.add(flight.getUUID().toString());
                returningSeen |= flight.returning();
                maximumFlight = Math.max(maximumFlight, flight.position().distanceTo(client.player.getEyePosition()));
            }
        }
    }

    private static Combat combat(Minecraft client) throws Exception {
        Object hand = currentHand.invoke(client);
        String clip = "";
        if (hand != null) {
            Object attack = hand.getClass().getMethod("attack").invoke(hand);
            clip = String.valueOf(attack.getClass().getMethod("animation").invoke(attack));
        }
        float partial = client.gameRenderer.getMainCamera().getPartialTickTime();
        return new Combat(hand != null, ((Number)swingProgress.invoke(client)).floatValue(),
                ((Number)upswingTicks.invoke(client)).intValue(), ((Number)comboCount.invoke(client)).intValue(),
                clip, client.player.getAttackAnim(partial));
    }

    private static List<RelicFlight> flights(Minecraft client) {
        return client.level.getEntitiesOfClass(RelicFlight.class, client.player.getBoundingBox().inflate(160),
                f -> f.getOwner() != null && f.getOwner().getUUID().equals(client.player.getUUID()) && matches(f.getItem()));
    }

    private static Map<String, Object> sample(Minecraft client, Combat combat, List<RelicFlight> flights) {
        var result = new LinkedHashMap<String, Object>();
        Trial trial = TRIALS.get(index);
        var held = client.player.getItemInHand(trial.hand);
        result.put("playerTick", client.player.tickCount);
        result.put("gameTime", client.level.getGameTime());
        result.put("elapsedTicks", inputAt < 0 ? -1 : client.player.tickCount - inputAt);
        result.put("fixtureStage", stage.name());
        result.put("playerPosition", client.player.position().toString());
        result.put("playerVelocity", client.player.getDeltaMovement().toString());
        result.put("walkDisplacement", walkOrigin == null ? 0 : client.player.position().distanceTo(walkOrigin));
        result.put("nativeViewBobbing", client.options.bobView().get());
        Bob nativeBob = bob(client);
        result.put("nativeBob", Map.of("walkDist", nativeBob.walk, "walkDistO", nativeBob.oldWalk,
                "bob", nativeBob.amount, "oBob", nativeBob.oldAmount, "partialTick", nativeBob.partial,
                "phase", nativeBob.phase, "swaySine", nativeBob.sway, "dipCosine", nativeBob.dip));
        result.put("walkElapsedTicks", walkOrigin == null ? -1 : client.player.tickCount - walkStartedAt);
        result.put("sprinting", client.player.isSprinting());
        result.put("onGround", client.player.onGround());
        result.put("betterCombatActive", combat.active);
        result.put("betterCombatProgress", combat.progress);
        result.put("betterCombatUpswingTicks", combat.upswing);
        result.put("betterCombatCombo", combat.combo);
        result.put("nativeAnimation", combat.clip);
        result.put("vanillaSwingProgress", combat.vanilla);
        result.put("attackStrength", client.player.getAttackStrengthScale(0));
        result.put("heldItem", BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
        result.put("heldCount", held.getCount());
        result.put("heldMatchesTrial", matches(held));
        result.put("heldDamage", held.getDamageValue());
        result.put("usingItem", client.player.isUsingItem());
        result.put("flights", flights.stream().map(f -> Map.of("uuid", f.getUUID().toString(),
                "position", f.position().toString(), "returning", f.returning(), "landed", f.landed(),
                "distanceFromEye", f.position().distanceTo(client.player.getEyePosition()))).toList());
        return result;
    }

    private static void capture(Minecraft client, String phase, Combat combat, List<RelicFlight> flights) throws Exception {
        require(client.options.getCameraType() == CameraType.FIRST_PERSON && client.options.fov().get() == 70
                        && client.getWindow().getGuiScale() == 3 && !client.options.hideGui,
                "Motion framing must match 1280x720/FOV70/GUI3 first-person baseline");
        Trial trial = TRIALS.get(index);
        String filename = String.format("%03d-%s-%s.png", index, trial.label(), phase);
        Files.createDirectories(out.resolve("screenshots"));
        try (NativeImage pixels = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            require(pixels.getWidth() == 1280 && pixels.getHeight() == 720, "Motion screenshots must be native 1280x720");
            pixels.writeToFile(out.resolve("screenshots").resolve(filename));
        }
        var record = new LinkedHashMap<String, Object>(sample(client, combat, flights));
        record.put("file", "motion/screenshots/" + filename);
        record.put("trial", index);
        record.put("item", "uwudimension:" + trial.id);
        record.put("hand", trial.hand.name());
        record.put("action", trial.action.name());
        record.put("phase", phase);
        record.put("camera", "FIRST_PERSON");
        record.put("fov", 70);
        record.put("guiScale", 3);
        record.put("cameraYaw", client.gameRenderer.getMainCamera().getYRot());
        record.put("cameraPitch", client.gameRenderer.getMainCamera().getXRot());
        record.put("cameraPartialTick", client.gameRenderer.getMainCamera().getPartialTickTime());
        FRAMES.add(record);
        CAPTURED.add(phase);
        lastFrameTick = client.player.tickCount;
        System.out.println("COMBINED VISUAL MOTION FRAME " + filename + " native_clip=" + combat.clip
                + " progress=" + combat.progress + " vanilla=" + combat.vanilla);
    }

    private static void end(Minecraft client, String issue) throws Exception {
        Trial trial = TRIALS.get(index);
        release(client);
        var result = new LinkedHashMap<String, Object>();
        result.put("trial", index);
        result.put("item", "uwudimension:" + trial.id);
        result.put("hand", trial.hand.name());
        result.put("action", trial.action.name());
        result.put("input", "Native MouseHandler.onPress PRESS/RELEASE via existing test-only invoker; number 1 via native keyboard callback");
        result.put("status", issue == null ? "CAPTURED" : "UNVERIFIED");
        result.put("issue", issue == null ? "" : issue);
        result.put("betterCombatProfile", profiled);
        result.put("nativeActionObserved", actionSeen);
        result.put("nativeClips", List.copyOf(CLIPS));
        result.put("activeFrameCount", activeFrames);
        result.put("physicalFlightSeen", flightSeen);
        result.put("emptyHeldDuringFlight", emptySeen);
        result.put("inwardReturnStateSeen", returningSeen);
        result.put("physicalFlightUuids", List.copyOf(FLIGHT_IDS));
        result.put("maximumFlightDistance", maximumFlight);
        result.put("nativeWalkDistance", walkedDistance);
        result.put("nativeWalkStopCaptured", walkStoppedCaptured);
        result.put("nativeWalkFrames", walkFrames);
        result.put("nativeSteadyWalkFrames", steadyWalkFrames);
        result.put("bestContinuousBobPhaseSpan", bestSequenceSpan);
        result.put("bestContinuousBobFrames", bestSequenceFrames);
        result.put("maximumObservedBobPhaseGap", maximumPhaseGap);
        result.put("fullNativeWalkingCyclesCaptured", fullNativeWalkCaptured);
        result.put("returnedCount", client.player.getItemInHand(trial.hand).getCount());
        result.put("captures", List.copyOf(FRAMES.subList(firstFrame, FRAMES.size())));
        result.put("nativeTickSamples", List.copyOf(SAMPLES));
        RESULTS.add(result);
        if (issue == null) verified++;
        System.out.println("COMBINED VISUAL MOTION " + (issue == null ? "CAPTURED " : "UNVERIFIED ")
                + trial.label() + (issue == null ? "" : ": " + issue));
        index++;
        if (index == TRIALS.size()) {
            stage = Stage.DONE;
            write(true);
            System.out.println("COMBINED VISUAL MOTION COMPLETE " + summary());
            if (successful()) System.out.println("COMBINED VISUAL MOTION PASS " + summary());
        } else {
            write(false);
            begin(client);
        }
    }

    private static boolean matches(ItemStack stack) {
        return !stack.isEmpty() && stack.is(expected.getItem())
                && token.equals(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(MARKER));
    }

    static boolean successful() { return started && stage == Stage.DONE && verified == 50 && RESULTS.size() == 50; }

    static Map<String, Object> summary() {
        return Map.of("started", started, "expectedCases", 50, "completedCases", RESULTS.size(),
                "capturedCases", verified, "unverifiedCases", RESULTS.size() - verified, "nativeFrames", FRAMES.size());
    }

    private static void write(boolean complete) throws Exception {
        if (out == null) return;
        Files.createDirectories(out);
        var document = new LinkedHashMap<String, Object>();
        document.put("complete", complete);
        document.put("summary", summary());
        document.put("baselineCommit", BASE);
        document.put("clippingSourceCommit", "9a6e699158992117e6b9ac4ae78fa83361518784");
        document.put("testedCombinedParent", "ea2d6d1ab2b2a35eec6026a3e2048ab84a57e15b");
        document.put("walkingCaptureContract", Map.of("warmupTicks", WALK_WARMUP_TICKS,
                "minimumWalkTicks", WALK_MIN_TICKS, "maximumWalkTicks", WALK_MAX_TICKS,
                "requiredContinuousPhaseSpan", REQUIRED_BOB_PHASE_SPAN,
                "maximumPhaseGap", MAX_RETAINED_PHASE_GAP, "minimumAbsoluteExtremum", .97,
                "sampling", "First rendered native frame per game tick; every retained frame keeps the complete HUD and viewport",
                "phaseDefinition", "-(walkDist + (walkDist-walkDistO)*cameraPartialTick); a full bob period spans 2",
                "verdict", "Coverage only; manual native-pixel review decides viewport and offhand-slot intersections"));
        document.put("runtimeCandidateSha256", System.getProperty("uwu.check.candidateSha256"));
        document.put("cases", RESULTS);
        document.put("frames", FRAMES);
        document.put("observability", "Better Combat public client API exposes the real current attack, progress, upswing and clip; vanilla controls use the player's native swing progress. Physical use reads real RelicFlight UUID/state and marked stack custody.");
        document.put("limits", List.of("Capture completion is not a no-clipping or no-flicker verdict",
                "Native attack/physical throw can intentionally carry the item outside the first-person viewport",
                "Fast inward-flight frames may be missed; inwardReturnStateSeen reports actual observation separately",
                "Crescent/Twin offhand-only modes remain masked by their shipped two-handed profiles",
                "Movement and action sequences are first-person only; third-person action transitions remain unverified",
                "Third-person cuff overlap and unrecorded animations remain separate from first-person framing"));
        Files.writeString(out.resolve("motion-results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(document));
    }

    static void abort(Minecraft client, Throwable failure) {
        if (!started) return;
        release(client);
        try {
            write(false);
            Files.writeString(out.resolve("failure.txt"), failure.toString());
        } catch (Exception secondary) { secondary.printStackTrace(); }
    }

    private static void select(Minecraft client) {
        if (TRIALS.get(index).action == Action.USE) {
            // Add a normal physical return backstop only after all walking and stop frames.
            // It cannot obstruct or artificially constrain the recorded walking cycle.
            server(client, actor -> {
                backstopZ = (int)Math.floor(actor.getZ()) + 9;
                for (int x = -2; x <= 3; x++) for (int y = 221; y <= 225; y++)
                    actor.serverLevel().setBlockAndUpdate(new BlockPos(x, y, backstopZ), Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
            });
        }
        key(client, GLFW.GLFW_KEY_1, GLFW.GLFW_PRESS);
        key(client, GLFW.GLFW_KEY_1, GLFW.GLFW_RELEASE);
        selectedAt = client.player.tickCount;
        stage = Stage.SELECT;
    }

    private static void key(Minecraft client, int key, int action) {
        client.keyboardHandler.keyPress(client.getWindow().getWindow(), key, 0, action, 0);
    }

    private static void mouse(Minecraft client, int button, int action) {
        ((MouseCallbacks)(Object)client.mouseHandler).hotbarCheck$press(client.getWindow().getWindow(), button, action, 0);
    }

    private static void release(Minecraft client) {
        mouse(client, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE);
        mouse(client, GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_RELEASE);
        key(client, GLFW.GLFW_KEY_W, GLFW.GLFW_RELEASE);
        client.options.bobView().set(false);
        if (client.player != null && client.player.isUsingItem() && client.gameMode != null)
            client.gameMode.releaseUsingItem(client.player);
    }

    private static void server(Minecraft client, Consumer<ServerPlayer> action) {
        var integrated = client.getSingleplayerServer();
        require(integrated != null && client.player != null, "Missing actual integrated server for motion");
        var uuid = client.player.getUUID();
        integrated.execute(() -> {
            try {
                var actor = integrated.getPlayerList().getPlayer(uuid);
                require(actor != null, "Missing native motion server player");
                action.accept(actor);
            } catch (Throwable failure) { serverFailure = failure; }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
