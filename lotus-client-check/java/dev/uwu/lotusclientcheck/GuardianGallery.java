package dev.uwu.lotusclientcheck;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import dev.uwu.dimension.BlossomWarden;

/** Front, both obliques and profile at two ranges, each with repeated subpixel changes of viewpoint. */
final class GuardianGallery {
    private static final int[] ANGLES={0,40,90,145,180,270};
    private static final double[] RANGES={4.8,8.4,13.0};
    private static final JsonArray INDEX=new JsonArray();
    private static int completed=-1,selected=-1,index,sample,wait;
    static boolean pending(GuardianWarningProbe.Trial t){
        return GuardianCamera.observer()&&(t.number()==0||t.number()==6)&&completed!=t.number();
    }
    static GuardianCamera.View view(GuardianWarningProbe.Trial t, BlossomWarden guard,float partial){
        if(!pending(t))return null;
        if(selected!=t.number()){selected=t.number();index=sample=0;wait=4;}
        int indexSafe=Math.min(index,ANGLES.length*RANGES.length-1);
        double angle=Math.toRadians(ANGLES[indexSafe%ANGLES.length])+Math.toRadians(guard.yBodyRot);
        double radius=RANGES[indexSafe/ANGLES.length];
        double offset=(sample==1?.00035:sample==2?-.00035:0);
        Vec3 target=guard.getPosition(partial).add(0,1.50,0);
        Vec3 eye=target.add(-Math.sin(angle)*radius+Math.cos(angle)*offset,.10,Math.cos(angle)*radius+Math.sin(angle)*offset);
        Vec3 d=target.subtract(eye);
        return new GuardianCamera.View(eye,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));
    }
    static void frame(GuardianWarningProbe.Trial t,boolean drawn,String texture)throws Exception{
        if(!pending(t)||selected!=t.number()||!drawn)return;
        if(wait-->0)return;
        var out=GuardianCamera.OUT.resolve("gallery");Files.createDirectories(out);
        int degrees=ANGLES[index%ANGLES.length];double radius=RANGES[index/ANGLES.length];String form=t.dusk()?"dusk":"petal";
        String name=String.format(java.util.Locale.ROOT,"%s-%02d-%03d-%d.png",form,(int)Math.round(radius*10),degrees,sample);
        try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(out.resolve(name));}
        var item=new JsonObject();item.addProperty("form",form);item.addProperty("angle",degrees);item.addProperty("range",radius);
        item.addProperty("subpixelSample",sample);item.addProperty("file",name);item.addProperty("texture",texture);INDEX.add(item);
        if(++sample==3){sample=0;index++;wait=1;}
        if(index==ANGLES.length*RANGES.length){
            completed=t.number();
            Files.writeString(GuardianCamera.OUT.resolve("gallery.json"),new GsonBuilder().create().toJson(INDEX));
            System.out.println("GUARDIAN NATIVE MALE ANGLES "+form+" views="+index);
        }
    }
    private GuardianGallery(){}
}
