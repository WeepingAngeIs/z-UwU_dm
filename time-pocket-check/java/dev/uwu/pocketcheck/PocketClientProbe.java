package dev.uwu.pocketcheck;

import com.mojang.blaze3d.platform.NativeImage;
import dev.uwu.dimension.TimePocket;
import dev.uwu.dimension.client.TimePocketClient;
import dev.uwu.lotusclientcheck.CandidateJarProbe;
import dev.uwu.pocketcheck.mixin.AudioSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.EXTEfx;

@Mod(value="uwu_pocket_check", dist=Dist.CLIENT)
public final class PocketClientProbe {
    private enum Stage { TITLE, PREPARE, BASELINE, WAIT_FIELD, COLOR_ON, COLOR_OFF, COLOR_AGAIN,
        MOTION, EXIT_EDGE, REENTER, END, HOME, RESTORED, TARGET_READY, TARGET_HIT, SECOND, LONG_WALK, SHORT_CAST, EARLY, GALLERY, DONE }
    static final Path OUT=Path.of(System.getProperty("uwu.pocket.evidence"));
    private static final Map<SoundInstance,String> markers=new ConcurrentHashMap<>();
    private static final Map<String,Float> gains=new ConcurrentHashMap<>(), air=new ConcurrentHashMap<>();
    private static Stage stage=Stage.TITLE;
    private static boolean opening,setup,requested,emitted,moveStarted,deadlinesArmed;
    private static int age,frameAge,trial;
    private static long started,until;
    private static String photo;
    private static Particle inside,outside;
    private static Vec3 insideAt,outsideAt;
    private static boolean particleCheck, walkFrame, reachedSolidWall, leftShort, shortPinged, cleanupPinged;
    private static boolean frozenMobDrawn, movingMobDrawn, frozenSandDrawn;

    public static void pose(net.minecraft.world.entity.Entity entity, float partial) {
        if(stage==Stage.DONE || !PocketWorldProbe.ready)return;
        var f=TimePocketClient.field(entity.level());
        if(f==null)return;
        if(entity instanceof net.minecraft.world.entity.animal.Pig || entity instanceof net.minecraft.world.entity.item.FallingBlockEntity) {
            if(f.at(entity.getX(),entity.getZ())) {
                if(partial!=1F) {fail(new AssertionError("Frozen model interpolated with "+partial));return;}
                if(entity instanceof net.minecraft.world.entity.animal.Pig)frozenMobDrawn=true;
                else frozenSandDrawn=true;
            } else if(entity instanceof net.minecraft.world.entity.animal.Pig && partial<.99F) movingMobDrawn=true;
        }
    }

    public PocketClientProbe() {
        CandidateJarProbe.verify();
        NeoForge.EVENT_BUS.addListener(PocketClientProbe::tick);
        NeoForge.EVENT_BUS.addListener(PocketClientProbe::frame);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PocketClientProbe::sound);
        NeoForge.EVENT_BUS.addListener(PocketGallery::beforeFrame);
        NeoForge.EVENT_BUS.addListener(PocketWorldProbe::tick);
        NeoForge.EVENT_BUS.addListener(PocketWorldProbe::used);
        NeoForge.EVENT_BUS.addListener(PocketWorldProbe::practiceImpact);
        NeoForge.EVENT_BUS.addListener(PocketWorldProbe::claim);
    }

    static void sound(PlaySoundSourceEvent event) {
        var snd=event.getSound();
        String label=markers.remove(snd);
        PocketCueProbe.event(event, label);
        if(label!=null) {
            int source=((AudioSource)event.getChannel()).pocketCheck$source();
            air.put(label,ALC.getCapabilities().ALC_EXT_EFX ? AL10.alGetSourcef(source,EXTEfx.AL_AIR_ABSORPTION_FACTOR) : -1F);
            gains.put(label,AL10.alGetSourcef(source,AL10.AL_GAIN));
        }
    }

    private static void ping(Minecraft mc,String label) {
        var p=mc.player;
        var marker=new SimpleSoundInstance(SoundEvents.WOODEN_BUTTON_CLICK_ON,SoundSource.BLOCKS,.8F,1F,
                RandomSource.create(),p.getX()+2,p.getEyeY(),p.getZ());
        markers.put(marker,label);
        mc.getSoundManager().play(marker);
    }

    private static void advance(Stage value) {
        stage=value;age=0;frameAge=0;requested=false;
        System.out.println("POCKET CLIENT STAGE "+value);
    }

    private static void tick(ClientTickEvent.Post ignored) {
        var mc=Minecraft.getInstance();
        if(stage==Stage.DONE)return;
        try {
            if(opening && !deadlinesArmed) { started=System.nanoTime();deadlinesArmed=true; }
            if(deadlinesArmed && System.nanoTime()-started>300_000_000_000L)throw new AssertionError("Timed out in "+stage+" age="+age+" gains="+gains);
            if(PocketWorldProbe.failure!=null)throw new AssertionError("Server fixture",PocketWorldProbe.failure);
            if(PocketCueProbe.failure!=null)throw new AssertionError("Native audio",PocketCueProbe.failure);
            if(!opening && mc.screen instanceof TitleScreen title) {
                opening=true;
                mc.createWorldOpenFlows().createFreshLevel("DisposableTimePocketClient",
                        new LevelSettings("Disposable Time Pocket",GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(152045L,false,false),WorldPresets::createNormalWorldDimensions,title);
                return;
            }
            if(opening && mc.player==null && mc.screen!=null) {
                for(var w:List.copyOf(mc.screen.children()))if(w instanceof Button b && Set.of("Proceed","I know what I'm doing!").contains(b.getMessage().getString())) {b.onPress();break;}
            }
            if(mc.player==null || mc.level==null || mc.screen!=null)return;
            age++;
            var server=mc.getSingleplayerServer();
            var player=mc.player;
            var f=TimePocketClient.field(mc.level);
            switch(stage) {
                case TITLE -> {
                    if(!setup) {
                        setup=true;var id=player.getUUID();Files.createDirectories(OUT);
                        server.execute(()->PocketWorldProbe.setup(server,id));
                        advance(Stage.PREPARE);
                    }
                }
                case PREPARE -> {
                    if(PocketWorldProbe.ready && atHome(mc) && age>=55 && frameAge>=8 &&
                            mc.level.getBlockState(new BlockPos(4,PocketWorldProbe.FLOOR+3,20)).is(Blocks.LIME_CONCRETE)
                            && player.getMainHandItem().is(TimePocket.CLOCK.get())) {
                        check(player.getMainHandItem().getHoverName().getString().equals("UwUzi"), "Wrong item name in the native client");
                        PocketCueProbe.requirePack(mc);
                        check(f==null,"Existing field at baseline");ping(mc,"before");photo="baseline";advance(Stage.BASELINE);
                    }
                }
                case BASELINE -> {
                    if(photo==null && gains.containsKey("before")) { pressUse(mc);advance(Stage.WAIT_FIELD); }
                }
                case WAIT_FIELD -> {
                    if(f!=null) {
                        check(f.negative() && f.caster().equals(player.getUUID()),"Wrong first state");
                        until=f.until();
                        inside=particle(mc,-3,214,12);outside=particle(mc,3,214,22);
                        insideAt=inside.getPos();outsideAt=outside.getPos();
                        ping(mc,"negative");advance(Stage.COLOR_ON);
                    }
                }
                case COLOR_ON -> {
                    check(f!=null && f.negative(),"Lost the active first field");
                    if(!requested && age>=3 && frameAge>=2) {photo="negative";requested=true;}
                    if(requested && photo==null && gains.containsKey("negative")) {
                        pressUse(mc);
                        server.execute(() -> {
                            PocketWorldProbe.duration(20);
                            PocketWorldProbe.setting(false);
                        });
                        advance(Stage.COLOR_OFF);
                    }
                }
                case COLOR_OFF -> {
                    check(f!=null && f.until()==until,"Filter toggle refreshed or canceled simulation");
                    if(!requested && !f.negative() && frameAge>=2) {ping(mc,"filter-off");photo="filter-off";requested=true;}
                    if(requested && photo==null && gains.containsKey("filter-off")) {
                        server.execute(()->PocketWorldProbe.setting(true));advance(Stage.COLOR_AGAIN);
                    }
                }
                case COLOR_AGAIN -> {
                    check(f!=null && f.until()==until,"Second toggle altered the duration");
                    if(!requested && f.negative() && frameAge>=2) {photo="negative-again";requested=true;}
                    if(requested && photo==null) {
                        check(inside.isAlive() && inside.getPos().distanceToSqr(insideAt)<1e-12,"Inside particle moved");
                        check(outside.getPos().distanceTo(outsideAt)>.05,"Outside particle stopped");
                        particleCheck=true;KeyMapping.click(mc.options.keyAttack.getKey());advance(Stage.MOTION);
                    }
                }
                case MOTION -> {
                    check(f!=null,"Field ended before motion was sampled");
                    if(!moveStarted && age>=5) {mc.options.keyRight.setDown(true);moveStarted=true;}
                    if(moveStarted && age>=12)mc.options.keyRight.setDown(false);
                    if(!emitted && age>=15 && PocketGallery.starOnCamera) {photo="projectile";emitted=true;}
                    if(age>=20 && emitted && photo==null) {
                        server.execute(()->server.getPlayerList().getPlayer(player.getUUID()).connection.teleport(.5,PocketWorldProbe.FLOOR+1,16.5,0,0));
                        advance(Stage.EXIT_EDGE);
                    }
                }
                case EXIT_EDGE -> {
                    check(f!=null && f.until()==until,"Crossing the edge ended the field");
                    if(!requested && !f.at(player.getX(),player.getZ()) && TimePocketClient.gain(SoundSource.BLOCKS)==1F){
                        ping(mc,"outside-field");requested=true;
                    }
                    if(requested && gains.containsKey("outside-field")){server.execute(PocketWorldProbe::home);advance(Stage.REENTER);}
                }
                case REENTER -> {
                    check(f!=null && f.until()==until,"Returning restarted the field");
                    if(!requested && atHome(mc) && TimePocketClient.gain(SoundSource.BLOCKS)<1F){ping(mc,"reentered");requested=true;}
                    if(requested && gains.containsKey("reentered"))advance(Stage.END);
                }
                case END -> {
                    if(f==null && PocketWorldProbe.resumeChecked && PocketWorldProbe.shotFinished) {
                        check(particleCheck && inside.getPos().distanceTo(insideAt)>.02,"Particle failed to resume");
                        check(frozenMobDrawn && movingMobDrawn && frozenSandDrawn,"Native pose evidence missing "+
                                frozenMobDrawn+"/"+movingMobDrawn+"/"+frozenSandDrawn);
                        server.execute(PocketWorldProbe::home);advance(Stage.HOME);
                    }
                }
                case HOME -> {
                    if (!requested && atHome(mc) && age >= 3) {
                        check(player.getCooldowns().isOnCooldown(TimePocket.CLOCK.get()), "Standard item allowed an immediate recast");
                        pressUse(mc);
                        requested = true;
                    }
                    if(requested && atHome(mc) && age>=8 && frameAge>=2) {
                        check(f == null, "Standard cooldown accepted a second activation after thaw");
                        ping(mc,"after");photo="restored";advance(Stage.RESTORED);
                    }
                }
                case RESTORED -> {
                    if(photo==null && gains.containsKey("after") && age>12) {
                        verifyAudio();
                        PocketCueProbe.firstThaw();
                        server.execute(()->PocketWorldProbe.prepareTarget(0));advance(Stage.TARGET_READY);
                    }
                }
                case TARGET_READY -> {
                    double want=32.5+4*trial;
                    if(Math.abs(player.getX()-want)<.15 && age>=5 && player.getMainHandItem().getItem() instanceof TimePocket.Clock
                            && (trial != 2 || player.getMainHandItem().is(TimePocket.NO_COOLDOWN.get()))) {
                        KeyMapping.click(mc.options.keyAttack.getKey());advance(Stage.TARGET_HIT);
                    }
                }
                case TARGET_HIT -> {
                    if(PocketWorldProbe.targetDone==trial) {
                        if(++trial<3){int next=trial;server.execute(()->PocketWorldProbe.prepareTarget(next));advance(Stage.TARGET_READY);}
                        else {server.execute(PocketWorldProbe::secondSetup);advance(Stage.SECOND);}
                    }
                }
                case SECOND -> {
                    if(atHome(mc) && age>5 && player.getMainHandItem().is(TimePocket.NO_COOLDOWN.get())) {
                        check(player.getMainHandItem().getHoverName().getString().equals("UwUzi (No Cooldown)"), "Missing second item name");
                        pressUse(mc);advance(Stage.LONG_WALK);
                    }
                }
                case LONG_WALK -> {
                    if (!requested && f != null) {
                        until = f.until();
                        requested = true;
                        photo = "walk-start";
                        server.execute(() -> PocketWorldProbe.duration(2));
                        mc.options.keyUp.setDown(true);
                    }
                    if (requested && f != null) {
                        check(f.until() == until, "A setting edit changed the freeze already in progress");
                        if (age == 10 || age == 250) pressUse(mc);
                        if (age == 80 || age == 181 || age == 345) ping(mc, "long-clock-" + age);
                        if (!walkFrame && photo == null && player.getZ() > 13 && player.getZ() < 18) {
                            check(mc.level.getBlockState(player.blockPosition().below()).is(Blocks.SMOOTH_STONE), "Walkable floor missing on client");
                            photo = "walk-middle";
                            walkFrame = true;
                        }
                        if (player.horizontalCollision && player.onGround() && player.getZ() > 19.65) reachedSolidWall = true;
                    }
                    if (f == null && PocketWorldProbe.walkEnded) {
                        mc.options.keyUp.setDown(false);
                        check(walkFrame && reachedSolidWall, "Native walking input did not meet the solid wall");
                        player.setXRot(-89);
                        pressUse(mc);
                        advance(Stage.SHORT_CAST);
                    }
                }
                case SHORT_CAST -> {
                    if (!requested && f != null) {
                        requested = true;
                        server.execute(() -> PocketWorldProbe.duration(1));
                    }
                    if (requested && !leftShort && f != null && age >= 10) {
                        int x = f.x(), z = f.z();
                        server.execute(() -> PocketWorldProbe.moveOutside(x, z));
                        leftShort = true;
                    }
                    if (leftShort && PocketWorldProbe.shortEnded && f == null && !shortPinged) {
                        check(TimePocketClient.gain(SoundSource.BLOCKS) == 1F, "Outside listener stayed damped");
                        ping(mc, "after-short");
                        shortPinged = true;
                    }
                    if (shortPinged && gains.containsKey("after-short")) {
                        PocketCueProbe.outsideThaw();
                        pressUse(mc);
                        advance(Stage.EARLY);
                    }
                }
                case EARLY -> {
                    if(!requested && f!=null && age>=6) {server.execute(PocketWorldProbe::cancelEarly);requested=true;}
                    if(requested && f==null && PocketWorldProbe.earlyEnded) {
                        server.execute(()->PocketGallery.setup(server,player.getUUID()));advance(Stage.GALLERY);
                    }
                }
                case GALLERY -> {
                    if(PocketGallery.complete()) {
                        if (!cleanupPinged) { ping(mc,"gallery-cleanup"); cleanupPinged = true; }
                        if (!gains.containsKey("gallery-cleanup")) return;
                        if(PocketCueProbe.failure!=null)throw new AssertionError("Native audio",PocketCueProbe.failure);
                        Files.writeString(OUT.resolve("audio.txt"),"OpenAL source gains "+gains+"\nAir absorption "+air+
                                "\n"+PocketCueProbe.summary()+"\n");
                        System.out.println("TIME POCKET CLIENT PASS "+gains+" "+air);
                        stage=Stage.DONE;mc.stop();
                    }
                }
                default -> {}
            }
            if (age>520)throw new AssertionError("Stage "+stage+" didn't finish; field="+f+" worldShot="+PocketWorldProbe.shotFinished+
                    " move="+PocketWorldProbe.casterMoved+" drawn="+PocketGallery.starOnCamera+" audio="+gains+" position="+player.position());
        } catch(Throwable ex){ fail(ex); }
    }

    private static Particle particle(Minecraft mc,double x,double y,double z) {
        Particle p=mc.particleEngine.createParticle(ParticleTypes.END_ROD,x,y,z,.15,0,.08);
        check(p!=null,"Test particle missing");p.setLifetime(180);return p;
    }
    private static void pressUse(Minecraft mc){KeyMapping.click(mc.options.keyUse.getKey());}
    private static boolean atHome(Minecraft mc){
        return Math.abs(mc.player.getX()-PocketWorldProbe.ORIGIN_X)<.03 && Math.abs(mc.player.getZ()-PocketWorldProbe.ORIGIN_Z)<.03;
    }

    private static void verifyAudio(){
        float before=gains.get("before");
        check(before>.02 && Math.abs(gains.get("negative")/before-.2)<.03 && Math.abs(gains.get("filter-off")/before-.2)<.03
                && Math.abs(gains.get("after")/before-1)<.03 && Math.abs(gains.get("outside-field")/before-1)<.03
                && Math.abs(gains.get("reentered")/before-.2)<.03,"Ordinary source volume did not duck and recover "+gains);
        if(air.get("before")>=0) check(air.get("negative")==10F && air.get("filter-off")==10F && air.get("after").equals(air.get("before")),
                "High-frequency damping failed to restore "+air);
    }

    private static void frame(RenderFrameEvent.Post ignored){
        if(stage==Stage.DONE)return;
        try {
            frameAge++;
            if(photo!=null && Minecraft.getInstance().screen==null) {save(photo+".png");photo=null;}
            if(stage==Stage.GALLERY)PocketGallery.capture();
        }catch(Throwable error){fail(error);}
    }
    static void save(String name) throws Exception {
        try(NativeImage im=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){im.writeToFile(OUT.resolve(name));}
    }
    static void check(boolean value,String why){PocketWorldProbe.check(value,why);}
    static void fail(Throwable error){
        if(stage==Stage.DONE)return;stage=Stage.DONE;
        System.err.println("TIME POCKET CHECK FAILED");error.printStackTrace();Minecraft.getInstance().stop();
    }
}
