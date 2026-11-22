package dev.uwu.depthcheck;

import com.mojang.blaze3d.platform.NativeImage;
import dev.uwu.dimension.CupidShrine;
import dev.uwu.dimension.ReefLife;
import dev.uwu.lotusclientcheck.CandidateJarProbe;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod("uwu_depth_check")
public final class DepthClient {
    private record Subject(String name, BlockPos block) {}
    private record Sweep(Subject subject, String name, double height, double radius, double elevation,
                         double first, double last, int samples) {}
    public record View(Vec3 center, Vec3 eye, float yaw, float pitch) {}

    private static final Subject SHRINE = new Subject("shrine",new BlockPos(8,252,8));
    private static final List<Subject> BLADES = List.of(
            new Subject("rose_dagger",new BlockPos(28,252,8)),
            new Subject("returnbloom_dirk",new BlockPos(8,252,28)),
            new Subject("counter_iron_twinblade",new BlockPos(28,252,28)));
    private static final List<Sweep> SWEEPS = sweeps();
    private static final Path OUTPUT = Path.of(System.getProperty("uwu.depth.evidence")).toAbsolutePath();
    private static boolean opening, setup, active, drawn, done;
    private static volatile boolean prepared;
    private static volatile Throwable serverError;
    private static int sweepIndex, sampleIndex, frameCount;
    private static long lastTick = -1, deadline;

    public DepthClient() {
        CandidateJarProbe.verify();
        NeoForge.EVENT_BUS.addListener(DepthClient::tick);
        NeoForge.EVENT_BUS.addListener(DepthClient::before);
        NeoForge.EVENT_BUS.addListener(DepthClient::frame);
    }

    private static List<Sweep> sweeps() {
        var result = new ArrayList<Sweep>();
        result.add(new Sweep(SHRINE,"hair-cross",1.53,1.55,16,-70,70,85));
        result.add(new Sweep(SHRINE,"hair-micro",1.54,1.45,19,6,10,41));
        result.add(new Sweep(SHRINE,"hair-hold",1.54,1.45,19,10,10,16));
        result.add(new Sweep(SHRINE,"rear",1.18,1.85,12,125,235,49));
        result.add(new Sweep(SHRINE,"middle",1.05,4.5,15,0,360,97));
        result.add(new Sweep(SHRINE,"far",1.05,12,15,0,360,73));
        for (var s : BLADES) {
            result.add(new Sweep(s,"near",1.26,2.1,9,-120,120,41));
            result.add(new Sweep(s,"middle",1.15,5.5,14,0,360,49));
            result.add(new Sweep(s,"far",1.15,12,14,0,360,37));
        }
        return List.copyOf(result);
    }

    private static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        try {
            if (!opening && mc.screen instanceof TitleScreen title) {
                opening=true;
                mc.createWorldOpenFlows().createFreshLevel("DisposableDepthCapture",
                        new LevelSettings("Model depth capture",GameType.CREATIVE,false,Difficulty.PEACEFUL,
                                true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(152045L,false,false),WorldPresets::createNormalWorldDimensions,title);
                return;
            }
            if (opening && mc.player==null && mc.screen!=null) {
                for (var widget : List.copyOf(mc.screen.children())) {
                    if (widget instanceof Button button && Set.of("Proceed","I know what I'm doing!").contains(button.getMessage().getString())) {
                        button.onPress();
                        break;
                    }
                }
            }
            if (done || mc.player==null || mc.level==null || mc.screen!=null) return;
            if (!setup) {
                setup=true;
                deadline=System.nanoTime()+240_000_000_000L;
                Files.createDirectories(OUTPUT.resolve("frames"));
                Files.writeString(OUTPUT.resolve("frames.tsv"),
                        "frame\tmodel\tview\tsample\ttick\tx\ty\tz\tyaw\tpitch\tdistance\ttargetX\ttargetY\ttargetZ\tfov\n");
                var server=mc.getSingleplayerServer();
                var uuid=mc.player.getUUID();
                server.execute(()->{
                    try {
                        var player=server.getPlayerList().getPlayer(uuid);
                        var level=server.getLevel(ReefLife.REACH);
                        if (player==null || level==null) throw new AssertionError("Missing native level or player");
                        player.setGameMode(GameType.SPECTATOR);
                        if (!player.teleportTo(level,18.5,254,18.5,Set.of(),0,0)) throw new AssertionError("Failed to enter Sakura Reach");
                        level.setDayTime(6000);
                        var subjects=new ArrayList<>(BLADES);
                        subjects.add(SHRINE);
                        for (var subject:subjects) {
                            var p=subject.block();
                            for (var floor:BlockPos.betweenClosed(p.offset(-4,-1,-4),p.offset(4,-1,4)))
                                level.setBlockAndUpdate(floor,Blocks.SMOOTH_STONE.defaultBlockState());
                            for (var air:BlockPos.betweenClosed(p.offset(-3,0,-3),p.offset(3,5,3)))
                                level.setBlockAndUpdate(air,Blocks.AIR.defaultBlockState());
                            if (subject==SHRINE) {
                                if (!level.setBlockAndUpdate(p,CupidShrine.state(Direction.SOUTH)))
                                    throw new AssertionError("Native shrine did not place");
                            } else {
                                var stand=new ArmorStand(level,p.getX()+.5,p.getY(),p.getZ()+.5);
                                var tag=stand.saveWithoutId(new CompoundTag());
                                tag.putBoolean("ShowArms",true);
                                tag.putBoolean("NoBasePlate",true);
                                var pose=new CompoundTag();
                                var right=new ListTag();
                                for (float angle:new float[]{-70,0,-18}) right.add(FloatTag.valueOf(angle));
                                pose.put("RightArm",right);
                                tag.put("Pose",pose);
                                stand.load(tag);
                                var item=BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("uwudimension",subject.name()));
                                var stack=new ItemStack(item);
                                if (stack.isEmpty()) throw new AssertionError("Missing "+subject.name());
                                stand.setItemSlot(EquipmentSlot.MAINHAND,stack);
                                if (!level.addFreshEntity(stand)) throw new AssertionError("Holder failed to enter world");
                            }
                        }
                        prepared=true;
                    } catch(Throwable ex) {serverError=ex;}
                });
            }
            if (serverError!=null) throw new AssertionError("World preparation",serverError);
            if (System.nanoTime()>deadline) throw new AssertionError("Timed out at sweep "+sweepIndex+" sample "+sampleIndex+" frames "+frameCount);
            if (prepared && mc.level.dimension().equals(ReefLife.REACH)
                    && mc.level.getBlockState(SHRINE.block()).is(CupidShrine.BLOCK.get())
                    && mc.level.getBlockEntity(SHRINE.block())!=null && mc.player.tickCount>50)
                active=true;
        } catch(Throwable e) { fail(e); }
    }

    public static View view() {
        if (!active || done) return null;
        var s=SWEEPS.get(sweepIndex);
        double t=(double)sampleIndex/(s.samples()-1);
        double az=Math.toRadians(s.first()+(s.last()-s.first())*t);
        double el=Math.toRadians(s.elevation());
        var c=Vec3.atBottomCenterOf(s.subject().block()).add(0,s.height(),0);
        var eye=c.add(s.radius()*Math.cos(el)*Math.sin(az),s.radius()*Math.sin(el),s.radius()*Math.cos(el)*Math.cos(az));
        var aim=c.subtract(eye);
        return new View(c,eye,(float)Math.toDegrees(Math.atan2(-aim.x,aim.z)),
                (float)-Math.toDegrees(Math.atan2(aim.y,aim.horizontalDistance())));
    }

    public static void shrineDrawn() {
        if (active && !done && SWEEPS.get(sweepIndex).subject()==SHRINE) drawn=true;
    }
    public static void itemDrawn(ItemStack stack) {
        if (active && !done && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals(SWEEPS.get(sweepIndex).subject().name())) drawn=true;
    }
    private static void before(RenderFrameEvent.Pre ignored) { drawn=false; }
    private static void frame(RenderFrameEvent.Post ignored) {
        if (!active || done || !drawn) return;
        try {
            var mc=Minecraft.getInstance();
            long tick=mc.level.getGameTime();
            if (tick==lastTick || mc.screen!=null) return;
            var actual=mc.gameRenderer.getMainCamera();
            var planned=view();
            if (actual.getPosition().distanceToSqr(planned.eye())>1e-8) return;
            lastTick=tick;
            var s=SWEEPS.get(sweepIndex);
            var pos=actual.getPosition();
            try (NativeImage image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(OUTPUT.resolve("frames").resolve(String.format(Locale.ROOT,"%04d.png",frameCount)));
            }
            Files.writeString(OUTPUT.resolve("frames.tsv"),String.format(Locale.ROOT,
                    "%d\t%s\t%s\t%d\t%d\t%.8f\t%.8f\t%.8f\t%.6f\t%.6f\t%.6f\t%.8f\t%.8f\t%.8f\t%d%n",
                    frameCount,s.subject().name(),s.name(),sampleIndex,tick,pos.x,pos.y,pos.z,actual.getYRot(),actual.getXRot(),
                    pos.distanceTo(planned.center()),planned.center().x,planned.center().y,planned.center().z,mc.options.fov().get()),
                    StandardOpenOption.APPEND);
            frameCount++;
            if (++sampleIndex==s.samples()) {
                System.out.println("DEPTH NATIVE SWEEP "+s.subject().name()+" "+s.name()+" distance="+s.radius()+" samples="+sampleIndex);
                sampleIndex=0;
                if (++sweepIndex==SWEEPS.size()) {
                    done=true;
                    System.out.println("DEPTH NATIVE CAPTURE COMPLETE frames="+frameCount);
                    mc.stop();
                }
            }
        } catch(Throwable e){fail(e);}
    }
    private static void fail(Throwable e) {
        if (done) return;
        done=true;
        System.err.println("DEPTH CAPTURE FAILED");
        e.printStackTrace();
        Minecraft.getInstance().stop();
    }
}
