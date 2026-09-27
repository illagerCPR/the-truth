package io.github.illagercpr.thetruth.uncertainty;

import io.github.illagercpr.thetruth.item.CertusCellItem;
import io.github.illagercpr.thetruth.item.EntanglementKeyItem;
import io.github.illagercpr.thetruth.item.UnformedMatterItem;
import io.github.illagercpr.thetruth.registry.TheTruthDataComponents;
import io.github.illagercpr.thetruth.registry.TheTruthItems;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Stack-level effects of uncertainty. Every operation here obeys the red line
 * "never silently delete player property":
 *
 * <ul>
 * <li>{@link #randomize} records the original size in a data component before
 *     shrinking a stack, so the change is cosmetic until reverted;</li>
 * <li>{@link #restore} writes the recorded sizes back;</li>
 * <li>{@link #convertSomeToUnformed} (M5, middle layer) degrades a stack into
 *     unformed matter that remembers the original — restored by the Certus
 *     Solidifier, never lost;</li>
 * <li>{@link #dissolveToUnformed} (M5, deep layer) replaces the old hard clear:
 *     everything convertible leaves the inventory as unformed matter entities
 *     that never despawn (docs/02 M3 登记项 3).</li>
 * </ul>
 */
public final class StackUncertainty {

    /** Chance per stack to degrade into unformed matter on a middle-layer pass. */
    public static final int CONVERSION_ONE_IN = 10;

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

    /**
     * M5 middle layer: on top of the size randomization, some stacks degrade
     * into unformed matter that remembers the original (docs/00 §4.2). Keys
     * and Certus Cells are exempt (the entanglement credential must never be
     * eaten; a cell would page itself away).
     */
    public static void convertSomeToUnformed(final Inventory inventory, final RandomSource random) {
        convertSomeToUnformed(inventory.items, random);
        convertSomeToUnformed(inventory.offhand, random);
    }

    public static void convertSomeToUnformed(final List<ItemStack> slots, final RandomSource random) {
        for (int i = 0; i < slots.size(); i++) {
            final ItemStack stack = slots.get(i);
            if (stack.isEmpty() || !isConvertible(stack) || random.nextInt(CONVERSION_ONE_IN) != 0) {
                continue;
            }
            slots.set(i, toUnformed(stack));
        }
    }

    /** Wraps a stack into one unformed matter item remembering the original. */
    public static ItemStack toUnformed(final ItemStack original) {
        final ItemStack unformed = new ItemStack(TheTruthItems.UNFORMED_MATTER.get());
        final ItemStack content = original.copy();
        content.remove(TheTruthDataComponents.UNCERTAIN_COUNT.get());
        io.github.illagercpr.thetruth.item.UnformedMatterItem.setContent(unformed, content);
        return unformed;
    }

    /**
     * Keys and Certus Cells never degrade; already-data items (unformed
     * matter, data fragments, the last record) stay as they are — wrapping
     * data into data would only lose information.
     */
    public static boolean isConvertible(final ItemStack stack) {
        return !(stack.getItem() instanceof EntanglementKeyItem)
            && !(stack.getItem() instanceof CertusCellItem)
            && !(stack.getItem() instanceof UnformedMatterItem)
            && !(stack.getItem() instanceof io.github.illagercpr.thetruth.item.DataFragmentItem)
            && !stack.is(TheTruthItems.LAST_RECORD.get());
    }

    /**
     * M5 deep-layer expiry (replaces {@code deleteAll}): every carried,
     * convertible stack leaves as an unformed matter entity at the player's
     * position with an infinite lifespan — announced, visible, recoverable by
     * the solidifier. Keys and Certus Cells stay in the inventory.
     */
    public static void dissolveToUnformed(final Inventory inventory, final Level level) {
        dissolveList(inventory.items, level, inventory.player);
        dissolveList(inventory.armor, level, inventory.player);
        dissolveList(inventory.offhand, level, inventory.player);
    }

    private static void dissolveList(final List<ItemStack> slots, final Level level,
                                     final net.minecraft.world.entity.Entity holder) {
        for (int i = 0; i < slots.size(); i++) {
            final ItemStack stack = slots.get(i);
            if (stack.isEmpty() || !isConvertible(stack)) {
                continue;
            }
            final ItemStack unformed = toUnformed(stack);
            final ItemEntity drop = new ItemEntity(level,
                holder.getX(), holder.getY() + 0.5, holder.getZ(), unformed);
            drop.lifespan = Integer.MAX_VALUE;
            level.addFreshEntity(drop);
            slots.set(i, ItemStack.EMPTY);
        }
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
