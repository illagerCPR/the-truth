package io.github.illagercpr.thetruth.gametest;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.blockentity.CertusAnchorBlockEntity;
import io.github.illagercpr.thetruth.blockentity.DataPortBlockEntity;
import io.github.illagercpr.thetruth.coverage.ObservatoryField;
import io.github.illagercpr.thetruth.entity.LastMeasurerEntity;
import io.github.illagercpr.thetruth.entity.ResidueEntity;
import io.github.illagercpr.thetruth.registry.TheTruthBlocks;
import io.github.illagercpr.thetruth.registry.TheTruthDataComponents;
import io.github.illagercpr.thetruth.registry.TheTruthDimensions;
import io.github.illagercpr.thetruth.registry.TheTruthEntities;
import io.github.illagercpr.thetruth.registry.TheTruthItems;
import io.github.illagercpr.thetruth.storage.DataDropHandler;
import io.github.illagercpr.thetruth.worldgen.CertusTerrainShape;
import io.github.illagercpr.thetruth.worldgen.ObservatoryLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M6 mechanics tests: the boss phase thresholds, the scan's shadow/gap
 * avoidance, the data-port overload path, drop data-fication (network write
 * vs. unformed dissolve), experience data-fication, and the observatory's
 * coverage. Pure layout functions are asserted against fixed coordinates.
 */
@GameTestHolder(TheTruth.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheTruthM6Tests {

    private static final BlockPos ANCHOR_POS = new BlockPos(1, 1, 1);
    private static final BlockPos POWER_POS = new BlockPos(1, 1, 2);
    private static final BlockPos DROP_POS = new BlockPos(1, 2, 2);
    private static final BlockPos PORT_POS = new BlockPos(2, 1, 1);
    private static final BlockPos BOSS_POS = new BlockPos(1, 3, 1);

    private TheTruthM6Tests() {
    }

    private static void check(final GameTestHelper helper, final boolean condition, final String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    // ---------------------------------------------------------------- boss

    @GameTest(template = "smoke")
    public static void bossPhaseThresholdsAdvance(final GameTestHelper helper) {
        final LastMeasurerEntity boss = helper.spawn(TheTruthEntities.LAST_MEASURER.get(), BOSS_POS);
        check(helper, boss.phase() == LastMeasurerEntity.MeasurerPhase.SCAN,
            "a full-health boss must start in SCAN");
        boss.setHealth(LastMeasurerEntity.PHASE_TWO_HEALTH - 1.0F);
        check(helper, boss.phase() == LastMeasurerEntity.MeasurerPhase.SUMMON,
            "below the phase-two threshold the boss must be in SUMMON");
        boss.setHealth(LastMeasurerEntity.PHASE_THREE_HEALTH - 1.0F);
        check(helper, boss.phase() == LastMeasurerEntity.MeasurerPhase.OVERLOAD,
            "below the phase-three threshold the boss must expose data ports");
        helper.succeed();
    }

    @GameTest(template = "smoke")
    public static void bossScanStallsInShadowAndGap(final GameTestHelper helper) {
        final ServerPlayer mock = helper.makeMockServerPlayerInLevel();
        helper.startSequence()
            .thenIdle(10)
            .thenExecute(() -> {
                // Daylight plot: the mock in the open is plainly visible.
                check(helper, !io.github.illagercpr.thetruth.entity.SurveyorEntity.isTargetHidden(
                        helper.getLevel(), mock),
                    "a target in the open must be measurable");
                // A sealed box (one air cell, six solid faces) is true shadow:
                // no sky light, no block light reaches the inside.
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            helper.setBlock(new BlockPos(1 + dx, 2 + dy, 1 + dz), Blocks.STONE);
                        }
                    }
                }
                final BlockPos inside = helper.absolutePos(new BlockPos(1, 2, 1));
                mock.teleportTo(inside.getX() + 0.5D, inside.getY(), inside.getZ() + 0.5D);
            })
            .thenIdle(5)
            .thenExecute(() -> {
                final BlockPos shadowPos = mock.blockPosition();
                check(helper, helper.getLevel().getMaxLocalRawBrightness(shadowPos) <= io.github.illagercpr.thetruth.entity.SurveyorEntity.SHADOW_LIGHT,
                    "the sealed box must actually darken the plot");
                check(helper, io.github.illagercpr.thetruth.entity.SurveyorEntity.isTargetHidden(
                        helper.getLevel(), mock),
                    "a target in unobserved shadow must stall the scan");
                // Grid-gap branch: pure modular arithmetic, independent of world.
                check(helper, !CertusTerrainShape.gridGapAt(0, 0),
                    "the observatory square must never be a gap");
                check(helper, CertusTerrainShape.gridGapAt(48, 0),
                    "column 48+ of the grid cell must be a gap (outside the observatory square)");
            })
            .thenSucceed();
    }

    @GameTest(template = "smoke")
    public static void bossOverloadViaPortExtraction(final GameTestHelper helper) {
        final LastMeasurerEntity boss = helper.spawn(TheTruthEntities.LAST_MEASURER.get(), BOSS_POS);
        boss.setHealth(LastMeasurerEntity.PHASE_THREE_HEALTH - 1.0F);
        helper.setBlock(PORT_POS, TheTruthBlocks.DATA_PORT.get());
        helper.startSequence()
            .thenIdle(2)
            .thenExecute(() -> {
                final DataPortBlockEntity port =
                    (DataPortBlockEntity) helper.getBlockEntity(PORT_POS);
                port.bindBoss(boss.getUUID());
                // Simulate a full siphon cycle: measurement data extracted away.
                for (int round = 0; round < LastMeasurerEntity.OVERLOAD_THRESHOLD; round++) {
                    port.getHandler().setStackInSlot(0, new ItemStack(TheTruthItems.MEASUREMENT_DATA.get()));
                    final ItemStack taken = port.getHandler().extractItem(0, 1, false);
                    check(helper, taken.getItem() == TheTruthItems.MEASUREMENT_DATA.get(),
                        "the port must yield measurement data to extraction");
                }
                check(helper, boss.overloadProgress() >= LastMeasurerEntity.OVERLOAD_THRESHOLD,
                    "each siphoned unit must credit the boss's overload progress");
                check(helper, !boss.isAlive(),
                    "enough siphoned data must overload the boss to death");
                check(helper, helper.getLevel().getBlockState(PORT_POS).isAir(),
                    "the boss must pull its data ports back on death");
            })
            .thenSucceed();
    }

    // ------------------------------------------------------------- drops

    @GameTest(template = "smoke")
    public static void blockDropsWriteToNetworkWhenCovered(final GameTestHelper helper) {
        helper.setBlock(ANCHOR_POS, TheTruthBlocks.CERTUS_ANCHOR.get());
        helper.setBlock(POWER_POS, creativeCell(helper));
        // Real reachable storage: an AE2 drive next to the anchor, holding the cell.
        final BlockPos drivePos = new BlockPos(2, 1, 1);
        helper.setBlock(drivePos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:drive")));
        helper.startSequence()
            .thenIdle(40)
            .thenExecute(() -> {
                final var drive = helper.getLevel().getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                    helper.absolutePos(drivePos), null);
                check(helper, drive != null, "the drive must expose its cell slots");
                final ItemStack leftover = drive.insertItem(0,
                    new ItemStack(TheTruthItems.CERTUS_CELL.get()), false);
                check(helper, leftover.isEmpty(), "the drive must accept the certus cell");
                final CertusAnchorBlockEntity anchor =
                    (CertusAnchorBlockEntity) helper.getBlockEntity(ANCHOR_POS);
                check(helper, anchor.isFieldActive(), "the anchor must be powered next to the cell");
                final CertusAnchorBlockEntity anchorEntity =
                    (CertusAnchorBlockEntity) helper.getBlockEntity(ANCHOR_POS);
                final List<ItemEntity> drops = new ArrayList<>();
                final BlockPos dropAt = helper.absolutePos(DROP_POS);
                drops.add(new ItemEntity(helper.getLevel(), dropAt.getX() + 0.5D, dropAt.getY() + 0.5D,
                    dropAt.getZ() + 0.5D, new ItemStack(Items.DIAMOND, 4)));
                DataDropHandler.processDrops(helper.getLevel(),
                    helper.absolutePos(DROP_POS), drops);
                check(helper, drops.isEmpty(),
                    "covered drops must be written into the covering network");
                // The ledger must show the diamonds now.
                final var inventory = anchorEntity.getGridNode(null).getGrid()
                    .getStorageService().getInventory();
                boolean found = false;
                for (final var entry : inventory.getAvailableStacks()) {
                    if (entry.getKey() instanceof appeng.api.stacks.AEItemKey itemKey
                        && itemKey.getItem() == Items.DIAMOND && entry.getLongValue() >= 4) {
                        found = true;
                    }
                }
                check(helper, found, "the diamonds must appear in the anchor's network");
            })
            .thenSucceed();
    }

    @GameTest(template = "smoke")
    public static void blockDropsDissolveToUnformedWhenUncovered(final GameTestHelper helper) {
        final List<ItemEntity> drops = new ArrayList<>();
        final BlockPos dropAt = helper.absolutePos(DROP_POS);
        drops.add(new ItemEntity(helper.getLevel(), dropAt.getX() + 0.5D, dropAt.getY() + 0.5D,
            dropAt.getZ() + 0.5D, new ItemStack(Items.DIAMOND, 2)));
        // Direct dissolve: the uncovered branch of the M6 verdict.
        DataDropHandler.dissolveAll(helper.getLevel(), helper.absolutePos(DROP_POS), drops);
        check(helper, drops.isEmpty(), "uncovered drops must be dissolved");
        final List<ItemEntity> spawned = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
            new net.minecraft.world.phys.AABB(helper.absolutePos(DROP_POS)).inflate(2.0D));
        int unformed = 0;
        for (final ItemEntity entity : spawned) {
            final ItemStack stack = entity.getItem();
            if (stack.getItem() == TheTruthItems.UNFORMED_MATTER.get()) {
                unformed++;
                final ItemStack remembered = stack.get(TheTruthDataComponents.UNFORMED_CONTENT.get()) == null
                    ? ItemStack.EMPTY
                    : io.github.illagercpr.thetruth.item.UnformedMatterItem.getContent(stack);
                check(helper, remembered.getItem() == Items.DIAMOND,
                    "each unformed item must remember the diamond it came from");
                check(helper, entity.lifespan == Integer.MAX_VALUE,
                    "dissolved data must never despawn");
            }
        }
        check(helper, unformed == 2, "two diamonds must become two unformed items, got " + unformed);
        helper.succeed();
    }

    // ------------------------------------------------- experience data-fication

    @GameTest(template = "smoke")
    public static void xpDeathYieldsDataFragments(final GameTestHelper helper) {
        check(helper, DataDropHandler.fragmentsFor(1) == 1, "a sliver of xp must still condense");
        check(helper, DataDropHandler.fragmentsFor(8) == 1, "one fragment per full value");
        check(helper, DataDropHandler.fragmentsFor(9) == 2, "spill rounds up to a second fragment");
        check(helper, DataDropHandler.fragmentsFor(40) == 5, "larger pools scale linearly");
        helper.succeed();
    }

    // -------------------------------------------------------- observatory

    @GameTest(template = "smoke")
    public static void observatoryFieldCoversBossArena(final GameTestHelper helper) {
        check(helper, ObservatoryField.isCovered(TheTruthDimensions.CERTUS, new BlockPos(0, -40, 0)),
            "the arena platform must be covered by the observatory field");
        check(helper, ObservatoryField.isCovered(TheTruthDimensions.CERTUS, new BlockPos(24, -45, 24)),
            "the ring wall must be inside the field square");
        check(helper, !ObservatoryField.isCovered(TheTruthDimensions.CERTUS, new BlockPos(0, 0, 0)),
            "the surface layer is outside the field's vertical span");
        check(helper, !ObservatoryField.isCovered(TheTruthDimensions.CERTUS, new BlockPos(27, -40, 0)),
            "the field ends past the platform ring");
        check(helper, !ObservatoryField.isCovered(Level.OVERWORLD, new BlockPos(0, -40, 0)),
            "the observatory covers Certus only");
        // Layout: core at the heart, pylons on the corners, gates open on the axes.
        check(helper, ObservatoryLayout.stateAt(0, -38, 0) != null
                && ObservatoryLayout.stateAt(0, -38, 0).is(TheTruthBlocks.MEASURER_CORE.get()),
            "the measurer core must sit one block above the plinth");
        check(helper, ObservatoryLayout.stateAt(0, -39, 0) != null
                && ObservatoryLayout.stateAt(0, -39, 0).is(TheTruthBlocks.CERTUS_FRAME.get()),
            "the core stands on a frame plinth");
        check(helper, ObservatoryLayout.stateAt(20, -37, 20) != null
                && ObservatoryLayout.stateAt(20, -37, 20).is(
                    BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:spatial_pylon"))),
            "corner pylons must be spatial pylons");
        check(helper, ObservatoryLayout.stateAt(12, -39, 0) == null,
            "the ring must keep its axis gates open");
        helper.succeed();
    }

    // ------------------------------------------------------------- residue

    @GameTest(template = "smoke")
    public static void residueAgitatesNearPlayers(final GameTestHelper helper) {
        final ServerPlayer mock = helper.makeMockServerPlayerInLevel();
        mock.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        helper.startSequence()
            .thenIdle(10)
            .thenExecute(() -> {
                final ResidueEntity residue = helper.spawn(TheTruthEntities.RESIDUE.get(), BOSS_POS);
                final BlockPos mockAt = helper.absolutePos(POWER_POS);
                mock.teleportTo(mockAt.getX() + 0.5D, mockAt.getY(), mockAt.getZ() + 0.5D);
                check(helper, !residue.isAgitated(), "no player nearby, no agitation yet");
                // Drive one server tick by hand: the scheduler is irrelevant to
                // the verdict, and this pins the behaviour deterministically.
                residue.tick();
                check(helper, residue.isAgitated(),
                    "one tick with the player within range must agitate (distSq="
                        + residue.distanceToSqr(mock) + ", creative=" + mock.isCreative() + ")");
                check(helper, residue.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                        .getBaseValue() > 0.25D,
                    "agitation must speed the residue up");
            })
            .thenSucceed();
    }

    /** Read-only access to the package-private speed constants. */
    private static final class ResidueEntitySpeedBridge {
        private static final double CALM = 0.25D;
    }

    // ------------------------------------------------------------ helpers

    /** AE2 creative cell by registry id; fails the test when AE2 did not load. */
    private static Block creativeCell(final GameTestHelper helper) {
        final Block cell = BuiltInRegistries.BLOCK.get(
            ResourceLocation.parse("ae2:creative_energy_cell"));
        check(helper, !cell.defaultBlockState().isAir(), "ae2:creative_energy_cell must exist");
        return cell;
    }
}
