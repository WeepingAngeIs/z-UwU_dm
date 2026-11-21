package dev.uwu.weaponcheck;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.bettercombat.api.MinecraftClient_BetterCombat;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Bounded consecutive native-input controls for both newly selected chakram combo clips. */
public final class ChakramCombos {
    private static final String LEFT = "simplyswords:one_handed_chakram_throw_left";
    private static final String RIGHT = "simplyswords:one_handed_throw_chakram_right";
    private static final Set<Integer> CAPTURE_TICKS = Set.of(0, 2, 4, 6, 8, 10, 12, 15, 18, 22, 28, 36, 44, 54, 62);
    record Trial(int index, String item, String camera, String arm) {
        String token() { return "combo-" + index; }
        String stem() { return "chakram-combo-" + String.format("%03d", index) + "-" + item + "-" + camera + "-" + arm; }
    }
    record State(Trial trial, String phase) {}
    private enum Phase { EQUIP, REST, ACTIVE, ACK, DONE }
    private static final List<Trial> TRIALS = new ArrayList<>();
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static Phase phase;
    private static State state;
    private static Motion motion;
    private static UUID owner;
    private static int index, entered, completed, frameSerial, drawFrame = -1;
    private static Vector3f draw;
    private static ItemDisplayContext drawContext;
    private static boolean restSaved, waitingComplete;
    private static long lastFrameTick = -1;

    static void start(Minecraft client) throws Exception {
        Evidence.require(!Evidence.BASELINE, "Combo regression controls require the candidate");
        for (String item : List.of("counter_iron_twinblade", "counter_iron_katana"))
            for (String camera : List.of("first", "third"))
                TRIALS.add(new Trial(TRIALS.size(), item, camera, "right"));
        owner = client.player.getUUID();
        Evidence.json("combo-trial-index.json", TRIALS);
        begin(client);
    }

    private static void begin(Minecraft client) throws Exception {
        Trial trial = TRIALS.get(index);
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("uwudimension", trial.item));
        WeaponFixture.equip(client, item.getDefaultInstance(), trial.token(), InteractionHand.MAIN_HAND);
        client.player.setMainArm(HumanoidArm.RIGHT);
        WeaponFixture.server(client, actor -> actor.setMainArm(HumanoidArm.RIGHT));
        client.options.setCameraType(trial.camera.equals("first") ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK);
        WeaponFixture.orbit(trial.camera.equals("first") ? 0 : 35);
        WeaponFixture.poseOwner(client);
        motion = new Motion(trial);
        draw = null;
        restSaved = false;
        entered = client.player.tickCount;
        lastFrameTick = -1;
        phase = Phase.EQUIP;
        state = new State(trial, "equip");
        Evidence.signal("combo.json", state);
    }

    static boolean hostTick(Minecraft client) throws Exception {
        if (phase == Phase.DONE) return true;
        var player = client.player;
        var combat = (MinecraftClient_BetterCombat)(Object)client;
        WeaponFixture.poseOwner(client);
        Evidence.require(player.tickCount - entered < 420, "Timed out in native second-combo control: " + state);
        switch (phase) {
            case EQUIP -> {
                if (!WeaponFixture.marker(player.getMainHandItem()).equals(state.trial.token())
                        || player.tickCount - entered < 30 || combat.getCurrentAttackHand() != null
                        || combat.getComboCount() != 0 || player.getAttackStrengthScale(0) < .999F) return false;
                state = new State(state.trial, "rest");
                Evidence.signal("combo.json", state);
                phase = Phase.REST;
            }
            case REST -> {
                if (!restSaved || !Files.exists(Evidence.CONTROL.resolve("combo-rest-" + index + ".json"))) return false;
                state = new State(state.trial, "active");
                Evidence.signal("combo.json", state);
                attack(client);
                Evidence.require(motion.clips.size() == 1, "First native combo input did not dispatch exactly once");
                phase = Phase.ACTIVE;
            }
            case ACTIVE -> {
                if (motion.clips.size() == 1) {
                    // Wait for the ordinary native cooldown, keeping the existing combo alive.
                    // Do not reset combo state, invoke an animation, or construct a combat packet.
                    if (combat.getComboCount() != 1 || combat.getCurrentAttackHand() != null
                            || player.getAttackStrengthScale(0) < .999F || motion.attacks.get(0).maximum <= 2.0
                            || motion.last == null || motion.rest == null || motion.last.distance(motion.rest) >= .45) return false;
                    attack(client);
                    Evidence.require(motion.clips.size() == 2, "Second native combo input did not dispatch exactly once");
                    return false;
                }
                Evidence.require(motion.clips.size() == 2, "Unexpected extra native combo dispatch");
                if (client.level.getGameTime() - motion.attackStarted < 70 || combat.getCurrentAttackHand() != null) return false;
                motion.finish();
                state = new State(state.trial, "complete");
                Evidence.signal("combo.json", state);
                phase = Phase.ACK;
            }
            case ACK -> {
                if (!Files.exists(Evidence.CONTROL.resolve("combo-complete-" + index + ".json"))) return false;
                RESULTS.add(motion.report());
                Evidence.json("combo-results.json", RESULTS);
                index++;
                if (index == TRIALS.size()) {
                    Evidence.require(RESULTS.size() == 4, "Incomplete native second-combo matrix");
                    System.out.println("UWU CHAKRAM HOST COMBO MATRIX PASS trials=4 native_attacks=8");
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
        var previousHit = client.hitResult;
        try {
            Vec3 point = client.player.getEyePosition().add(0, 0, 20);
            client.hitResult = BlockHitResult.miss(point, Direction.SOUTH, BlockPos.containing(point));
            Method action = Minecraft.class.getDeclaredMethod("startAttack");
            action.setAccessible(true);
            action.invoke(client);
        } finally { client.hitResult = previousHit; }
    }

    static void observerTick(Minecraft client, UUID observedOwner) throws Exception {
        owner = observedOwner;
        var actor = client.level.getPlayerByUUID(owner);
        Evidence.require(actor instanceof RemotePlayer, "Combo observer must render the real TCP RemotePlayer");
        State incoming = Evidence.read("combo.json", State.class);
        if (incoming == null) return;
        if (state == null || state.trial.index != incoming.trial.index) {
            Evidence.require(state == null || waitingComplete, "Observer missed the previous native combo completion");
            motion = new Motion(incoming.trial);
            draw = null;
            restSaved = false;
            waitingComplete = false;
            lastFrameTick = -1;
        }
        state = incoming;
        if (state.phase.equals("complete") && !waitingComplete) {
            motion.finish();
            RESULTS.add(motion.report());
            Evidence.json("combo-results.json", RESULTS);
            Evidence.signal("combo-complete-" + state.trial.index + ".json", motion.report());
            waitingComplete = true;
            completed++;
        }
    }

    static void finishObserver() throws Exception {
        Evidence.require(completed == 4 && RESULTS.size() == 4, "Observer missed second-combo controls: " + completed);
        Evidence.json("combo-results.json", RESULTS);
        System.out.println("UWU CHAKRAM OBSERVER COMBO MATRIX PASS trials=4 native_attacks=8");
    }

    public static void observeDraw(ItemStack stack, ItemDisplayContext context, PoseStack poses) {
        if (state == null || motion == null || !WeaponFixture.marker(stack).equals(state.trial.token())) return;
        if (context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND && context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) return;
        draw = poses.last().pose().transformPosition(new Vector3f(.5F, .5F, .5F));
        drawContext = context;
        drawFrame = frameSerial;
    }

    public static void observeAnimation(AbstractClientPlayer actor, String clip, String hand, float length, float upswing) {
        if (state == null || state.phase.equals("complete") || motion == null || owner == null
                || !actor.getUUID().equals(owner)
                || !WeaponFixture.marker(actor.getMainHandItem()).equals(state.trial.token())) return;
        try {
            Evidence.require(motion.clips.size() < 2, "Unexpected third native combo attack");
            if (motion.clips.size() == 1)
                motion.attacks.get(0).finalDisplacement = motion.rest == null || motion.last == null
                        ? -1 : motion.last.distance(motion.rest);
            motion.clips.add(clip);
            motion.attackStarted = Minecraft.getInstance().level.getGameTime();
            Evidence.line("combo-animation-events.jsonl", Evidence.JSON.toJson(Map.of(
                    "trial", state.trial.index, "attack", motion.clips.size(), "clip", clip,
                    "owner", actor.getUUID().toString(), "remote", actor instanceof RemotePlayer,
                    "hand", hand, "length", length, "upswing", upswing,
                    "game_time", motion.attackStarted)).replace('\n', ' '));
        } catch (Exception | AssertionError failure) { WeaponFixture.fail(failure); }
    }

    static void frame(Minecraft client) throws Exception {
        frameSerial++;
        if (state == null || motion == null || draw == null || client.level == null || state.phase.equals("complete")) return;
        long tick = client.level.getGameTime();
        if (state.phase.equals("rest") && !restSaved) {
            var actor = Evidence.OBSERVER ? client.level.getPlayerByUUID(owner) : client.player;
            if (actor == null || !WeaponFixture.marker(actor.getMainHandItem()).equals(state.trial.token())) return;
            motion.rest = new Vector3f(draw);
            motion.last = new Vector3f(draw);
            Evidence.screenshot(state.trial.stem() + "-rest");
            restSaved = true;
            if (Evidence.OBSERVER) Evidence.signal("combo-rest-" + state.trial.index + ".json", Map.of("rendered", true));
            return;
        }
        if (motion.clips.isEmpty() || lastFrameTick == tick || motion.rest == null) return;
        lastFrameTick = tick;
        int attackIndex = motion.clips.size() - 1;
        int elapsed = (int)(tick - motion.attackStarted);
        Attack evidence = motion.attacks.get(attackIndex);
        if (drawFrame >= frameSerial - 2) {
            motion.last = new Vector3f(draw);
            evidence.maximum = Math.max(evidence.maximum, draw.distance(motion.rest));
            evidence.samples++;
            evidence.contexts.add(drawContext.toString());
            Evidence.line("combo-native-draws.tsv", state.trial.index + "\t" + (attackIndex + 1) + "\t" + elapsed
                    + "\t" + drawContext + "\t" + draw.x + "\t" + draw.y + "\t" + draw.z + "\t" + draw.distance(motion.rest));
        }
        if (CAPTURE_TICKS.contains(elapsed) && evidence.captures.add(elapsed))
            Evidence.screenshot(state.trial.stem() + "-attack-" + (attackIndex + 1) + "-" + String.format("%03d", elapsed));
    }

    private static final class Attack {
        final int ordinal;
        int samples;
        double maximum;
        double finalDisplacement = -1;
        final Set<String> contexts = new LinkedHashSet<>();
        final Set<Integer> captures = new LinkedHashSet<>();
        Attack(int ordinal) { this.ordinal = ordinal; }
        Map<String, Object> report() {
            return Map.of("ordinal", ordinal, "samples", samples, "maximum_draw_displacement", maximum,
                    "final_draw_displacement", finalDisplacement, "native_contexts", contexts, "captures", captures);
        }
    }

    private static final class Motion {
        final Trial trial;
        final List<String> clips = new ArrayList<>();
        final List<Attack> attacks = List.of(new Attack(1), new Attack(2));
        Vector3f rest, last;
        long attackStarted;
        Motion(Trial trial) { this.trial = trial; }
        void finish() {
            Evidence.require(clips.equals(List.of(LEFT, RIGHT)), "Native consecutive combo did not select both existing throw clips: " + report());
            attacks.get(1).finalDisplacement = rest == null || last == null ? -1 : last.distance(rest);
            for (Attack attack : attacks) {
                Evidence.require(attack.samples >= 5 && attack.captures.size() >= 3,
                        "Incomplete real rendered combo observations: " + report());
                Evidence.require(attack.maximum > 2.0, "Combo item did not travel visibly beyond an arm-only swing: " + report());
                Evidence.require(attack.finalDisplacement >= 0 && attack.finalDisplacement < .45,
                        "Native combo item did not return before the following attack or completion: " + report());
            }
            Evidence.require(rest != null && last != null && last.distance(rest) < .45,
                    "Native consecutive combo did not return to the original grip: " + report());
        }
        Map<String, Object> report() {
            return Map.of("trial", trial, "role", Evidence.OBSERVER ? "observer" : "host",
                    "animation_events", clips.size(), "clips", clips,
                    "attack_results", attacks.stream().map(Attack::report).toList(),
                    "final_draw_displacement", rest == null || last == null ? -1 : last.distance(rest));
        }
    }
}
