package dev.uwu.balancecheck;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.uwu.dimension.AcquisitionPolicy;
import dev.uwu.dimension.CounterArsenal;
import dev.uwu.dimension.GlitchBow;
import dev.uwu.dimension.RevivalBlossoms;
import dev.uwu.weaponcheck.CandidateJarProbe;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

@Mod("uwu_weapon_settings_check")
public final class WeaponSettingsProbe {
    private static final BlockPos START = new BlockPos(259,181,259);
    private static final String CHARM = "uwudimension.charm_owner", END = "uwudimension.charm_end";
    private static final String W = "uwu weapon ", D = W + "returnbloom_dirk ", C = "uwu cupid charmseconds";
    private static final InteractionHand HAND = InteractionHand.MAIN_HAND;
    private static final boolean RESTART = "restart".equals(System.getProperty("uwu.check.stage"));
    private final List<String> checks = new ArrayList<>();
    private final List<ServerPlayer> players = new ArrayList<>();
    private final List<EmbeddedChannel> channels = new ArrayList<>();
    private int stage, shot, sequence;
    private long since, firstEnd, shortEnd, turnEnd, throwEnd;
    private MinecraftServer server;
    private ServerLevel level;
    private ServerPlayer archer, opponent;
    private Mob original, shortCharm, pet, dummy;
    private LivingEntity aim;
    private ItemStack bow, dirk;
    private Path config;
    private double attackSpeed;
    private final List<Double> sentDirkAdjustments = new ArrayList<>();

    private final List<Float> counterHits = new ArrayList<>();

    public WeaponSettingsProbe() {
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Post event) -> {
            if (stage == 14 && event.getEntity() == dummy && event.getSource().getEntity() == archer
                    && event.getSource().is(DamageTypes.PLAYER_ATTACK)) counterHits.add(event.getNewDamage());
        });
    }
    private void tick(ServerTickEvent.Post event) {
        if (stage < 0) return;
        try {
            if (server == null) {
                server = event.getServer();
                level = server.overworld();
                var chunk = new ChunkPos(START);
                level.setChunkForced(chunk.x, chunk.z, true);
                since = now();
            }
            require(now() - since < 400, "Timed out at stage " + stage + " shot " + shot);
            // These connected actors have an in-memory channel without a listener tick loop.
            for (ServerPlayer player : players) player.doTick();
            step();
        } catch (Throwable failure) {
            stage = -1;
            System.err.println("UWU WEAPON SETTINGS FAILED");
            failure.printStackTrace(System.err);
            server.halt(false);
        }
    }

    private void step() throws Exception {
        switch (stage) {
            case 0 -> {
                if (!level.isPositionEntityTicking(START)) return;
                CandidateJarProbe.verify();
                require(!ModList.get().isLoaded("unyielding_quests"), "Expected standalone UwU server");
                config = ModConfigs.getModConfigs("uwudimension").stream().filter(c -> c.getSpec() == AcquisitionPolicy.SPEC)
                        .findFirst().orElseThrow().getFullPath().toRealPath();
                for (var p : BlockPos.betweenClosed(START.offset(-1,-1,-1), START.offset(9,-1,9)))
                    level.setBlock(p, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                archer = player("BalanceArcher", START);
                if (RESTART) {
                    require(AcquisitionPolicy.cupidBowCharmSeconds() == 7 && configText().contains("cupidBowCharmSeconds = 7"), "Cold config reload failed");
                    equal(value("returnbloom_dirk", "baseHitDamage").get(), 13, "Cold Dirk base");
                } else {
                    require(AcquisitionPolicy.cupidBowCharmSeconds() == 40, "Default Cupid setting differs");
                    equal(value("returnbloom_dirk", "baseHitDamage").get(), 15, "Default Dirk base");
                    equal(value("returnbloom_dirk", "throwDamage").get(), 10, "Default Dirk throw");
                    for (var entry : CounterArsenal.ROSTER) if (CounterArsenal.selectable(entry)) {
                        equal(value(entry.id(), "baseHitDamage").get(), entry.damage(), entry.id());
                        equal(value(entry.id(), "abilityDamage").get(), entry.damage(), entry.id());
                        equal(value(entry.id(), "castCooldownSeconds").get(), 3, entry.id());
                    }
                    reject(C + " 0", console()); reject(C + " 107374183", console()); reject(C + " 1.5", console());
                    reject(C + " 2", console().withPermission(1));
                    reject(D + "throwCooldownSeconds 0", console()); reject(D + "throwDamage 257", console());
                    reject(D + "baseHitDamage 10", console().withPermission(1));
                    run(C + " 107374182"); require(AcquisitionPolicy.cupidBowCharmSeconds() * 20 == 2147483640, "Tick conversion overflow");
                    run(C + " 40"); run(C); run(D + "throwDamage");
                    log("Typed defaults for every selectable counter, operator permissions, bounds, query and maximum whole seconds");
                }
                original = mob(EntityType.HUSK, START.east(5));
                startBow(original, false);
                next(1);
            }
            case 1 -> {
                if (archer.getTicksUsingItem() < 20) return;
                archer.releaseUsingItem();
                if (shot == 1) run(C + " 2");
                next(2);
            }
            case 2 -> {
                if (!aim.getPersistentData().hasUUID(CHARM)) return;
                int expected = RESTART ? 140 : switch (shot) { case 0, 3 -> 800; case 1 -> 40; default -> 60; };
                long end = aim.getPersistentData().getLong(END);
                require(archer.getUUID().equals(aim.getPersistentData().getUUID(CHARM)), "Wrong charm owner");
                equal(end - now(), expected, "Actual bow impact duration, shot " + shot);
                equal(aim.getHealth(), aim.getMaxHealth(), "Charm caused arrow damage");
                if (RESTART) {
                    log("Cold restart loaded the saved seven seconds and a real Cupid Bow arrow charmed for exactly 140 ticks");
                    finish(); return;
                }
                switch (shot++) {
                    case 0 -> {
                        firstEnd = end;
                        shortCharm = mob(EntityType.HUSK, START.south(5));
                        startBow(shortCharm, true);
                        next(1);
                    }
                    case 1 -> {
                        shortEnd = end;
                        equal(original.getPersistentData().getLong(END), firstEnd, "Old charm retimed by change in flight");
                        // The companion editor writes this exact registered setting on the server thread.
                        AcquisitionPolicy.CUPID_BOW_CHARM_SECONDS.set(3);
                        AcquisitionPolicy.CUPID_BOW_CHARM_SECONDS.save();
                        run(C);
                        require(configText().contains("cupidBowCharmSeconds = 3"), "Typed companion edit did not save");
                        next(3);
                    }
                    case 2 -> {
                        var wolf = mob(EntityType.WOLF, START.south(5));
                        wolf.tame(archer);
                        wolf.setHealth(wolf.getMaxHealth());
                        pet = wolf;
                        startBow(pet, false);
                        next(1);
                    }
                    case 3 -> {
                        log("Classic and smooth releases, edit after launch, exact mob expiry, eligible PvP, unchanged active charm and owned-pet wish marker");
                        shortCharm.discard(); original.discard(); pet.discard();
                        opponent.moveTo(Vec3.atBottomCenterOf(START.offset(8,0,8)));
                        dirk = item("returnbloom_dirk");
                        archer.setItemInHand(HAND, dirk);
                        next(4);
                    }
                    default -> throw new AssertionError("Unexpected shot");
                }
            }
            case 3 -> {
                if (now() < shortEnd) {
                    require(shortCharm.getPersistentData().hasUUID(CHARM), "Short charm ended early"); return;
                }
                equal(now(), shortEnd, "Missed expiry frame");
                require(!shortCharm.getPersistentData().hasUUID(CHARM), "Short charm survived its deadline");
                shortCharm.discard();
                opponent = player("BalanceOpponent", START.offset(4,0,4));
                startBow(opponent, true);
                next(1);
            }
            case 4 -> {
                if (age() < 4) return;
                equal(archer.getAttributeValue(Attributes.ATTACK_DAMAGE), 15, "Stock held Dirk damage");
                attackSpeed = archer.getAttributeValue(Attributes.ATTACK_SPEED);
                run(D + "baseHitDamage 7"); run(D + "backstabMultiplier 3");
                run(D + "turnBackCooldownSeconds 2.5"); run(D + "throwCooldownSeconds 1.25"); run(D + "throwDamage 4");
                next(5);
            }
            case 5 -> {
                if (age() < 16) return;
                require(archer.getMainHandItem() == dirk, "Edited held item required a replacement");
                equal(archer.getAttributeValue(Attributes.ATTACK_DAMAGE), 7, "Already-equipped Dirk was stale");
                equal(archer.getAttributeValue(Attributes.ATTACK_SPEED), attackSpeed, "Attack speed changed");
                require(sentDirkAdjustments.contains(-8d), "Owner did not receive the changed held Dirk over its inventory channel");
                require(archer.getAttackStrengthScale(.5f) > .99, "Fixture melee swing was not charged");
                dummy = mob(EntityType.COW, START.east(2));
                dummy.setYHeadRot(-90); // Looking east, away from the archer.
                archer.setYRot(33);
                float before = dummy.getHealth();
                archer.attack(dummy);
                equal(before - dummy.getHealth(), 21, "Real backstab, base and multiplier");
                turnEnd = archer.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getLong("uwu.returnbloom_turn_back_until");
                equal(turnEnd - now(), 50, "Turn Back cooldown");
                equal(dummy.getYRot(), 33, "Turn Back did not execute");
                run(D + "turnBackCooldownSeconds 9");
                equal(archer.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getLong("uwu.returnbloom_turn_back_until"), turnEnd, "Existing Turn Back retimed");
                dummy.discard();
                value("returnbloom_dirk", "baseHitDamage").set(13d);
                value("returnbloom_dirk", "baseHitDamage").save();
                next(6);
            }
            case 6 -> {
                if (age() < 3) return;
                equal(archer.getAttributeValue(Attributes.ATTACK_DAMAGE), 13, "Editor-style write did not reach held equipment");
                require(sentDirkAdjustments.contains(-2d), "Companion edit did not resynchronize held-slot contents");
                dummy = mob(EntityType.COW, START.east(5));
                archer.lookAt(Anchor.EYES, dummy.getBoundingBox().getCenter());
                ability(1);
                require(archer.getMainHandItem().isEmpty(), "Native ability receiver did not launch the Dirk");
                throwEnd = archer.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getLong("uwu.returnbloom_throw_until");
                equal(throwEnd - now(), 25, "Dirk throw cooldown");
                run(D + "throwCooldownSeconds 8");
                next(7);
            }
            case 7 -> {
                if (dummy.getHealth() == dummy.getMaxHealth()) return;
                equal(dummy.getMaxHealth() - dummy.getHealth(), 4, "Physical Dirk used base hit damage instead of its throw setting");
                equal(archer.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getLong("uwu.returnbloom_throw_until"), throwEnd, "Active throw retimed");
                log("Already-held Dirk, separate base and physical throw damage, unchanged attack speed, real melee backstab, Turn Back, fractional cooldowns and unchanged deadlines");
                next(8);
            }
            case 8 -> {
                if (!archer.getMainHandItem().is(RevivalBlossoms.DIRK.get())) return;
                run(W + "blossom_revival rechargeSeconds 3.5");
                archer.hurt(archer.damageSources().generic(), 1000);
                equal(archer.getHealth(), archer.getMaxHealth(), "Fatal hit did not trigger revival");
                equal(RevivalBlossoms.remaining(archer, archer.getMainHandItem()), 70, "Shared revival duration");
                equal(RevivalBlossoms.remaining(archer, item("dawnpetal_axe")), 70, "Second blossom bypassed shared lockout");
                run(W + "blossom_revival rechargeSeconds 1");
                equal(RevivalBlossoms.remaining(archer, archer.getMainHandItem()), 70, "Running revival retimed");
                archer.setItemInHand(HAND, new ItemStack(Items.IRON_SWORD));
                next(9);
            }
            case 9 -> {
                if (age() < 4) return;
                equal(archer.getAttributeValue(Attributes.ATTACK_DAMAGE), 6, "Vanilla sword retained UwU damage");
                log("Shared fatal-hit revival and vanilla equipment isolation");
                run(W + "heart_launcher castCooldownSeconds 1.5");
                run(W + "heart_launcher abilityDamage 6");
                dummy.discard(); dummy = mob(EntityType.COW, START.south(5));
                var launcher = item("heart_launcher"); archer.setItemInHand(HAND, launcher);
                archer.lookAt(Anchor.EYES, dummy.getBoundingBox().getCenter());
                launcher.use(level, archer, HAND);
                require(archer.getCooldowns().isOnCooldown(launcher.getItem()), "Launcher did not set a cooldown");
                next(10);
            }
            case 10 -> {
                if (age() < 30) {
                    require(archer.getCooldowns().isOnCooldown(item("heart_launcher").getItem()), "Launcher cooldown ended early");
                    return;
                }
                require(!archer.getCooldowns().isOnCooldown(item("heart_launcher").getItem()), "Launcher cooldown ran long");
                equal(dummy.getMaxHealth() - dummy.getHealth(), 6, "Separate launcher blast damage");
                log("Real Heart Launcher direct use, 30-tick cooldown and six-point blast");
                dummy.discard();
                archer.moveTo(Vec3.atBottomCenterOf(START));
                archer.setDeltaMovement(Vec3.ZERO);
                dummy = mob(EntityType.COW, START.east(3));
                archer.setItemInHand(HAND, item("dawnpetal_axe"));
                run(W + "dawnpetal_axe heartSpinDamage 5");
                run(W + "dawnpetal_axe heartSpinCooldownSeconds 12.5");
                next(11);
            }
            case 11 -> {
                if (age() < 3) return;
                ability(2);
                equal(archer.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getLong("uwu.dawnpetal_heart_spin_until") - now(),
                        250, "Heart Spin deadline longer than the shipped window");
                next(12);
            }
            case 12 -> {
                if (age() < 22) return;
                equal(dummy.getMaxHealth() - dummy.getHealth(), 5, "Heart Spin must use its own hit setting once per target");
                dummy.discard(); dummy = mob(EntityType.COW, START.south(5));
                require("chakram".equals(CounterArsenal.entry("counter_iron_twinblade").shape()), "Fixture must use an actual playable chakram");
                reject(W + "counter_iron_chakram abilityDamage 3", console()); // Retired ring bow answers the enemy chakram.
                run(W + "counter_iron_twinblade castCooldownSeconds 1.75");
                run(W + "counter_iron_twinblade abilityDamage 3");
                run(W + "counter_iron_twinblade baseHitDamage 8");
                archer.setItemInHand(HAND, item("counter_iron_twinblade")); next(13);
            }
            case 13 -> {
                if (age() < 3) return;
                equal(archer.getAttributeValue(Attributes.ATTACK_DAMAGE), 8, "Counter's tuned melee base");
                archer.lookAt(Anchor.EYES, dummy.getBoundingBox().getCenter());
                archer.getMainHandItem().use(level, archer, HAND);
                require(archer.getMainHandItem().isEmpty(), "Actual chakram did not leave the hand");
                next(14);
            }
            case 14 -> {
                if (age() < 35) {
                    require(archer.getCooldowns().isOnCooldown(item("counter_iron_twinblade").getItem()), "Chakram cooldown released early");
                    return;
                }
                require(!archer.getCooldowns().isOnCooldown(item("counter_iron_twinblade").getItem()), "Chakram cooldown ran long");
                require(counterHits.equals(List.of(3F)), "Expected one tuned three-point direct chakram hit, saw " + counterHits);
                require(dummy.getMaxHealth() - dummy.getHealth() >= 3, "Direct chakram hit did not reduce health");
                equal(value("counter_gold_twinblade", "abilityDamage").get(), 19, "Second chakram leaked balance");
                var glitch = item("glitch_bow");
                int form = GlitchBow.FORMS.indexOf("counter_iron_twinblade");
                CustomData.update(DataComponents.CUSTOM_DATA, glitch, tag -> tag.putInt("uwu_form",form));
                archer.setItemInHand(HAND, glitch); next(15);
            }
            case 15 -> {
                if (age() < 3) return;
                equal(archer.getAttributeValue(Attributes.ATTACK_DAMAGE), 8, "Glitch missed the displayed form's melee value");
                equal(archer.getAttributeValue(Attributes.ATTACK_SPEED), 1.3, "Glitch attack speed changed");
                run(W + "counter_iron_twinblade baseHitDamage 15"); next(16);
            }
            case 16 -> {
                if (age() < 3) return;
                equal(archer.getAttributeValue(Attributes.ATTACK_DAMAGE), 15, "Held Glitch retained balance after reset");
                log("Independent Heart Spin damage and long deadline, real chakram throw, separate counter rows, held Glitch form and reset");
                run(C + " 7");
                require(configText().contains("cupidBowCharmSeconds = 7"), "Command did not persist seconds");
                finish();
            }
            default -> throw new AssertionError("Unknown test stage");
        }
    }

    private void startBow(LivingEntity target, boolean smooth) {
        aim = target;
        bow = item("cupid_bow");
        if (smooth) CustomData.update(DataComponents.CUSTOM_DATA, bow, tag -> tag.putBoolean("uwudimension:smooth_cupid", true));
        archer.setItemInHand(HAND, bow);
        archer.lookAt(Anchor.EYES, target.getBoundingBox().getCenter());
        require(bow.use(level, archer, HAND).getResult().consumesAction(), "Bow refused to draw");
    }

    private void ability(int index) throws Exception {
        var useType = Class.forName("dev.uwu.dimension.DawnpetalAbilities$Use");
        var make = useType.getDeclaredConstructors()[0]; make.setAccessible(true);
        var tag = archer.getMainHandItem().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        require(tag.hasUUID("uwu_revival_id"), "Held blossom missing its identity");
        Object packet = make.newInstance((long) ++sequence, now(), level.dimension().location(),
                archer.getInventory().selected, tag.getUUID("uwu_revival_id"), index);
        var receive = Class.forName("dev.uwu.dimension.DawnpetalAbilities")
                .getDeclaredMethod("receive", ServerPlayer.class, useType);
        receive.setAccessible(true); receive.invoke(null, archer, packet);
    }

    private <T extends Mob> T mob(EntityType<T> type, BlockPos pos) {
        T mob = type.create(level); require(mob != null, "Missing fixture entity");
        mob.moveTo(Vec3.atBottomCenterOf(pos)); mob.setNoAi(true); mob.setPersistenceRequired();
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        mob.setHealth(mob.getMaxHealth());
        require(level.addFreshEntity(mob), "Fixture mob rejected");
        return mob;
    }

    @SuppressWarnings("unchecked")
    private ServerPlayer player(String name, BlockPos pos) throws Exception {
        var player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
        var link = new Connection(PacketFlow.SERVERBOUND);
        new ServerGamePacketListenerImpl(server, link, player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet, PacketSendListener listener) {
                if (packet instanceof ClientboundContainerSetSlotPacket slot && player == archer && slot.getSlot() == 36
                        && slot.getItem().is(RevivalBlossoms.DIRK.get())) {
                    var mods = slot.getItem().get(DataComponents.ATTRIBUTE_MODIFIERS);
                    if (mods != null) mods.modifiers().stream().filter(m -> m.modifier().id().toString().equals("uwudimension:configured_base_hit"))
                            .forEach(m -> sentDirkAdjustments.add(m.modifier().amount()));
                }
            }
        };
        channels.add(new EmbeddedChannel(link));
        player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
        player.moveTo(Vec3.atBottomCenterOf(pos));
        var list = PlayerList.class.getDeclaredField("players"); list.setAccessible(true);
        ((List<ServerPlayer>) list.get(server.getPlayerList())).add(player);
        var byId = PlayerList.class.getDeclaredField("playersByUUID"); byId.setAccessible(true);
        ((Map<UUID,ServerPlayer>) byId.get(server.getPlayerList())).put(player.getUUID(), player);
        level.addNewPlayer(player); player.initInventoryMenu(); players.add(player); return player;
    }

    private static ModConfigSpec.DoubleValue value(String weapon, String key) {
        return AcquisitionPolicy.SPEC.getValues().get(List.of("weapons", weapon, key));
    }
    private CommandSourceStack console() { return server.createCommandSourceStack(); }
    private void run(String command) throws Exception {
        try {
            require(server.getCommands().getDispatcher().execute(command, console()) == 1, "Command failed: " + command);
        } catch (CommandSyntaxException failure) {
            throw new AssertionError("Registered command failed: " + command, failure);
        }
    }
    private void reject(String command, CommandSourceStack source) throws Exception {
        try { server.getCommands().getDispatcher().execute(command, source); }
        catch (CommandSyntaxException expected) { return; }
        throw new AssertionError("Bad or unauthorized input accepted: " + command);
    }
    private void finish() throws Exception {
        String stageName = RESTART ? "restart" : "fresh";
        log("Loaded SERVER file " + config);
        Path out = Path.of(System.getProperty("uwu.check.out"));
        Files.write(out.resolve(stageName + "-checks.txt"), checks);
        stage = -1;
        System.out.println("UWU WEAPON SETTINGS PASS " + stageName);
        server.halt(false);
    }
    private String configText() throws Exception { return Files.readString(config); }
    private static ItemStack item(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("uwudimension",id)).getDefaultInstance(); }
    private void log(String message) { checks.add(message); System.out.println("BALANCE CHECK " + message); }
    private long now() { return level.getGameTime(); }
    private long age() { return now() - since; }
    private void next(int next) { stage = next; since = now(); }
    private static void equal(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < .001, message + ": expected " + expected + ", observed " + actual);
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
