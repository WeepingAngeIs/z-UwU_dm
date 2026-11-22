package dev.uwu.dialoguecheck;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import dev.uwu.dimension.BlossomWarden;
import dev.uwu.dimension.GuardianGreeting;
import dev.uwu.dimension.People;
import dev.uwu.dimension.ReefLife;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Walk real visitors up to residents, let the production goal and renderer do all greeting work. */
public final class WelcomeCheck {
    private enum Stage { OFF, CONTROL, APPROACH, SHOW, EXIT, RETURN, LEAVING_APPROACH, OUTSIDE, COMPETE, RECONNECT, NEXT_DAWN, INTERRUPT, WARN, DONE }
    private static Stage stage = Stage.OFF;
    private static final BlockPos COURT = new BlockPos(128, 296, 128);
    private static final Vec3 ARRIVAL = Vec3.atBottomCenterOf(COURT).add(0, 0, -8);
    private static final Path OUT = Path.of(System.getProperty("uwu.check.evidence"), "welcome");
    private static final boolean REFERENCE = "with-reference".equals(System.getProperty("uwu.check.dialogueMode"));
    static volatile Throwable problem;
    static volatile boolean done;
    private static ServerLevel level;
    private static ServerPlayer owner, peer;
    private static BlossomWarden guard, rival;
    private static GuardianGreeting previous = GuardianGreeting.NONE, performed;
    private static long lastChange, lastDay;
    private static volatile int trial, entityId = -1;
    private static volatile boolean filming;
    private static double startGap, greetingGap, bestExit;
    private static long atFirst;
    private static int starts, maxSimultaneous;
    private static boolean wasG1, wasG2, sawApproach;
    private static long beganSecondVisit;
    private static CompoundTag firstVisitorReceipt, reconnectReceipt;
    private static volatile int witnessA = -1, witnessB = -1, witnessFrames;
    private static final EnumSet<GuardianGreeting> males = EnumSet.noneOf(GuardianGreeting.class);
    private static final EnumSet<GuardianGreeting> females = EnumSet.noneOf(GuardianGreeting.class);
    private static final List<Map<String,Object>> trials = new ArrayList<>();
    private static final Map<String, Integer> frameCounts = new HashMap<>();
    private static final Set<String> photos = new HashSet<>();
    private static volatile int hearts, dings;
    private static int heartBefore, dingBefore;
    private static BlossomWarden drawing, seen;
    private static int draws;
    private static String frameStyle;
    private static float alpha;
    public record View(double x, double y, double z, float yaw, float pitch) {}

    static boolean enabled() { return "true".equals(System.getenv("UWU_GUARDIAN_WELCOME_CHECK")); }
    static void register() {
        NeoForge.EVENT_BUS.addListener(WelcomeCheck::tick);
        NeoForge.EVENT_BUS.addListener(WelcomeCheck::frame);
    }

    static void start(ServerPlayer player) {
        owner = player;
        level = owner.server.getLevel(ReefLife.REACH);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, owner.server);
        // The previous dialogue scenes create real village Guardians. Give the single-speaker
        // captures their own court before explicitly bringing two residents together below.
        for (int x=-15;x<=15;x++) for (int z=-20;z<=20;z++) {
            var floor = Math.abs(x) == 5 ? Blocks.CHERRY_PLANKS
                    : Math.abs(x) < 5 ? Blocks.SMOOTH_QUARTZ : Blocks.MOSS_BLOCK;
            level.setBlockAndUpdate(COURT.offset(x,-1,z), floor.defaultBlockState());
            if (Math.abs(x) > 6 && Math.floorMod(x*11+z*5, 17) == 0)
                level.setBlockAndUpdate(COURT.offset(x,0,z), Blocks.PINK_PETALS.defaultBlockState());
        }
        for (int x : new int[]{-6,6}) for (int z : new int[]{-10,4,15}) {
            level.setBlockAndUpdate(COURT.offset(x,0,z), Blocks.CHERRY_FENCE.defaultBlockState());
            level.setBlockAndUpdate(COURT.offset(x,1,z), Blocks.LANTERN.defaultBlockState());
        }
        owner.setGameMode(GameType.SURVIVAL);
        position();
        guard = create(false, false, 4.5, false);
        change(Stage.CONTROL);
    }

    private static void position() {
        require(owner.teleportTo(level, ARRIVAL.x, ARRIVAL.y, ARRIVAL.z, Set.of(), 0, -10), "visitor teleport");
        owner.fallDistance = 0;
    }

    private static BlossomWarden create(boolean female, boolean dusk, double x, boolean resident) {
        var npc = People.BLOSSOM_WARDEN.get().create(level);
        var data = npc.saveWithoutId(new CompoundTag());
        data.putBoolean("FemaleGuardian", female);
        data.putBoolean("DuskGuardian", dusk);
        data.putBoolean("KeeperSummonedGuardian", true); // permits a second resident at one court in the concurrency test
        npc.readAdditionalSaveData(data);
        npc.moveTo(COURT.getX()+x,COURT.getY(),COURT.getZ()+4.5,180,0);
        if (resident) npc.bindVillage(COURT);
        require(level.addFreshEntity(npc), "guardian spawn");
        return npc;
    }

    private static void dawn(int day) {
        long time = day * 24000L + 6000;
        owner.server.overworld().setDayTime(time);
        level.setDayTime(time);
    }

    private static void beginTrial() {
        if (guard != null) guard.discard();
        if (rival != null) { rival.discard(); rival = null; }
        position();
        dawn(100 + trial);
        guard = create(trial >= 6 && trial <= 12, trial >= 12, .5, true);
        entityId = guard.getId(); startGap = owner.distanceTo(guard);
        heartBefore = hearts; dingBefore = dings; filming = true; sawApproach = false;
        change(Stage.APPROACH);
    }

    private static void tick(ServerTickEvent.Post event) {
        if (stage == Stage.OFF || stage == Stage.DONE || problem != null) return;
        try {
            long age = level.getGameTime() - lastChange;
            if (age == 150 && stage == Stage.APPROACH) reportApproach();
            if (peer != null && (age == 65 || age == 180)) reportPeer();
            require(age < 460, "server timeout " + stage + " trial=" + trial + " gap=" + owner.distanceTo(guard)
                    + " guard=" + guard.position() + " visitor=" + owner.position() + " greeting=" + guard.greeting());
            switch (stage) {
                case CONTROL -> {
                    require(guard.greeting() == GuardianGreeting.NONE, "unbound Guardian greeted");
                    if (age >= 45) beginTrial();
                }
                case APPROACH -> {
                    sawApproach |= guard.approachingVisitor() && !guard.getNavigation().isDone();
                    if (guard.greeting() == GuardianGreeting.NONE) return;
                    require(sawApproach && !guard.approachingVisitor(), "missing running approach or it continued through welcome");
                    performed = guard.greeting();
                    require(performed != previous, "repeated yesterday's gesture");
                    greetingGap = owner.distanceTo(guard);
                    require(startGap > 11 && greetingGap <= 3.8 && greetingGap > 1.1, "approach distance " + greetingGap);
                    require(visit(owner).getLong("Day") == 100 + trial, "daily receipt missing");
                    (guard.female() ? females : males).add(performed);
                    previous = performed; bestExit = greetingGap; atFirst = level.getGameTime();
                    change(Stage.SHOW);
                }
                case SHOW -> {
                    if (guard.greeting() == performed) return;
                    require(guard.greeting() == GuardianGreeting.NONE, "chained gestures in one welcome");
                    require(level.getGameTime()-atFirst >= performed.duration, "truncated greeting");
                    change(Stage.EXIT);
                }
                case EXIT -> {
                    require(guard.greeting() == GuardianGreeting.NONE, "same visit spammed");
                    bestExit = Math.max(bestExit, owner.distanceTo(guard));
                    if (age < 30) return;
                    require(bestExit > greetingGap + 1.0, "did not walk away gap=" + greetingGap + " exit=" + bestExit);
                    require(hearts-heartBefore == (performed == GuardianGreeting.KISS ? 1 : 0), "heart cue count " + performed);
                    require(dings-dingBefore == (performed == GuardianGreeting.MEME_WINK ? 1 : 0), "ding cue count " + performed);
                    trials.add(Map.of("day",100+trial,"female",guard.female(),"dusk",guard.duskGuardian(),
                            "gesture",performed.toString(),"arrival",startGap,"at_greeting",greetingGap,"walked_away",bestExit));
                    filming = false;
                    if (trial == 0) {
                        owner.teleportTo(level, ARRIVAL.x, ARRIVAL.y, ARRIVAL.z+68, Set.of(), 0, 0);
                        change(Stage.RETURN);
                    } else if (++trial < 14) beginTrial();
                    else {
                        require(males.containsAll(Set.of(GuardianGreeting.WAVE,GuardianGreeting.JACKS,GuardianGreeting.SPIN_BOW,GuardianGreeting.MEME_WINK)), "bulky repertoire");
                        require(females.containsAll(Set.of(GuardianGreeting.TWIRL,GuardianGreeting.KISS,GuardianGreeting.WINK,GuardianGreeting.SPIN_BOW)), "dainty repertoire");
                        startCompetition();
                    }
                }
                case RETURN -> {
                    if (age == 10) { position(); rival = create(false,false,2,true); }
                    require(guard.greeting() == GuardianGreeting.NONE && (rival == null || rival.greeting() == GuardianGreeting.NONE),
                            "same-day re-entry/second guard greeted");
                    if (age >= 70) { trial++; beginTrial(); }
                }
                case LEAVING_APPROACH -> {
                    require(!visit(peer).contains("Day"), "a distant approach spent the daily greeting");
                    if (!guard.approachingVisitor() && !rival.approachingVisitor()) return;
                    require(Math.min(peer.distanceTo(guard),peer.distanceTo(rival)) > 4, "visitor already reached");
                    peer.setPos(ARRIVAL.add(2,0,-36));
                    change(Stage.OUTSIDE);
                }
                case OUTSIDE -> {
                    require(!visit(peer).contains("Day"), "interrupted approach spent a greeting");
                    if (age < 18) return;
                    require(!guard.approachingVisitor() && !rival.approachingVisitor(), "abandoned approach still reserved");
                    peer.setPos(ARRIVAL.add(1,0,5));
                    beganSecondVisit=-1;
                    witnessA = guard.getId(); witnessB = rival.getId(); witnessFrames=0;
                    change(Stage.COMPETE);
                }
                case COMPETE -> {
                    boolean a = guard.greeting() != GuardianGreeting.NONE, b = rival.greeting() != GuardianGreeting.NONE;
                    starts += (a && !wasG1 ? 1 : 0) + (b && !wasG2 ? 1 : 0);
                    wasG1 = a; wasG2 = b;
                    maxSimultaneous = Math.max(maxSimultaneous, (a?1:0) + (b?1:0));
                    require(starts<=1 && maxSimultaneous<=1, "duplicate welcome for the second visitor");
                    require(visit(owner).equals(firstVisitorReceipt), "watching changed the first visitor's receipt");
                    if ((a || b) && beganSecondVisit<0) {
                        var speaker = a ? guard : rival;
                        require(visit(peer).getLong("Day")==lastDay && speaker.distanceTo(peer)<=3.8, "wrong visitor assigned");
                        beganSecondVisit=level.getGameTime();
                    }
                    if (beganSecondVisit<0 || level.getGameTime()-beganSecondVisit<85) return;
                    require(starts==1 && witnessFrames>=2, "first player never saw the other visitor's overhead welcome: " + witnessFrames);
                    witnessA = witnessB = -1;
                    reconnectReceipt = visit(peer).copy();
                    var saved = peer.saveWithoutId(new CompoundTag());
                    var profile = peer.getGameProfile();
                    ServerSpeechProbe.detach(peer);
                    peer = ServerSpeechProbe.attach(owner, level, profile, ARRIVAL.add(1,0,0), saved);
                    require(visit(peer).equals(reconnectReceipt), "real player save/load lost daily receipt");
                    change(Stage.RECONNECT);
                }
                case RECONNECT -> {
                    require(guard.greeting() == GuardianGreeting.NONE && rival.greeting() == GuardianGreeting.NONE, "reconnect welcomed twice");
                    if (age < 65) return;
                    owner.setGameMode(GameType.SPECTATOR);
                    dawn(100 + trial);
                    change(Stage.NEXT_DAWN);
                }
                case NEXT_DAWN -> {
                    if (visit(peer).getLong("Day") != 100+trial) return;
                    require(visit(peer).getInt("LastGesture") != reconnectReceipt.getInt("LastGesture")
                            && visit(owner).equals(firstVisitorReceipt), "new morning did not vary or changed observer");
                    ServerSpeechProbe.detach(peer); peer = null;
                    owner.setGameMode(GameType.SURVIVAL);
                    rival.discard(); rival = null;
                    guard.discard(); guard = create(false,false,.5,true);
                    dawn(100 + trial);
                    position(); change(Stage.INTERRUPT);
                }
                case INTERRUPT -> {
                    if (guard.greeting() == GuardianGreeting.NONE) return;
                    owner.attack(guard);
                    change(Stage.WARN);
                }
                case WARN -> {
                    require(guard.greeting() == GuardianGreeting.NONE, "combat and welcome overlapped");
                    if (!guard.warningShake()) return;
                    require(age < 95, "first warning was delayed by the greeting");
                    stage = Stage.DONE;
                    System.out.println("GUARDIAN DAILY VISITS PASS days=14 tested departures, re-entry, interrupted approach, observer, player save/rejoin, next morning, two residents and combat preemption");
                    Files.createDirectories(OUT);
                    Files.writeString(OUT.resolve("visits.json"), new GsonBuilder().setPrettyPrinting().create().toJson(trials));
                    done = true;
                }
                default -> { }
            }
        } catch (Throwable ex) { problem = ex; }
    }

    private static void reportApproach() {
        var nearby = level.getEntitiesOfClass(BlossomWarden.class, owner.getBoundingBox().inflate(30));
        System.out.println("WELCOME APPROACH trial=" + trial + " visit=" + visit(owner) + " calendar="
                + owner.server.overworld().getDayTime() + " player=" + owner.isAlive() + "/" + owner.isSpectator()
                + " guardians=" + nearby.stream().map(g -> g.getId() + "/" + g.greeting() + "/"
                    + g.position() + "/path=" + g.getNavigation().getPath() + "/ai=" + g.isNoAi()
                    + "/juvenile=" + g.isBaby() + "/warning=" + g.chasing()).toList());
    }

    private static void startCompetition() throws Exception {
        guard.discard();
        position();
        guard = create(false,false,-1.5,true);
        rival = create(true,true,2.5,true);
        lastDay = 100 + trial - 1;
        firstVisitorReceipt = visit(owner).copy();
        peer = ServerSpeechProbe.attach(owner, level, new GameProfile(UUID.randomUUID(),"WelcomeVisitor"),
                ARRIVAL.add(2,0,-3), null);
        require(!visit(peer).contains("Day"), "independent visitor already spent a greeting");
        starts = 0; maxSimultaneous = 0; wasG1 = wasG2 = false;
        change(Stage.LEAVING_APPROACH);
    }

    private static void reportPeer() {
        System.out.println("WELCOME SECOND VISITOR stage=" + stage + " peer=" + peer.position() + " alive=" + peer.isAlive()
            + " removed=" + peer.isRemoved() + " sleeping=" + peer.isSleeping() + " spectator=" + peer.isSpectator()
            + " registered=" + level.players().contains(peer) + " sameLevel=" + (peer.level()==level) + " record=" + visit(peer)
            + " main=" + visit(owner) + " first=" + guard.position() + "/" + guard.approachingVisitor() + "/" + guard.greeting()
            + " second=" + rival.position() + "/" + rival.approachingVisitor() + "/" + rival.greeting());
    }

    private static CompoundTag visit(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound("UwUDimensionGuardianVisit");
    }

    private static void change(Stage s) { stage = s; lastChange = level.getGameTime(); }

    private static BlossomWarden subject() {
        var mc = Minecraft.getInstance();
        return filming && mc.level != null && mc.level.getEntity(entityId) instanceof BlossomWarden g ? g : null;
    }

    public static View camera(float partial) {
        if (!enabled()) return null;
        var g = subject();
        if (g == null || g.greeting()==GuardianGreeting.NONE) return null;
        float t = g.greetingTicks(partial);
        if (t < 22) return null; // the first twenty-two ticks use the actual visitor's first-person viewpoint
        double angle = t < 29 ? 75 : 157, distance = t < 29 ? 5.6 : 7.2;
        angle = Math.toRadians(angle) + .015 * Math.sin(t * .36);
        double x = g.getX() + distance * Math.sin(angle), z = g.getZ() - distance * Math.cos(angle);
        double y = g.getY() + (g.female() ? 1.7 : 2.05);
        return new View(x,y,z,(float)Math.toDegrees(angle),-2F);
    }

    public static void speaker(BlossomWarden entity) { drawing = entity; }
    public static void draw(Component text, String style, float fade) {
        if (drawing != null && (drawing.getId()==witnessA || drawing.getId()==witnessB) && text.getString().equals("Welcome")) witnessFrames++;
        if (drawing == null || !filming || drawing.getId()!=entityId) return;
        require(text.getString().equals("Welcome"), "wrong overhead words " + text.getString());
        seen=drawing; draws++; frameStyle=style; alpha=fade;
    }

    public static boolean near(double x,double y,double z) {
        var g = subject(); return g != null && g.distanceToSqr(x,y,z) < 12;
    }
    public static void heart() { hearts++; }
    public static void ding() { dings++; }

    private static void frame(RenderFrameEvent.Post event) {
        try {
            if (!filming || seen == null || problem != null) return;
            var mc=Minecraft.getInstance();
            require(draws==1, "duplicate frame " + draws);
            String expected = !seen.female() ? "LEAF" : seen.duskGuardian() ? "PLAIN" : "LOTUS";
            require(expected.equals(frameStyle), "wrong Guardian frame " + frameStyle);
            if (REFERENCE) {
                require(DialogueClientCheck.referenceMessages(seen).isEmpty(), "Chat Bubbles also owns the NPC bubble");
                require(!DialogueClientCheck.referenceMessages(mc.player).contains("Welcome"), "extra player Welcome bubble");
            }
            int t = (int)seen.greetingTicks(0);
            var gesture=seen.greeting();
            if (gesture==GuardianGreeting.NONE || alpha<.9F || t<5) return;
            String angle=t<22?"front":t<29?"side":"rear";
            String key=String.format("%02d-%s-%s-%s",trial,frameStyle,gesture,angle);
            frameCounts.merge(key,1,Integer::sum);
            boolean take = angle.equals("front") ? t>=12 && t<=21 : true;
            if (!take || photos.contains(key)) return;
            Files.createDirectories(OUT);
            try (var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(OUT.resolve(key+".png")); photos.add(key);
            }
        } catch (Throwable ex) { problem=ex; }
        finally { seen=null; draws=0; }
    }

    private static void require(boolean ok,String why) { if(!ok) throw new AssertionError(why); }
    private WelcomeCheck() {}
}
