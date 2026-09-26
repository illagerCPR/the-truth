package io.github.illagercpr.thetruth.gametest;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.blockentity.CertusAnchorBlockEntity;
import io.github.illagercpr.thetruth.coverage.CertaintyCoverage;
import io.github.illagercpr.thetruth.registry.TheTruthBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M3 coverage tests for the Certus Anchor: unpowered anchors project nothing,
 * a powered grid activates the field, adjacent anchors (and AE2 devices) connect
 * automatically, and the field respects its radius. The AE2 creative energy
 * cell is resolved through the block registry by id, so no AE2 internal class
 * is imported (red line: appeng.api only).
 *
 * <p>Note on isolation: the per-level anchor registry is shared across all
 * GameTest plots, so "negative" coverage assertions against the world-wide
 * verdict are unreliable here; per-node assertions carry the negative case
 * instead. Positive assertions (coverage within range) are unaffected.
 */
@GameTestHolder(TheTruth.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheTruthCoverageTests {

    /** The anchor sits here in every template of this class. */
    private static final BlockPos ANCHOR_POS = new BlockPos(1, 1, 1);
    private static final BlockPos CELL_POS = new BlockPos(1, 1, 2);

    private TheTruthCoverageTests() {
    }

    @GameTest(template = "smoke")
    public static void certusAnchorWithoutPowerDoesNotCover(final GameTestHelper helper) {
        helper.setBlock(ANCHOR_POS, TheTruthBlocks.CERTUS_ANCHOR.get());
        helper.startSequence()
            .thenIdle(40)
            .thenExecute(() -> {
                final var anchor = helper.getBlockEntity(ANCHOR_POS);
                check(helper, anchor instanceof CertusAnchorBlockEntity,
                    "anchor block entity must exist");
                check(helper, ((CertusAnchorBlockEntity) anchor).getMainNode().isReady(),
                    "grid node must be created even without power");
                check(helper, !((CertusAnchorBlockEntity) anchor).isFieldActive(),
                    "field must stay inactive without a powered grid");
            })
            .thenSucceed();
    }

    @GameTest(template = "smoke")
    public static void certusAnchorWithPowerCovers(final GameTestHelper helper) {
        helper.setBlock(ANCHOR_POS, TheTruthBlocks.CERTUS_ANCHOR.get());
        helper.setBlock(CELL_POS, creativeCell(helper));
        helper.startSequence()
            .thenIdle(60)
            .thenExecute(() -> {
                final var anchor = helper.getBlockEntity(ANCHOR_POS);
                check(helper, anchor instanceof CertusAnchorBlockEntity
                        && ((CertusAnchorBlockEntity) anchor).isFieldActive(),
                    "anchor must be active next to a creative energy cell");
                check(helper, CertaintyCoverage.withinAnchorField(helper.getLevel(),
                    helper.absolutePos(ANCHOR_POS)), "anchor position must be covered");
                check(helper, CertaintyCoverage.withinAnchorField(helper.getLevel(),
                    helper.absolutePos(new BlockPos(1, 1, 0))), "adjacent position must be covered");
            })
            .thenSucceed();
    }

    @GameTest(template = "anchor_field")
    public static void certusAnchorFieldRespectsRadius(final GameTestHelper helper) {
        helper.setBlock(ANCHOR_POS, TheTruthBlocks.CERTUS_ANCHOR.get());
        helper.setBlock(CELL_POS, creativeCell(helper));
        helper.startSequence()
            .thenIdle(60)
            .thenExecute(() -> {
                final var anchor = (CertusAnchorBlockEntity) helper.getBlockEntity(ANCHOR_POS);
                // Default range is 16 blocks; x offset 16 from the anchor is
                // inside, 17 is outside. The boundary is asserted against this
                // anchor alone: the global registry spans every GameTest plot,
                // so a neighbouring plot's powered anchor can legally cover the
                // far probe position (M3 isolation trap, AGENTS.md).
                check(helper, CertaintyCoverage.isPosCoveredByAnchor(anchor,
                        helper.absolutePos(new BlockPos(17, 1, 1))), "distance 16 must be covered");
                check(helper, !CertaintyCoverage.isPosCoveredByAnchor(anchor,
                        helper.absolutePos(new BlockPos(18, 1, 1))), "distance 17 must not be covered");
                check(helper, CertaintyCoverage.withinAnchorField(helper.getLevel(),
                        helper.absolutePos(new BlockPos(17, 1, 1))),
                    "the registry path must still report coverage inside the field");
            })
            .thenSucceed();
    }

    @GameTest(template = "smoke")
    public static void certusAnchorPairAutoConnects(final GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), TheTruthBlocks.CERTUS_ANCHOR.get());
        helper.setBlock(new BlockPos(1, 1, 2), TheTruthBlocks.CERTUS_ANCHOR.get());
        helper.startSequence()
            .thenIdle(40)
            .thenExecute(() -> {
                final var a = (CertusAnchorBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 1));
                final var b = (CertusAnchorBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 2));
                check(helper, a.getActionableNode().getConnections().size() >= 1,
                    "two adjacent anchors must auto-connect");
                check(helper, b.getActionableNode().getConnections().size() >= 1,
                    "both ends of the connection must see it");
            })
            .thenSucceed();
    }

    /** AE2 creative cell by registry id; fails the test when AE2 did not load. */
    private static Block creativeCell(final GameTestHelper helper) {
        final Block cell = BuiltInRegistries.BLOCK.get(
            ResourceLocation.parse("ae2:creative_energy_cell"));
        check(helper, !cell.defaultBlockState().isAir(), "ae2:creative_energy_cell must exist");
        return cell;
    }

    private static void check(final GameTestHelper helper, final boolean condition, final String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
