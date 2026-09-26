package io.github.illagercpr.thetruth.gametest;

import java.util.List;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.blockentity.QuantumEntranceBlockEntity;
import io.github.illagercpr.thetruth.event.TheTruthRemnantHandlers;
import io.github.illagercpr.thetruth.registry.TheTruthBlocks;
import io.github.illagercpr.thetruth.registry.TheTruthDataComponents;
import io.github.illagercpr.thetruth.registry.TheTruthItems;
import io.github.illagercpr.thetruth.transport.EntanglementPairData;
import io.github.illagercpr.thetruth.transport.QuantumTransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M4 entrance tests: ring geometry, the binding ritual and its spatial-IO
 * infrastructure gate, key pair validation for the outbound jump, and the
 * data-remnant lifetime guarantee. The actual cross-dimension jump is verified
 * manually (mock players do not survive a real changeDimension); the jump
 * itself only composes DimensionTransition, whose signature was javap-verified
 * (docs/01).
 *
 * <p>The spatial gate is exercised with real AE2 blocks (pylons, cable, port,
 * power), never with storage doubles: a spatial storage cell can never appear
 * in network storage in a real world, so injecting one through a test double
 * used to verify an unreachable state (AGENTS.md, M4 fix).
 */
@GameTestHolder(TheTruth.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheTruthEntranceTests {

    /** Core position inside the 5x4x5 quantum_ring template. */
    private static final BlockPos CORE_POS = new BlockPos(2, 1, 2);
    /** The cavity above the core doubles as the energy-cell seat: it is part of
     * no ring edge, so the cell never breaks the structure probe. */
    private static final BlockPos CELL_POS = CORE_POS.above();

    private TheTruthEntranceTests() {
    }

    @GameTest(template = "quantum_ring")
    public static void quantumRingFormsAndDetectsMissingFrame(final GameTestHelper helper) {
        placeRing(helper);
        helper.setBlock(CORE_POS, TheTruthBlocks.QUANTUM_ENTRANCE.get());
        helper.startSequence()
            .thenIdle(5)
            .thenExecute(() -> {
                final QuantumEntranceBlockEntity core = coreOf(helper);
                check(helper, core.isStructureFormed(), "ring must be formed");
                check(helper, helper.getBlockState(CELL_POS).isAir(),
                    "the cavity must stay open in a pure ring");
            })
            // Break one frame and the verdict must flip after a re-probe.
            .thenExecute(() -> helper.setBlock(new BlockPos(1, 1, 2), Blocks.AIR))
            .thenExecute(() -> {
                final QuantumEntranceBlockEntity core = coreOf(helper);
                core.markStructureDirty();
                check(helper, !core.isStructureFormed(), "ring must fail with a missing frame");
            })
            .thenSucceed();
    }

    @GameTest(template = "quantum_ring")
    public static void bindingRequiresSpatialIoInfrastructure(final GameTestHelper helper) {
        final ItemStack key = preparePoweredRing(helper);
        helper.startSequence()
            .thenIdle(40)
            .thenExecute(() -> {
                final QuantumEntranceBlockEntity core = coreOf(helper);
                final var failure = QuantumTransport.validateBinding(core, key, helper.getLevel());
                check(helper, failure == QuantumTransport.Failure.NO_SPATIAL_IO,
                    "binding without spatial IO infrastructure must be refused, got " + failure);
            })
            // Real blocks only: AE2's own minimal 1x1x1 pylon assembly plus a
            // spatial IO port, bridged into the core's grid. The pylons need a
            // few ticks to form their clusters and report a valid region.
            .thenExecute(() -> placeSpatialInfrastructure(helper))
            .thenIdle(40)
            .thenExecute(() -> {
                final QuantumEntranceBlockEntity core = coreOf(helper);
                assertSpatialInfrastructureOnline(helper, core);
                final var failure = QuantumTransport.validateBinding(core, key, helper.getLevel());
                check(helper, failure == null,
                    "binding with a port and a valid pylon array must pass, got " + failure);
            })
            .thenSucceed();
    }

    @GameTest(template = "quantum_ring")
    public static void performBindingWritesPairData(final GameTestHelper helper) {
        final ItemStack key = preparePoweredRing(helper);
        placeSpatialInfrastructure(helper);
        final ServerPlayer mock = helper.makeMockServerPlayerInLevel();
        helper.startSequence()
            .thenIdle(40)
            .thenExecute(() -> {
                final QuantumEntranceBlockEntity core = coreOf(helper);
                assertSpatialInfrastructureOnline(helper, core);
                final var failure = QuantumTransport.performBinding(mock, core, key);
                check(helper, failure == null, "binding must succeed, got " + failure);
            })
            .thenExecute(() -> {
                final QuantumEntranceBlockEntity core = coreOf(helper);
                final EntanglementPairData pair = key.get(TheTruthDataComponents.ENTANGLEMENT_PAIR.get());
                check(helper, pair != null,
                    "the held key must carry pair data (corePair=" + core.getPairId() + ")");
                check(helper, core.getPairId() != null && core.getPairId().equals(pair.pairId()),
                    "the core must record the same pair id");
                // No second key is minted: the pair is core <-> held key.
                int boundKeys = 0;
                for (int i = 0; i < mock.getInventory().getContainerSize(); i++) {
                    final ItemStack stack = mock.getInventory().getItem(i);
                    if (!stack.isEmpty()
                        && stack.getItem() instanceof io.github.illagercpr.thetruth.item.EntanglementKeyItem
                        && stack.get(TheTruthDataComponents.ENTANGLEMENT_PAIR.get()) != null) {
                        boundKeys++;
                    }
                }
                check(helper, boundKeys == 0,
                    "no additional key must be handed over, got " + boundKeys);
            })
            .thenSucceed();
    }

    @GameTest(template = "quantum_ring")
    public static void outboundRequiresMatchingPair(final GameTestHelper helper) {
        final ItemStack key = preparePoweredRing(helper);
        placeSpatialInfrastructure(helper);
        helper.startSequence()
            .thenIdle(40)
            .thenExecute(() -> {
                final QuantumEntranceBlockEntity core = coreOf(helper);
                assertSpatialInfrastructureOnline(helper, core);
                final ServerPlayer mock = helper.makeMockServerPlayerInLevel();
                check(helper, QuantumTransport.performBinding(mock, core, key) == null,
                    "binding must succeed before the outbound check");
            })
            .thenExecute(() -> {
                final QuantumEntranceBlockEntity core = coreOf(helper);
                // An unpaired key is not a ticket...
                final ItemStack unpaired = new ItemStack(TheTruthItems.ENTANGLEMENT_KEY.get());
                check(helper, QuantumTransport.validateOutbound(core, unpaired)
                        == QuantumTransport.Failure.NO_KEY,
                    "an unpaired key must be refused outbound");
                // ...and a foreign pair is refused as well.
                final ItemStack foreign = new ItemStack(TheTruthItems.ENTANGLEMENT_KEY.get());
                foreign.set(TheTruthDataComponents.ENTANGLEMENT_PAIR.get(),
                    new EntanglementPairData(java.util.UUID.randomUUID(),
                        ResourceLocation.withDefaultNamespace("overworld"), CORE_POS));
                check(helper, QuantumTransport.validateOutbound(core, foreign)
                        == QuantumTransport.Failure.KEY_MISMATCH,
                    "a foreign pair must be refused outbound");
                // The right pair validates (no teleport is attempted here).
                check(helper, QuantumTransport.validateOutbound(core, key) == null,
                    "the bound key must pass outbound validation");
            })
            .thenSucceed();
    }

    @GameTest(template = "smoke")
    public static void dataRemnantsNeverDespawn(final GameTestHelper helper) {
        final ItemEntity drop = new ItemEntity(helper.getLevel(), 0.5, 1.0, 0.5,
            new ItemStack(Items.DIAMOND));
        TheTruthRemnantHandlers.markAsDataRemnant(drop);
        check(helper, drop.lifespan == Integer.MAX_VALUE,
            "a data remnant must have an unlimited lifetime");
        check(helper, drop.getCustomName() != null, "a data remnant must be named");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Places the 7 frames of an X-aligned ring around the (unplaced) core. */
    private static void placeRing(final GameTestHelper helper) {
        for (final BlockPos offset : List.of(
            new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0),
            new BlockPos(-1, 1, 0), new BlockPos(1, 1, 0),
            new BlockPos(-1, 2, 0), new BlockPos(0, 2, 0), new BlockPos(1, 2, 0))) {
            helper.setBlock(CORE_POS.offset(offset), TheTruthBlocks.CERTUS_FRAME.get());
        }
    }

    /** Ring + energy cell; returns a fresh unpaired key for the ritual. */
    private static ItemStack preparePoweredRing(final GameTestHelper helper) {
        placeRing(helper);
        helper.setBlock(CORE_POS, TheTruthBlocks.QUANTUM_ENTRANCE.get());
        helper.setBlock(CELL_POS, creativeCell(helper));
        return new ItemStack(TheTruthItems.ENTANGLEMENT_KEY.get());
    }

    /**
     * AE2 spatial IO infrastructure built from real blocks, cable-free: every
     * device joins the core's grid by direct adjacency. (Cables are avoided
     * because helper.setBlock applies the default blockstate and skips the
     * connection calculation, leaving ae2:cable_bus connected to nothing.)
     *
     * <p>Layout inside the 7x4x7 template (ring at x1..3/y1..3/z2, core at
     * 2,1,2, cell at 2,2,2):
     * <ul>
     *   <li>Pylon A (z-run, 3 blocks): (2,0,0) (2,0,1) (2,0,2) — tail touches
     *       the core at (2,1,2).</li>
     *   <li>Spatial IO port: (2,0,3) — touches pylon A's tail.</li>
     *   <li>Pylon B (y-run, 2 blocks): (2,1,3) (2,2,3) — head touches the port
     *       and the core; tail touches the energy cell.</li>
     *   <li>Pylon C (x-run, 2 blocks): (3,0,3) (4,0,3) — head touches the
     *       port.</li>
     * </ul>
     * Three independent straight pylon clusters bound a 3x3x4 shell
     * (x2..4, y0..2, z0..3), which contracts to a valid 1x1x2 region.
     */
    private static void placeSpatialInfrastructure(final GameTestHelper helper) {
        final Block pylon = ae2Block(helper, "ae2:spatial_pylon");
        final Block port = ae2Block(helper, "ae2:spatial_io_port");
        // Pylon A (z-run): the tail sits directly under the core.
        helper.setBlock(new BlockPos(2, 0, 0), pylon);
        helper.setBlock(new BlockPos(2, 0, 1), pylon);
        helper.setBlock(new BlockPos(2, 0, 2), pylon);
        // Port bridges from pylon A's tail to pylons B and C.
        helper.setBlock(new BlockPos(2, 0, 3), port);
        // Pylon B (y-run).
        helper.setBlock(new BlockPos(2, 1, 3), pylon);
        helper.setBlock(new BlockPos(2, 2, 3), pylon);
        // Pylon C (x-run).
        helper.setBlock(new BlockPos(3, 0, 3), pylon);
        helper.setBlock(new BlockPos(4, 0, 3), pylon);
    }

    /** Fails the test unless the port is on the grid and the region is valid. */
    private static void assertSpatialInfrastructureOnline(final GameTestHelper helper,
                                                          final QuantumEntranceBlockEntity core) {
        final var node = core.getMainNode().getNode();
        check(helper, node != null, "the core must have a grid node");
        final IGrid grid = node.getGrid();
        check(helper, grid != null, "the core must be on a grid");
        boolean portInGrid = false;
        int pylonCount = 0;
        for (final IGridNode gridNode : grid.getNodes()) {
            if (gridNode.getOwner() instanceof BlockEntity be) {
                final var blockId = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock());
                if (blockId.equals(ResourceLocation.parse("ae2:spatial_io_port"))) {
                    portInGrid = true;
                } else if (blockId.equals(ResourceLocation.parse("ae2:spatial_pylon"))) {
                    pylonCount++;
                }
            }
        }
        final var spatial = grid.getSpatialService();
        final String diagnostics = "nodes=" + grid.size()
            + " pylonsInGrid=" + pylonCount
            + " portInNet=" + portInGrid
            + " hasRegion=" + spatial.hasRegion()
            + " regionValid=" + spatial.isValidRegion()
            + " min=" + spatial.getMin() + " max=" + spatial.getMax();
        check(helper, pylonCount == 7, "all 7 pylon blocks must join the grid: " + diagnostics);
        check(helper, portInGrid, "the spatial IO port must join the core's grid: " + diagnostics);
        check(helper, spatial.isValidRegion(),
            "the pylon array must bound a valid region: " + diagnostics);
    }

    private static QuantumEntranceBlockEntity coreOf(final GameTestHelper helper) {
        final var be = helper.getBlockEntity(CORE_POS);
        check(helper, be instanceof QuantumEntranceBlockEntity,
            "quantum entrance block entity must exist");
        return (QuantumEntranceBlockEntity) be;
    }

    /** The AE2 creative cell, resolved through the block registry like M3. */
    private static Block creativeCell(final GameTestHelper helper) {
        return ae2Block(helper, "ae2:creative_energy_cell");
    }

    /** An AE2 block resolved by registry id (no AE2 internals imported). */
    private static Block ae2Block(final GameTestHelper helper, final String id) {
        final Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        check(helper, !block.defaultBlockState().isAir(), id + " must exist");
        return block;
    }

    private static void check(final GameTestHelper helper, final boolean condition, final String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
