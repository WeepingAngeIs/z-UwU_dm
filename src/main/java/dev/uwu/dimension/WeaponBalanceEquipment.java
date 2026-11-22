package dev.uwu.dimension;

import java.util.ArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Vanilla equipment detection applies the changed component and sends the item and attribute to its observers. */
final class WeaponBalanceEquipment {
    private static final ResourceLocation ADJUSTMENT = Arsenal.id("configured_base_hit");

    static void tick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        refresh(player.getMainHandItem());
        refresh(player.getOffhandItem());
    }

    private static void refresh(ItemStack stack) {
        if (stack.isEmpty()) return;
        var item = stack.getItem();
        String id = item instanceof GlitchBow ? GlitchBow.form(stack) : BuiltInRegistries.ITEM.getKey(item).getPath();
        if (!(item instanceof GlitchBow) && !BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("uwudimension")) return;
        var setting = WeaponBalance.BASE.get(id);
        if (setting == null && !(item instanceof GlitchBow)) return;
        double delta = setting == null ? 0 : setting.current() - setting.original();
        var components = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        var previous = components.modifiers().stream().filter(e -> e.modifier().id().equals(ADJUSTMENT)).findFirst();
        if (previous.map(e -> e.modifier().amount()).orElse(0d) == delta) return;
        var entries = new ArrayList<>(components.modifiers());
        previous.ifPresent(entries::remove);
        if (delta != 0) entries.add(new ItemAttributeModifiers.Entry(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(ADJUSTMENT, delta, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND));
        var changed = new ItemAttributeModifiers(entries, components.showInTooltip());
        var stock = item.components().get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (changed.equals(stock) || (stock == null && changed.equals(ItemAttributeModifiers.EMPTY)))
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, stock);
        else stack.set(DataComponents.ATTRIBUTE_MODIFIERS, changed);
    }

    private WeaponBalanceEquipment() {}
}
