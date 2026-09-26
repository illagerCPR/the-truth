package io.github.illagercpr.thetruth.gametest;

import io.github.illagercpr.thetruth.TheTruth;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M0 GameTest smoke tests: prove the headless test harness works end to end.
 * Real mechanics tests (certainty coverage, instability, cell states) arrive with M3/M5.
 */
@GameTestHolder(TheTruth.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheTruthGameTests {

    private TheTruthGameTests() {
    }

    @GameTest(template = "smoke")
    public static void smokePlaceAndVerify(final GameTestHelper helper) {
        helper.setBlock(1, 1, 1, Blocks.STONE);
        helper.succeedWhenBlockPresent(Blocks.STONE, 1, 1, 1);
    }
}
