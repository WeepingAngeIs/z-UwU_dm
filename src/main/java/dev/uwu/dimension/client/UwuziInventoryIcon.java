package dev.uwu.dimension.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class UwuziInventoryIcon {
    private static final ModelResourceLocation ICON = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath("uwudimension", "item/uwuzi_inventory"));

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        event.register(ICON);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void bake(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        var icon = models.get(ICON);
        if (icon == null || icon == models.get(ModelBakery.MISSING_MODEL_VARIANT)) return;
        for (String id : List.of("pocket_clock_prototype", "uwuzi_no_cooldown")) {
            var item = ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath("uwudimension", id));
            models.computeIfPresent(item, (key, original) -> new BakedModelWrapper<BakedModel>(original) {
                @Override
                public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
                    var model = context == ItemDisplayContext.GUI ? icon : original;
                    return model.applyTransform(context, pose, leftHand);
                }
            });
        }
    }

    private UwuziInventoryIcon() {}
}
