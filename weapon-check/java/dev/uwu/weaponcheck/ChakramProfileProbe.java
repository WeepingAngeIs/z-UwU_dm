package dev.uwu.weaponcheck;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.uwu.dimension.CounterArsenal;
import dev.uwu.dimension.WeaponAnimationAdapter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Reads the native registry beneath the adapter, without changing its registrations or player stacks. */
public final class ChakramProfileProbe {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String LEFT = "simplyswords:one_handed_chakram_throw_left";
    private static final String RIGHT = "simplyswords:one_handed_throw_chakram_right";

    private ChakramProfileProbe() {}

    public static void verify(boolean baseline) {
        require(ModList.get().isLoaded("bettercombat") && ModList.get().isLoaded("simplyswords")
                && ModList.get().isLoaded("playeranimator"), "Pinned combat animation dependencies are missing");
        try {
            Method rawLookup = WeaponRegistry.class.getDeclaredMethod("getAttributes", ResourceLocation.class);
            rawLookup.setAccessible(true);
            JsonArray profiles = new JsonArray();
            int count = 0, nativeProfiles = 0, vanillaProfiles = 0, throwsSeen = 0, handSwings = 0;
            ItemStack controlStack = null;
            for (var entry : CounterArsenal.ROSTER) {
                if (!entry.shape().equals("chakram")) continue;
                count++;
                ItemStack stack = new ItemStack(CounterArsenal.ITEMS.get(entry.id()).get());
                if (controlStack == null) controlStack = stack;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                WeaponAttributes raw = (WeaponAttributes) rawLookup.invoke(null, id);
                JsonElement before = JSON.toJsonTree(raw);
                WeaponAttributes adapted = WeaponRegistry.getAttributes(stack);
                require(before.equals(JSON.toJsonTree(raw)), "Native profile mutated: " + id);
                require(mechanics(raw).equals(mechanics(adapted)), "Gameplay/pose fields changed: " + id);
                JsonObject row = new JsonObject();
                row.addProperty("id", id.toString());
                row.addProperty("roster_shape", entry.shape());
                row.add("native_profile", before);
                row.add("adapted_profile", JSON.toJsonTree(adapted));
                profiles.add(row);
                if (raw == null) {
                    vanillaProfiles++;
                    require(adapted == null, "A new combat profile was manufactured: " + id);
                    System.out.println("CHAKRAM NATIVE PROFILE " + id + " vanilla=true");
                    continue;
                }
                nativeProfiles++;
                require(raw.attacks().length == adapted.attacks().length, "Combo length changed: " + id);
                StringBuilder names = new StringBuilder();
                for (int i = 0; i < raw.attacks().length; i++) {
                    var original = raw.attacks()[i];
                    var visible = adapted.attacks()[i];
                    boolean conditional = original.conditions() != null && original.conditions().length > 0;
                    if (conditional) {
                        require(Objects.equals(original.animation(), visible.animation()),
                                "Conditional/dual-wield clip changed: " + id + " attack=" + i);
                    } else if (throwClip(visible.animation())) {
                        throwsSeen++;
                    } else {
                        handSwings++;
                        require(baseline, "Chakram still selects a hand-only clip: " + id + " attack=" + i
                                + " two_handed=" + raw.two_handed() + " animation=" + visible.animation());
                    }
                    if (i > 0) names.append(',');
                    names.append(visible.animation());
                }
                explicitPresetControl(stack, raw);
                System.out.println("CHAKRAM NATIVE PROFILE " + id + " two_handed=" + raw.two_handed()
                        + " category=" + raw.category() + " clips=" + names);
            }
            require(count == 20, "Expected the shipped 20 roster chakrams, observed " + count);
            require(nativeProfiles > 0 && throwsSeen > 0, "No native throw-animation profile was exercised");
            // The dependency's real chakram profile supplies conditional main/off-hand combo controls.
            WeaponAttributes conditional = (WeaponAttributes) rawLookup.invoke(null,
                    ResourceLocation.fromNamespaceAndPath("simplyswords", "iron_chakram"));
            require(conditional != null, "Missing native Simply Swords conditional control");
            WeaponAttributes result = (WeaponAttributes) WeaponAnimationAdapter.adapt(controlStack, conditional);
            require(mechanics(conditional).equals(mechanics(result)), "Conditional control mechanics changed");
            int conditionalCount = 0;
            for (int i = 0; i < conditional.attacks().length; i++) {
                var attack = conditional.attacks()[i];
                if (attack.conditions() != null && attack.conditions().length > 0) {
                    conditionalCount++;
                    require(Objects.equals(attack.animation(), result.attacks()[i].animation()),
                            "Native conditional main/off-hand animation changed");
                }
            }
            require(conditionalCount >= 2, "Expected native main/off-hand conditional controls");
            JsonObject report = new JsonObject();
            report.addProperty("baseline", baseline);
            report.addProperty("roster_chakrams", count);
            report.addProperty("native_profiles", nativeProfiles);
            report.addProperty("vanilla_profiles_preserved", vanillaProfiles);
            report.addProperty("unconditional_throw_clips", throwsSeen);
            report.addProperty("unconditional_hand_only_clips", handSwings);
            report.addProperty("conditional_controls", conditionalCount);
            report.add("profiles", profiles);
            Path output = Path.of(System.getProperty("uwu.check.evidence", "weapon-evidence"));
            Files.createDirectories(output);
            Files.writeString(output.resolve("chakram-profiles.json"), JSON.toJson(report) + "\n");
            System.out.println((baseline ? "CHAKRAM PROFILE BASELINE OBSERVED" : "CHAKRAM PROFILE PASS")
                    + " roster=" + count + " native=" + nativeProfiles + " vanilla=" + vanillaProfiles
                    + " throwClips=" + throwsSeen + " handOnlyClips=" + handSwings
                    + " gameplay, poses, explicit presets, and conditional main/off-hand clips preserved");
        } catch (ReflectiveOperationException | java.io.IOException error) {
            throw new IllegalStateException("Could not audit native chakram profiles", error);
        }
    }

    private static boolean throwClip(String clip) {
        return LEFT.equals(clip) || RIGHT.equals(clip);
    }

    private static JsonElement mechanics(WeaponAttributes profile) {
        JsonElement json = JSON.toJsonTree(profile);
        if (json.isJsonObject()) {
            JsonArray attacks = json.getAsJsonObject().getAsJsonArray("attacks");
            if (attacks != null) for (JsonElement attack : attacks) attack.getAsJsonObject().remove("animation");
        }
        return json;
    }

    @SuppressWarnings("unchecked")
    private static void explicitPresetControl(ItemStack stack, WeaponAttributes raw) {
        var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(
                ResourceLocation.fromNamespaceAndPath("bettercombat", "preset_id")).orElseThrow();
        ItemStack explicit = stack.copy();
        explicit.set((DataComponentType<ResourceLocation>) type,
                ResourceLocation.fromNamespaceAndPath("bettercombat", "sword"));
        require(WeaponAnimationAdapter.adapt(explicit, raw) == raw, "Explicit stack preset was overridden");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("WEAPON CHECK FAILED " + message);
    }
}
