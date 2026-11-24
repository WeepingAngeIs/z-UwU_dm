package dev.uwu.dimension;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.uwu.dimension.mixin.FarmHeadPartsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ChickenModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ChickenCoatArt {
    private static final ResourceLocation PETAL_SKIN = UwUDimension.id("textures/entity/native_coats/petal_chicken.png");
    private static final ResourceLocation DUSK_SKIN = UwUDimension.id("textures/entity/native_coats/dusk_chicken.png");
    private static final ModelResourceLocation BLOSSOMS = piece("chicken_blossoms"), CREST = piece("chicken_crest"),
            FRINGE = piece("chicken_fringe"), FEATHER_WING = piece("petal_wing"), BAT_WING = piece("dusk_wing");

    private static ModelResourceLocation piece(String name) {
        return ModelResourceLocation.standalone(UwUDimension.id("item/native_coats/" + name));
    }

    public static ResourceLocation texture(Chicken chicken) {
        return switch (NativeChickens.visibleCoat(chicken)) {
            case NativeChickens.PETAL -> PETAL_SKIN;
            case NativeChickens.DUSK -> DUSK_SKIN;
            default -> null;
        };
    }

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional event) {
        for (var model : new ModelResourceLocation[]{BLOSSOMS, CREST, FRINGE, FEATHER_WING, BAT_WING}) event.register(model);
    }

    @SubscribeEvent
    public static void attach(EntityRenderersEvent.AddLayers event) {
        if (event.getRenderer(EntityType.CHICKEN) instanceof ChickenRenderer renderer) {
            renderer.addLayer(new Finery(renderer));
        }
    }

    private static final class Finery extends RenderLayer<Chicken, ChickenModel<Chicken>> {
        Finery(ChickenRenderer renderer) { super(renderer); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, Chicken chicken,
                float limb, float stride, float partialTick, float age, float yaw, float pitch) {
            byte coat = NativeChickens.visibleCoat(chicken);
            if (coat == NativeChickens.VANILLA || chicken.isInvisible()) return;
            boolean petal = coat == NativeChickens.PETAL;
            int overlay = LivingEntityRenderer.getOverlayCoords(chicken, 0);
            var model = getParentModel();
            var head = ((FarmHeadPartsAccessor) model).uwu$headParts().iterator().next();
            pose.pushPose();
            if (model.young) pose.translate(0, 5f / 16, 2f / 16);
            head.translateAndRotate(pose);
            pose.mulPose(Axis.XP.rotationDegrees(180));
            // The bird's persistent random UUID is synced by vanilla to every tracking client.
            var trim = petal ? BLOSSOMS : (chicken.getUUID().getLeastSignificantBits() & 1L) == 0L ? CREST : FRINGE;
            draw(pose, buffers, light, overlay, trim);
            pose.popPose();

            pose.pushPose();
            if (model.young) {
                pose.scale(0.5f, 0.5f, 0.5f);
                pose.translate(0, 24f / 16, 0);
            }
            pose.scale(1f / 16, 1f / 16, 1f / 16);
            float lift = (float) Math.sin((chicken.tickCount + partialTick) * 0.14f + chicken.getId()) * 4;
            for (int side = -1; side <= 1; side += 2) {
                pose.pushPose();
                pose.translate(side * 2.4f, 13.39f, 3.65f);
                pose.mulPose(Axis.YP.rotationDegrees(side == 1 ? -25 : -155));
                pose.mulPose(Axis.ZP.rotationDegrees(lift));
                pose.scale(3.1f, 2.45f, 3f);
                pose.mulPose(Axis.XP.rotationDegrees(180));
                pose.translate(-0.07f, 0.73f - 1, -0.5f);
                draw(pose, buffers, light, overlay, petal ? FEATHER_WING : BAT_WING);
                pose.popPose();
            }
            pose.popPose();
        }
    }

    private static void draw(PoseStack pose, MultiBufferSource buffers, int light, int overlay, ModelResourceLocation id) {
        var client = Minecraft.getInstance();
        var vertices = buffers.getBuffer(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        client.getItemRenderer().renderModelLists(client.getModelManager().getModel(id), ItemStack.EMPTY,
                light, overlay, pose, vertices);
    }
}
