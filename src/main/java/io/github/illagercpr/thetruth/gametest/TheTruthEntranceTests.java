package io.github.illagercpr.thetruth.gametest;

import java.util.List;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M4 entrance tests: ring geometry, the binding ritual and its spatial-cell
 * gate, key pair validation for the outbound jump, and the data-remnant
 * lifetime guarantee. The actual cross-dimension jump is verified manually
 * (mock players do not survive a real changeDimension); the jump itself only
 * composes DimensionTransition, whose signature was javap-verified (docs/01).
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
    public static void bindingRequiresSpatialCellInNetwork(final GameTestHelper helper) {
        final ItemStack key = preparePoweredRing(helper);
        helper.startSequence()
            .thenIdle(40)
            .thenExecute(() -> {
                final QuantumEntranceBlockEntity core = coreOf(helper);
                final var failure = QuantumTransport.validateBinding(core, key, helper.getLevel());
                check(helper, failure == QuantumTransport.Failure.NO_SPATIAL_CELL,
                    "binding without a spatial cell must be refused, got " + failure);
            })
            // Deliver the spatial cell into the network via a test-owned storage
            // provider (pure API; mounts synchronously), then the gate opens.
            .thenExecute(() -> {
                final QuantumEntranceBlockEntity core = coreOf(helper);
                final IGrid grid = core.getMainNode().getNode().getGrid();
                check(helper, grid != null, "grid must exist with a powered cell");
                final TestStorageProvider provider = new TestStorageProvider();
                grid.getStorageService().addGlobalStorageProvider(provider);
                final long inserted = grid.getStorageService().getInventory()
                    .insert(AEItemKey.of(spatialCellItem(helper)), 1, Actionable.MODULATE,
                        IActionSource.empty());
                final var available = new StringBuilder();
                for (final var entry : grid.getStorageService().getInventory().getAvailableStacks()) {
                    available.append(entry.getKey()).append(" x").append(entry.getLongValue()).append("; ");
                }
                check(helper, inserted == 1,
                    "spatial cell insert returned " + inserted + "; available: " + available);
            })
            .thenExecute(() -> {
                final QuantumEntranceBlockEntity core = coreOf(helper);
                final var failure = QuantumTransport.validateBinding(core, key, helper.getLevel());
                check(helper, failure == null,
                    "binding with a spatial cell must pass, got " + failure);
            })
            .thenSucceed();
    }

    @GameTest(template = "quantum_ring")
    public static void performBindingWritesPairData(final GameTestHelper helper) {
        final ItemStack key = preparePoweredRing(helper);
        final ServerPlayer mock = helper.makeMockServerPlayerInLevel();
        helper.startSequence()
            .thenIdle(40)
            .thenExecute(() -> {
                final QuantumEntranceBlockEntity core = coreOf(helper);
                final IGrid grid = core.getMainNode().getNode().getGrid();
                grid.getStorageService().addGlobalStorageProvider(new TestStorageProvider());
                grid.getStorageService().getInventory()
                    .insert(AEItemKey.of(spatialCellItem(helper)), 1, Actionable.MODULATE,
                        IActionSource.empty());
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
                // The second key of the pair went to the mock player's inventory.
                int boundKeys = 0;
                for (int i = 0; i < mock.getInventory().getContainerSize(); i++) {
                    final ItemStack stack = mock.getInventory().getItem(i);
                    if (!stack.isEmpty()
                        && stack.getItem() instanceof io.github.illagercpr.thetruth.item.EntanglementKeyItem
                        && stack.get(TheTruthDataComponents.ENTANGLEMENT_PAIR.get()) != null) {
                        boundKeys++;
                    }
                }
                check(helper, boundKeys == 1,
                    "exactly one additional bound key must be handed over, got " + boundKeys);
            })
            .thenSucceed();
    }

    @GameTest(template = "quantum_ring")
    public static void outboundRequiresMatchingPair(final GameTestHelper helper) {
        final ItemStack key = preparePoweredRing(helper);
        helper.startSequence()
            .thenIdle(40)
            .thenExecute(() -> {
                final QuantumEntranceBlockEntity core = coreOf(helper);
                final IGrid grid = core.getMainNode().getNode().getGrid();
                grid.getStorageService().addGlobalStorageProvider(new TestStorageProvider());
                grid.getStorageService().getInventory()
                    .insert(AEItemKey.of(spatialCellItem(helper)), 1, Actionable.MODULATE,
                        IActionSource.empty());
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

    private static QuantumEntranceBlockEntity coreOf(final GameTestHelper helper) {
        final var be = helper.getBlockEntity(CORE_POS);
        check(helper, be instanceof QuantumEntranceBlockEntity,
            "quantum entrance block entity must exist");
        return (QuantumEntranceBlockEntity) be;
    }

    /** The AE2 creative cell, resolved through the block registry like M3. */
    private static Block creativeCell(final GameTestHelper helper) {
        final Block cell = BuiltInRegistries.BLOCK.get(
            ResourceLocation.parse("ae2:creative_energy_cell"));
        check(helper, !cell.defaultBlockState().isAir(), "ae2:creative_energy_cell must exist");
        return cell;
    }

    /** The AE2 spatial storage cell (2^3), resolved by id. */
    private static Item spatialCellItem(final GameTestHelper helper) {
        final Item cell = BuiltInRegistries.ITEM.get(
            ResourceLocation.parse("ae2:spatial_storage_cell_2"));
        check(helper, cell != Items.AIR, "ae2:spatial_storage_cell_2 must exist");
        return cell;
    }

    /**
     * Test-owned network storage (pure appeng.api): accepts everything and
     * reports it back, playing the role of a filled ME drive without touching
     * AE2 internals. addGlobalStorageProvider mounts synchronously.
     */
    private static final class TestStorageProvider implements IStorageProvider {

        private final TestStorage storage = new TestStorage();

        @Override
        public void mountInventories(final IStorageMounts mounts) {
            mounts.mount(storage, IStorageMounts.DEFAULT_PRIORITY);
        }
    }

    private static final class TestStorage implements MEStorage {

        private final KeyCounter stored = new KeyCounter();

        @Override
        public long insert(final AEKey what, final long amount, final Actionable mode,
                           final IActionSource source) {
            if (mode == Actionable.SIMULATE) {
                return amount;
            }
            stored.add(what, amount);
            return amount;
        }

        @Override
        public long extract(final AEKey what, final long amount, final Actionable mode,
                            final IActionSource source) {
            final long taken = Math.min(stored.get(what), amount);
            if (mode == Actionable.MODULATE && taken > 0) {
                stored.remove(what, taken);
            }
            return taken;
        }

        @Override
        public void getAvailableStacks(final KeyCounter out) {
            out.addAll(stored);
        }

        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return net.minecraft.network.chat.Component.literal("truth-test-storage");
        }
    }

    private static void check(final GameTestHelper helper, final boolean condition, final String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
