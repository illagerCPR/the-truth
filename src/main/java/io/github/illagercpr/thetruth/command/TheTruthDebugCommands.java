package io.github.illagercpr.thetruth.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.cycle.ObservationCycle;
import io.github.illagercpr.thetruth.registry.TheTruthDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

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
            .then(Commands.literal("cycle").executes(TheTruthDebugCommands::showObservationCycle)));
    }

    private static int teleportToCertus(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerLevel certus = TheTruthDimensions.certusLevel(context.getSource().getServer());
        if (certus == null) {
            context.getSource().sendFailure(Component.literal("Certus dimension is not loaded"));
            return 0;
        }
        // The horizontal island mask leaves many columns fully void, so never
        // assume the origin column is solid: spiral outwards until one holds.
        final BlockPos arrival = findArrivalColumn(certus, 8, 8);
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
        final BlockPos arrival = findArrivalColumn(overworld, spawn.getX(), spawn.getZ());
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

    private static int teleport(final CommandSourceStack source, final ServerLevel level,
                                final double x, final double y, final double z, final String message)
            throws CommandSyntaxException {
        final ServerPlayer player = source.getPlayerOrException();
        player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    /**
     * Spirals outwards from the center column in 16-block steps and returns the
     * first column whose surface can be stood on (as {@code BlockPos} of the
     * standing position), or null when a 25x25-chunk area holds nothing.
     *
     * <p>Empty columns are cheap-skipped at {@link ChunkStatus#SURFACE} before
     * paying for a FULL generation and block scan.
     */
    private static BlockPos findArrivalColumn(final ServerLevel level, final int centerX, final int centerZ) {
        for (int radius = 0; radius <= 12; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    final int x = centerX + dx * 16;
                    final int z = centerZ + dz * 16;
                    // Cheap pre-filter: SURFACE chunks only carry the *_WG
                    // worldgen heightmaps (FINAL heightmaps start at CARVERS),
                    // so query WORLD_SURFACE_WG directly on the proto chunk.
                    final ChunkAccess proto = level.getChunk(x >> 4, z >> 4, ChunkStatus.SURFACE, true);
                    if (proto.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x & 15, z & 15)
                        <= level.getMinBuildHeight()) {
                        continue;
                    }
                    final double y = findStandingY(level, x, z);
                    if (y >= 0.0) {
                        return new BlockPos(x, (int) y, z);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Standing height at a column, or -1 when none exists. Forces the chunk to
     * FULL status first (an un-generated chunk reports a preliminary heightmap),
     * then scans downwards for the first solid block with two air blocks above it.
     */
    private static double findStandingY(final ServerLevel level, final int x, final int z) {
        level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
        final int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        final int start = top > level.getMinBuildHeight()
            ? top
            : level.getMaxBuildHeight() - 1;
        for (int y = start; y > level.getMinBuildHeight(); y--) {
            final BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()
                && level.getBlockState(pos.above(2)).isAir()) {
                return y + 1.0;
            }
        }
        return -1.0;
    }
}
