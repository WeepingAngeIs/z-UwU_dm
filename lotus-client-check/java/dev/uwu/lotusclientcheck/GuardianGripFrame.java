package dev.uwu.lotusclientcheck;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dimension.BlossomWarden;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Independent draw-space measurements. No production holder lookup or grip-position helper is called. */
public final class GuardianGripFrame {
    private static Vector3f palms,waist,guardianUp,playerUp,vertical;
    public static boolean active(){
        return GuardianCamera.observer()&&GuardianWarningProbe.trial!=null&&!GuardianGallery.pending(GuardianWarningProbe.trial);
    }
    static void reset(){palms=waist=guardianUp=playerUp=vertical=null;}
    public static void vertical(Matrix4f before){
        if(active())vertical=before.transformDirection(new Vector3f(0,1,0)).normalize();
    }
    public static void draw(LivingEntity entity,EntityModel<?> model,PoseStack poses){
        if(entity instanceof BlossomWarden guard&&GuardianCamera.recording(guard)&&model instanceof HierarchicalModel<?> guardian){
            guardianUp=poses.last().pose().transformDirection(new Vector3f(0,-1,0)).normalize();
            poses.pushPose();
            try{
                ModelPart root=guardian.root();root.translateAndRotate(poses);
                Vector3f left=palm(poses,root.getChild("left"),guard.female(),1);
                Vector3f right=palm(poses,root.getChild("right"),guard.female(),-1);
                palms=left.add(right).mul(.5F);
            }finally{poses.popPose();}
        }else if(entity==Minecraft.getInstance().player&&model instanceof HumanoidModel<?> player){
            poses.pushPose();
            try{
                player.body.translateAndRotate(poses);
                // Vanilla PlayerRenderer uses 0.9375 scale; y=1.501 in the model plane is the foot anchor.
                waist=poses.last().pose().transformPosition(new Vector3f(0,1.501F-.58F/.9375F,0));
                playerUp=poses.last().pose().transformDirection(new Vector3f(0,-1,0)).normalize();
            }finally{poses.popPose();}
        }
    }
    private static Vector3f palm(PoseStack poses,ModelPart arm,boolean female,int side){
        poses.pushPose();
        try{
            arm.translateAndRotate(poses);
            if(female){
                // The existing Blockbench hand is a centered cube at the authored child pivot.
                arm.getChild("hand").translateAndRotate(poses);
                return poses.last().pose().transformPosition(new Vector3f());
            }
            // Center of the actual left/right hand surface in guardian-art/male-mesh.cjs, model pixels.
            return poses.last().pose().transformPosition(new Vector3f(side*.08F/16,15.01F/16,-.38F/16));
        }finally{poses.popPose();}
    }
    static void evidence(JsonObject row){
        if(palms==null||waist==null||playerUp==null||guardianUp==null||vertical==null)return;
        row.addProperty("palmToPlayerAnchor",palms.distance(waist));
        row.addProperty("actorBodyUp",playerUp.dot(vertical));
        row.addProperty("drawnGuardianUp",guardianUp.dot(vertical));
        row.addProperty("actorGuardAlignment",playerUp.dot(guardianUp));
    }
    private GuardianGripFrame(){}
}
