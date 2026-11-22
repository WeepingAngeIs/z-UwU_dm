package dev.uwu.duskfrequency;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import dev.uwu.dimension.AcquisitionPolicy;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Negative packet-handler checks, separate from the native UI's successful edits. */
public final class ConfigGuardProbe {
    private static final String KEY = "uwudimension.duskVillageChancePercent";
    private record Case(String label, String key, String expected, String value, boolean operator) { }

    private ConfigGuardProbe() { }

    @SuppressWarnings("unchecked")
    public static JsonArray run(ServerPlayer operator) throws Exception {
        require(operator.hasPermissions(2), "The actual UI operator lost permission before guard checks.");
        require(AcquisitionPolicy.duskVillageChancePercent() == 100,
                "Guard checks must follow the native UI's successful final edit to 100.");
        Path config = FrequencySupport.configPath();
        byte[] original = Files.readAllBytes(config);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original));

        Class<?> editor = Class.forName("dev.unyielding.quests.settings.ConfigEditor");
        Class<?> actionClass = Class.forName("dev.unyielding.quests.settings.ConfigEditor$Action");
        var constructor = actionClass.getConstructor(String.class, String.class, String.class);
        var handle = editor.getMethod("handle", ServerPlayer.class, actionClass);
        StreamCodec<RegistryFriendlyByteBuf, Object> codec =
                (StreamCodec<RegistryFriendlyByteBuf, Object>) actionClass.getField("CODEC").get(null);
        ServerPlayer nonOperator = new FakePlayer(operator.serverLevel(),
                new GameProfile(new UUID(0L, 0x4455534B434647L), "DuskConfigNoOp")) {
            @Override public boolean hasPermissions(int level) { return false; }
        };
        // The fake non-operator is never registered or connected. The handler must
        // reject it at its permission check, before attempting an outgoing sync.
        List<Case> cases = List.of(
                new Case("non_operator", KEY, "100", "0", false),
                new Case("stale_expected_value", KEY, "75", "0", true),
                new Case("unknown_key", "uwudimension.notARealSetting", "100", "0", true),
                new Case("below_minimum", KEY, "100", "-1", true),
                new Case("above_maximum", KEY, "100", "101", true),
                new Case("fractional_number", KEY, "100", "74.5", true),
                new Case("boolean_value", KEY, "100", "true", true),
                new Case("null_value", KEY, "100", "null", true),
                new Case("array_value", KEY, "100", "[]", true),
                new Case("quoted_number", KEY, "100", "\"75\"", true));
        JsonArray report = new JsonArray();
        for (Case scenario : cases) {
            Object action = constructor.newInstance(scenario.key(), scenario.expected(), scenario.value());
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), operator.registryAccess());
            Object decoded;
            try {
                codec.encode(buffer, action);
                decoded = codec.decode(buffer);
                require(action.equals(decoded) && buffer.readableBytes() == 0,
                        "Action codec round trip failed for " + scenario.label());
            } finally {
                buffer.release();
            }
            handle.invoke(null, scenario.operator() ? operator : nonOperator, decoded);
            require(AcquisitionPolicy.duskVillageChancePercent() == 100,
                    "Rejected edit changed the UwU getter: " + scenario.label());
            require(Arrays.equals(original, Files.readAllBytes(config)),
                    "Rejected edit changed the UwU server TOML: " + scenario.label());
            JsonObject row = new JsonObject();
            row.addProperty("label", scenario.label());
            row.addProperty("path", "real ConfigEditor.Action codec and ConfigEditor.handle; negative test only");
            row.addProperty("operator", scenario.operator());
            row.addProperty("expectedJson", scenario.expected());
            row.addProperty("submittedJson", scenario.value());
            row.addProperty("wireRoundTrip", true);
            row.addProperty("getterUnchanged", true);
            row.addProperty("tomlByteIdentical", true);
            row.addProperty("currentPercent", 100);
            row.addProperty("serverTomlSha256", digest);
            report.add(row);
        }
        System.out.println("DUSK FREQUENCY CONFIG GUARDS PASS count=" + report.size() + " current=100");
        return report;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
