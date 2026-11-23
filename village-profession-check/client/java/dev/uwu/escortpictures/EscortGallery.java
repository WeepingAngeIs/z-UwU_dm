package dev.uwu.escortpictures;

import com.mojang.blaze3d.platform.NativeImage;
import dev.uwu.dimension.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;

@Mod("uwu_escort_pictures")
public final class EscortGallery {
    public static volatile int entityId=-1;
    public static boolean rendered;
    private static final BlockPos STAGE=new BlockPos(8,221,8);
    private static final int COUNT=12;
    private static final Path OUT=Path.of(System.getProperty("uwu.check.evidence"));
    private final HarvestGallery harvest=new HarvestGallery();
    private boolean opening,done;
    private int shot=-1,ticks,stable;
    private volatile Vec3 eye;
    private volatile float yaw,pitch;
    private CompletableFuture<Void> work;
    public EscortGallery(){
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre e)->rendered=false);
        NeoForge.EVENT_BUS.addListener(this::frame);
    }
    private void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        try{
            if(done)return;
            if(!opening && mc.screen instanceof TitleScreen title){
                opening=true;
                mc.createWorldOpenFlows().createFreshLevel("EscortGallery",new LevelSettings("Escort gallery",GameType.CREATIVE,false,
                        Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(152045L,false,false),WorldPresets::createNormalWorldDimensions,title);
                return;
            }
            if(mc.player==null){
                if(opening && mc.screen!=null)for(var widget:List.copyOf(mc.screen.children()))
                    if(widget instanceof Button b && Set.of("Proceed","I know what I'm doing!").contains(b.getMessage().getString())){b.onPress();break;}
                return;
            }
            if(mc.player.tickCount<50 || mc.screen!=null)return;
            if(work!=null){if(!work.isDone())return;work.join();work=null;}
            if(++ticks>2000)throw new IllegalStateException("Missing native gallery frame "+shot+" "+stable);
            if(shot<0 || stable<0){
                shot++;
                if(shot>=COUNT){done=true;System.out.println("ESCORT PICK NATIVE GALLERY PASS");harvest.start();return;}
                mc.options.hideGui=true;mc.options.setCameraType(CameraType.FIRST_PERSON);
                var server=mc.getSingleplayerServer();UUID owner=mc.player.getUUID();int index=shot;
                stable=0;work=server.submit(()->position(server.getPlayerList().getPlayer(owner),index));return;
            }
            if(eye!=null && mc.player.position().distanceToSqr(eye)<.3){
                mc.player.setYRot(yaw);mc.player.setXRot(pitch);stable++;
            }
        } catch(Throwable e){e.printStackTrace();done=true;mc.stop();}
    }
    private void position(ServerPlayer player,int index){
        ServerLevel l=player.serverLevel();
        try{
            if(index==0){
                for(var p:BlockPos.betweenClosed(STAGE.offset(-14,-1,-14),STAGE.offset(14,-1,14)))
                    l.setBlock(p,Blocks.SMOOTH_QUARTZ.defaultBlockState(),2);
                l.setDayTime(2000);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());
                var g=People.BLOSSOM_WARDEN.get().create(l);method(g,"setFemale",true);g.setNoAi(true);g.setNoGravity(true);
                g.moveTo(STAGE.getX()+.5,STAGE.getY(),STAGE.getZ()+.5,0,0);g.setYBodyRot(0);g.setYHeadRot(0);g.setPersistenceRequired();
                l.addFreshEntity(g);entityId=g.getId();
            }
            var g=(BlossomWarden)l.getEntity(entityId);g.setDuskGuardian(index>=6);
            method(g,"showEscortPick",true);
            int angle=index%3;double distance=(index%6)<3?3.3:8;
            double direction=angle==0?.06:angle==1?Math.PI*.5:Math.PI;
            Vec3 look=g.position().add(0,1.08,0);
            eye=g.position().add(Math.sin(direction)*distance,.05,Math.cos(direction)*distance);
            Vec3 diff=look.subtract(eye.add(0,player.getEyeHeight(),0));
            yaw=(float)Math.toDegrees(Math.atan2(-diff.x,diff.z));
            pitch=(float)-Math.toDegrees(Math.atan2(diff.y,diff.horizontalDistance()));
            player.teleportTo(l,eye.x,eye.y,eye.z,Set.of(),yaw,pitch);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void method(BlossomWarden g,String name,boolean val)throws Exception{
        var m=BlossomWarden.class.getDeclaredMethod(name,boolean.class);m.setAccessible(true);m.invoke(g,val);
    }
    private void frame(RenderFrameEvent.Post event){
        var mc=Minecraft.getInstance();
        if(done||stable<10||work!=null||!rendered||mc.screen!=null||shot<0)return;
        try{
            Files.createDirectories(OUT);
            String view=new String[]{"front","side","rear"}[shot%3];
            String name=(shot<6?"petal":"dusk")+"-"+(shot%6<3?"near":"middle")+"-"+view;
            try(NativeImage img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(OUT.resolve(name+".png"));}
            stable=-1;
        }catch(Exception e){e.printStackTrace();done=true;mc.stop();}
    }
}
