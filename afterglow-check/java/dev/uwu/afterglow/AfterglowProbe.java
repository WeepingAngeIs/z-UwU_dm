package dev.uwu.afterglow;

import com.mojang.blaze3d.platform.NativeImage;
import dev.uwu.lotusclientcheck.CandidateJarProbe;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.opengl.GL11;

@Mod("uwu_afterglow_check")
public final class AfterglowProbe {
    private static final Path OUT=Path.of(System.getProperty("uwu.afterglow.evidence"));
    private static final boolean BENCH=Boolean.getBoolean("uwu.afterglow.bench");
    private static final ResourceKey<Level> REACH=ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath("uwudimension","sakura_reach"));
    private static final String PACK="UwU Afterglow.zip";
    private record View(String name,ResourceKey<Level> dim,int quality,int moon,int liquid,float yaw,float pitch,boolean rain) {}
    private static final List<View> views=new ArrayList<>();
    private static final Set<ResourceKey<Level>> prepared=new HashSet<>();
    private static int index,ticks,frames;
    private static String optionsKey="";
    private static volatile boolean ready;
    private static boolean opened,started,running,finished;
    private static volatile Throwable serverError;
    private static long start,firstBench,lastBench;
    private static final ArrayList<Long> frameTimes=new ArrayList<>();

    public AfterglowProbe() throws Exception {
        CandidateJarProbe.verify();
        Files.createDirectories(OUT);
        if(BENCH) {
            for(int round=0;round<3;round++) for(int i=0;i<3;i++) {
                int quality=(i+round)%3;
                views.add(new View("bench-"+round+"-"+quality,REACH,quality,0,90,180,14,true));
            }
        } else {
            for(String label:List.of("0","off-repeat","neutral","previous","2"))
                views.add(new View("overworld-"+label,Level.OVERWORLD,
                    label.equals("0")||label.equals("off-repeat")?0:2,0,90,180,5,false));
            for(int q:List.of(0,2)) views.add(new View("the_nether-"+q,Level.NETHER,q,0,90,180,5,false));
            for(int q:List.of(0,2)) views.add(new View("overworld-night-"+q,Level.OVERWORLD,q,0,90,180,-48,false));
            for(int q:List.of(0,2)) views.add(new View("overworld-rain-"+q,Level.OVERWORLD,q,0,90,180,4,true));
            views.add(new View("end-off",Level.END,0,0,90,180,0,false));
            views.add(new View("end-north",Level.END,2,0,90,180,0,false));
            views.add(new View("end-north-later",Level.END,2,0,90,180,0,false));
            views.add(new View("end-south",Level.END,2,0,90,0,0,false));
            views.add(new View("end-up",Level.END,2,0,90,180,-58,false));
            views.add(new View("end-down",Level.END,2,0,90,180,55,false));
            views.add(new View("end-off-return",Level.END,0,0,90,180,0,false));
            views.add(new View("moon-off",REACH,0,0,90,180,-89,false));
            for(int moon:List.of(0,1,2)) views.add(new View("moon-"+moon,REACH,2,moon,90,180,-89,false));
            views.add(new View("moon-off-return",REACH,0,0,90,180,-89,false));
            for(int b:List.of(100,55)) views.add(new View("liquids-"+b,REACH,2,0,b,180,18,false));
            for(int q:List.of(0,1,2)) views.add(new View("rain-"+q,REACH,q,0,90,180,14,true));
        }
        NeoForge.EVENT_BUS.addListener(AfterglowProbe::tick);
        NeoForge.EVENT_BUS.addListener(AfterglowProbe::beforeFrame);
        NeoForge.EVENT_BUS.addListener(AfterglowProbe::afterFrame);
    }

    private static void fail(Throwable t) {
        finished=true;
        System.err.println("AFTERGLOW CHECK FAILED");
        t.printStackTrace();
        Minecraft.getInstance().stop();
    }
    private static void tick(ClientTickEvent.Post ignored) {
        if(finished)return;
        try {
            Minecraft mc=Minecraft.getInstance();
            if(!opened && mc.screen instanceof TitleScreen title) {
                opened=true;
                mc.createWorldOpenFlows().createFreshLevel("AfterglowFixture",new LevelSettings("Afterglow fixture",
                    GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new WorldOptions(7235147L,false,false),WorldPresets::createNormalWorldDimensions,title);
                return;
            }
            if(mc.player==null || mc.level==null) {
                if(opened && mc.screen!=null) for(var child:List.copyOf(mc.screen.children()))
                    if(child instanceof Button b && Set.of("Proceed","I know what I'm doing!").contains(b.getMessage().getString())) { b.onPress();break; }
                return;
            }
            if(serverError!=null) throw new IllegalStateException("Fixture preparation",serverError);
            View v=views.get(index);
            if(!started) {
                if(mc.screen!=null) return;
                started=true; ready=false; running=false; ticks=0;frames=0;
                var server=mc.getSingleplayerServer();
                var id=mc.player.getUUID();
                server.execute(()->{
                    try {
                        var level=Objects.requireNonNull(server.getLevel(v.dim));
                        var player=Objects.requireNonNull(server.getPlayerList().getPlayer(id));
                        for(var rule:List.of(GameRules.RULE_DAYLIGHT,GameRules.RULE_WEATHER_CYCLE,GameRules.RULE_DOMOBSPAWNING))
                            level.getGameRules().getRule(rule).set(false,server);
                        long time=v.name.startsWith("moon")||v.name.startsWith("overworld-night")?18000L:6000L;
                        server.overworld().setDayTime(time);level.setDayTime(time);
                        server.overworld().setWeatherParameters(v.rain?0:100000,v.rain?100000:0,v.rain,false);
                        level.setRainLevel(v.rain?1f:0f);
                        if(prepared.add(v.dim)) fixture(level);
                        player.setGameMode(GameType.SPECTATOR);
                        if(!player.teleportTo(level,32.5,216,32.5,Set.of(),v.yaw,v.pitch)) throw new IllegalStateException("Teleport "+v.name);
                        ready=true;
                    } catch(Throwable t) {serverError=t;}
                });
            }
            if(!ready || mc.screen!=null || !mc.level.dimension().equals(v.dim) || mc.player.distanceToSqr(32.5,216,32.5)>0.02) return;
            mc.options.hideGui=true;
            mc.player.setDeltaMovement(Vec3.ZERO);
            mc.level.setRainLevel(v.rain?1f:0f);
            if(!running) {
                if(++ticks<12) return;
                apply(v);
                start=System.nanoTime();firstBench=0;lastBench=0;frameTimes.clear();frames=0;running=true;
                System.out.println("AFTERGLOW SCENE "+v.name);
            }
        } catch(Throwable t) { fail(t); }
    }

    private static void fixture(ServerLevel l) {
        for(int x=23;x<=41;x++) for(int z=17;z<=36;z++) {
            for(int y=214;y<=220;y++) l.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
            l.setBlock(new BlockPos(x,213,z),(l.dimension()==Level.END?Blocks.END_STONE:Blocks.SMOOTH_STONE).defaultBlockState(),2);
        }
        if(!l.dimension().equals(REACH))return;
        for(int x=27;x<=37;x++)for(int z=22;z<=26;z++) {
            if(x==32)continue;
            l.setBlock(new BlockPos(x,213,z),(x<32?Blocks.WATER:Blocks.LAVA).defaultBlockState(),2);
            l.setBlock(new BlockPos(x,212,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
        }
        var lamp=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("uwudimension","moonpearl_lamp"));
        if(lamp==Blocks.AIR)throw new IllegalStateException("Missing Moonpearl lamp");
        l.setBlock(new BlockPos(28,216,25),lamp.defaultBlockState(),2);
        l.setBlock(new BlockPos(36,216,25),Blocks.SHROOMLIGHT.defaultBlockState(),2);
    }
    private static Class<?> iris() throws Exception { return Class.forName("net.irisshaders.iris.Iris"); }
    private static void apply(View v) throws Exception {
        String pack=switch(v.name) {
            case "overworld-neutral" -> "Neutral Iris control";
            case "overworld-previous" -> "Afterglow d227 diagnostic.zip";
            default -> PACK;
        };
        String key=pack+"/"+v.quality+"/"+v.moon+"/"+v.liquid;
        if(key.equals(optionsKey))return;
        optionsKey=key;
        var iris=iris();var cfg=iris.getMethod("getIrisConfig").invoke(null);
        cfg.getClass().getMethod("setShaderPackName",String.class).invoke(cfg,pack);
        cfg.getClass().getMethod("setShadersEnabled",boolean.class).invoke(cfg,v.quality!=0);
        cfg.getClass().getMethod("save").invoke(cfg);
        if(v.quality!=0 && pack.equals(PACK)) {
            Properties p=new Properties();
            p.setProperty("REACH_MOON",Integer.toString(v.moon));
            p.setProperty("AFTERGLOW_QUALITY",Integer.toString(v.quality));
            p.setProperty("LIQUID_BRIGHTNESS",Integer.toString(v.liquid));
            iris.getMethod("queueShaderPackOptionsFromProperties",Properties.class).invoke(null,p);
        }
        iris.getMethod("reload").invoke(null);
    }
    private static void beforeFrame(RenderFrameEvent.Pre ignored) {
        if(!running||finished)return;
        var mc=Minecraft.getInstance();
        if(mc.player==null)return;
        var v=views.get(index);
        double phase=BENCH && firstBench!=0?Math.min(1.0,(System.nanoTime()-firstBench)/60e9):0;
        float yaw=v.yaw+(BENCH?(float)(12*Math.sin(2*Math.PI*phase)):0);
        mc.player.setYRot(yaw);mc.player.yRotO=yaw;
        mc.player.setXRot(v.pitch);mc.player.xRotO=v.pitch;
    }
    private static void afterFrame(RenderFrameEvent.Post ignored) {
        if(!running||finished)return;
        try {
            Minecraft mc=Minecraft.getInstance();if(mc.screen!=null)return;
            View v=views.get(index);
            long now=System.nanoTime();
            if(++frames<12 || now-start<(BENCH?5.0:2.2)*1e9)return;
            if(BENCH) {
                if(firstBench==0){firstBench=lastBench=now;return;}
                frameTimes.add(now-lastBench);lastBench=now;
                if(now-firstBench<60_000_000_000L)return;
                var lines=frameTimes.stream().map(String::valueOf).toList();
                Files.write(OUT.resolve(v.name+".ns"),lines);
            }
            var iris=iris();var manager=iris.getMethod("getPipelineManager").invoke(null);
            Object pipe=manager.getClass().getMethod("getPipelineNullable").invoke(manager);
            String path=pipe==null?"none":pipe.getClass().getName();
            boolean active=(boolean)iris.getMethod("isPackInUseQuick").invoke(null);
            if((v.quality!=0)!=active) throw new IllegalStateException("Wrong Iris pack state for "+v.name+" "+path+" active="+active);
            if(v.quality!=0 && !path.contains("IrisRenderingPipeline"))
                throw new IllegalStateException("Iris fell back in "+v.name+": "+path);
            try(NativeImage shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())) { shot.writeToFile(OUT.resolve(v.name+".png")); }
            var camera=mc.gameRenderer.getMainCamera();
            var eye=camera.getPosition();
            String info=v.name+"\t"+v.dim.location()+"\t"+path+"\t"+GL11.glGetString(GL11.GL_RENDERER)+"\t"+
                mc.getWindow().getWidth()+"x"+mc.getWindow().getHeight()+"\t"+v.quality+"\t"+mc.level.getRainLevel(1)+"\t"+mc.level.getDayTime()+
                "\t"+eye.x+"\t"+eye.y+"\t"+eye.z+"\t"+camera.getYRot()+"\t"+camera.getXRot()+"\t"+mc.options.fov().get()+"\n";
            Files.writeString(OUT.resolve("views.tsv"),info,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            System.out.println("AFTERGLOW VIEW PASS "+info.trim());
            if(++index==views.size()) {
                finished=true;System.out.println("AFTERGLOW CLIENT FINISHED");mc.stop();
            } else {running=false;started=false;ready=false;}
        } catch(Throwable t) { fail(t); }
    }
}
