package dev.uwu.lotusclientcheck;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.uwu.dimension.BlossomWarden;
import dev.uwu.dimension.BlossomWardenRenderer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/** Records the actual Minecraft framebuffer, rendered face and body, and the independent camera pose. */
public final class GuardianCamera {
    static final Path OUT=Path.of(System.getenv().getOrDefault("UWU_GUARDIAN_EVIDENCE","build/guardian-evidence")).toAbsolutePath();
    private static final boolean ACTIVE="true".equals(System.getenv("UWU_GUARDIAN_WARNING_CHECK"));
    private static final boolean OBSERVER="observer".equals(System.getenv("UWU_GUARDIAN_VIEW"));
    public record View(Vec3 eye,float yaw,float pitch) {}
    private static final JsonArray frames=new JsonArray();
    private static int lastTick=-100,sequence;
    private static boolean guardDraw,playerDraw;
    private static float head,arms,up;
    private static String texture;
    public static boolean observer(){return ACTIVE&&OBSERVER;}

    private static BlossomWarden mob() {
        var request=GuardianWarningProbe.trial;
        var mc=Minecraft.getInstance();
        return request!=null && mc.level!=null && mc.level.getEntity(request.id()) instanceof BlossomWarden g ? g : null;
    }
    public static boolean recording(BlossomWarden g){var t=GuardianWarningProbe.trial;return ACTIVE&&t!=null&&g.getId()==t.id();}
    public static void observeUp(BlossomWarden g,float y){if(recording(g))up=y;}
    public static View observerView(float partial){
        var mc=Minecraft.getInstance();var g=mob();var a=mc.player;
        if(g==null||a==null)return null;
        var gallery=GuardianGallery.view(GuardianWarningProbe.trial,g,partial);
        if(gallery!=null)return gallery;
        Vec3 guardian=g.getPosition(partial).add(0,1.36,0), actor=a.getPosition(partial).add(0,1,0);
        double distance=guardian.distanceTo(actor);
        // A separate tracking-camera pass. The player's inputs, collision box, view packets and velocity stay native.
        Vec3 center=guardian.lerp(actor,Math.min(.46,Math.max(0,(distance-3)/9)));
        double back=6.0+.62*Math.max(0,distance-3);
        Vec3 eye=center.add(back*.81,1.20+back*.17,back*.72);
        Vec3 direction=center.subtract(eye);
        return new View(eye,(float)Math.toDegrees(Math.atan2(-direction.x,direction.z)),
                (float)-Math.toDegrees(Math.atan2(direction.y,direction.horizontalDistance())));
    }
    static void before(RenderFrameEvent.Pre event){guardDraw=false;playerDraw=false;texture="";up=Float.NaN;}
    static void gallerySubject(RenderLivingEvent.Pre<?,?> event){
        var t=GuardianWarningProbe.trial;
        if(t!=null&&GuardianGallery.pending(t)&&event.getEntity()==Minecraft.getInstance().player)
            event.setCanceled(true);
    }
    static void draw(RenderLivingEvent.Post<?,?> event){
        var actor=Minecraft.getInstance().player;
        if(actor==event.getEntity())playerDraw=true;
        if(!(event.getEntity() instanceof BlossomWarden g)||!recording(g)||!(event.getRenderer() instanceof BlossomWardenRenderer renderer))return;
        guardDraw=true;texture=renderer.getTextureLocation(g).getPath();
        HierarchicalModel<?> hierarchy=(HierarchicalModel<?>)event.getRenderer().getModel();
        var root=hierarchy.root();
        head=root.getChild("head").yRot*Mth.RAD_TO_DEG;
        arms=Math.min(Math.abs(root.getChild("left").xRot),Math.abs(root.getChild("right").xRot))*Mth.RAD_TO_DEG;
    }
    static void frame(RenderFrameEvent.Post event){
        var mc=Minecraft.getInstance();var a=mc.player;var g=mob();var t=GuardianWarningProbe.trial;
        if(t==null || g==null || a==null || mc.screen!=null || GuardianWarningProbe.failed)return;
        try {
            if(GuardianGallery.pending(t)){GuardianGallery.frame(t,guardDraw,texture);return;}
        }catch(Throwable error){GuardianWarningProbe.fail(error);return;}
        boolean film=t.number()!=5 && t.number()!=7;
        if(!film || !GuardianWarningProbe.filming(t) || a.tickCount<lastTick+1)return;
        try {
            int tick=a.tickCount-GuardianWarningProbe.clientStart;
            if(tick<0)return;
            boolean closeUp=g.warningShake()||g.warningWink()||g.warningFlip();
            if(!closeUp&&a.tickCount<lastTick+2)return;
            lastTick=a.tickCount;
            var camera=mc.gameRenderer.getMainCamera();
            if(observer() && !camera.isDetached() || !observer() && camera.isDetached())return;
            var row=new JsonObject();
            row.addProperty("trial",t.number());row.addProperty("tick",tick);row.addProperty("worldTick",mc.level.getGameTime());
            row.addProperty("action",g.warningShake()?"shake":g.warningWink()?"wink":g.warningFlip()?"flip":g.gripping()?"grip":g.chasing()?"approach":g.throwing()?"throw":"free");
            row.addProperty("phaseTick",g.warningPhaseTicks(0));row.addProperty("guardianDraw",guardDraw);row.addProperty("actorDraw",playerDraw);
            row.addProperty("headYaw",guardDraw?head:0);row.addProperty("minArmRaise",guardDraw?arms:0);
            if(guardDraw&&Float.isFinite(up))row.addProperty("bodyUp",up);
            row.addProperty("texture",texture);row.addProperty("playerGround",a.onGround());
            // This is the exact hurt/death predicate used by LivingEntityRenderer.getOverlayCoords.
            row.addProperty("hurtOverlay",g.hurtTime>0||g.deathTime>0);
            row.addProperty("height",a.getY()-t.floor());row.addProperty("cameraToPlayer",camera.getPosition().distanceTo(a.getEyePosition()));
            row.addProperty("cameraType",mc.options.getCameraType().name());
            String name=String.format(java.util.Locale.ROOT,"%02d-%04d.png",t.number(),sequence++);
            row.addProperty("frame",name);var folder=OUT.resolve("frames");Files.createDirectories(folder);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                image.writeToFile(folder.resolve(name));
            }
            frames.add(row);
        }catch(Throwable e){GuardianWarningProbe.fail(e);}
    }
    static void finish() throws Exception {
        Files.createDirectories(OUT);
        Files.writeString(OUT.resolve("frames.json"),new GsonBuilder().create().toJson(frames));
    }
    private GuardianCamera(){}
}
