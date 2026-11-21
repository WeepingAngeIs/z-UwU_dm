package dev.uwu.dimension.client;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import org.jetbrains.annotations.Nullable;

/** Opt-in blade relief; the original sprite model keeps its native render passes. */
@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WeaponBladeModels {
    private WeaponBladeModels() {}

    /**
     * Read landmarks from the unchanged native layers of our own relief wrapper.
     * The caller must first verify that selected is the actual renderer-selected model;
     * other wrappers are deliberately not traversed or treated as native geometry.
     */
    public static BakedModel landmarkModel(BakedModel selected) {
        return selected instanceof ReliefModel relief ? relief.nativeLayers() : selected;
    }

    @SubscribeEvent
    public static void loaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ResourceLocation.fromNamespaceAndPath("uwudimension", "blade_relief"), new Loader());
    }

    private static final class Loader implements IGeometryLoader<Geometry> {
        @Override
        public Geometry read(JsonObject json, JsonDeserializationContext reader) throws JsonParseException {
            JsonObject base = GsonHelper.getAsJsonObject(json, "base");
            JsonObject relief = GsonHelper.getAsJsonObject(json, "relief");
            if (!GsonHelper.getAsString(base, "loader").equals("neoforge:item_layers")
                    || !GsonHelper.getAsString(relief, "loader").equals("neoforge:elements")) {
                throw new JsonParseException("Blade relief requires native item layers and elements");
            }
            return new Geometry(reader.deserialize(base, BlockModel.class), reader.deserialize(relief, BlockModel.class));
        }
    }

    private record Geometry(BlockModel base, BlockModel relief) implements IUnbakedGeometry<Geometry> {
        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> models, IGeometryBakingContext context) {
            base.resolveParents(models);
            relief.resolveParents(models);
        }

        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                Function<Material, TextureAtlasSprite> sprites, ModelState state, ItemOverrides overrides) {
            return new ReliefModel(base.bake(baker, base, sprites, state, true),
                    relief.bake(baker, relief, sprites, state, true), overrides);
        }
    }

    private static final class ReliefModel extends BakedModelWrapper<BakedModel> {
        private final BakedModel relief;
        private final ItemOverrides overrides;
        private final List<BakedModel> normalPasses;
        private final List<BakedModel> fabulousPasses;

        private ReliefModel(BakedModel base, BakedModel relief, ItemOverrides overrides) {
            super(base);
            this.relief = relief;
            this.overrides = overrides;
            // Both accepted native loaders have static pass lists. Preserve each pass's
            // own cutout render type and emissive quads instead of nesting composites.
            normalPasses = joined(base.getRenderPasses(ItemStack.EMPTY, false), relief.getRenderPasses(ItemStack.EMPTY, false));
            fabulousPasses = joined(base.getRenderPasses(ItemStack.EMPTY, true), relief.getRenderPasses(ItemStack.EMPTY, true));
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext view, PoseStack pose, boolean left) {
            originalModel.applyTransform(view, pose, left);
            // Inventory/hotbar identity remains the shipped sprite. The native model
            // space gallery (NONE), held, ground and item-frame views use the relief.
            if (view == ItemDisplayContext.GUI) return originalModel;
            return this;
        }

        @Override
        public ItemOverrides getOverrides() { return overrides; }

        private BakedModel nativeLayers() { return originalModel; }

        @Override
        public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            return fabulous ? fabulousPasses : normalPasses;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return joined(originalModel.getQuads(state, side, random), relief.getQuads(state, side, random));
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                ModelData data, @Nullable RenderType renderType) {
            return joined(originalModel.getQuads(state, side, random, data, renderType),
                    relief.getQuads(state, side, random, data, renderType));
        }

        private static <T> List<T> joined(List<T> first, List<T> second) {
            if (second.isEmpty()) return first;
            var result = new ArrayList<T>(first.size() + second.size());
            result.addAll(first);
            result.addAll(second);
            return List.copyOf(result);
        }
    }
}
