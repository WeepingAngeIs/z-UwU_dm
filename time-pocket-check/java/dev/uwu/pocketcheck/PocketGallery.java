package dev.uwu.pocketcheck;

import dev.uwu.dimension.TimePocket;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Two unlayered placeholder sprites, three angles, two distances, and three small camera motions. */
public final class PocketGallery {
    public record View(Vec3 eye,float yaw,float pitch) {}
    private static final int[] ANGLES={0,90,180};
    private static final double[] DISTANCES={2.6,5.5};
    private static volatile boolean prepared;
    static boolean starOnCamera;
    private static boolean drawn;
    private static int sample;
    private static long lastTick=-1;

    static void setup(MinecraftServer server,UUID id){
        try {
            var level=server.overworld();
            var player=server.getPlayerList().getPlayer(id);
            player.connection.teleport(-6.5,PocketWorldProbe.FLOOR+1,8.5,0,0);
            for(int i=0;i<2;i++){
                var stand=new ArmorStand(level,1.5+i*8,PocketWorldProbe.FLOOR+1,10.5);
                var nbt=stand.saveWithoutId(new CompoundTag());
                nbt.putBoolean("ShowArms",true);nbt.putBoolean("NoBasePlate",true);
                var pose=new CompoundTag();var arm=new ListTag();
                for(float a:new float[]{-65,0,-15})arm.add(FloatTag.valueOf(a));
                pose.put("RightArm",arm);nbt.put("Pose",pose);stand.load(nbt);
                stand.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(i==0?TimePocket.CLOCK.get():TimePocket.STAR.get()));
                PocketWorldProbe.check(level.addFreshEntity(stand),"Gallery holder missing");
            }
            prepared=true;
        } catch(Throwable error) {PocketWorldProbe.failure=error;}
    }
    public static View view(){
        if(!prepared || complete())return null;
        double radius=DISTANCES[sample/9%2];
        double az=Math.toRadians(ANGLES[sample/3%3]+(sample%3-1)*.10), el=Math.toRadians(9);
        var center=new Vec3(1.5+8*(sample/18),PocketWorldProbe.FLOOR+2.22,10.5);
        var eye=center.add(radius*Math.cos(el)*Math.sin(az),radius*Math.sin(el),radius*Math.cos(el)*Math.cos(az));
        var look=center.subtract(eye);
        return new View(eye,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
    }
    public static void draw(ItemStack stack,ItemDisplayContext context){
        if(stack.is(TimePocket.STAR.get()) && context==ItemDisplayContext.GROUND)starOnCamera=true;
        if(prepared && !complete() && context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND &&
                stack.is(sample<18?TimePocket.CLOCK.get():TimePocket.STAR.get()))drawn=true;
    }
    static void beforeFrame(RenderFrameEvent.Pre event){drawn=false;}
    static void capture() throws Exception{
        if(!prepared || complete() || !drawn)return;
        var mc=Minecraft.getInstance();var v=view();long tick=mc.level.getGameTime();
        if(tick==lastTick || mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(v.eye())>1e-8)return;
        lastTick=tick;
        PocketClientProbe.save(String.format(Locale.ROOT,"model-%s-%d-%03d-%d.png",sample<18?"clock":"star",
                (int)(DISTANCES[sample/9%2]*10),ANGLES[sample/3%3],sample%3));
        sample++;
    }
    static boolean complete(){return sample==36;}
    private PocketGallery(){}
}

