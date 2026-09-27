package io.github.illagercpr.thetruth.item;

import io.github.illagercpr.thetruth.registry.TheTruthDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Unformed Matter (M5): matter data that was never written into a certain
 * instance (docs/00 §6.2). Carries the original stack it degraded from in the
 * {@code unformed_content} component when it comes from the instability curve;
 * mined residual clusters drop "free" unformed matter without content.
 * The Certus Solidifier writes it back ("re-serializes it").
 */
public class UnformedMatterItem extends Item {

    public UnformedMatterItem(final Properties properties) {
        super(properties);
    }

    /** Reads the remembered original stack, or empty on free (mined) matter. */
    public static ItemStack getContent(final ItemStack unformed) {
        final ItemContainerContents contents = unformed.get(TheTruthDataComponents.UNFORMED_CONTENT.get());
        if (contents == null) {
            return ItemStack.EMPTY;
        }
        for (final ItemStack stack : contents.nonEmptyItems()) {
            return stack;
        }
        return ItemStack.EMPTY;
    }

    /** Records the remembered original stack (single content group per item). */
    public static void setContent(final ItemStack unformed, final ItemStack content) {
        unformed.set(TheTruthDataComponents.UNFORMED_CONTENT.get(),
            ItemContainerContents.fromItems(List.of(content)));
    }

    /** Unwritten data shimmers. */
    @Override
    public boolean isFoil(final ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        final ItemStack content = getContent(stack);
        if (!content.isEmpty()) {
            tooltip.add(Component.translatable("item.thetruth.unformed_matter.tooltip.content",
                content.getHoverName()).withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("item.thetruth.unformed_matter.tooltip.free")
                .withStyle(ChatFormatting.GRAY));
        }
    }
}
