package dev.uwu.hotbarcheck;

import com.google.gson.Gson;
import dev.uwu.dimension.DawnpetalSpinCue;
import dev.uwu.dimension.GlitchBow;
import dev.uwu.dimension.RelicFlight;
import dev.uwu.dimension.RevivalBlossoms;
import dev.uwu.dimension.SharedAbilityHotbar;
import dev.uwu.hotbarcheck.mixin.MouseCallbacks;
import dev.uwu.weaponcheck.CandidateJarProbe;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.client.input.SpellHotbar;
import org.lwjgl.glfw.GLFW;

/**
 * Disposable observer mod. All acceptance actions enter the real KeyboardHandler/MouseHandler.
 * Server actions only arrange/reset fixtures, inspect results, and deliver a native mob attack.
 * No production ability, shield-use, attack, or cast method is invoked to make a check pass.
 */
@Mod("uwu_hotbar_check")
public final class HotbarProbe {
    private static final UUID DIRK = UUID.fromString("206e7d9c-e43b-4568-990a-6f789760f17e");
    private static final UUID AXE = UUID.fromString("67abe257-dc53-4709-a098-bb51da5261cd");
    private static final ArrayDeque<Step> STEPS = new ArrayDeque<>();
    private static final AtomicInteger SENT = new AtomicInteger(), RECEIVED = new AtomicInteger();
    private static final AtomicInteger ATTACKS = new AtomicInteger(), MINING = new AtomicInteger();
    private static final AtomicInteger MELEE = new AtomicInteger(), PROJECTILES = new AtomicInteger();
    private static final AtomicInteger DIRK_FLIGHTS = new AtomicInteger(), AXE_FLIGHTS = new AtomicInteger();
    private static final List<Integer> SENT_IDS = new CopyOnWriteArrayList<>();
    private static final List<Entity> OWNED_PROJECTILES = new CopyOnWriteArrayList<>();
    private static final List<String> PASSED = new ArrayList<>(), EXCEPTIONS = new ArrayList<>();
    private static final Gson JSON = new Gson();
    private static final Path EVIDENCE = Path.of(System.getProperty("uwu.check.evidence", "combined-evidence"));
    private static volatile boolean active, finished, serverEmpty, serverLanded;
    private static volatile Throwable serverFailure;
    private static volatile UUID playerId;
    private static volatile Vec3 home;
    private static volatile BlockPos floor;
    private static volatile RelicFlight flight;
    private static volatile ItemEntity dropped;
    private static volatile IronGolem target;
    private static volatile Zombie striker;
    private static boolean opening, planned, clientEmpty, observedCue;
    private static Step current;
    private static int ticks, token, suppressedSpellKeys, iconFrames, axeFrames;
    private static int beforeSlot, beforeIcons, frameCells;
    private static boolean fifthCellAxe;
    private static ItemStack[] beforeFrame;
    private static Snapshot baseline;
    private static float priorTargetHealth;
    private static int initialDirkFlights;
    private static final long STARTED = System.currentTimeMillis();

    public HotbarProbe() {
        CandidateJarProbe.verify();
        if ("visuals".equals(System.getProperty("uwu.check.mode", "gameplay"))) {
            CombinedVisualProbe.start(Minecraft.getInstance());
            return;
        }
        active = true;
        NeoForge.EVENT_BUS.addListener(HotbarProbe::tick);
        NeoForge.EVENT_BUS.addListener(HotbarProbe::joined);
        NeoForge.EVENT_BUS.addListener(HotbarProbe::serverTick);
        NeoForge.EVENT_BUS.addListener(HotbarProbe::beforeFrame);
        NeoForge.EVENT_BUS.addListener(HotbarProbe::afterFrame);
    }

    private static final class Step {
        final String name;
        final int timeout;
        final Consumer<Minecraft> begin;
        final Predicate<Minecraft> poll;
        int began;
        Step(String name, int timeout, Consumer<Minecraft> begin, Predicate<Minecraft> poll) {
            this.name = name; this.timeout = timeout; this.begin = begin; this.poll = poll;
        }
    }

    private record Snapshot(int sent, int received, int attack, int mining, int melee, int projectiles) {
        static Snapshot now() {
            return new Snapshot(SENT.get(), RECEIVED.get(), ATTACKS.get(), MINING.get(), MELEE.get(), PROJECTILES.get());
        }
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (finished) { client.stop(); return; }
        try {
            require(System.currentTimeMillis() - STARTED < 18 * 60_000L, "Overall gameplay timeout");
            if (!opening && client.screen instanceof TitleScreen title) {
                opening = true;
                client.createWorldOpenFlows().createFreshLevel("CombinedGameplayCheck",
                        new LevelSettings("Combined candidate gameplay", GameType.SURVIVAL, false,
                                Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(152045L, false, false), WorldPresets::createNormalWorldDimensions, title);
            } else if (opening && client.player == null && client.screen != null) {
                for (var widget : List.copyOf(client.screen.children())) {
                    if (widget instanceof Button button && (button.getMessage().getString().equals("Proceed")
                            || button.getMessage().getString().equals("I know what I'm doing!"))) {
                        button.onPress(); break;
                    }
                }
            }
            if (serverFailure != null) throw new AssertionError("Server fixture/check failed", serverFailure);
            if (client.player == null || client.screen != null) return;
            client.setWindowActive(true);
            ticks++;
            if (!planned) {
                if (client.player.tickCount < 45) return;
                planned = true;
                playerId = client.player.getUUID();
                plan();
            }
            if (current == null) {
                current = STEPS.pollFirst();
                if (current == null) { success(); return; }
                current.began = ticks;
                System.out.println("COMBINED STEP " + current.name);
                current.begin.accept(client);
            }
            require(age() <= current.timeout, "Timeout in " + current.name + ": " + diagnostic(client));
            if (current.poll.test(client)) current = null;
        } catch (Throwable failure) { fail(failure); }
    }

    private static int age() { return ticks - current.began; }
    private static void step(String name, int timeout, Consumer<Minecraft> start, Predicate<Minecraft> poll) {
        STEPS.add(new Step(name, timeout, start, poll));
    }
    private static void act(String name, Consumer<Minecraft> action) {
        step(name, 20, action, client -> true);
    }
    private static void waitFor(String name, int timeout, int minimum, Predicate<Minecraft> condition) {
        step(name, timeout, client -> {}, client -> age() >= minimum && condition.test(client));
    }
    private static void pause(String name, int duration) { waitFor(name, duration + 40, duration, client -> true); }
    private static void serverStep(String name, Consumer<ServerPlayer> action) {
        AtomicBoolean done = new AtomicBoolean();
        step(name, 100, client -> serverAction(client, actor -> { action.accept(actor); done.set(true); }),
                client -> done.get());
    }
    private static void serverAction(Minecraft client, Consumer<ServerPlayer> action) {
        client.getSingleplayerServer().execute(() -> {
            try {
                ServerPlayer actor = client.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                require(actor != null, "Integrated-server actor is missing");
                action.accept(actor);
            } catch (Throwable failure) { serverFailure = failure; }
        });
    }

    private static void plan() {
        act("dependency and Better Combat hold settings", client -> {
            require(ModList.get().isLoaded("bettercombat"), "Better Combat was not loaded");
            require(ModList.get().isLoaded("spell_engine") && ModList.get().isLoaded("spell_power"),
                    "Spell Engine conflict dependencies were not loaded");
            try {
                Object config = Class.forName("net.bettercombat.client.BetterCombatClientMod").getField("config").get(null);
                boolean hold = config.getClass().getField("isHoldToAttackEnabled").getBoolean(config);
                boolean mine = config.getClass().getField("isMiningWithWeaponsEnabled").getBoolean(config);
                require(hold && mine, "Fixture needs Better Combat hold-to-attack and mining enabled");
                evidence("fixture", Map.of("mode", "Survival", "difficulty", "NORMAL", "holdToAttack", hold,
                        "miningWithWeapons", mine, "input", "KeyboardHandler and MouseHandler native callbacks"));
            } catch (ReflectiveOperationException error) { throw new AssertionError("Cannot verify Better Combat settings", error); }
        });
        serverStep("build deterministic combat floor", HotbarProbe::prepareArena);
        planVanillaInputs();
        planMeleeAndThrow();
        planShieldControl();
        for (String shield : List.of("minecraft:shield", "uwudimension:petalfold_shield")) {
            for (String weapon : List.of("uwudimension:returnbloom_dirk", "uwudimension:dawnpetal_axe")) {
                for (int cell = 0; cell < 4; cell++) planShield(weapon, shield, cell, false, false);
                planShield(weapon, shield, 1, true, false);
            }
        }
        planAxeSkills();
        for (String weapon : List.of("uwudimension:rose_dagger", "uwudimension:counter_iron_chakram",
                "uwudimension:lantern_spear", "uwudimension:tidelace_trident", "uwudimension:ripple_bow",
                "uwudimension:vesper_scythe", "uwudimension:heart_launcher", "@glitch")) {
            planShield(weapon, "minecraft:shield", 0, false, true);
            planShield(weapon, "minecraft:shield", 0, true, true);
        }
        planNativeUse("uwudimension:rose_dagger", false);
        planNativeUse("uwudimension:counter_iron_chakram", false);
        planNativeUse("uwudimension:lantern_spear", false);
        planNativeUse("uwudimension:tidelace_trident", false);
        planNativeUse("uwudimension:heart_launcher", false);
        planNativeUse("uwudimension:ripple_bow", true);
        planNativeUse("uwudimension:cupid_bow", true);
        planNativeUse("@glitch", true);
        planNativeUse("minecraft:trident", true);
        planCrouchFishing("uwudimension:lantern_spear");
        planCrouchFishing("uwudimension:counter_iron_spear");
        act("coverage statement", client -> evidence("coverageLimits", Map.of(
                "notClaimed", List.of("Every item in the weapon roster", "Every crouch alternate",
                        "Every rebound-key combination", "Multiplayer network latency and server-only installs",
                        "Unrelated village and escort behavior", "Shield override for explicitly two-handed weapons"),
                "twoHandedExceptionsObserved", EXCEPTIONS)));
    }

    private static void planVanillaInputs() {
        equip("input fixture", "uwudimension:returnbloom_dirk", "", 0, 82F, false);
        serverStep("fill ordinary inventory slots", actor -> {
            for (int slot = 1; slot <= 8; slot++) actor.getInventory().setItem(slot, new ItemStack(Items.STICK, slot));
            actor.containerMenu.broadcastChanges();
        });
        waitFor("live Spell Engine cells and inventory HUD", 160, 8, client ->
                iconFrames >= 2 && !SpellHotbar.INSTANCE.slots.isEmpty() && client.player.getInventory().getItem(8).getCount() == 8);
        act("rapid contextual numbers and held Spell Engine keys", client -> {
            baseline = Snapshot.now();
            for (int repeat = 0; repeat < 4; repeat++) for (int cell = 0; cell < 4; cell++) number(client, cell);
            for (int cell = 0; cell < 4; cell++) key(client, GLFW.GLFW_KEY_1 + cell, GLFW.GLFW_PRESS);
        });
        pause("poll held competing keys", 3);
        act("check contextual inventory reservation", client -> {
            for (int cell = 0; cell < 4; cell++) key(client, GLFW.GLFW_KEY_1 + cell, GLFW.GLFW_RELEASE);
            require(client.player.getInventory().selected == 0 && hasDirk(client.player.getMainHandItem()),
                    "A contextual number selected a physical inventory slot");
            require(suppressedSpellKeys > 0, "Competing held Spell Engine keys were not observed");
            require(SENT.get() == baseline.sent(), "Number selection sent an ability without a mouse press");
            requireCell(0); // Empty Dirk cells 3/4 return to ordinary attack.
        });
        for (int index = 4; index <= 8; index++) {
            final int slot = index;
            act("native number " + (slot + 1), client -> number(client, slot));
            waitFor("ordinary slot " + (slot + 1), 40, 2, client -> client.player.getInventory().selected == slot);
            act("check ordinary slot " + (slot + 1), client -> {
                require(!SharedAbilityHotbar.visibleFor(client.player), "Ability HUD remained on ordinary item");
                require(client.player.getMainHandItem().is(Items.STICK)
                        && client.player.getMainHandItem().getCount() == slot, "Wrong ordinary numbered item");
                number(client, 0);
            });
            waitFor("native key 1 returns from ordinary slot", 40, 2, client -> hasDirk(client.player.getMainHandItem()));
        }
        act("native wheel", client -> mouse(client).hotbarCheck$scroll(window(client), 0, 1));
        waitFor("native wheel selected slot 9", 40, 2, client -> client.player.getInventory().selected == 8);
        act("native key 1 before drop", client -> number(client, 0));
        waitFor("dirk ready to drop", 40, 2, client -> hasDirk(client.player.getMainHandItem()));
        act("native Q drop", client -> { dropped = null; tapKey(client, GLFW.GLFW_KEY_Q); });
        waitFor("native dropped ItemEntity and cleared HUD", 80, 2, client -> dropped != null
                && client.player.getMainHandItem().isEmpty() && !SharedAbilityHotbar.visibleFor(client.player));
        act("input regression passed", client -> passed("numbered-keys-scroll-Q",
                Map.of("competingHeldKeysObserved", suppressedSpellKeys, "heldIconFrames", iconFrames)));
    }

    private static void planMeleeAndThrow() {
        equip("dirk normal melee with offhand shield", "uwudimension:returnbloom_dirk", "minecraft:shield", 0, 0F, true);
        act("select ordinary attack and native LMB", client -> {
            number(client, 0); baseline = Snapshot.now();
        });
        pause("settle ordinary attack selection", 3);
        serverStep("measure target before melee", actor -> priorTargetHealth = target.getHealth());
        act("ordinary LMB press", client -> pressMouse(client, GLFW.GLFW_MOUSE_BUTTON_LEFT));
        pause("ordinary LMB held one tick", 1);
        act("ordinary LMB release", client -> releaseMouse(client, GLFW.GLFW_MOUSE_BUTTON_LEFT));
        pause("Better Combat native upswing", 32);
        serverStep("ordinary melee changed target health", actor -> {
            require(target.getHealth() < priorTargetHealth, "Slot 1 native LMB failed to damage its frontal target");
            require(SENT.get() == baseline.sent() && PROJECTILES.get() == baseline.projectiles(),
                    "Slot 1 performed an ability or throw");
            require(MELEE.get() > baseline.melee(), "No ordinary server Player.attack observed");
            passed("dirk-slot-1-native-melee", Map.of("targetHealthBefore", priorTargetHealth,
                    "targetHealthAfter", target.getHealth(), "serverMeleeCalls", MELEE.get() - baseline.melee()));
        });

        equip("dirk held throw with offhand shield", "uwudimension:returnbloom_dirk", "minecraft:shield", 0, 82F, false);
        act("select throw only", client -> { baseline = Snapshot.now(); number(client, 1); });
        pause("settle throw selection", 3);
        act("throw selection has no side effect", client -> {
            requireCell(1);
            require(SENT.get() == baseline.sent(), "Selecting slot 2 cast before LMB");
            baseline = Snapshot.now(); initialDirkFlights = DIRK_FLIGHTS.get();
            flight = null; serverEmpty = false; serverLanded = false; clientEmpty = false;
            pressMouse(client, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        waitFor("one physical throw naturally lands and returns while LMB stays down", 300, 4, client -> {
            clientEmpty |= client.player.getMainHandItem().isEmpty();
            return clientEmpty && serverEmpty && serverLanded && DIRK_FLIGHTS.get() > initialDirkFlights
                    && hasDirk(client.player.getMainHandItem());
        });
        act("verify exact custody and request count", client -> {
            require(client.options.keyAttack.isDown(), "Attack was released before the throw returned");
            require(DIRK_FLIGHTS.get() == initialDirkFlights + 1, "Held LMB launched multiple physical Dirks");
            require(SENT.get() == baseline.sent() + 1 && RECEIVED.get() == baseline.received() + 1,
                    "Throw did not send and receive exactly one ability request");
            require(SENT_IDS.get(SENT_IDS.size() - 1) == 1, "Slot 2 sent the wrong ability wire ID");
            require(countIdentity(client.player.getInventory(), DIRK) == 1, "Dirk custody duplicated or lost its identity");
            requireCell(0);
            assertNoOrdinaryAttack();
        });
        serverStep("put a live frontal target in front after return", actor -> spawnTarget(actor));
        act("look at target without releasing held LMB", client -> look(client, 0F));
        pause("held attack remains suppressed after return", 45);
        serverStep("no post-return melee", actor -> {
            require(target.getHealth() == 500F, "Held throw fell through to post-return melee");
            assertNoOrdinaryAttack();
            target.discard(); target = null;
            actor.serverLevel().setBlockAndUpdate(floor, Blocks.DIRT.defaultBlockState());
        });
        act("look at dirt while still holding the original LMB", client -> look(client, 90F));
        pause("held attack remains suppressed for a mineable block", 35);
        serverStep("no post-return mining", actor -> {
            require(actor.serverLevel().getBlockState(floor).is(Blocks.DIRT), "Held throw fell through to block destruction");
            assertNoOrdinaryAttack();
            passed("dirk-slot-2-held-through-return", Map.of("abilityRequests", SENT.get() - baseline.sent(),
                    "physicalDirks", DIRK_FLIGHTS.get() - initialDirkFlights, "emptyClient", clientEmpty,
                    "emptyServer", serverEmpty, "landed", serverLanded, "nativeAttackFallthrough", ATTACKS.get() - baseline.attack(),
                    "nativeMiningFallthrough", MINING.get() - baseline.mining(), "serverMeleeFallthrough", MELEE.get() - baseline.melee()));
        });
        act("release captured LMB", client -> releaseMouse(client, GLFW.GLFW_MOUSE_BUTTON_LEFT));
        pause("release state clears", 4);
        act("slot 1 fresh press can mine", client -> { number(client, 0); });
        pause("settle normal mining cell", 3);
        act("native mine press", client -> { baseline = Snapshot.now(); pressMouse(client, GLFW.GLFW_MOUSE_BUTTON_LEFT); });
        pause("ordinary mining duration", 45);
        act("native mine release", client -> releaseMouse(client, GLFW.GLFW_MOUSE_BUTTON_LEFT));
        serverStep("ordinary mining after release control", actor -> {
            require(!actor.serverLevel().getBlockState(floor).is(Blocks.DIRT), "Release left ordinary slot-1 mining blocked");
            require(MINING.get() > baseline.mining(), "No native mining call after release");
            actor.serverLevel().setBlockAndUpdate(floor, Blocks.DEEPSLATE.defaultBlockState());
            passed("dirk-release-restores-slot-1-mining", Map.of("nativeMiningCalls", MINING.get() - baseline.mining()));
        });
    }

    private static void planShieldControl() {
        equip("unguarded frontal hit control", "uwudimension:returnbloom_dirk", "", 0, 0F, false);
        serverStep("native unguarded zombie hit", actor -> {
            createStriker(actor);
            float before = actor.getHealth();
            boolean accepted = striker.doHurtTarget(actor);
            float after = actor.getHealth();
            require(accepted && after < before, "The native frontal-hit control did not hurt the unguarded player");
            passed("native-frontal-hit-unguarded-control", Map.of("before", before, "after", after,
                    "damage", before - after, "attacker", "minecraft:zombie", "attack", "Mob.doHurtTarget"));
        });
    }

    private static void planShield(String weapon, String shield, int cell, boolean block, boolean respectTwoHanded) {
        String label = weapon + " / " + shield + " / cell " + (cell + 1) + " / " + (block ? "inert block" : "air");
        AtomicBoolean excepted = new AtomicBoolean();
        equip("shield " + label, weapon, shield, 0, block ? 82F : 0F, false);
        act("select shield trial " + label, client -> {
            boolean twoHanded = isTwoHanded(client.player.getMainHandItem());
            if (twoHanded && respectTwoHanded) {
                excepted.set(true);
                String exception = id(client.player.getMainHandItem()) + " has explicit Better Combat two-handed attributes";
                if (!EXCEPTIONS.contains(exception)) EXCEPTIONS.add(exception);
                evidence("intentionalException", Map.of("case", label, "reason", exception));
                return;
            }
            require(!twoHanded, "Core Dirk/Axe unexpectedly has explicit two-handed attributes");
            number(client, cell);
            baseline = Snapshot.now();
        });
        pause("settle shield selection " + label, 3);
        act("native RMB hold " + label, client -> {
            if (excepted.get()) return;
            if (block) require(client.hitResult instanceof BlockHitResult
                            && client.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK,
                    "Inert-block right-click fixture was not targeting a block");
            pressMouse(client, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        });
        pause("shield raise delay " + label, 10);
        serverStep("actual guarded frontal hit " + label, actor -> {
            if (excepted.get()) return;
            require(actor.isUsingItem() && actor.getUsedItemHand() == InteractionHand.OFF_HAND && actor.isBlocking(),
                    "Native RMB did not raise the offhand shield: " + label);
            require(SENT.get() == baseline.sent() && PROJECTILES.get() == baseline.projectiles(),
                    "RMB cast a main-hand ability while shield was equipped: " + label);
            createStriker(actor);
            float before = actor.getHealth();
            int durability = actor.getOffhandItem().getDamageValue();
            actor.invulnerableTime = 0;
            striker.doHurtTarget(actor);
            float after = actor.getHealth();
            int durabilityAfter = actor.getOffhandItem().getDamageValue();
            require(after == before, "Actual frontal hit bypassed the raised shield: " + label);
            require(durabilityAfter > durability, "No native shield durability cost; frontal hit may not have connected: " + label);
            passed("shield-native-frontal-hit", Map.of("case", label, "resolvedWeapon", id(actor.getMainHandItem()),
                    "healthBefore", before, "healthAfter", after, "shieldDamageBefore", durability,
                    "shieldDamageAfter", durabilityAfter, "usedHand", actor.getUsedItemHand().toString()));
        });
        act("release RMB " + label, client -> releaseMouse(client, GLFW.GLFW_MOUSE_BUTTON_RIGHT));
        pause("release use " + label, 4);
    }

    private static void planAxeSkills() {
        equip("axe on physical slot 5", "uwudimension:dawnpetal_axe", "", 4, 0F, true);
        waitFor("native fifth-cell axe icon", 120, 5, client -> axeFrames >= 2);
        act("select axe Heart Spin on contextual slot 3", client -> { baseline = Snapshot.now(); number(client, 2); });
        pause("settle axe Heart Spin", 3);
        act("Heart Spin LMB", client -> {
            requireCell(2); require(client.player.getInventory().selected == 4, "Axe cell 3 changed physical slot 5");
            require(SENT.get() == baseline.sent(), "Heart Spin selection alone sent a request");
            observedCue = false; pressMouse(client, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        waitFor("server-accepted Heart Spin cue", 80, 1, client -> {
            observedCue |= DawnpetalSpinCue.frame(client.player, 0F, true) != null;
            return observedCue;
        });
        act("release Heart Spin press", client -> releaseMouse(client, GLFW.GLFW_MOUSE_BUTTON_LEFT));
        pause("Heart Spin server sweep", 28);
        serverStep("Heart Spin actual target hit", actor -> {
            require(SENT.get() == baseline.sent() + 1 && RECEIVED.get() == baseline.received() + 1,
                    "Heart Spin did not have exactly one real request");
            require(SENT_IDS.get(SENT_IDS.size() - 1) == 2, "Axe slot 3 was not HEART_SPIN wire ID 2");
            require(target.getHealth() < 500F, "Accepted Heart Spin did not damage its nearby target");
            require(ATTACKS.get() == baseline.attack() && MELEE.get() == baseline.melee(),
                    "Heart Spin also triggered ordinary melee");
            passed("axe-slot-3-heart-spin", Map.of("wireId", 2, "acceptedCue", observedCue, "targetHealth", target.getHealth()));
        });
        act("cooldown press stays a skill without melee", client -> { baseline = Snapshot.now(); tapMouse(client, GLFW.GLFW_MOUSE_BUTTON_LEFT); });
        pause("cooldown rejection observation", 8);
        act("verify Heart Spin cooldown suppressed ordinary attack", client -> {
            require(SENT.get() == baseline.sent(), "Heart Spin cooldown press sent a second ability");
            require(ATTACKS.get() == baseline.attack() && MELEE.get() == baseline.melee(),
                    "Cooldown click fell through to ordinary melee");
            passed("axe-heart-spin-cooldown-no-fallthrough", Map.of("additionalRequests", 0, "additionalMelee", 0));
        });
        serverStep("reset target geometry for ordinary Spin", actor -> spawnTarget(actor));
        act("select axe Spin on contextual slot 4", client -> { baseline = Snapshot.now(); number(client, 3); });
        pause("settle axe Spin", 3);
        act("Spin LMB", client -> {
            requireCell(3); require(client.player.getInventory().selected == 4, "Axe cell 4 changed physical slot 5");
            observedCue = false; pressMouse(client, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        waitFor("server-accepted Spin cue", 80, 1, client -> {
            observedCue |= DawnpetalSpinCue.frame(client.player, 0F, true) != null;
            return observedCue;
        });
        act("release Spin press", client -> releaseMouse(client, GLFW.GLFW_MOUSE_BUTTON_LEFT));
        pause("Spin server sweep", 28);
        serverStep("Spin actual target hit", actor -> {
            require(SENT.get() == baseline.sent() + 1 && RECEIVED.get() == baseline.received() + 1,
                    "Spin did not have exactly one real request");
            require(SENT_IDS.get(SENT_IDS.size() - 1) == 0, "Axe slot 4 was not SPIN wire ID 0");
            require(target.getHealth() < 500F, "Accepted Spin did not damage its nearby target");
            require(ATTACKS.get() == baseline.attack() && MELEE.get() == baseline.melee(), "Spin also triggered ordinary melee");
            passed("axe-slot-4-spin", Map.of("wireId", 0, "acceptedCue", observedCue, "targetHealth", target.getHealth(), "nativeFifthCellFrames", axeFrames));
        });
    }

    private static void planNativeUse(String weapon, boolean draw) {
        equip("no-shield native use " + weapon, weapon, "", 0, 82F, false);
        act("native no-shield RMB " + weapon, client -> {
            baseline = Snapshot.now();
            pressMouse(client, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        });
        pause("native use/draw duration " + weapon, draw ? 26 : 1);
        if (draw) serverStep("native main-hand draw observed " + weapon, actor -> {
            require(actor.isUsingItem() && actor.getUsedItemHand() == InteractionHand.MAIN_HAND,
                    "No-shield native draw did not start in main hand: " + weapon);
            require(PROJECTILES.get() == baseline.projectiles(), "Draw released a projectile before RMB release: " + weapon);
        });
        act("native RMB release " + weapon, client -> releaseMouse(client, GLFW.GLFW_MOUSE_BUTTON_RIGHT));
        waitFor("native projectile from use/release " + weapon, 100, 4,
                client -> PROJECTILES.get() > baseline.projectiles());
        serverStep("verify native use kept ability network idle " + weapon, actor -> {
            require(SENT.get() == baseline.sent(), "Noncontextual native use was redirected to a Dawnpetal ability");
            passed("no-shield-native-use", Map.of("weapon", weapon, "drawRelease", draw,
                    "serverProjectiles", PROJECTILES.get() - baseline.projectiles()));
        });
        pause("settle native cast before fixture cleanup", 8);
    }

    private static void planCrouchFishing(String weapon) {
        equip("crouch fishing " + weapon, weapon, "minecraft:shield", 0, 15F, false);
        act("native crouch key " + weapon, client -> key(client, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_PRESS));
        pause("crouch reaches server", 4);
        act("native crouch RMB " + weapon, client -> { baseline = Snapshot.now(); tapMouse(client, GLFW.GLFW_MOUSE_BUTTON_RIGHT); });
        pause("fishing hook reaches server", 6);
        serverStep("crouch alternate preserved with shield equipped " + weapon, actor -> {
            require(actor.isShiftKeyDown() && actor.fishing != null, "Crouch fishing alternate failed: " + weapon);
            require(!actor.isBlocking(), "Crouch alternate incorrectly raised offhand shield: " + weapon);
            passed("crouch-fishing-alternate", Map.of("weapon", weapon, "hook", actor.fishing.getUUID().toString(), "shieldEquipped", true));
        });
        act("native crouch release", client -> key(client, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_RELEASE));
        pause("uncrouch settles", 4);
    }

    private static void equip(String name, String weapon, String shield, int slot, float pitch, boolean withTarget) {
        final int fixtureToken = ++token;
        act("release input before " + name, HotbarProbe::releaseAll);
        serverStep("arrange " + name, actor -> {
            cleanup(actor);
            actor.getInventory().clearContent();
            actor.getInventory().selected = slot;
            actor.connection.send(new ClientboundSetCarriedItemPacket(slot));
            ItemStack held = stack(weapon);
            CustomData.update(DataComponents.CUSTOM_DATA, held, tag -> tag.putInt("combined_fixture", fixtureToken));
            actor.setItemInHand(InteractionHand.MAIN_HAND, held);
            actor.setItemInHand(InteractionHand.OFF_HAND, shield.isEmpty() ? ItemStack.EMPTY : stack(shield));
            actor.getInventory().setItem(15, new ItemStack(Items.ARROW, 64));
            actor.removeAllEffects(); actor.setHealth(20F); actor.invulnerableTime = 0;
            actor.getFoodData().setFoodLevel(20);
            actor.serverLevel().setBlockAndUpdate(floor, Blocks.DEEPSLATE.defaultBlockState());
            actor.setDeltaMovement(Vec3.ZERO);
            actor.teleportTo(actor.serverLevel(), home.x, home.y, home.z, Set.of(), 0F, pitch);
            actor.fallDistance = 0;
            if (withTarget) spawnTarget(actor);
            actor.containerMenu.broadcastChanges();
        });
        waitFor("client fixture synchronized " + name, 100, 6, client -> {
            var held = client.player.getMainHandItem();
            int actual = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("combined_fixture");
            return actual == fixtureToken && client.player.getInventory().selected == slot
                    && client.player.distanceToSqr(home) < .06 && Math.abs(client.player.getXRot() - pitch) < 2F
                    && (shield.isEmpty() ? client.player.getOffhandItem().isEmpty() : client.player.getOffhandItem().is(stack(shield).getItem()));
        });
        pause("equip and attack cooldown settle " + name, 18);
    }

    private static void prepareArena(ServerPlayer actor) {
        actor.setGameMode(GameType.SURVIVAL);
        actor.serverLevel().setDayTime(6000);
        actor.serverLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, actor.getServer());
        actor.serverLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, actor.getServer());
        floor = new BlockPos(actor.getBlockX(), 220, actor.getBlockZ());
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            BlockPos base = floor.offset(x, 0, z);
            actor.serverLevel().setBlockAndUpdate(base.below(), Blocks.DEEPSLATE.defaultBlockState());
            actor.serverLevel().setBlockAndUpdate(base, Blocks.DEEPSLATE.defaultBlockState());
            for (int y = 1; y <= 5; y++) actor.serverLevel().setBlockAndUpdate(base.above(y), Blocks.AIR.defaultBlockState());
        }
        home = Vec3.atBottomCenterOf(floor.above());
    }

    private static void cleanup(ServerPlayer actor) {
        actor.stopUsingItem();
        if (target != null) { target.discard(); target = null; }
        if (striker != null) { striker.discard(); striker = null; }
        if (dropped != null) { dropped.discard(); dropped = null; }
        if (actor.fishing != null) actor.fishing.discard();
        for (Entity entity : OWNED_PROJECTILES) if (!entity.isRemoved()) entity.discard();
        OWNED_PROJECTILES.clear();
    }

    private static void spawnTarget(ServerPlayer actor) {
        if (target != null) target.discard();
        IronGolem created = EntityType.IRON_GOLEM.create(actor.serverLevel());
        require(created != null, "Could not create native melee target");
        created.setNoAi(true); created.setPersistenceRequired(); created.setSilent(true);
        created.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
        created.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        created.setHealth(500F);
        created.moveTo(home.x, home.y, home.z + 2.2, 180F, 0F);
        actor.serverLevel().addFreshEntity(created);
        target = created;
    }

    private static void createStriker(ServerPlayer actor) {
        if (striker != null) striker.discard();
        Zombie created = EntityType.ZOMBIE.create(actor.serverLevel());
        require(created != null, "Could not create native attack source");
        created.setNoAi(true); created.setPersistenceRequired(); created.setSilent(true);
        created.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        created.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(8);
        created.moveTo(actor.getX(), actor.getY(), actor.getZ() + 2.2, 180F, 0F);
        actor.serverLevel().addFreshEntity(created);
        striker = created;
    }

    private static ItemStack stack(String name) {
        Item item;
        if (name.equals("@glitch")) item = BuiltInRegistries.ITEM.stream().filter(value -> value instanceof GlitchBow)
                .findFirst().orElseThrow(() -> new AssertionError("No registered GlitchBow item"));
        else item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(name));
        require(item != Items.AIR, "Fixture item is unregistered: " + name);
        ItemStack stack = new ItemStack(item);
        if (item == RevivalBlossoms.DIRK.get() || item == RevivalBlossoms.AXE.get())
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID("uwu_revival_id",
                    item == RevivalBlossoms.DIRK.get() ? DIRK : AXE));
        if (item == RevivalBlossoms.DIRK.get()) stack.set(SpellDataComponents.SPELL_CONTAINER,
                new SpellContainer(SpellContainer.ContentType.ANY, "", "", 5,
                        List.of("rpg_series:swift_strikes", "rpg_series:cleave", "rpg_series:swipe", "rpg_series:thrust", "rpg_series:whirlwind")));
        return stack;
    }

    private static boolean isTwoHanded(ItemStack stack) {
        try {
            Object attributes = Class.forName("net.bettercombat.logic.WeaponRegistry")
                    .getMethod("getAttributes", ItemStack.class).invoke(null, stack);
            return attributes != null && (boolean)attributes.getClass().getMethod("isTwoHanded").invoke(attributes);
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot inspect actual Better Combat handedness", failure); }
    }
    private static String id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    private static boolean identity(ItemStack stack, UUID id) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID("uwu_revival_id") && id.equals(tag.getUUID("uwu_revival_id"));
    }
    private static boolean hasDirk(ItemStack stack) { return stack.is(RevivalBlossoms.DIRK.get()) && identity(stack, DIRK); }
    private static boolean hasAxe(ItemStack stack) { return stack.is(RevivalBlossoms.AXE.get()) && identity(stack, AXE); }
    private static int countIdentity(Inventory inventory, UUID id) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
            if (identity(inventory.getItem(slot), id)) count += inventory.getItem(slot).getCount();
        return count;
    }

    private static void joined(EntityJoinLevelEvent event) {
        if (!active || finished || event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof ItemEntity item && hasDirk(item.getItem())) dropped = item;
        if (event.getEntity() instanceof Projectile projectile && projectile.getOwner() != null
                && projectile.getOwner().getUUID().equals(playerId)) {
            PROJECTILES.incrementAndGet(); OWNED_PROJECTILES.add(projectile);
        }
        if (event.getEntity() instanceof RelicFlight observed && observed.physical()) {
            if (hasDirk(observed.getItem())) { DIRK_FLIGHTS.incrementAndGet(); flight = observed; }
            if (hasAxe(observed.getItem())) { AXE_FLIGHTS.incrementAndGet(); flight = observed; }
        }
    }
    private static void serverTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer actor) || !actor.getUUID().equals(playerId)) return;
        if (flight != null) { serverEmpty |= actor.getMainHandItem().isEmpty(); serverLanded |= flight.landed(); }
    }
    public static void sentUse(int id) { if (active && !finished) { SENT.incrementAndGet(); SENT_IDS.add(id); } }
    public static void receivedUse() { if (active && !finished) RECEIVED.incrementAndGet(); }
    public static void nativeAttack() { if (active && !finished) ATTACKS.incrementAndGet(); }
    public static void nativeMining() { if (active && !finished) MINING.incrementAndGet(); }
    public static void serverMelee(Player player) {
        if (active && !finished && player instanceof ServerPlayer && player.getUUID().equals(playerId)) MELEE.incrementAndGet();
    }
    public static void suppressedSpellKey() { if (active && !finished) suppressedSpellKeys++; }
    private static void assertNoOrdinaryAttack() {
        require(ATTACKS.get() == baseline.attack() && MINING.get() == baseline.mining() && MELEE.get() == baseline.melee(),
                "Captured ability press fell through to ordinary attack/mining; baseline=" + baseline + " current=" + Snapshot.now());
    }

    private static void beforeFrame(RenderFrameEvent.Pre event) {
        beforeFrame = null; frameCells = 0; fifthCellAxe = false;
        var player = Minecraft.getInstance().player;
        if (!active || finished || player == null || !hasDirk(player.getMainHandItem()) && !hasAxe(player.getMainHandItem())) return;
        Inventory inventory = player.getInventory(); beforeSlot = inventory.selected; beforeIcons = iconFrames;
        beforeFrame = new ItemStack[inventory.getContainerSize()];
        for (int slot = 0; slot < beforeFrame.length; slot++) beforeFrame[slot] = inventory.getItem(slot).copy();
    }
    private static void afterFrame(RenderFrameEvent.Post event) {
        var player = Minecraft.getInstance().player;
        if (!active || finished || player == null || beforeFrame == null) return;
        boolean axe = hasAxe(player.getMainHandItem()) && beforeSlot == 4;
        if (!axe && iconFrames == beforeIcons) return;
        try {
            require(frameCells == 0b1111, "HUD did not replace exactly the first four contextual cells");
            if (axe) { require(fifthCellAxe, "Axe not drawn in native physical slot 5"); axeFrames++; }
            Inventory inventory = player.getInventory();
            require(beforeSlot == inventory.selected, "HUD changed inventory selection");
            for (int slot = 0; slot < beforeFrame.length; slot++)
                require(ItemStack.matches(beforeFrame[slot], inventory.getItem(slot)), "HUD changed physical inventory slot " + slot);
        } catch (Throwable failure) { fail(failure); }
    }
    public static void drawnIcon(ItemStack stack) {
        var player = Minecraft.getInstance().player;
        if (!active || finished || player == null) return;
        if (hasAxe(player.getMainHandItem()) && player.getInventory().selected == 4) {
            fail(new AssertionError("Axe in physical slot 5 got a duplicated held-item icon")); return;
        }
        if (hasDirk(player.getMainHandItem())) {
            if (!ItemStack.matches(stack, player.getMainHandItem())) fail(new AssertionError("HUD substituted a different Dirk stack"));
            else iconFrames++;
        }
    }
    public static void drawnCell(int index) { if (active && beforeFrame != null) frameCells |= 1 << index; }
    public static void drawnVanillaFifth(ItemStack stack) { if (active && beforeFrame != null && beforeSlot == 4 && hasAxe(stack)) fifthCellAxe = true; }

    private static void requireCell(int expected) { require(SharedAbilityHotbar.selectedCell() == expected, "Wrong contextual cell; expected " + expected + " got " + SharedAbilityHotbar.selectedCell()); }
    private static long window(Minecraft client) { return client.getWindow().getWindow(); }
    private static MouseCallbacks mouse(Minecraft client) { return (MouseCallbacks)(Object)client.mouseHandler; }
    private static void key(Minecraft client, int key, int action) { client.keyboardHandler.keyPress(window(client), key, 0, action, 0); }
    private static void tapKey(Minecraft client, int key) { key(client, key, GLFW.GLFW_PRESS); key(client, key, GLFW.GLFW_RELEASE); }
    private static void number(Minecraft client, int slot) { tapKey(client, GLFW.GLFW_KEY_1 + slot); }
    private static void pressMouse(Minecraft client, int button) { mouse(client).hotbarCheck$press(window(client), button, GLFW.GLFW_PRESS, 0); }
    private static void releaseMouse(Minecraft client, int button) { mouse(client).hotbarCheck$press(window(client), button, GLFW.GLFW_RELEASE, 0); }
    private static void tapMouse(Minecraft client, int button) { pressMouse(client, button); releaseMouse(client, button); }
    private static void releaseAll(Minecraft client) {
        releaseMouse(client, GLFW.GLFW_MOUSE_BUTTON_LEFT); releaseMouse(client, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        key(client, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_RELEASE);
    }
    private static void look(Minecraft client, float pitch) { client.player.setYRot(0F); client.player.setXRot(pitch); client.player.setYHeadRot(0F); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static synchronized void evidence(String kind, Map<String, ?> values) {
        try {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("gameplay.jsonl"), JSON.toJson(Map.of("kind", kind,
                    "phase", current == null ? "none" : current.name, "tick", ticks, "values", values)) + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception error) { throw new AssertionError("Cannot write gameplay evidence", error); }
    }
    private static synchronized void passed(String name, Map<String, ?> values) {
        PASSED.add(name); evidence("pass", Map.of("check", name, "observed", values));
        System.out.println("COMBINED CHECK PASS " + name + " " + JSON.toJson(values));
    }
    private static String diagnostic(Minecraft client) {
        return "counts=" + Snapshot.now() + ", cell=" + SharedAbilityHotbar.selectedCell() + ", physicalSlot="
                + client.player.getInventory().selected + ", held=" + client.player.getMainHandItem()
                + ", emptyClient/server=" + clientEmpty + "/" + serverEmpty + ", landed=" + serverLanded;
    }
    private static void success() {
        evidence("complete", Map.of("passedChecks", PASSED, "twoHandedExceptions", EXCEPTIONS,
                "hudFrames", iconFrames, "axeFrames", axeFrames));
        System.out.println("COMBINED GAMEPLAY PASS"); finished = true;
    }
    private static void fail(Throwable failure) {
        if (finished) return;
        System.err.println("COMBINED GAMEPLAY FAILED phase=" + (current == null ? "startup" : current.name));
        failure.printStackTrace();
        try { evidence("failure", Map.of("error", failure.toString(), "passedBeforeFailure", PASSED)); }
        catch (Throwable ignored) { }
        finished = true;
    }
}
