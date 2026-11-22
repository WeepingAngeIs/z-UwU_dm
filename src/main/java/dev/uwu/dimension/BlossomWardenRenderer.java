package dev.uwu.dimension;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid="uwudimension",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class BlossomWardenRenderer extends MobRenderer<BlossomWarden,BlossomWardenRenderer.Model> {
    private static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("uwudimension","blossom_warden"),"petals");
    private static final ResourceLocation PETAL_MALE=skin("petal_guardian_male");
    private static final ResourceLocation DUSK_MALE=skin("dusk_guardian_male");
    private static final ResourceLocation MALE_WINK=skin("petal_guardian_male_wink");
    private static final ResourceLocation FEMALE_WINK=skin("petal_guardian_female_wink");
    private static final ResourceLocation DUSK_PAD=skin("dusk_guardian_lily_pad");
    private static ResourceLocation skin(String name){return Arsenal.id("textures/entity/"+name+".png");}
    private static final ResourceLocation PAD=ResourceLocation.fromNamespaceAndPath("uwudimension","textures/block/drift_blossom_pad.png");
    private static final ModelLayerLocation FEMALE_LAYER = new ModelLayerLocation(Arsenal.id("female_guardian"), "body");
    private static ResourceLocation[] expressionFrames(String name) {
        return new ResourceLocation[]{skin(name),skin(name+"_half"),skin(name+"_blink"),skin(name+"_half_lips"),skin(name+"_blink_lips")};
    }
    private static final ResourceLocation[] FEMALE_TEXTURES=expressionFrames("female_uwu_guardian");
    private static final ResourceLocation[] DUSK_FEMALE_TEXTURES=expressionFrames("dusk_guardian_female");
    private final Model maleModel;
    private final Model femaleModel;

    public BlossomWardenRenderer(EntityRendererProvider.Context context) {
        super(context,new Model(context.bakeLayer(LAYER)),.78F);
        maleModel = model;
        femaleModel = new Model(FemaleGuardianModel.sizeCubes(context.bakeLayer(FEMALE_LAYER)));
        addLayer(new LilyPadCrownLayer(this));
    }
    @Override protected void scale(BlossomWarden guardian,PoseStack poses,float partialTick) {
        // The two hierarchical guardian meshes are adult-sized. Vanilla's generic entity
        // scale already applies outside this hook, while its age scale controls the hitbox.
        float age=guardian.getAgeScale();
        poses.scale(age,age,age);
    }
    @Override public ResourceLocation getTextureLocation(BlossomWarden entity) {
        if (entity.warningWink() && !entity.duskGuardian()) return entity.female()?FEMALE_WINK:MALE_WINK;
        if (!entity.female()) return entity.duskGuardian()?DUSK_MALE:PETAL_MALE;
        boolean standingIdle = entity.idlePose() && entity.isAlive() && entity.onGround()
                && !entity.isPassenger() && !entity.isInWaterOrBubble() && !entity.walkAnimation.isMoving();
        int index = OwlExpression.textureFrame(entity.getUUID(), entity.tickCount, standingIdle);
        return (entity.duskGuardian()?DUSK_FEMALE_TEXTURES:FEMALE_TEXTURES)[index];
    }
    @Override protected void setupRotations(BlossomWarden entity,PoseStack poses,float age,float bodyYaw,float partial,float scale) {
        super.setupRotations(entity,poses,age,bodyYaw,partial,scale);
        if (!entity.warningFlip()) return;
        float elapsed=entity.warningPhaseTicks(partial);
        float size=entity.getAgeScale(); // Generic entity scale is already on the incoming pose stack.
        double pivot=GuardianWarningMotion.PIVOT_Y*size;
        poses.translate(0,pivot+GuardianWarningMotion.flipLift(elapsed)*size,0);
        poses.mulPose(Axis.XP.rotation(GuardianWarningMotion.flipAngle(elapsed)));
        poses.translate(0,-pivot,0);
    }
    @Override public void render(BlossomWarden entity, float yaw, float partial, PoseStack poses,
            MultiBufferSource buffers, int light) {
        model = entity.female() ? femaleModel : maleModel;
        super.render(entity, yaw, partial, poses, buffers, light);
    }
    @SubscribeEvent public static void model(EntityRenderersEvent.RegisterLayerDefinitions e) { e.registerLayerDefinition(LAYER,MaleGuardianModel::layer); e.registerLayerDefinition(FEMALE_LAYER,FemaleGuardianModel::layer); }
    @SubscribeEvent public static void renderer(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(People.BLOSSOM_WARDEN.get(),BlossomWardenRenderer::new);
    }

    private static final class LilyPadCrownLayer extends RenderLayer<BlossomWarden,Model> {
        LilyPadCrownLayer(BlossomWardenRenderer renderer){super(renderer);}
        @Override public void render(PoseStack poses,MultiBufferSource buffers,int light,BlossomWarden entity,
                float limbSwing,float limbSwingAmount,float partialTick,float age,float netHeadYaw,float headPitch){
            if (entity.female()) return; // Her lily pads and blooms are part of her model.
            poses.pushPose();
            getParentModel().root().getChild("head").translateAndRotate(poses);
            VertexConsumer consumer=buffers.getBuffer(RenderType.entityCutoutNoCull(entity.duskGuardian()?DUSK_PAD:PAD));
            for(int i=0;i<10;i++){
                float angle=(float)(Math.PI*2*i/10.0);
                poses.pushPose();
                poses.translate(Math.sin(angle)*.46,-.65,Math.cos(angle)*.46);
                poses.mulPose(Axis.YP.rotation(angle));
                poses.mulPose(Axis.XP.rotationDegrees(48));
                // Radial petals taper at their stems and occupy separate angular sectors.
                RangedRelicRenderer.vertex(consumer,poses.last(),new Vec3(-.081,-.208,0),0,1,0xffffffff,light);
                RangedRelicRenderer.vertex(consumer,poses.last(),new Vec3(.081,-.208,0),1,1,0xffffffff,light);
                RangedRelicRenderer.vertex(consumer,poses.last(),new Vec3(.154,.208,0),1,0,0xffffffff,light);
                RangedRelicRenderer.vertex(consumer,poses.last(),new Vec3(-.154,.208,0),0,0,0xffffffff,light);
                poses.popPose();
            }
            poses.popPose();
        }
    }

    static final class Model extends HierarchicalModel<BlossomWarden> {
        private final ModelPart root,head,left,right,footL,footR;
        private final ModelPart[] gaitParts;
        private final GuardianJaw studyJaw;
        private final MaleGuardianModel hero;
        Model(ModelPart root) {
            this.root=root;head=root.getChild("head");left=root.getChild("left");right=root.getChild("right");
            footL=root.getChild("left_root");footR=root.getChild("right_root");
            studyJaw=head.hasChild("warm_face") ? new GuardianJaw(root) : null;
            hero=studyJaw==null?new MaleGuardianModel(root):null;
            gaitParts = new ModelPart[] {root.getChild("basket"), head, left, right, footL, footR};
        }
        @Override public ModelPart root(){return root;}
        @Override public void renderToBuffer(PoseStack poses,VertexConsumer vertices,int light,int overlay,int color) {
            if(hero!=null) hero.render(poses,vertices,light,overlay,color);
            else studyJaw.render(poses,vertices,light,overlay,color);
        }
        @Override public void setupAnim(BlossomWarden w,float walk,float speed,float age,float yaw,float pitch) {
            boolean female = w.female();
            if (female) {
                OwlGuardianGaits.reset(gaitParts);
            }
            head.yRot=yaw*Mth.DEG_TO_RAD*.85F;head.xRot=pitch*Mth.DEG_TO_RAD*.55F;
            if (w.warningShake()) {
                float partial=Mth.clamp(age-w.tickCount,0F,1F);
                head.yRot=GuardianWarningMotion.headShake(w.warningPhaseTicks(partial))*Mth.DEG_TO_RAD;
            }
            float step=Mth.cos(walk*.68F)*speed;
            footL.xRot=step;footR.xRot=-step;
            float grab=w.gripping()?-1.25F:w.throwing()?-2.7F:0;
            left.xRot=grab+step*.45F;right.xRot=grab-step*.45F;
            left.zRot=.08F+Mth.sin(age*.06F)*.03F;right.zRot=-left.zRot;
            if (w.warningFlip()) {
                // Curl the forearms toward the held player's ribs for the whole revolution.
                left.xRot=right.xRot=-1.34F;left.zRot=.28F;right.zRot=-.28F;
                footL.xRot=footR.xRot=-.20F;
            }
            if (female) {
                boolean grounded = w.onGround() && !w.isPassenger() && !w.isInWaterOrBubble();
                var clip = OwlGuardianGaits.select(grounded, w.gripping() || w.throwing() || w.warningShake() || w.warningWink() || w.warningFlip(), w.chasing(), speed);
                if (clip != null) {
                    OwlGuardianGaits.apply(gaitParts, head, clip, age, speed);
                }
            }
        }
    }
}
