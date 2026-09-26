package io.github.illagercpr.thetruth.item;

import java.util.List;

import io.github.illagercpr.thetruth.registry.TheTruthDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Entanglement Key (M4): the red-line-1 credential. Unpaired keys are blank;
 * the Overworld core's binding ritual mints a pair — one key's data points at
 * the home core, a second key joins the player's inventory. Losing both is
 * recoverable: rebind the core with a fresh unpaired key (docs/02 M4).
 */
public class EntanglementKeyItem extends Item {

    public EntanglementKeyItem(final Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        final var pair = stack.get(TheTruthDataComponents.ENTANGLEMENT_PAIR.get());
        if (pair == null) {
            tooltip.add(Component.translatable("item.thetruth.entanglement_key.tooltip.unbound")
                .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.thetruth.entanglement_key.tooltip.bound",
                pair.homePos().toShortString()).withStyle(ChatFormatting.AQUA));
        }
    }
}
