package dev.uwu.weaponcheck;

import dev.uwu.dimension.CounterArsenal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Actual held captures of each changed blade family in both views and physical hands. */
public final class BladeHeldProbe {
    record Representative(String item, String shape, String source) {}
    record View(int index, Representative representative, String camera, String arm) {
        String token() { return "blade-held-" + index; }
        String screenshot() { return "blade-held-" + String.format("%03d", index) + "-" + representative.item + "-" + camera + "-" + arm; }
        ItemDisplayContext context() {
            if (camera.equals("first")) return arm.equals("left") ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
            return arm.equals("left") ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        }
    }
    private static final List<View> VIEWS = new ArrayList<>();
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final Set<String> CONTEXTS = new LinkedHashSet<>();
    private static boolean active, equipped, pending;
    private static int index, entered;

    static void start() {
        List<Representative> representatives = new ArrayList<>(List.of(
                new Representative("bellheart_axe", "axe", "native"),
                new Representative("blossom_greatsword", "axe", "native"),
                new Representative("vesper_scythe", "scythe", "native"),
                new Representative("rose_dagger", "dagger", "native"),
                new Representative("lantern_spear", "spear", "native"),
                new Representative("tidelace_trident", "trident", "native")));
        for (String shape : List.of("axe", "chakram", "dagger", "scythe", "spear")) {
            var entry = CounterArsenal.ROSTER.stream().filter(candidate -> candidate.shape().equals(shape)).findFirst().orElseThrow();
            representatives.add(new Representative(entry.id(), shape, "counter"));
        }
        representatives.add(new Representative("returnbloom_dirk", "dagger", "obj"));
        Evidence.require(representatives.size() == 12, "Unexpected held blade representative inventory");
        for (var representative : representatives) for (String camera : List.of("first", "third")) for (String arm : List.of("right", "left"))
            VIEWS.add(new View(VIEWS.size(), representative, camera, arm));
        active = true;
    }

    static boolean tick(Minecraft client) throws Exception {
        if (index == VIEWS.size()) {
            active = false;
            client.player.setMainArm(HumanoidArm.RIGHT);
            WeaponFixture.server(client, actor -> actor.setMainArm(HumanoidArm.RIGHT));
            Evidence.require(RESULTS.size() == 48, "Incomplete native held blade views");
            Evidence.json("blade-held-views.json", RESULTS);
            System.out.println("UWU BLADE HELD VIEWS PASS representatives=12 views=48 first_person=24 third_person=24");
            return true;
        }
        View view = VIEWS.get(index);
        WeaponFixture.poseOwner(client);
        if (!equipped) {
            ItemStack stack = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("uwudimension", view.representative.item)).getDefaultInstance();
            Evidence.require(!stack.isEmpty(), "Missing native held blade: " + view.representative.item);
            WeaponFixture.equip(client, stack, view.token(), InteractionHand.MAIN_HAND);
            HumanoidArm arm = view.arm.equals("left") ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
            client.player.setMainArm(arm);
            WeaponFixture.server(client, actor -> actor.setMainArm(arm));
            client.options.setCameraType(view.camera.equals("first") ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_FRONT);
            // View the equipped physical side so left-hand blades are not hidden by the torso.
            WeaponFixture.orbit(view.camera.equals("first") ? 0 : view.arm.equals("left") ? -90 : 90);
            CONTEXTS.clear();
            entered = client.player.tickCount;
            equipped = true;
        }
        Evidence.require(client.player.tickCount - entered < 200, "Native held blade view timed out: " + view);
        pending = client.player.tickCount - entered >= 8 && WeaponFixture.marker(client.player.getMainHandItem()).equals(view.token());
        if (pending && view.representative.item.equals("vesper_scythe")) VesperTraversalProbe.verify(client);
        return false;
    }

    public static void observeDraw(ItemStack stack, ItemDisplayContext context) {
        if (active && index < VIEWS.size() && WeaponFixture.marker(stack).equals(VIEWS.get(index).token())) CONTEXTS.add(context.toString());
    }

    static void frame() throws Exception {
        if (!active || !pending || index >= VIEWS.size()) return;
        View view = VIEWS.get(index);
        if (!CONTEXTS.contains(view.context().toString())) return;
        Evidence.screenshot(view.screenshot());
        RESULTS.add(Map.of("view", view, "native_context", view.context().toString(),
                "observed_contexts", List.copyOf(CONTEXTS), "screenshot", view.screenshot() + ".png"));
        index++;
        pending = false;
        equipped = false;
    }
}
