package dev.uwu.dimension;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.neoforge.common.ItemAbilities;

/** A main-hand weapon must pass ordinary use through to an equipped offhand shield. */
final class OffhandShieldPriority {
    private OffhandShieldPriority() {}

    static boolean equipped(Player player) {
        if (player == null) return false;
        var offhand = player.getOffhandItem();
        return !offhand.isEmpty()
                && (offhand.getItem() instanceof ShieldItem || offhand.canPerformAction(ItemAbilities.SHIELD_BLOCK));
    }

    static boolean defers(Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND && equipped(player) && !player.isShiftKeyDown();
    }
}
