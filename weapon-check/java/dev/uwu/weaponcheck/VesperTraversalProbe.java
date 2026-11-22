package dev.uwu.weaponcheck;

import dev.uwu.dimension.Arsenal;
import dev.uwu.dimension.traversal.client.TraversalLandmarks;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.CompositeModel;

/** Exercise the existing model/CRC/UV-dependent traversal guard using the actual held actor. */
final class VesperTraversalProbe {
    // Approved tips for the unchanged Playtest 9 sweep and Snowkiss texture silhouettes.
    private static final Vec3 VESPER_TIP = new Vec3(.5 / 32, 1 - 6.5 / 32, .5);
    private static final Vec3 SNOWKISS_TIP = new Vec3(26.5 / 32, 1 - 20.5 / 32, .5);
    private static boolean done;

    static void verify(Minecraft client) throws Exception {
        if (done) return;
        var player = client.player;
        ItemStack held = player.getMainHandItem();
        HumanoidArm originalArm = player.getMainArm();
        Evidence.require(held.is(Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get()), "Vesper traversal probe did not receive the real held weapon");
        // Read the integration owner's actual approved grip instead of substituting a probe grip.
        Field gripField = Class.forName("dev.uwu.dimension.traversal.client.TraversalRenderPass").getDeclaredField("WRAP");
        gripField.setAccessible(true);
        Vec3 grip = (Vec3)gripField.get(null);
        List<Map<String, Object>> views = new ArrayList<>();
        List<Map<String, Object>> snowkiss = new ArrayList<>();
        int negativeControls = 0, nativeBaseRejections = 0;
        try {
            for (int element = 0; element < 4; element++) for (boolean credit : List.of(false, true)) {
                ItemStack variant = held.copy();
                int selectedElement = element;
                CustomData.update(DataComponents.CUSTOM_DATA, variant, tag -> {
                    tag.putInt("uwu_element", selectedElement);
                    if (credit) tag.putLong("uwu.flower_credit_until", Long.MAX_VALUE);
                    else tag.remove("uwu.flower_credit_until");
                });
                player.setItemInHand(InteractionHand.MAIN_HAND, variant);
                for (HumanoidArm arm : HumanoidArm.values()) {
                    player.setMainArm(arm);
                    for (ItemDisplayContext context : contexts(arm)) {
                        BakedModel model = client.getItemRenderer().getModel(variant, client.level, player, player.getId() + context.ordinal());
                        var points = TraversalLandmarks.vesper(player, InteractionHand.MAIN_HAND, context, variant, model, grip);
                        Evidence.require(points != null && points.element() == element && points.flowerCredit() == credit
                                        && points.wrappedGrip().equals(grip) && points.tip().equals(VESPER_TIP),
                                "Native Vesper traversal certification was disabled or changed: element=" + element + " credit=" + credit + " " + context);
                        Evidence.require(TraversalLandmarks.vesper(player, InteractionHand.OFF_HAND, context, variant, model, grip) == null,
                                "Off-hand traversal control was incorrectly accepted");
                        Evidence.require(TraversalLandmarks.vesper(player, InteractionHand.MAIN_HAND, ItemDisplayContext.GUI, variant, model, grip) == null,
                                "GUI traversal control was incorrectly accepted");
                        BakedModel foreign = new BakedModelWrapper<BakedModel>(model) {};
                        Evidence.require(TraversalLandmarks.vesper(player, InteractionHand.MAIN_HAND, context, variant, foreign, grip) == null,
                                "An unselected foreign model borrowed native traversal landmarks");
                        negativeControls += 3;
                        BakedModel nativeModel = model;
                        if (model.getClass().getName().equals("dev.uwu.dimension.client.WeaponBladeModels$ReliefModel")) {
                            Field baseField = BakedModelWrapper.class.getDeclaredField("originalModel");
                            baseField.setAccessible(true);
                            BakedModel base = (BakedModel)baseField.get(model);
                            nativeModel = base;
                            Evidence.require(TraversalLandmarks.vesper(player, InteractionHand.MAIN_HAND, context, variant, base, grip) == null,
                                    "Traversal lost actual selected-wrapper identity validation");
                            nativeBaseRejections++;
                        }
                        Evidence.require(nativeModel.getClass() == CompositeModel.Baked.class,
                                "Traversal source is no longer the original native composite");
                        views.add(Map.of("element", element, "flower_credit", credit, "context", context.name(),
                                "arm", arm.name(), "selected_model_class", model.getClass().getName(),
                                "certified_model_id", points.modelId().toString(), "tip", coordinates(points.tip()),
                                "grip", coordinates(grip), "native_model_class", nativeModel.getClass().getName()));
                    }
                }
            }
            ItemStack whip = new ItemStack(Arsenal.WEAPONS.get(Arsenal.Relic.FROST_WHIP).get());
            player.setItemInHand(InteractionHand.MAIN_HAND, whip);
            for (HumanoidArm arm : HumanoidArm.values()) {
                player.setMainArm(arm);
                for (ItemDisplayContext context : contexts(arm)) {
                    BakedModel model = client.getItemRenderer().getModel(whip, client.level, player, player.getId() + context.ordinal());
                    Vec3 tip = TraversalLandmarks.snowkiss(player, InteractionHand.MAIN_HAND, context, whip, model);
                    Evidence.require(SNOWKISS_TIP.equals(tip) && model.getClass() == CompositeModel.Baked.class,
                            "Unchanged native Snowkiss traversal control failed: " + context);
                    Evidence.require(TraversalLandmarks.snowkiss(player, InteractionHand.OFF_HAND, context, whip, model) == null,
                            "Snowkiss off-hand rejection changed");
                    negativeControls++;
                    snowkiss.add(Map.of("context", context.name(), "arm", arm.name(), "tip", coordinates(tip),
                            "selected_model_class", model.getClass().getName()));
                }
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            player.setMainArm(originalArm);
        }
        Evidence.require(views.size() == 32 && snowkiss.size() == 4 && negativeControls == 100,
                "Incomplete native traversal certification matrix");
        if (!Evidence.BASELINE) Evidence.require(nativeBaseRejections == 32, "The native wrapper identity guard was not exercised for all Vesper views");
        Evidence.json("vesper-traversal.json", Map.of("baseline", Evidence.BASELINE, "variants", 8, "views", views,
                "snowkiss_controls", snowkiss, "negative_controls", negativeControls,
                "unselected_native_base_rejections", nativeBaseRejections, "approved_grip_source", "TraversalRenderPass.WRAP",
                "approved_grip", coordinates(grip),
                "negative_control_counts", Map.of("off_hand", 36, "gui", 32, "foreign_model", 32)));
        System.out.println("UWU VESPER TRAVERSAL CERTIFICATION PASS variants=8 views=32 snowkiss=4 negative_controls=100 native_base_rejections=" + nativeBaseRejections);
        done = true;
    }

    private static List<Double> coordinates(Vec3 point) {
        return List.of(point.x, point.y, point.z);
    }

    private static List<ItemDisplayContext> contexts(HumanoidArm arm) {
        return arm == HumanoidArm.LEFT ? List.of(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                : List.of(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
    }
}
