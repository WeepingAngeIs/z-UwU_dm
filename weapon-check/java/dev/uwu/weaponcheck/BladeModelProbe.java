package dev.uwu.weaponcheck;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Fixture-only inspection of native original/relief models; never a product render hook. */
public final class BladeModelProbe {
    private static final float EPSILON = 1.0e-6F;
    private static final int ROWS = 4;
    private static final List<Pair> PAIRS = new ArrayList<>();
    private static final List<Map<String, Object>> MODELS = new ArrayList<>();
    private static final List<Map<String, Object>> GUI_RESULTS = new ArrayList<>();
    private static int page;
    private static boolean drawn;
    private static boolean guiClockActive;
    private static int fixedGlintCalls;
    private static final long GUI_GLINT_MILLIS = 172800000L;
    record Pair(String name, ItemStack stack, BakedModel wrapper, BakedModel original) {}

    static void inspect(String name, ItemStack stack, BakedModel model) throws Exception {
        if (!model.getClass().getName().equals("dev.uwu.dimension.client.WeaponBladeModels$ReliefModel")) return;
        Field originalField = BakedModelWrapper.class.getDeclaredField("originalModel");
        originalField.setAccessible(true);
        BakedModel original = (BakedModel)originalField.get(model);
        Field reliefField = model.getClass().getDeclaredField("relief");
        reliefField.setAccessible(true);
        BakedModel relief = (BakedModel)reliefField.get(model);
        List<Map<String, Object>> passes = new ArrayList<>();
        for (boolean fabulous : List.of(false, true)) {
            List<BakedModel> nativePasses = original.getRenderPasses(stack, fabulous);
            List<BakedModel> addedPasses = relief.getRenderPasses(stack, fabulous);
            List<BakedModel> actualPasses = model.getRenderPasses(stack, fabulous);
            Evidence.require(!nativePasses.isEmpty() && !addedPasses.isEmpty(), "Empty native/relief passes: " + name);
            Evidence.require(actualPasses.size() == nativePasses.size() + addedPasses.size(), "Lost or duplicated native passes: " + name);
            for (int i = 0; i < actualPasses.size(); i++) {
                BakedModel expected = i < nativePasses.size() ? nativePasses.get(i) : addedPasses.get(i - nativePasses.size());
                Evidence.require(actualPasses.get(i) == expected, "Native pass identity/order changed: " + name);
                Map<String, Object> before = snapshot(expected, stack, fabulous);
                Evidence.require(before.equals(snapshot(actualPasses.get(i), stack, fabulous)), "Native quad/light data changed: " + name);
                passes.add(Map.of("fabulous", fabulous, "index", i, "original", i < nativePasses.size(), "native", before));
            }
        }
        int transforms = 0;
        for (ItemDisplayContext view : ItemDisplayContext.values()) for (boolean left : List.of(false, true)) {
            PoseStack nativePose = new PoseStack(), wrappedPose = new PoseStack();
            original.applyTransform(view, nativePose, left);
            BakedModel returned = model.applyTransform(view, wrappedPose, left);
            Evidence.require(returned == (view == ItemDisplayContext.GUI ? original : model),
                    "Native GUI model/wrapped held model was discarded: " + name + " " + view + " left=" + left);
            Evidence.require(nativePose.last().pose().equals(wrappedPose.last().pose(), EPSILON)
                            && nativePose.last().normal().equals(wrappedPose.last().normal(), EPSILON),
                    "Native display transform changed: " + name + " " + view + " left=" + left);
            transforms++;
        }
        float[] nativeBounds = bounds(original, stack), actualBounds = bounds(model, stack), extraBounds = bounds(relief, stack);
        for (int axis : new int[] {0, 1, 3, 4})
            Evidence.require(Math.abs(nativeBounds[axis] - actualBounds[axis]) < EPSILON, "Native X/Y silhouette bounds changed: " + name);
        Evidence.require(actualBounds[2] < nativeBounds[2] - EPSILON && actualBounds[5] > nativeBounds[5] + EPSILON,
                "Native blade relief did not extend both sides: " + name);
        for (BakedQuad quad : quads(relief, stack, false)) {
            boolean front = true, back = true;
            int[] vertices = quad.getVertices();
            int stride = vertices.length / 4;
            for (int v = 0; v < 4; v++) {
                float z = Float.intBitsToFloat(vertices[v * stride + 2]);
                front &= z <= nativeBounds[2] + EPSILON;
                back &= z >= nativeBounds[5] - EPSILON;
            }
            Evidence.require(front || back, "Relief duplicated a wall through the original central slab: " + name);
        }
        MODELS.add(Map.of("name", name, "native_bounds", nativeBounds, "actual_bounds", actualBounds,
                "relief_bounds", extraBounds, "transforms_checked", transforms, "passes", passes,
                "gui_returns_original", true, "original_pass_identity_preserved", true));
        PAIRS.add(new Pair(name, stack.copy(), model, original));
    }

    static void initialized() throws Exception {
        if (!Evidence.BASELINE) Evidence.require(MODELS.size() == 314,
                "Expected 314 native/glitch relief variants across all 102 blade roots, resolved " + MODELS.size());
        Evidence.json("blade-native-models.json", Map.of("baseline", Evidence.BASELINE, "models", MODELS));
        if (!PAIRS.isEmpty()) {
            Pair seed = PAIRS.getFirst();
            ItemStack foil = seed.stack.copy();
            foil.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            PAIRS.add(new Pair(seed.name + "-foil-control", foil, seed.wrapper, seed.original));
        }
    }

    static boolean complete() { return page * ROWS >= PAIRS.size(); }

    static void gui(GuiGraphics gui) {
        if (complete()) return;
        guiClockActive = true;
        try { drawGui(gui); } finally { guiClockActive = false; }
    }

    /** Called only by the fixture redirect around vanilla glint texturing. */
    public static long glintMillis() {
        if (!guiClockActive) return Util.getMillis();
        fixedGlintCalls++;
        return GUI_GLINT_MILLIS;
    }

    private static void drawGui(GuiGraphics gui) {
        Minecraft client = Minecraft.getInstance();
        Evidence.require(client.options.hideGui, "Native GUI pair evidence must exclude the HUD");
        gui.fill(0, 0, gui.guiWidth(), gui.guiHeight(), 0xFF242A36);
        gui.drawString(client.font, "Actual native GUI model | candidate and original | page " + (page + 1), 8, 5, 0xFFFCE5EE, false);
        gui.drawString(client.font, "candidate", 330, 23, 0xFFC0D2E8, false);
        gui.drawString(client.font, "original", 560, 23, 0xFFC0D2E8, false);
        for (int row = 0; row < ROWS; row++) {
            int index = page * ROWS + row;
            if (index >= PAIRS.size()) break;
            Pair pair = PAIRS.get(index);
            int cy = 90 + row * 95;
            for (int off = 0, line = 0; off < pair.name.length(); off += 33, line++)
                gui.drawString(client.font, pair.name.substring(off, Math.min(off + 33, pair.name.length())),
                        8, cy - 10 + line * 10, 0xFFFFD2E2, false);
            render(gui, pair, pair.wrapper, 360, cy);
            render(gui, pair, pair.original, 590, cy);
        }
        gui.flush();
        drawn = true;
    }

    private static void render(GuiGraphics gui, Pair pair, BakedModel model, int cx, int cy) {
        PoseStack pose = gui.pose();
        pose.pushPose();
        try {
            pose.translate(cx, cy, 200);
            pose.scale(48, -48, 48);
            if (model.usesBlockLight()) Lighting.setupFor3DItems(); else Lighting.setupForFlatItems();
            Minecraft.getInstance().getItemRenderer().render(pair.stack, ItemDisplayContext.GUI, false, pose,
                    gui.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
            gui.flush();
        } finally { pose.popPose(); }
    }

    static void frame() throws Exception {
        if (!drawn || complete()) return;
        String name = "gui-identity-" + String.format("%03d", page);
        Evidence.screenshot(name);
        try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            double scale = Minecraft.getInstance().getWindow().getGuiScale();
            for (int row = 0; row < ROWS; row++) {
                int index = page * ROWS + row;
                if (index >= PAIRS.size()) break;
                int different = 0, occupied = 0;
                int cy = (int)((90 + row * 95) * scale);
                int left = (int)(360 * scale), right = (int)(590 * scale);
                for (int y = -(int)(42 * scale); y < (int)(42 * scale); y++)
                    for (int x = -(int)(64 * scale); x < (int)(64 * scale); x++) {
                        int first = image.getPixelRGBA(left + x, cy + y), second = image.getPixelRGBA(right + x, cy + y);
                        if (first != second) different++;
                        if ((first & 0xFFFFFF) != 0x362A24) occupied++;
                    }
                GUI_RESULTS.add(Map.of("name", PAIRS.get(index).name, "screenshot", name + ".png",
                        "different_pixels", different, "occupied_pixels", occupied, "foil", PAIRS.get(index).stack.hasFoil(),
                        "glint_clock_ms", GUI_GLINT_MILLIS, "fixed_glint_calls", fixedGlintCalls));
                Evidence.require(occupied > 20, "Empty actual native GUI model: " + PAIRS.get(index).name);
                Evidence.require(different == 0, "Candidate GUI differs from the identical original native model: " + PAIRS.get(index).name + " pixels=" + different);
            }
        }
        Evidence.json("blade-gui-identity.json", GUI_RESULTS);
        page++;
        drawn = false;
        if (complete()) {
            Evidence.require(fixedGlintCalls > 0, "The native GUI foil control did not exercise the fixed glint clock");
            System.out.println("UWU BLADE NATIVE MODEL PASS models=" + MODELS.size()
                    + " transforms=" + MODELS.size() * ItemDisplayContext.values().length * 2
                    + " gui_pairs=" + GUI_RESULTS.size() + " glint_calls=" + fixedGlintCalls);
        }
    }

    private static Map<String, Object> snapshot(BakedModel pass, ItemStack stack, boolean fabulous) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        int quadCount = 0, litVertices = 0;
        List<String> types = new ArrayList<>();
        for (var type : pass.getRenderTypes(stack, fabulous)) {
            types.add(type.toString());
            digest.update(type.toString().getBytes(StandardCharsets.UTF_8));
            for (Direction direction : directions()) for (BakedQuad quad : pass.getQuads(null, direction, RandomSource.create(42), ModelData.EMPTY, type)) {
                int[] vertices = quad.getVertices();
                ByteBuffer bytes = ByteBuffer.allocate(vertices.length * Integer.BYTES);
                for (int vertex : vertices) bytes.putInt(vertex);
                digest.update(bytes.array());
                digest.update((quad.getDirection() + ":" + quad.getTintIndex() + ":" + quad.isShade()
                        + ":" + quad.hasAmbientOcclusion() + ":" + quad.getSprite().contents().name()).getBytes(StandardCharsets.UTF_8));
                int stride = vertices.length / 4;
                for (int v = 0; v < 4; v++) if (stride > 6 && vertices[v * stride + 6] != 0) litVertices++;
                quadCount++;
            }
        }
        return Map.of("model_class", pass.getClass().getName(), "render_types", types, "quads", quadCount,
                "vertices_with_native_light", litVertices, "quad_and_light_sha256", HexFormat.of().formatHex(digest.digest()));
    }

    private static List<Direction> directions() {
        List<Direction> directions = new ArrayList<>();
        directions.add(null); directions.addAll(List.of(Direction.values()));
        return directions;
    }

    private static List<BakedQuad> quads(BakedModel model, ItemStack stack, boolean fabulous) {
        List<BakedQuad> quads = new ArrayList<>();
        for (BakedModel pass : model.getRenderPasses(stack, fabulous)) for (var type : pass.getRenderTypes(stack, fabulous))
            for (Direction direction : directions()) quads.addAll(pass.getQuads(null, direction, RandomSource.create(42), ModelData.EMPTY, type));
        return quads;
    }

    private static float[] bounds(BakedModel model, ItemStack stack) {
        float[] bounds = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (BakedQuad quad : quads(model, stack, false)) {
            int[] vertices = quad.getVertices();
            int stride = vertices.length / 4;
            for (int v = 0; v < 4; v++) for (int axis = 0; axis < 3; axis++) {
                float value = Float.intBitsToFloat(vertices[v * stride + axis]);
                bounds[axis] = Math.min(bounds[axis], value);
                bounds[axis + 3] = Math.max(bounds[axis + 3], value);
            }
        }
        Evidence.require(Float.isFinite(bounds[0]), "Native blade pass did not contain geometry");
        return bounds;
    }
}
