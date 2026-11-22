package dev.uwu.dimension;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

final class WeaponBalanceCommands {
    static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("uwu");
        var charm = Commands.literal("charmseconds")
                .executes(c -> report(c.getSource(), "Cupid Bow enemy charm (seconds)", AcquisitionPolicy.cupidBowCharmSeconds(), false))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, AcquisitionPolicy.MAX_CUPID_BOW_CHARM_SECONDS))
                        .executes(c -> save(c.getSource(), "Cupid Bow enemy charm (seconds)",
                                AcquisitionPolicy.CUPID_BOW_CHARM_SECONDS, IntegerArgumentType.getInteger(c, "seconds"))));
        root.then(Commands.literal("cupid").requires(LauncherExcavationControl::hasPermission).then(charm));
        var weapons = Commands.literal("weapon").requires(LauncherExcavationControl::hasPermission)
                .executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("Choose a weapon and setting with Tab. Omit the value to read it. Timers use seconds."), false);
                    return 1;
                });
        WeaponBalance.GROUPS.forEach((id, settings) -> {
            var weapon = Commands.literal(id).executes(c -> {
                c.getSource().sendSuccess(() -> Component.literal(id + " settings: " + String.join(", ", settings.keySet())), false);
                return 1;
            });
            settings.forEach((key, setting) -> weapon.then(Commands.literal(key)
                    .executes(c -> report(c.getSource(), setting.label(), setting.current(), false))
                    .then(Commands.argument("value", DoubleArgumentType.doubleArg(setting.min(), setting.max()))
                            .executes(c -> save(c.getSource(), setting.label(), setting.value(),
                                    DoubleArgumentType.getDouble(c, "value"))))));
            weapons.then(weapon);
        });
        event.getDispatcher().register(root.then(weapons));
    }

    private static <T extends Number> int save(CommandSourceStack source, String label, ModConfigSpec.ConfigValue<T> setting, T value) {
        if (!AcquisitionPolicy.SPEC.isLoaded()) {
            source.sendFailure(Component.literal("The UwU server settings are still loading."));
            return 0;
        }
        T previous = setting.get();
        try {
            setting.set(value);
            setting.save();
        } catch (RuntimeException exception) {
            setting.set(previous);
            com.mojang.logging.LogUtils.getLogger().error("Could not save {}", label, exception);
            source.sendFailure(Component.literal("Could not save " + label + ". The previous value is still active."));
            return 0;
        }
        return report(source, label, value, true);
    }

    private static int report(CommandSourceStack source, String label, Number value, boolean broadcast) {
        source.sendSuccess(() -> Component.literal(label + " = " + value), broadcast);
        return 1;
    }

    private WeaponBalanceCommands() {}
}
