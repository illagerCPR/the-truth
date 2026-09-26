package io.github.illagercpr.thetruth.gametest;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.registry.TheTruthDataComponents;
import io.github.illagercpr.thetruth.uncertainty.StackUncertainty;
import io.github.illagercpr.thetruth.uncertainty.UncertaintyCurve;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M3 mechanics tests: the three-tier curve constants and the reversible
 * stack-randomization record — see docs/02 M3 parameter table. Assertion
 * failures inside the test body fail the GameTest via thrown exceptions.
 */
@GameTestHolder(TheTruth.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheTruthUncertaintyTests {

    private TheTruthUncertaintyTests() {
    }

    @GameTest(template = "smoke")
    public static void certusCurveConstantsMatchSpec(final GameTestHelper helper) {
        // Layer boundaries mirror the terrain layers.
        check(helper, UncertaintyCurve.Layer.SURFACE == UncertaintyCurve.layerOf(80),
            "y=80 must be SURFACE");
        check(helper, UncertaintyCurve.Layer.MIDDLE == UncertaintyCurve.layerOf(64),
            "y=64 must be MIDDLE");
        check(helper, UncertaintyCurve.Layer.MIDDLE == UncertaintyCurve.layerOf(-16),
            "y=-16 must be MIDDLE");
        check(helper, UncertaintyCurve.Layer.DEEP == UncertaintyCurve.layerOf(-17),
            "y=-17 must be DEEP");

        // Rates: surface teaches, middle is slow, deep is fast.
        check(helper, UncertaintyCurve.ratePerSecond(UncertaintyCurve.Layer.SURFACE) == 0,
            "surface must not accumulate");
        check(helper, UncertaintyCurve.ratePerSecond(UncertaintyCurve.Layer.MIDDLE) > 0,
            "middle must accumulate");
        check(helper, UncertaintyCurve.ratePerSecond(UncertaintyCurve.Layer.DEEP)
                > UncertaintyCurve.ratePerSecond(UncertaintyCurve.Layer.MIDDLE),
            "deep must accumulate faster than middle");

        // The deep warning window must sit inside the 3–5 s spec.
        check(helper, UncertaintyCurve.DEEP_WARNING_TICKS >= 60,
            "warning window must be >= 3 s, was " + UncertaintyCurve.DEEP_WARNING_TICKS);
        check(helper, UncertaintyCurve.DEEP_WARNING_TICKS <= 100,
            "warning window must be <= 5 s, was " + UncertaintyCurve.DEEP_WARNING_TICKS);

        // Covered players must decay, never accumulate.
        check(helper, UncertaintyCurve.COVERED_DECAY_PER_SECOND > 0, "covered decay must be positive");
        check(helper, UncertaintyCurve.RECOVER_THRESHOLD < UncertaintyCurve.MIDDLE_RANDOMIZE_THRESHOLD,
            "recover threshold must sit below the randomization threshold");
        helper.succeed();
    }

    @GameTest(template = "smoke")
    public static void certusStackRandomizationIsReversible(final GameTestHelper helper) {
        final NonNullList<ItemStack> slots = NonNullList.withSize(9, ItemStack.EMPTY);
        final RandomSource random = RandomSource.create(42L);

        slots.set(0, new ItemStack(Items.DIAMOND, 64));
        slots.set(1, new ItemStack(Items.STICK, 32));
        slots.set(2, new ItemStack(Items.APPLE, 1)); // singles are never touched

        StackUncertainty.randomize(slots, random);

        final int shrunken = slots.get(0).getCount();
        check(helper, shrunken >= 1 && shrunken <= 64,
            "randomized size must stay in 1..original, was " + shrunken);
        check(helper, slots.get(0).get(TheTruthDataComponents.UNCERTAIN_COUNT.get()) == 64,
            "original size must be recorded before shrinking");
        check(helper, slots.get(2).getCount() == 1, "single stacks must not be randomized");

        // Two shrink passes must not compound the loss: the record is the truth.
        StackUncertainty.randomize(slots, random);
        check(helper, StackUncertainty.restore(slots) == 2, "restore must report both affected stacks");
        check(helper, slots.get(0).getCount() == 64, "restore must write the recorded size back");
        check(helper, !slots.get(0).has(TheTruthDataComponents.UNCERTAIN_COUNT.get()),
            "restore must clear the marker");
        check(helper, slots.get(1).getCount() == 32, "second stack must restore to its record");
        helper.succeed();
    }

    private static void check(final GameTestHelper helper, final boolean condition, final String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
