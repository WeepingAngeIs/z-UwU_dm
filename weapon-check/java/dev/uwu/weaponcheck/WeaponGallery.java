package dev.uwu.weaponcheck;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import dev.uwu.dimension.AcquisitionPolicy;
import dev.uwu.dimension.Arsenal;
import dev.uwu.dimension.CounterArsenal;
import dev.uwu.dimension.GlitchBow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Native in-game ItemRenderer views of every actual registered weapon and its marked forms. */
final class WeaponGallery {
    record Entry(String name, ItemStack stack, boolean held) {}
    static final List<Entry> ALL = new ArrayList<>();
    static final List<Entry> HELD = new ArrayList<>();
    private static final int ROWS = 5;
    private static final float[] ANGLES = {0, 60, 90, 120};
    private static final Set<String> TOOL_CONTROLS = Set.of("silver_pickaxe", "silver_axe", "silver_shovel", "silver_hoe",
            "roseglass_pickaxe", "roseglass_axe", "roseglass_shovel", "roseglass_hoe",
            "vesper_opal_pickaxe", "vesper_opal_axe", "vesper_opal_shovel", "vesper_opal_hoe");
    private static int page;
    private static boolean drawn;
    private static boolean finished;

    static void initialize(Minecraft client) throws Exception {
        var items = BuiltInRegistries.ITEM.stream().filter(item ->
                BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("uwudimension"))
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList();
        int weapons = 0, controls = 0;
        for (Item item : items) {
            ItemStack stack = item.getDefaultInstance();
            String name = BuiltInRegistries.ITEM.getKey(item).getPath();
            boolean weapon = AcquisitionPolicy.isWeapon(stack);
            boolean control = !weapon && (TOOL_CONTROLS.contains(name) || item instanceof ShieldItem);
            if (!weapon && !control) continue;
            add(name, stack, true);
            if (weapon) weapons++; else controls++;
        }
        Evidence.require(weapons == 153, "Weapon registry inventory changed: " + weapons);
        Evidence.require(controls == 15, "Tool/shield control inventory changed: " + controls);
        ItemStack smooth = new ItemStack(Arsenal.WEAPONS.get(Arsenal.Relic.CUPID_BOW).get());
        CustomData.update(DataComponents.CUSTOM_DATA, smooth, tag -> tag.putBoolean("uwudimension:smooth_cupid", true));
        add("cupid_bow_detailed", smooth, true);
        for (CounterArsenal.Entry entry : CounterArsenal.ROSTER) {
            if (entry.shape().equals("scythe")) elements(entry.id(), new ItemStack(CounterArsenal.ITEMS.get(entry.id()).get()), false);
        }
        ItemStack vesper = new ItemStack(Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get());
        elements("vesper_scythe", vesper, true);
        Item glitch = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("uwudimension", "glitch_bow"));
        for (int index = 0; index < GlitchBow.FORMS.size(); index++) {
            String form = GlitchBow.FORMS.get(index);
            if (form.equals("cupid_bow")) continue;
            ItemStack stack = new ItemStack(glitch);
            int selected = index;
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("uwu_form", selected));
            add("glitch_" + form, stack, false);
            if (GlitchBow.isScythe(stack)) elements("glitch_" + form, stack, form.equals("vesper_scythe"));
        }
        ItemStack staticForm = new ItemStack(glitch);
        CustomData.update(DataComponents.CUSTOM_DATA, staticForm, tag -> tag.putLong("uwu_static_until", Long.MAX_VALUE));
        add("glitch_static", staticForm, false);
        Evidence.json("gallery-index.json", Map.of("weapons", weapons, "controls", controls,
                "held_views", HELD.size(), "variant_views", ALL.size(), "angles", ANGLES, "hud_hidden", client.options.hideGui,
                "entries", ALL.stream().map(entry -> Map.of("name", entry.name,
                        "item", BuiltInRegistries.ITEM.getKey(entry.stack.getItem()).toString(),
                        "data", entry.stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().toString(),
                        "held", entry.held)).toList()));
        List<Map<String, Object>> geometry = new ArrayList<>();
        for (var entry : ALL) {
            BakedModel model = client.getItemRenderer().getModel(entry.stack, client.level, client.player, 42);
            BladeModelProbe.inspect(entry.name, entry.stack, model);
            geometry.add(Map.of("name", entry.name, "model_class", model.getClass().getName(),
                    "bounds", bounds(model, entry.stack), "custom_renderer", model.isCustomRenderer()));
        }
        Evidence.json("native-models.json", geometry);
        BladeModelProbe.initialized();
    }

    private static void elements(String name, ItemStack stack, boolean credit) {
        for (int element = 1; element <= 3; element++) {
            ItemStack copy = stack.copy();
            int selected = element;
            CustomData.update(DataComponents.CUSTOM_DATA, copy, tag -> tag.putInt("uwu_element", selected));
            add(name + "_element" + element, copy, false);
        }
        if (credit) for (int element = 0; element <= 3; element++) {
            ItemStack copy = stack.copy();
            int selected = element;
            CustomData.update(DataComponents.CUSTOM_DATA, copy, tag -> {
                tag.putInt("uwu_element", selected);
                tag.putLong("uwu.flower_credit_until", Long.MAX_VALUE);
            });
            add(name + "_flower_element" + element, copy, false);
        }
    }

    private static void add(String name, ItemStack stack, boolean held) {
        var entry = new Entry(name, stack, held);
        ALL.add(entry);
        if (held) HELD.add(entry);
    }

    static boolean complete() { return finished && BladeModelProbe.complete(); }

    static void gui(GuiGraphics gui) throws Exception {
        if (finished) { BladeModelProbe.gui(gui); return; }
        if (ALL.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        gui.fill(0, 0, gui.guiWidth(), gui.guiHeight(), 0xFF242A36);
        gui.drawString(client.font, "UwU actual models | " + (Evidence.BASELINE ? "integrated baseline" : "candidate")
                + " | page " + (page + 1), 8, 5, 0xFFFCE5EE, false);
        for (int col = 0; col < ANGLES.length; col++) {
            gui.drawString(client.font, ANGLES[col] + " degrees", 248 + col * 145, 20, 0xFFC0D2E8, false);
        }
        for (int row = 0; row < ROWS; row++) {
            int index = page * ROWS + row;
            if (index >= ALL.size()) break;
            Entry entry = ALL.get(index);
            int y = 42 + row * 84;
            String label = entry.name;
            for (int off = 0, line = 0; off < label.length(); off += 33, line++) {
                gui.drawString(client.font, label.substring(off, Math.min(off + 33, label.length())),
                        8, y + 20 + line * 10, 0xFFFFD2E2, false);
            }
            BakedModel model = client.getItemRenderer().getModel(entry.stack, client.level, client.player, 42);
            Evidence.require(model != client.getModelManager().getMissingModel(), "Missing actual model: " + entry.name);
            float[] bounds = bounds(model, entry.stack);
            float span = Math.max(Math.max(bounds[3] - bounds[0], bounds[4] - bounds[1]), bounds[5] - bounds[2]);
            float scale = 68 / Math.max(1, span);
            for (int col = 0; col < ANGLES.length; col++) {
                var poses = gui.pose();
                poses.pushPose();
                try {
                    poses.translate(277 + col * 145, y + 40, 200);
                    poses.scale(scale, -scale, scale);
                    poses.mulPose(Axis.YP.rotationDegrees(ANGLES[col]));
                    // ItemRenderer's native model translation centres a [0,1] item at the origin.
                    poses.translate(.5F - (bounds[0] + bounds[3]) / 2,
                            .5F - (bounds[1] + bounds[4]) / 2, .5F - (bounds[2] + bounds[5]) / 2);
                    Lighting.setupFor3DItems();
                    client.getItemRenderer().render(entry.stack, ItemDisplayContext.NONE, false, poses,
                            gui.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
                    gui.flush();
                } finally { poses.popPose(); }
            }
        }
        gui.flush();
        drawn = true;
    }

    static void frame() throws Exception {
        if (finished) { BladeModelProbe.frame(); return; }
        if (!drawn) return;
        String name = "gallery-" + String.format("%03d", page);
        Evidence.screenshot(name);
        try (NativeImage pixels = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
            for (int row = 0; row < ROWS; row++) {
                int index = page * ROWS + row;
                if (index >= ALL.size()) break;
                for (int col = 0; col < ANGLES.length; col++) {
                    int cx = 277 + col * 145, cy = 82 + row * 84;
                    int x0 = (int)((cx - 62) * guiScale), x1 = (int)((cx + 62) * guiScale);
                    int y0 = (int)((cy - 38) * guiScale), y1 = (int)((cy + 38) * guiScale);
                    int count = 0, minX = x1, maxX = x0, minY = y1, maxY = y0;
                    for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                        int color = pixels.getPixelRGBA(x, y);
                        int r = color & 255, g = color >>> 8 & 255, b = color >>> 16 & 255;
                        if (Math.abs(r - 0x24) + Math.abs(g - 0x2A) + Math.abs(b - 0x36) < 20) continue;
                        count++; minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    }
                    Evidence.line("native-silhouettes.tsv", ALL.get(index).name + "\t" + ANGLES[col] + "\t" + count
                            + "\t" + (count == 0 ? 0 : maxX - minX + 1) + "\t" + (count == 0 ? 0 : maxY - minY + 1)
                            + "\t" + name);
                }
            }
        }
        page++;
        drawn = false;
        if (page * ROWS >= ALL.size()) {
            finished = true;
            System.out.println("UWU WEAPONS NATIVE GALLERY PASS entries=" + ALL.size() + " pages=" + page
                    + " native ItemRenderer at 0/60/90/120 degrees");
        }
    }

    private static float[] bounds(BakedModel model, ItemStack stack) {
        float[] b = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        List<Direction> sides = new ArrayList<>();
        sides.add(null); sides.addAll(List.of(Direction.values()));
        for (BakedModel pass : model.getRenderPasses(stack, true))
        for (var renderType : pass.getRenderTypes(stack, true))
        for (Direction side : sides) for (var quad : pass.getQuads(null, side, RandomSource.create(42), ModelData.EMPTY, renderType)) {
            int[] vertices = quad.getVertices();
            int stride = vertices.length / 4;
            for (int v = 0; v < 4; v++) for (int axis = 0; axis < 3; axis++) {
                float value = Float.intBitsToFloat(vertices[v * stride + axis]);
                b[axis] = Math.min(b[axis], value);
                b[axis + 3] = Math.max(b[axis + 3], value);
            }
        }
        if (!Float.isFinite(b[0])) return new float[] {0, 0, 0, 1, 1, 1};
        return b;
    }
}
