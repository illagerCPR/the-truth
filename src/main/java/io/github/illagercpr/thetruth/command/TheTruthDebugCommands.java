package io.github.illagercpr.thetruth.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.registry.TheTruthDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
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
            .then(Commands.literal("overworld").executes(TheTruthDebugCommands::teleportToOverworld)));
    }

    private static int teleportToCertus(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerLevel certus = TheTruthDimensions.certusLevel(context.getSource().getServer());
        if (certus == null) {
            context.getSource().sendFailure(Component.literal("Certus dimension is not loaded"));
            return 0;
        }
        final int x = 8;
        final int z = 8;
        final double y = findStandingY(certus, x, z);
        return teleport(context.getSource(), certus, x + 0.5, y, z + 0.5, "Teleported to Certus at "
            + x + ", " + (int) y + ", " + z);
    }

    private static int teleportToOverworld(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerLevel overworld = context.getSource().getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            context.getSource().sendFailure(Component.literal("Overworld is not loaded"));
            return 0;
        }
        final BlockPos spawn = overworld.getSharedSpawnPos();
        final double y = findStandingY(overworld, spawn.getX(), spawn.getZ());
        return teleport(context.getSource(), overworld, spawn.getX() + 0.5, y, spawn.getZ() + 0.5,
            "Returned to the Overworld spawn");
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
     * Standing height at a column. Forces the chunk to FULL status first (an
     * un-generated chunk reports a preliminary heightmap, which previously made
     * the teleport land inside solid stone), then scans downwards for the first
     * solid block with two air blocks above it.
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
        return 128.0;
    }
}
