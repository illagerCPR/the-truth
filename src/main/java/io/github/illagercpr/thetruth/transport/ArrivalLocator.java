package io.github.illagercpr.thetruth.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Finds a standable arrival column in a dimension (M4). The horizontal island
 * mask of Certus leaves many columns fully void and the dimension's stored
 * spawn coordinate is unreliable, so arrivals spiral outwards until a column
 * holds solid ground.
 *
 * <p>Algorithm moved verbatim from the M1 debug teleport command so the
 * gameplay entrance and the debug command share one source of truth.
 */
public final class ArrivalLocator {

    /** Maximum search radius in chunks (debug command parity: 12 chunks). */
    public static final int MAX_CHUNK_RADIUS = 12;

    private ArrivalLocator() {
    }

    /**
     * Spirals outwards from the center column in 16-block steps and returns the
     * first column whose surface can be stood on (as {@code BlockPos} of the
     * standing position), or null when a 25x25-chunk area holds nothing.
     *
     * <p>Empty columns are cheap-skipped at {@link ChunkStatus#SURFACE} before
     * paying for a FULL generation and block scan.
     */
    public static BlockPos findArrivalColumn(final ServerLevel level, final int centerX, final int centerZ) {
        for (int radius = 0; radius <= MAX_CHUNK_RADIUS; radius++) {
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
    public static double findStandingY(final ServerLevel level, final int x, final int z) {
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
