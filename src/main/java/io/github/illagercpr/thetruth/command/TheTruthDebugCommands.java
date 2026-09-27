package io.github.illagercpr.thetruth.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.block.QuantumEntranceBlock;
import io.github.illagercpr.thetruth.blockentity.QuantumEntranceBlockEntity;
import io.github.illagercpr.thetruth.coverage.CertaintyCoverage;
import io.github.illagercpr.thetruth.cycle.ObservationCycle;
import io.github.illagercpr.thetruth.registry.TheTruthAttachments;
import io.github.illagercpr.thetruth.registry.TheTruthDataComponents;
import io.github.illagercpr.thetruth.registry.TheTruthDimensions;
import io.github.illagercpr.thetruth.uncertainty.UncertaintyCurve;
import io.github.illagercpr.thetruth.uncertainty.UncertaintyData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import io.github.illagercpr.thetruth.transport.ArrivalLocator;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Debug-only teleport commands for M1 acceptance ("enter Certus, see terrain,
 * re-enter stably"). They require OP permission level 2 and are explicitly NOT
 * the gameplay entry: red line 1 (AE2 late-game gate) arrives in M4.
 */
/** bus() 默认即为 GAME 总线（该属性已标记废弃，显式传值会触发 removal 警告）。 */
@EventBusSubscriber(modid = TheTruth.MOD_ID)
public final class TheTruthDebugCommands {

    private TheTruthDebugCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        final CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("thetruthdebug")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("certus").executes(TheTruthDebugCommands::teleportToCertus))
            .then(Commands.literal("overworld").executes(TheTruthDebugCommands::teleportToOverworld))
            .then(Commands.literal("cycle").executes(TheTruthDebugCommands::showObservationCycle))
            .then(Commands.literal("uncertainty")
                .executes(TheTruthDebugCommands::showUncertainty)
                .then(Commands.literal("set")
                    .then(Commands.argument("value", IntegerArgumentType.integer(0,
                        UncertaintyCurve.CAP))
                        .executes(TheTruthDebugCommands::setUncertainty)))
                .then(Commands.literal("reset").executes(TheTruthDebugCommands::resetUncertainty)))
            .then(Commands.literal("entrance").executes(TheTruthDebugCommands::showEntranceState))
            .then(Commands.literal("endgame").executes(TheTruthDebugCommands::showEndgameState)));
    }

    /**
     * Reads the quantum entrance the caller is looking at: ring completeness,
     * grid node state, entanglement pair, remembered Certus position, and the
     * held key's pair data. Every acceptance gate is one command away.
     */
    private static int showEntranceState(final CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        final ServerPlayer player = context.getSource().getPlayerOrException();
        final HitResult hit = player.pick(8.0, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit)
            || !(player.level().getBlockState(blockHit.getBlockPos()).getBlock()
                instanceof QuantumEntranceBlock)) {
            context.getSource().sendFailure(Component.literal("Look at a Quantum Entrance core"));
            return 0;
        }
        if (!(player.level().getBlockEntity(blockHit.getBlockPos()) instanceof QuantumEntranceBlockEntity core)) {
            context.getSource().sendFailure(Component.literal("No entrance block entity at target"));
            return 0;
        }
        final String side = player.level().dimension() == Level.OVERWORLD ? "overworld" : "certus";
        final String pair = core.getPairId() == null
            ? "none"
            : core.getPairId().toString().substring(0, 8);
        final String lastPos = core.getLastCertusPos() == null
            ? "none"
            : core.getLastCertusPos().toShortString();
        final var heldKey = player.getMainHandItem().get(TheTruthDataComponents.ENTANGLEMENT_PAIR.get());
        final String keyState = heldKey == null
            ? "unpaired"
            : "pair=" + heldKey.pairId().toString().substring(0, 8)
                + " home=" + heldKey.homePos().toShortString();
        final IGridNode node = core.getActionableNode();
        final boolean networkActive = node != null && node.isActive();
        final IGrid grid = node != null ? node.getGrid() : null;
        final String spatialState;
        if (grid != null) {
            boolean portInGrid = false;
            for (final IGridNode gridNode : grid.getNodes()) {
                if (gridNode.getOwner() instanceof BlockEntity be
                    && net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(be.getBlockState().getBlock())
                        .equals(net.minecraft.resources.ResourceLocation.parse("ae2:spatial_io_port"))) {
                    portInGrid = true;
                }
            }
            spatialState = "portInNet=" + portInGrid
                + ", pylonRegionValid=" + grid.getSpatialService().isValidRegion();
        } else {
            spatialState = "n/a (no grid)";
        }
        context.getSource().sendSuccess(() -> Component.literal(String.format(
            "Entrance(%s) at %s: ringFormed=%s, nodeReady=%s, networkActive=%s, spatialIO[%s], corePair=%s, lastCertusPos=%s, heldKey: %s",
            side, core.getBlockPos().toShortString(),
            core.isStructureFormed(),
            core.getMainNode().isReady(),
            networkActive,
            spatialState,
            pair, lastPos, keyState)), false);
        return 1;
    }

    /** M7 readout: whether this save has gone through the endgame. */
    private static int showEndgameState(final CommandContext<CommandSourceStack> context) {
        final boolean flipped =
            io.github.illagercpr.thetruth.endgame.EndgameState.isUnlocked(context.getSource().getServer());
        context.getSource().sendSuccess(
            () -> Component.literal("Endgame: truthBroughtBack=" + flipped), false);
        return 1;
    }

    private static int teleportToCertus(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerLevel certus = TheTruthDimensions.certusLevel(context.getSource().getServer());
        if (certus == null) {
            context.getSource().sendFailure(Component.literal("Certus dimension is not loaded"));
            return 0;
        }
        // The horizontal island mask leaves many columns fully void, so never
        // assume the origin column is solid: spiral outwards until one holds.
        final BlockPos arrival = ArrivalLocator.findArrivalColumn(certus, 8, 8);
        if (arrival == null) {
            context.getSource().sendFailure(Component.literal("No solid column found within 12 chunks of origin"));
            return 0;
        }
        return teleport(context.getSource(), certus, arrival.getX() + 0.5, arrival.getY(),
            arrival.getZ() + 0.5, "Teleported to Certus at " + arrival.getX() + ", "
                + arrival.getY() + ", " + arrival.getZ());
    }

    private static int teleportToOverworld(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerLevel overworld = context.getSource().getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            context.getSource().sendFailure(Component.literal("Overworld is not loaded"));
            return 0;
        }
        final BlockPos spawn = overworld.getSharedSpawnPos();
        final BlockPos arrival = ArrivalLocator.findArrivalColumn(overworld, spawn.getX(), spawn.getZ());
        if (arrival == null) {
            context.getSource().sendFailure(Component.literal("No safe column found near world spawn"));
            return 0;
        }
        return teleport(context.getSource(), overworld, arrival.getX() + 0.5, arrival.getY(),
            arrival.getZ() + 0.5, "Returned to the Overworld spawn");
    }

    /**
     * Prints the current observation-cycle state of the Certus level. The
     * transitions are 11.5 min apart, so a live readout beats waiting and
     * squinting at the sky during manual acceptance.
     */
    private static int showObservationCycle(final CommandContext<CommandSourceStack> context) {
        final ServerLevel certus = TheTruthDimensions.certusLevel(context.getSource().getServer());
        if (certus == null) {
            context.getSource().sendFailure(Component.literal("Certus dimension is not loaded"));
            return 0;
        }
        final long cycleTick = ObservationCycle.cycleTick(certus.getGameTime());
        final String status = String.format(
            "Observation cycle: tick %d/%d, phase %s, blend %.3f, sky phase %d (level dayTime %d)",
            cycleTick, ObservationCycle.CYCLE_TICKS,
            ObservationCycle.phaseOf(cycleTick),
            ObservationCycle.observationBlend(cycleTick),
            ObservationCycle.mapToSkyPhase(cycleTick),
            certus.getDayTime());
        context.getSource().sendSuccess(() -> Component.literal(status), false);
        return 1;
    }

    /**
     * Prints the caller's uncertainty state: value, curve layer, coverage
     * verdict with its source, and any open deep-layer warning window. The
     * "set"/"reset" sub-commands let acceptance testing jump straight to a
     * desired state instead of waiting for accumulation.
     */
    private static int showUncertainty(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerPlayer player = context.getSource().getPlayerOrException();
        final UncertaintyData data = player.getData(TheTruthAttachments.UNCERTAINTY.get());
        final UncertaintyCurve.Layer layer = UncertaintyCurve.layerOf(player.blockPosition().getY());
        final String status = String.format(
            "Uncertainty %d/%d | layer %s | covered %s (%s) | warning ticks left %d | next re-randomize %d",
            data.uncertainty(), UncertaintyCurve.CAP, layer, data.covered(),
            CertaintyCoverage.describeCoverage(player), data.warningTicksLeft(), data.nextRandomizeAt());
        context.getSource().sendSuccess(() -> Component.literal(status), false);
        return 1;
    }

    private static int setUncertainty(final CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        final ServerPlayer player = context.getSource().getPlayerOrException();
        final int value = IntegerArgumentType.getInteger(context, "value");
        final UncertaintyData current = player.getData(TheTruthAttachments.UNCERTAINTY.get());
        player.setData(TheTruthAttachments.UNCERTAINTY.get(), current.withUncertainty(value));
        context.getSource().sendSuccess(() -> Component.literal("Uncertainty set to " + value), false);
        return 1;
    }

    private static int resetUncertainty(final CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        final ServerPlayer player = context.getSource().getPlayerOrException();
        player.setData(TheTruthAttachments.UNCERTAINTY.get(), UncertaintyData.DEFAULT);
        context.getSource().sendSuccess(() -> Component.literal("Uncertainty state reset"), false);
        return 1;
    }

    private static int teleport(final CommandSourceStack source, final ServerLevel level,
                                final double x, final double y, final double z, final String message)
            throws CommandSyntaxException {
        final ServerPlayer player = source.getPlayerOrException();
        player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal(message), true);
        return 1;
    }
}
