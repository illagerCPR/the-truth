package io.github.illagercpr.thetruth.uncertainty;

import io.github.illagercpr.thetruth.registry.TheTruthDataComponents;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Stack-level effects of uncertainty. Every operation here obeys the red line
 * "never silently delete player property":
 *
 * <ul>
 * <li>{@link #randomize} records the original size in a data component before
 *     shrinking a stack, so the change is cosmetic until reverted;</li>
 * <li>{@link #restore} writes the recorded sizes back;</li>
 * <li>{@link #deleteAll} is only ever invoked by the manager after the announced
 *     deep-layer salvage window has expired un-covered.</li>
 * </ul>
 */
public final class StackUncertainty {

    private StackUncertainty() {
    }

    /**
     * Randomizes visible stack sizes of a player's main inventory + offhand
     * (armor is left alone). First randomization records the original size;
     * later passes shrink from the original again, never compounding the loss.
     */
    public static void randomize(final Inventory inventory, final RandomSource random) {
        randomize(inventory.items, random);
        randomize(inventory.offhand, random);
    }

    public static void randomize(final NonNullList<ItemStack> slots, final RandomSource random) {
        for (final ItemStack stack : slots) {
            if (stack.isEmpty() || stack.getCount() <= 1) {
                continue;
            }
            final int original = stack.getOrDefault(TheTruthDataComponents.UNCERTAIN_COUNT.get(), stack.getCount());
            if (!stack.has(TheTruthDataComponents.UNCERTAIN_COUNT.get())) {
                stack.set(TheTruthDataComponents.UNCERTAIN_COUNT.get(), original);
            }
            // Uniform 1..original, biased low so the world visibly "eats" counts.
            stack.setCount(1 + random.nextInt(original));
        }
    }

    /**
     * Writes recorded original sizes back and clears the marker. Returns the
     * number of stacks restored (0 = nothing was indeterminate).
     */
    public static int restore(final Inventory inventory) {
        return restore(inventory.items) + restore(inventory.offhand) + restore(inventory.armor);
    }

    public static int restore(final List<ItemStack> slots) {
        int restored = 0;
        for (final ItemStack stack : slots) {
            if (stack.has(TheTruthDataComponents.UNCERTAIN_COUNT.get())) {
                final int original = stack.get(TheTruthDataComponents.UNCERTAIN_COUNT.get());
                stack.remove(TheTruthDataComponents.UNCERTAIN_COUNT.get());
                if (!stack.isEmpty()) {
                    stack.setCount(original);
                    restored++;
                }
            }
        }
        return restored;
    }

    /** Deep-layer expiry: clear everything the player carries (announced loss). */
    public static void deleteAll(final Inventory inventory) {
        inventory.items.clear();
        inventory.armor.clear();
        inventory.offhand.clear();
    }

    /** True while any carried stack still carries a recorded original size. */
    public static boolean hasIndeterminateStacks(final Inventory inventory) {
        return hasIndeterminateStacks(inventory.items) || hasIndeterminateStacks(inventory.offhand)
            || hasIndeterminateStacks(inventory.armor);
    }

    private static boolean hasIndeterminateStacks(final List<ItemStack> slots) {
        for (final ItemStack stack : slots) {
            if (stack.has(TheTruthDataComponents.UNCERTAIN_COUNT.get())) {
                return true;
            }
        }
        return false;
    }
}
