package dev.uwu.weaponcheck;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dimension.CounterArsenal;
import dev.uwu.dimension.RelicFlight;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Uses normal attack/use input and a second real connection; samples the native rendered item matrices. */
public final class ChakramTrials {
    record Trial(int index, String item, String camera, String arm, boolean physical) {
        String token() { return "trial-" + index; }
        String stem() { return "chakram-" + String.format("%03d", index) + "-" + item + "-" + camera + "-" + arm; }
    }
    record State(Trial trial, String phase, long start) {}
    private enum Phase { EQUIP, REST, ACTIVE, ACK, DONE }
    private static final List<Trial> TRIALS = new ArrayList<>();
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final Set<Integer> CAPTURE_TICKS = Set.of(0, 2, 4, 6, 8, 10, 12, 15, 18, 22, 28, 36, 44, 54, 62);
    private static Phase phase;
    private static int index, entered, completed;
    private static State state;
    private static Motion motion;
    private static Vector3f draw;
    private static ItemDisplayContext drawContext;
    private static int drawFrame = -1;
    private static int frameSerial;
    private static UUID observedOwner;
    private static boolean observerRestSaved;
    private static boolean hostRestSaved;
    private static boolean waitingComplete;
    private static long lastFrameTick = -1;
    private static volatile int serverFlights;
    private static volatile String serverReturn;
    private static volatile int serverCount;
    private static volatile int serverDamage;

    static void start(Minecraft client) throws Exception {
        List<CounterArsenal.Entry> entries = CounterArsenal.ROSTER.stream().filter(e -> e.shape().equals("chakram")).toList();
        Evidence.require(entries.size() == 20, "The actual chakram roster changed");
        long profiled = entries.stream().filter(entry -> WeaponRegistry.getAttributes(
                new ItemStack(CounterArsenal.ITEMS.get(entry.id()).get())) != null).count();
        Evidence.require(profiled == 15, "Expected 15 native Better Combat chakram profiles and five vanilla controls: " + profiled);
        for (var entry : entries) Evidence.require((WeaponRegistry.getAttributes(
                new ItemStack(CounterArsenal.ITEMS.get(entry.id()).get())) == null) == entry.id().endsWith("_sai"),
                "Native melee control classification changed: " + entry.id());
        for (var entry : entries) for (String camera : List.of("first", "third"))
            TRIALS.add(new Trial(TRIALS.size(), entry.id(), camera, "right", false));
        for (String family : List.of("twinblade", "rapier", "katana", "sai"))
            for (String camera : List.of("first", "third"))
                TRIALS.add(new Trial(TRIALS.size(), "counter_iron_" + family, camera, "left", false));
        for (String family : List.of("twinblade", "rapier", "katana", "sai"))
            for (String camera : List.of("first", "third"))
                for (String arm : List.of("right", "left"))
                    TRIALS.add(new Trial(TRIALS.size(), "counter_iron_" + family, camera, arm, true));
        Evidence.json("trial-index.json", TRIALS);
        observedOwner = client.player.getUUID();
        WeaponFixture.server(client, actor -> {
            // A native target catches physical throws at a fixed distance. It does not attack or move.
            var cow = EntityType.COW.create(actor.serverLevel());
            Evidence.require(cow != null, "Could not create a disposable native throw target");
            cow.setNoAi(true);
            cow.setSilent(true);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);
            cow.setHealth(10000);
            cow.moveTo(.5, 201, 8.5, 0, 0);
            actor.serverLevel().addFreshEntity(cow);
        });
        begin(client);
    }

    private static void begin(Minecraft client) throws Exception {
        Trial trial = TRIALS.get(index);
        ItemStack stack = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("uwudimension", trial.item)).getDefaultInstance();
        WeaponFixture.equip(client, stack, trial.token(), InteractionHand.MAIN_HAND);
        HumanoidArm arm = trial.arm.equals("left") ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        client.player.setMainArm(arm);
        WeaponFixture.server(client, actor -> actor.setMainArm(arm));
        client.options.setCameraType(trial.camera.equals("first") ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK);
        WeaponFixture.orbit(trial.camera.equals("first") ? 0 : 35);
        WeaponFixture.poseOwner(client);
        motion = new Motion(trial);
        draw = null;
        hostRestSaved = false;
        entered = client.player.tickCount;
        phase = Phase.EQUIP;
        state = new State(trial, "equip", client.level.getGameTime());
        Evidence.signal("trial.json", state);
        serverFlights = 0; serverReturn = ""; serverCount = 0; serverDamage = -1;
    }

    static boolean hostTick(Minecraft client) throws Exception {
        if (phase == Phase.DONE) return true;
        Trial trial = TRIALS.get(index);
        var player = client.player;
        WeaponFixture.poseOwner(client);
        if (trial.physical) { player.setXRot(3); player.xRotO = 3; }
        Evidence.require(player.tickCount - entered < 420, "Timed out in native chakram " + phase + " trial=" + trial);
        switch (phase) {
            case EQUIP -> {
                if (!WeaponFixture.marker(player.getMainHandItem()).equals(trial.token()) || player.tickCount - entered < 30
                        || ((MinecraftClient_BetterCombat)(Object)client).getCurrentAttackHand() != null
                        || player.getAttackStrengthScale(0) < .999F) return false;
                state = new State(trial, "rest", client.level.getGameTime());
                Evidence.signal("trial.json", state);
                phase = Phase.REST;
            }
            case REST -> {
                if (!hostRestSaved || !Files.exists(Evidence.CONTROL.resolve("rest-" + index + ".json"))) return false;
                state = new State(trial, trial.physical ? "flight" : "attack", client.level.getGameTime());
                Evidence.signal("trial.json", state);
                if (trial.physical) use(client);
                else attack(client);
                phase = Phase.ACTIVE;
            }
            case ACTIVE -> {
                int elapsed = (int)(client.level.getGameTime() - state.start);
                if (trial.physical) {
                    observeFlight(client);
                    if (player.getMainHandItem().isEmpty()) motion.emptyHeld = true;
                    if (elapsed > 8 && motion.flightSeen && WeaponFixture.marker(player.getMainHandItem()).equals(trial.token())
                            && flights(client, trial).isEmpty()) {
                        if (!motion.returnedStack) motion.returnTick = client.level.getGameTime();
                        motion.returnedStack = true;
                        motion.returnedCount = player.getMainHandItem().getCount();
                        motion.returnedDamage = player.getMainHandItem().getDamageValue();
                    }
                    if (!motion.returnedStack || client.level.getGameTime() - motion.returnTick < 10 || elapsed < 35) return false;
                    Evidence.require(motion.returnedCount == 1, "Physical return duplicated/lost stack " + trial);
                } else if (elapsed < 70 || ((MinecraftClient_BetterCombat)(Object)client).getCurrentAttackHand() != null) return false;
                motion.finish(false);
                state = new State(trial, "complete", state.start);
                Evidence.signal("trial.json", state);
                phase = Phase.ACK;
            }
            case ACK -> {
                if (!Files.exists(Evidence.CONTROL.resolve("complete-" + index + ".json"))) return false;
                RESULTS.add(motion.report());
                Evidence.json("motion-results.json", RESULTS);
                System.out.println("UWU CHAKRAM HOST TRIAL " + (Evidence.BASELINE ? "OBSERVED " : "PASS ")
                        + Evidence.JSON.toJson(motion.report()).replace('\n', ' '));
                index++;
                if (index == TRIALS.size()) {
                    Evidence.require(RESULTS.size() == 64, "Incomplete owner trial matrix");
                    System.out.println("UWU CHAKRAM HOST MATRIX PASS trials=" + RESULTS.size());
                    phase = Phase.DONE;
                    return true;
                }
                begin(client);
            }
            case DONE -> { return true; }
        }
        return false;
    }

    private static void attack(Minecraft client) throws Exception {
        var hit = client.hitResult;
        try {
            Vec3 point = client.player.getEyePosition().add(0, 0, 20);
            client.hitResult = BlockHitResult.miss(point, Direction.SOUTH, BlockPos.containing(point));
            Method action = Minecraft.class.getDeclaredMethod("startAttack");
            action.setAccessible(true);
            action.invoke(client);
        } finally { client.hitResult = hit; }
        // No direct packet or animation call: Better Combat's native input handler owns the attack.
    }

    private static void use(Minecraft client) throws Exception {
        Method action = Minecraft.class.getDeclaredMethod("startUseItem");
        action.setAccessible(true);
        action.invoke(client);
    }

    static void observerTick(Minecraft client, UUID owner) throws Exception {
        observedOwner = owner;
        var actor = client.level.getPlayerByUUID(owner);
        Evidence.require(actor instanceof RemotePlayer, "Observer is not viewing a real network RemotePlayer");
        State incoming = Evidence.read("trial.json", State.class);
        if (incoming == null) return;
        if (state == null || state.trial.index != incoming.trial.index) {
            Evidence.require(state == null || waitingComplete, "Observer missed completion of the preceding trial");
            state = incoming;
            motion = new Motion(incoming.trial);
            draw = null;
            observerRestSaved = false;
            waitingComplete = false;
            lastFrameTick = -1;
        } else state = incoming;
        if (state.phase.equals("flight")) observeFlight(client);
        if (state.phase.equals("complete") && !waitingComplete) {
            if (state.trial.physical && !flights(client, state.trial).isEmpty()) return;
            if (state.trial.physical) {
                motion.returnedStack = WeaponFixture.marker(actor.getMainHandItem()).equals(state.trial.token());
                motion.returnedCount = actor.getMainHandItem().getCount();
            }
            motion.finish(true);
            RESULTS.add(motion.report());
            Evidence.json("motion-results.json", RESULTS);
            Evidence.signal("complete-" + state.trial.index + ".json", motion.report());
            waitingComplete = true;
            completed++;
            System.out.println("UWU CHAKRAM OBSERVER TRIAL " + (Evidence.BASELINE ? "OBSERVED " : "PASS ")
                    + state.trial.index + " distance=" + motion.maximum + " clips=" + motion.clips);
        }
        if (!state.phase.equals("complete")) waitingComplete = false;
    }

    static void finishObserver() throws Exception {
        Evidence.require(completed == 64 && RESULTS.size() == 64, "Observer missed trials: " + completed);
        Evidence.json("motion-results.json", RESULTS);
        System.out.println("UWU CHAKRAM OBSERVER MATRIX PASS trials=" + completed + " real network player rendered");
    }

    private static List<RelicFlight> flights(Minecraft client, Trial trial) {
        List<RelicFlight> found = new ArrayList<>();
        for (var entity : client.level.entitiesForRendering()) if (entity instanceof RelicFlight flight
                && WeaponFixture.marker(flight.getItem()).equals(trial.token())) found.add(flight);
        return found;
    }

    private static void observeFlight(Minecraft client) {
        for (RelicFlight flight : flights(client, state.trial)) {
            motion.flightSeen = true;
            motion.flightIds.add(flight.getUUID().toString());
            motion.returnSeen |= flight.returning();
            motion.maximumFlight = Math.max(motion.maximumFlight, flight.position().distanceTo(new Vec3(.5, 202.5, .5)));
        }
    }

    public static void observeDraw(ItemStack stack, ItemDisplayContext context, PoseStack pose) {
        if (state == null || motion == null || !WeaponFixture.marker(stack).equals(state.trial.token())) return;
        if (context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND && context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) return;
        // This is after PlayerAnimator, the hand layer and the actual model's display transform.
        draw = pose.last().pose().transformPosition(new Vector3f(.5F, .5F, .5F));
        drawContext = context;
        drawFrame = frameSerial;
    }

    public static void observeAnimation(AbstractClientPlayer actor, String clip, String hand, float length, float upswing) {
        if (state == null || state.phase.equals("complete") || motion == null || observedOwner == null
                || !actor.getUUID().equals(observedOwner)) return;
        motion.clips.add(clip);
        motion.animationEvents++;
        try {
            Evidence.line("animation-events.jsonl", Evidence.JSON.toJson(Map.of("trial", state.trial.index,
                    "owner", actor.getUUID().toString(), "remote", actor instanceof RemotePlayer,
                    "clip", clip, "hand", hand, "length", length, "upswing", upswing)).replace('\n', ' '));
        } catch (Exception failure) { WeaponFixture.fail(failure); }
    }

    static void frame(Minecraft client) throws Exception {
        frameSerial++;
        if (state == null || motion == null || draw == null || client.level == null) return;
        long tick = client.level.getGameTime();
        if (state.phase.equals("rest")) {
            if (!WeaponFixture.marker((Evidence.OBSERVER ? client.level.getPlayerByUUID(observedOwner) : client.player)
                    .getMainHandItem()).equals(state.trial.token())) return;
            if ((Evidence.OBSERVER && observerRestSaved) || (!Evidence.OBSERVER && hostRestSaved)) return;
            motion.rest = new Vector3f(draw);
            motion.last = new Vector3f(draw);
            Evidence.screenshot(state.trial.stem() + "-rest");
            if (Evidence.OBSERVER) {
                observerRestSaved = true;
                Evidence.signal("rest-" + state.trial.index + ".json", Map.of("rendered", true, "context", drawContext.toString()));
            } else hostRestSaved = true;
            return;
        }
        if (!(state.phase.equals("attack") || state.phase.equals("flight")) || lastFrameTick == tick) return;
        lastFrameTick = tick;
        int elapsed = (int)(tick - state.start);
        var actor = Evidence.OBSERVER ? client.level.getPlayerByUUID(observedOwner) : client.player;
        if (actor != null) motion.vanillaSwingSeen |= actor.swinging || actor.getAttackAnim(.5F) > .01F;
        if (drawFrame >= frameSerial - 2 && motion.rest != null) {
            motion.last = new Vector3f(draw);
            motion.maximum = Math.max(motion.maximum, draw.distance(motion.rest));
            motion.samples++;
            motion.contexts.add(drawContext.toString());
            Evidence.line("native-draws.tsv", state.trial.index + "\t" + elapsed + "\t" + drawContext
                    + "\t" + draw.x + "\t" + draw.y + "\t" + draw.z + "\t" + draw.distance(motion.rest));
        }
        if (CAPTURE_TICKS.contains(elapsed) && motion.captures.add(elapsed))
            Evidence.screenshot(state.trial.stem() + "-" + state.phase + "-" + String.format("%03d", elapsed));
    }

    private static final class Motion {
        final Trial trial;
        final boolean nativeMeleeProfile;
        Vector3f rest, last;
        double maximum, maximumFlight;
        int samples, animationEvents, returnedCount, returnedDamage;
        long returnTick;
        boolean flightSeen, returnSeen, emptyHeld, returnedStack, vanillaSwingSeen;
        final Set<String> clips = new LinkedHashSet<>();
        final Set<String> contexts = new LinkedHashSet<>();
        final Set<String> flightIds = new LinkedHashSet<>();
        final Set<Integer> captures = new LinkedHashSet<>();
        Motion(Trial trial) {
            this.trial = trial;
            ItemStack stack = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("uwudimension", trial.item)).getDefaultInstance();
            nativeMeleeProfile = WeaponRegistry.getAttributes(stack) != null;
        }
        void finish(boolean observer) {
            Evidence.require(rest != null && samples >= 5, "No real rendered native item samples for " + trial);
            if (trial.physical) {
                Evidence.require(flightSeen && returnSeen && maximumFlight > 2 && flightIds.size() == 1,
                        "Physical throw was not observed outbound and returning exactly once: " + report());
                Evidence.require(returnedStack && returnedCount == 1, "Physical thrown stack did not return intact: " + report());
                if (!observer) Evidence.require(emptyHeld, "Physical throw did not remove its native held stack");
            } else if (!Evidence.BASELINE && nativeMeleeProfile) {
                Evidence.require(animationEvents > 0, "Native BC animation event was not observed: " + trial);
                Evidence.require(clips.stream().anyMatch(clip -> clip.contains("chakram")), "Native attack retained a hand-only clip: " + report());
                // Successful baseline hand-only slashes already reach 1.52; native item throws exceed 2.88.
                Evidence.require(maximum > 2.0, "Native rendered chakram did not leave the hand visibly: " + report());
                Evidence.require(last != null && last.distance(rest) < .45, "Native rendered chakram did not return to its grip: " + report());
            } else if (!nativeMeleeProfile) {
                Evidence.require(vanillaSwingSeen, "The preserved native vanilla-melee control did not swing: " + report());
            }
            Evidence.require(captures.size() >= 5, "Too few actual in-game motion captures for " + trial);
        }
        Map<String, Object> report() {
            return Map.ofEntries(Map.entry("trial", trial), Map.entry("role", Evidence.OBSERVER ? "observer" : "host"),
                    Map.entry("native_melee_profile", nativeMeleeProfile), Map.entry("vanilla_swing_seen", vanillaSwingSeen),
                    Map.entry("samples", samples), Map.entry("animation_events", animationEvents), Map.entry("clips", clips),
                    Map.entry("native_contexts", contexts), Map.entry("maximum_draw_displacement", maximum),
                    Map.entry("final_draw_displacement", rest == null || last == null ? -1 : last.distance(rest)),
                    Map.entry("physical_outbound_distance", maximumFlight), Map.entry("physical_seen", flightSeen),
                    Map.entry("physical_return_seen", returnSeen), Map.entry("physical_entity_uuids", flightIds),
                    Map.entry("empty_held_during_flight", emptyHeld), Map.entry("returned_stack", returnedStack),
                    Map.entry("returned_count", returnedCount), Map.entry("returned_damage", returnedDamage), Map.entry("captures", captures));
        }
    }
}
