package dev.uwu.pocketcheck;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dimension.TimePocket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Observes native GUI draws and checks each variant's actual baked context selection. */
public final class PocketIconProbe {
    private static final Map<String, Object> models = new LinkedHashMap<>();
    private static final Map<String, Integer> guiDraws = new LinkedHashMap<>();

    static void inspect(Minecraft mc) throws Exception {
        for (var item : List.of(TimePocket.CLOCK.get(), TimePocket.NO_COOLDOWN.get())) {
            var stack = new ItemStack(item);
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            var model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 31);
            var contexts = new LinkedHashMap<String, Object>();
            for (var context : List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
                var poses = new PoseStack();
                boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                var selected = ClientHooks.handleCameraTransforms(poses, model, context, left);
                var quads = new ArrayList<BakedQuad>();
                for (var pass : selected.getRenderPasses(stack, false)) {
                    quads.addAll(pass.getQuads(null, null, RandomSource.create(31), ModelData.EMPTY, null));
                    for (Direction face : Direction.values())
                        quads.addAll(pass.getQuads(null, face, RandomSource.create(31), ModelData.EMPTY, null));
                }
                Set<String> sprites = new LinkedHashSet<>();
                for (BakedQuad quad : quads) sprites.add(quad.getSprite().contents().name().toString());
                String expected = context == ItemDisplayContext.GUI ? "uwudimension:item/uwuzi_inventory" : "minecraft:item/clock_00";
                PocketWorldProbe.check(!quads.isEmpty() && sprites.equals(Set.of(expected)),
                        "Wrong UwUzi model selection " + id + " " + context + " " + sprites);
                contexts.put(context.name(), Map.of("quads", quads.size(), "sprites", sprites, "transform", poses.last().pose().toString()));
            }
            models.put(id, contexts);
        }
        save(false);
    }

    public static void draw(ItemStack stack, ItemDisplayContext context) {
        if (context == ItemDisplayContext.GUI && stack.getItem() instanceof TimePocket.Clock) {
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            guiDraws.merge(id, 1, Integer::sum);
        }
    }

    static void finish() throws Exception {
        PocketWorldProbe.check(models.size() == 2, "Both UwUzi models were not inspected");
        for (var item : List.of(TimePocket.CLOCK.get(), TimePocket.NO_COOLDOWN.get())) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            PocketWorldProbe.check(guiDraws.getOrDefault(id, 0) > 0, "Missing native GUI draw for " + id);
        }
        save(true);
        System.out.println("UWUZI BOTH GUI ICONS PASS " + guiDraws);
    }

    private static void save(boolean complete) throws Exception {
        Files.writeString(PocketClientProbe.OUT.resolve("uwuzi-models.json"), new GsonBuilder().setPrettyPrinting().create()
                .toJson(Map.of("complete", complete, "models", models, "nativeGuiDraws", guiDraws)) + "\n");
    }

    private PocketIconProbe() {}
}
