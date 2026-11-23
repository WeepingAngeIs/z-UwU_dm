package dev.uwu.escortpictures;

import com.mojang.blaze3d.platform.NativeImage;
import dev.uwu.dimension.ReefLife;
import dev.uwu.dimension.SakuraMaterials;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;

/** Disposable Reach display for inspecting the packaged textures through Minecraft's own block and GUI renderers. */
final class HarvestGallery {
    private static final String[] CROPS={"uwu_wheat","uwu_potato","uwu_lettuce","sakura_rice","heartberry",
            "frostpetal","sweetbean","starfruit","glowginger","vanilla_bean"};
    private static final String[] TREES={"heartbloom","starpetal","moonberry","giant_sakura"};
    private static final String[] NEW_DISHES={"petal_harvest_galette","moonlit_potato_chowder","dewleaf_picnic_wrap"};
    private static final String[] OLDER_DISHES={"rose_berry_pie_slice","shulker_soup","heartberry_burger"};
    private static final String[] NAMES={"field-day","field-dusk","saplings-front","saplings-side","saplings-rear","saplings-middle","pantry"};
    private static final BlockPos PLOT=new BlockPos(-9453,121,14501);
    private final Path out=Path.of(System.getProperty("uwu.check.evidence")).resolveSibling("harvest");
    private boolean active,done;
    private int shot=-1,stable,ticks;
    private CompletableFuture<Void> work;
    private volatile Vec3 feet;
    private volatile float yaw,pitch;

    HarvestGallery(){
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::frame);
    }
    void start(){active=true;}
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("uwudimension",name);}
    private static Block block(String name){return BuiltInRegistries.BLOCK.getOptional(id(name)).orElseThrow();}
    private static void inspectTexture(Minecraft mc,String path)throws Exception {
        try(var bytes=mc.getResourceManager().getResourceOrThrow(id("textures/"+path+".png")).open();
            var png=NativeImage.read(bytes)){
            if(png.getWidth()!=32 || png.getHeight()!=32)throw new IllegalStateException("Packaged sprite size "+path);
        }
    }
    private void inspectAtlas(Minecraft mc)throws Exception{
        for(String crop:CROPS)for(int age=0;age<8;age++)inspectTexture(mc,"block/"+crop+"_crop_stage"+age);
        for(String tree:TREES)inspectTexture(mc,"item/"+tree+"_sapling");
        for(String dish:NEW_DISHES)inspectTexture(mc,"item/"+dish);
        for(String ingredient:new String[]{"uwu_wheat","uwu_potato","uwu_lettuce","uwu_wheat_seeds","uwu_lettuce_seeds"})
            inspectTexture(mc,"item/"+ingredient);
        System.out.println("PACKAGED HARVEST ART 80 crop ages, 4 saplings, 3 dishes, 5 harvest and seed items");
    }
    private void plant(ServerLevel level){
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
        for(int x=-10;x<=10;x++)for(int z=-7;z<=12;z++){
            var ground=PLOT.offset(x,-1,z);
            level.setBlock(ground,SakuraMaterials.GRASS.get().defaultBlockState(),2);
            for(int y=1;y<=7;y++)if(!level.isEmptyBlock(ground.above(y)))level.setBlock(ground.above(y),Blocks.AIR.defaultBlockState(),2);
        }
        for(int i=0;i<CROPS.length;i++){
            var crop=(CropBlock)block(CROPS[i]+"_crop");
            int x=-8+(i%5)*4,z=i<5?-3:1;
            for(int col=0;col<3;col++)for(int row=0;row<2;row++){
                var p=PLOT.offset(x+col,0,z+row);
                level.setBlock(p.below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7),2);
                var plant=crop.getStateForAge(col==0?3:col==1?6:7);
                level.setBlock(p,plant,2);
                if(plant.getLightEmission(level,p)!=0)throw new IllegalStateException("Glowing field block "+p);
            }
        }
        for(int i=0;i<TREES.length;i++){
            var p=PLOT.offset(-3+2*i,0,7);
            level.setBlock(p.below(),SakuraMaterials.LOAM.get().defaultBlockState(),2);
            level.setBlock(p,block(TREES[i]+"_sapling").defaultBlockState(),2);
        }
    }
    private void position(ServerPlayer player,int index){
        var level=player.getServer().getLevel(ReefLife.REACH);
        if(index==0){plant(level);player.setGameMode(GameType.SPECTATOR);}
        long time=index==1?13000L:3500L;
        for(var world:level.getServer().getAllLevels())world.setDayTime(time);
        var target=Vec3.atBottomCenterOf(PLOT).add(index<2?1:0,.62,index<2?-.5:7);
        Vec3 offset=switch(index){
            case 0,1 -> new Vec3(3.9,5.7,11.3);
            case 2 -> new Vec3(.15,1.65,4.8);
            case 3 -> new Vec3(3.4,1.6,3.8);
            case 4 -> new Vec3(-.4,1.55,-5.1);
            default -> new Vec3(1.9,2.1,8);
        };
        Vec3 eye=target.add(offset);Vec3 direction=target.subtract(eye);
        yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));
        pitch=(float)-Math.toDegrees(Math.atan2(direction.y,direction.horizontalDistance()));
        feet=eye.subtract(0,player.getEyeHeight(),0);
        player.teleportTo(level,feet.x,feet.y,feet.z,Set.of(),yaw,pitch);
    }
    private void tick(ClientTickEvent.Post event){
        if(!active||done)return;
        var mc=Minecraft.getInstance();
        try{
            if(++ticks>2200)throw new IllegalStateException("Harvest camera did not settle "+shot+" "+stable);
            if(mc.player==null)return;
            if(work!=null){if(!work.isDone())return;work.join();work=null;}
            if(shot<0 || stable<0){
                if(++shot>=NAMES.length){done=true;System.out.println("NATIVE HARVEST GALLERY PASS");mc.stop();return;}
                if(shot==0)inspectAtlas(mc);
                stable=0;
                if(shot==NAMES.length-1){mc.setScreen(new PantryScreen());return;}
                var server=mc.getSingleplayerServer();var owner=mc.player.getUUID();int index=shot;
                work=server.submit(()->position(server.getPlayerList().getPlayer(owner),index));return;
            }
            if(shot==NAMES.length-1){if(mc.screen instanceof PantryScreen)stable++;return;}
            if(mc.level!=null && mc.level.dimension().equals(ReefLife.REACH) && mc.screen==null && feet!=null &&
                    mc.player.position().distanceToSqr(feet)<1 && mc.level.getBlockState(PLOT.offset(3,0,7)).is(block("giant_sakura_sapling"))){
                mc.player.setYRot(yaw);mc.player.setXRot(pitch);stable++;
            }
        }catch(Throwable failure){failure.printStackTrace();done=true;mc.stop();}
    }
    private void frame(RenderFrameEvent.Post event){
        if(!active||done||work!=null||stable<32||shot<0)return;
        var mc=Minecraft.getInstance();
        try{
            Files.createDirectories(out);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(out.resolve(NAMES[shot]+".png"));}
            stable=-1;
        }catch(Exception failure){failure.printStackTrace();done=true;mc.stop();}
    }
    private static final class PantryScreen extends Screen {
        PantryScreen(){super(Component.literal("The village pantry"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
            g.fill(0,0,width,height,0xff322e36);
            g.drawCenteredString(font,"The village pantry",width/2,13,0xffffe6ce);
            for(int row=0;row<2;row++){
                int y=42+row*82;String[] items=row==0?NEW_DISHES:OLDER_DISHES;
                g.drawString(font,row==0?"Village harvest":"Existing pantry",12,y,0xffd1bcb6,false);
                for(int i=0;i<items.length;i++){
                    ItemStack stack=new ItemStack(BuiltInRegistries.ITEM.getOptional(id(items[i])).orElseThrow());
                    int x=width/2+(i-1)*112;
                    g.pose().pushPose();g.pose().translate(x-20,y+10,0);g.pose().scale(2.5F,2.5F,1);g.renderItem(stack,0,0);g.pose().popPose();
                    g.renderItem(stack,x+23,y+27);g.drawCenteredString(font,stack.getHoverName(),x,y+54,0xffffe7d2);
                }
            }
        }
    }
}
