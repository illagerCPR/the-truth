package io.github.illagercpr.thetruth.gametest;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.StorageCell;
import appeng.api.storage.StorageCells;
import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.blockentity.CertusSolidifierBlockEntity;
import io.github.illagercpr.thetruth.blockentity.UmbilicalAnchorBlockEntity;
import io.github.illagercpr.thetruth.coverage.UmbilicalNetwork;
import io.github.illagercpr.thetruth.registry.TheTruthBlocks;
import io.github.illagercpr.thetruth.registry.TheTruthDataComponents;
import io.github.illagercpr.thetruth.registry.TheTruthItems;
import io.github.illagercpr.thetruth.storage.CertusCellInventory;
import io.github.illagercpr.thetruth.worldgen.CertusTerrainShape;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M5 mechanics tests: residual cluster distribution (pure shape function),
 * the solidifier's restore/solidify paths behind AE power, the Certus Cell
 * ledger round-trip with offline persistence, and the umbilical pair verdict.
 * The global umbilical pair-count limit is deliberately not asserted here
 * (per-server state, cross-plot pollution — docs/02 M5 decision 9).
 */
@GameTestHolder(TheTruth.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheTruthStorageTests {

    private static final BlockPos SOLIDIFIER_POS = new BlockPos(1, 1, 1);
    private static final BlockPos POWER_POS = new BlockPos(1, 1, 2);
    private static final BlockPos ANCHOR_A_POS = new BlockPos(1, 1, 1);
    private static final BlockPos ANCHOR_B_POS = new BlockPos(1, 1, 2);
    private static final BlockPos ANCHOR_POWER_POS = new BlockPos(1, 1, 3);

    private TheTruthStorageTests() {
    }

    // ------------------------------------------------- residual clusters

    @GameTest(template = "smoke")
    public static void residualClusterDistributionIsDeterministic(final GameTestHelper helper) {
        final CertusTerrainShape shape = shape(helper);
        for (int x = -2000; x <= 2000; x += 7) {
            for (int z = -2000; z <= 2000; z += 7) {
                if (shape.isGridGap(x, z) && shape.isResidualClusterColumn(x, z)) {
                    helper.fail("a residual cluster must never sit inside a grid gap at " + x + "," + z);
                    return;
                }
            }
        }
        long candidates = 0;
        long clusters = 0;
        long firstClusterX = Long.MAX_VALUE;
        long firstClusterZ = Long.MAX_VALUE;
        int firstClusterY = Integer.MIN_VALUE;
        for (int x = -6000; x <= 6000; x += 3) {
            for (int z = -6000; z <= 6000; z += 3) {
                if (shape.isGridGap(x, z)) {
                    continue;
                }
                candidates++;
                if (shape.isResidualClusterColumn(x, z)) {
                    clusters++;
                    if (clusters == 1) {
                        firstClusterX = x;
                        firstClusterZ = z;
                        firstClusterY = shape.residualClusterY(x, z);
                    }
                }
            }
        }
        check(helper, candidates > 1_000_000, "sample must be large, was " + candidates);
        check(helper, clusters > 0, "no residual cluster found in the sample; the deposit would never generate");
        final double frequency = clusters / (double) candidates;
        check(helper, frequency > 0.002 && frequency < 0.01,
            "cluster frequency must sit near 0.005, was " + frequency);
        check(helper, firstClusterY == shape.sedimentTop((int) firstClusterX, (int) firstClusterZ) + 1,
            "cluster must rest one block above the sediment top");
        helper.succeed();
    }

    @GameTest(template = "smoke")
    public static void residualMatterDropsUnformedMatter(final GameTestHelper helper) {
        // GameTestHelper.destroyBlock only swaps to air (no loot); evaluate the
        // block's loot table the way the loot engine would instead.
        final var lootTable = helper.getLevel().getServer().reloadableRegistries()
            .getLootTable(TheTruthBlocks.RESIDUAL_MATTER.get().getLootTable());
        final LootParams params = new LootParams.Builder(helper.getLevel())
            .withParameter(LootContextParams.ORIGIN,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
            .withParameter(LootContextParams.BLOCK_STATE,
                TheTruthBlocks.RESIDUAL_MATTER.get().defaultBlockState())
            .withParameter(LootContextParams.TOOL, new ItemStack(Items.DIAMOND_PICKAXE))
            .withOptionalParameter(LootContextParams.THIS_ENTITY, null)
            .create(LootContextParamSets.BLOCK);
        final List<ItemStack> drops = lootTable.getRandomItems(params);
        check(helper, !drops.isEmpty(), "mining a residual cluster must drop unformed matter");
        for (final ItemStack drop : drops) {
            check(helper, drop.is(TheTruthItems.UNFORMED_MATTER.get()),
                "the cluster must only drop unformed matter, got " + drop);
            check(helper, drop.getCount() >= 1 && drop.getCount() <= 3,
                "drop count must stay in the 1..3 initial range, was " + drop.getCount());
        }
        helper.succeed();
    }

    // ------------------------------------------------------- solidifier

    @GameTest(template = "smoke")
    public static void solidifierRestoresRecordedContent(final GameTestHelper helper) {
        helper.setBlock(SOLIDIFIER_POS, TheTruthBlocks.CERTUS_SOLIDIFIER.get());
        helper.setBlock(POWER_POS, creativeCell(helper));
        final ItemStack unformed = new ItemStack(TheTruthItems.UNFORMED_MATTER.get());
        io.github.illagercpr.thetruth.item.UnformedMatterItem.setContent(unformed, new ItemStack(Items.DIAMOND, 5));
        helper.startSequence()
            .thenIdle(40)
            .thenExecute(() -> {
                final CertusSolidifierBlockEntity solidifier =
                    (CertusSolidifierBlockEntity) helper.getBlockEntity(SOLIDIFIER_POS);
                check(helper, solidifier.insertInput(unformed).isEmpty(), "input slot must accept unformed matter");
            })
            .thenIdle(45)
            .thenExecute(() -> {
                final CertusSolidifierBlockEntity solidifier =
                    (CertusSolidifierBlockEntity) helper.getBlockEntity(SOLIDIFIER_POS);
                final ItemStack out = solidifier.extractOutput();
                check(helper, out.is(Items.DIAMOND) && out.getCount() == 5,
                    "the recorded content must be restored, got " + out);
            })
            .thenSucceed();
    }

    @GameTest(template = "smoke")
    public static void solidifierWritesFreeMatterIntoMatrix(final GameTestHelper helper) {
        helper.setBlock(SOLIDIFIER_POS, TheTruthBlocks.CERTUS_SOLIDIFIER.get());
        helper.setBlock(POWER_POS, creativeCell(helper));
        helper.startSequence()
            .thenIdle(40)
            .thenExecute(() -> {
                final CertusSolidifierBlockEntity solidifier =
                    (CertusSolidifierBlockEntity) helper.getBlockEntity(SOLIDIFIER_POS);
                check(helper, solidifier.insertInput(new ItemStack(TheTruthItems.UNFORMED_MATTER.get())).isEmpty(),
                    "free unformed matter must be accepted");
            })
            .thenIdle(45)
            .thenExecute(() -> {
                final CertusSolidifierBlockEntity solidifier =
                    (CertusSolidifierBlockEntity) helper.getBlockEntity(SOLIDIFIER_POS);
                final ItemStack out = solidifier.extractOutput();
                check(helper, out.is(TheTruthItems.CERTUS_MATRIX.get()) && out.getCount() == 1,
                    "free matter must be written into one Certus Matrix, got " + out);
            })
            .thenSucceed();
    }

    @GameTest(template = "smoke")
    public static void solidifierRefusesToWorkWithoutPower(final GameTestHelper helper) {
        helper.setBlock(SOLIDIFIER_POS, TheTruthBlocks.CERTUS_SOLIDIFIER.get());
        helper.startSequence()
            .thenIdle(40)
            .thenExecute(() -> {
                final CertusSolidifierBlockEntity solidifier =
                    (CertusSolidifierBlockEntity) helper.getBlockEntity(SOLIDIFIER_POS);
                check(helper, solidifier.insertInput(new ItemStack(TheTruthItems.UNFORMED_MATTER.get())).isEmpty(),
                    "input must still be accepted without power");
            })
            .thenIdle(60)
            .thenExecute(() -> {
                final CertusSolidifierBlockEntity solidifier =
                    (CertusSolidifierBlockEntity) helper.getBlockEntity(SOLIDIFIER_POS);
                check(helper, solidifier.extractOutput().isEmpty(),
                    "no writing may happen without AE power (red line 3)");
            })
            .thenSucceed();
    }

    // ------------------------------------------------------- certus cell

    @GameTest(template = "smoke")
    public static void certusCellLedgerRoundTrip(final GameTestHelper helper) {
        check(helper, StorageCells.isCellHandled(new ItemStack(TheTruthItems.CERTUS_CELL.get())),
            "the AE2 drive registry must recognize the Certus Cell");
        final ItemStack host = new ItemStack(TheTruthItems.CERTUS_CELL.get());
        final CertusCellInventory ledger = CertusCellInventory.of(host);
        final AEItemKey diamonds = AEItemKey.of(new ItemStack(Items.DIAMOND));
        final long inserted = ledger.insert(diamonds, 128, appeng.api.config.Actionable.MODULATE, IActionSource.empty());
        check(helper, inserted == 128, "insert must accept the full amount, got " + inserted);
        check(helper, ledger.getAvailableStacks().get(diamonds) == 128, "the ledger must expose the contents");
        final long simulated = ledger.extract(diamonds, 100, appeng.api.config.Actionable.SIMULATE, IActionSource.empty());
        check(helper, simulated == 100, "simulate must not consume");
        check(helper, ledger.getAvailableStacks().get(diamonds) == 128, "simulate must leave the ledger intact");
        final long extracted = ledger.extract(diamonds, 100, appeng.api.config.Actionable.MODULATE, IActionSource.empty());
        check(helper, extracted == 100, "extract must hand out the requested amount");
        check(helper, ledger.getAvailableStacks().get(diamonds) == 28, "28 must remain after extraction");
        helper.succeed();
    }

    @GameTest(template = "smoke")
    public static void certusCellRejectsItselfAndPersistsOffline(final GameTestHelper helper) {
        final ItemStack host = new ItemStack(TheTruthItems.CERTUS_CELL.get());
        final CertusCellInventory ledger = CertusCellInventory.of(host);
        final AEItemKey self = AEItemKey.of(new ItemStack(TheTruthItems.CERTUS_CELL.get()));
        check(helper, ledger.insert(self, 1, appeng.api.config.Actionable.MODULATE, IActionSource.empty()) == 0,
            "a Certus Cell must never accept another Certus Cell (no recursive paging)");
        final AEItemKey sticks = AEItemKey.of(new ItemStack(Items.STICK));
        check(helper, ledger.insert(sticks, 5000, appeng.api.config.Actionable.MODULATE, IActionSource.empty()) == 5000,
            "normal stacks must insert");
        ledger.persist();
        final List<GenericStack> stored = host.get(TheTruthDataComponents.CERTUS_CELL_CONTENT.get());
        check(helper, stored != null && stored.size() == 1,
            "the ledger must be persisted into the host item");
        // Offline persistence: a fresh inventory over the same item sees the data.
        final CertusCellInventory reloaded = CertusCellInventory.of(host);
        check(helper, reloaded.storedTypes() == 1, "contents must survive being taken off the network");
        check(helper, reloaded.extract(sticks, 5000, appeng.api.config.Actionable.MODULATE, IActionSource.empty()) == 5000,
            "reattaching (fresh ledger over the same item) must restore access");
        helper.succeed();
    }

    // -------------------------------------------------- umbilical anchors

    @GameTest(template = "smoke")
    public static void umbilicalPairProjectsCoverage(final GameTestHelper helper) {
        helper.setBlock(ANCHOR_A_POS, TheTruthBlocks.UMBILICAL_ANCHOR.get());
        helper.setBlock(ANCHOR_B_POS, TheTruthBlocks.UMBILICAL_ANCHOR.get());
        helper.setBlock(ANCHOR_POWER_POS, creativeCell(helper));
        final UUID pairId = UUID.randomUUID();
        helper.startSequence()
            .thenIdle(40)
            .thenExecute(() -> {
                final UmbilicalAnchorBlockEntity a =
                    (UmbilicalAnchorBlockEntity) helper.getBlockEntity(ANCHOR_A_POS);
                final UmbilicalAnchorBlockEntity b =
                    (UmbilicalAnchorBlockEntity) helper.getBlockEntity(ANCHOR_B_POS);
                check(helper, a.isNodeActive() && b.isNodeActive(),
                    "both anchors must be online next to the energy cell");
                a.bindPair(pairId);
                b.bindPair(pairId);
            })
            .thenIdle(5)
            .thenExecute(() -> {
                final UmbilicalAnchorBlockEntity a =
                    (UmbilicalAnchorBlockEntity) helper.getBlockEntity(ANCHOR_A_POS);
                check(helper, UmbilicalNetwork.isFieldActive(a),
                    "a paired, powered anchor with a live peer must be active");
                check(helper, UmbilicalNetwork.peerOf(a)
                        == helper.getBlockEntity(ANCHOR_B_POS),
                    "the peer lookup must find the other end of the pair");
                check(helper, UmbilicalNetwork.isPosCovered(helper.getLevel(),
                        helper.absolutePos(ANCHOR_A_POS)),
                    "the active cord must project deterministic coverage");
            })
            .thenSucceed();
    }

    @GameTest(template = "smoke")
    public static void umbilicalUnboundAnchorStaysSilent(final GameTestHelper helper) {
        helper.setBlock(ANCHOR_A_POS, TheTruthBlocks.UMBILICAL_ANCHOR.get());
        helper.setBlock(ANCHOR_B_POS, creativeCell(helper));
        helper.startSequence()
            .thenIdle(40)
            .thenExecute(() -> {
                final UmbilicalAnchorBlockEntity a =
                    (UmbilicalAnchorBlockEntity) helper.getBlockEntity(ANCHOR_A_POS);
                check(helper, a.isNodeActive(), "the node must be online even without a pair");
                check(helper, !UmbilicalNetwork.isFieldActive(a),
                    "an unbound anchor must never project coverage");
            })
            .thenSucceed();
    }

    @GameTest(template = "smoke")
    public static void umbilicalFullPairReportsPairFullVerdict(final GameTestHelper helper) {
        helper.setBlock(ANCHOR_A_POS, TheTruthBlocks.UMBILICAL_ANCHOR.get());
        helper.setBlock(ANCHOR_B_POS, TheTruthBlocks.UMBILICAL_ANCHOR.get());
        helper.setBlock(ANCHOR_POWER_POS, creativeCell(helper));
        final UUID pairId = UUID.randomUUID();
        helper.startSequence()
            .thenIdle(40)
            .thenExecute(() -> {
                final UmbilicalAnchorBlockEntity a =
                    (UmbilicalAnchorBlockEntity) helper.getBlockEntity(ANCHOR_A_POS);
                final UmbilicalAnchorBlockEntity b =
                    (UmbilicalAnchorBlockEntity) helper.getBlockEntity(ANCHOR_B_POS);
                a.bindPair(pairId);
                b.bindPair(pairId);
            })
            .thenIdle(1)
            .thenExecute(() -> {
                check(helper, UmbilicalNetwork.checkBind(pairId, helper.getLevel())
                        == UmbilicalNetwork.BindVerdict.PAIR_FULL,
                    "a pair with both anchors bound must report PAIR_FULL, not a global limit");
            })
            .thenSucceed();
    }

    // ------------------------------------------------------------ helpers

    /** AE2 creative cell by registry id; fails the test when AE2 did not load. */
    private static Block creativeCell(final GameTestHelper helper) {
        final Block cell = BuiltInRegistries.BLOCK.get(
            ResourceLocation.parse("ae2:creative_energy_cell"));
        check(helper, !cell.defaultBlockState().isAir(), "ae2:creative_energy_cell must exist");
        return cell;
    }

    private static CertusTerrainShape shape(final GameTestHelper helper) {
        final var access = helper.getLevel().getServer().registryAccess();
        final var noiseParameters = access.registryOrThrow(Registries.NOISE);
        final NormalNoise terrain = NormalNoise.create(
            RandomSource.create(42L), noiseParameters.get(CertusTerrainShape.CERTUS_TERRAIN_NOISE));
        final NormalNoise islands = NormalNoise.create(
            RandomSource.create(42L), noiseParameters.get(CertusTerrainShape.CERTUS_ISLANDS_NOISE));
        return new CertusTerrainShape(terrain, islands);
    }

    private static void check(final GameTestHelper helper, final boolean condition, final String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
