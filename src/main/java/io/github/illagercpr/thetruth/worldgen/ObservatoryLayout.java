package io.github.illagercpr.thetruth.worldgen;

import io.github.illagercpr.thetruth.registry.TheTruthBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Deterministic layout of the deep observatory installation (M6, docs/00
 * section 7): a frame plinth with the Measurer Core at its heart, a ring wall
 * with four gates, and four spatial-pylon columns standing in the corners.
 * Pure coordinate arithmetic — each chunk builds its own columns, so no
 * cross-chunk placement order matters. Parallel fill workers only ever read
 * registries here.
 */
public final class ObservatoryLayout {

    /** Lowest y the layout writes (the first layer above the platform top). */
    public static final int LAYOUT_BOTTOM_Y = -39;
    /** Highest y the layout writes (exclusive bound). */
    public static final int LAYOUT_TOP_EXCLUSIVE_Y = -30;

    private static final int PLINTH_HALF = 4;
    private static final int RING_HALF = 12;
    private static final int GATE_HALF = 2;
    private static final int PILLAR_OFFSET = 20;
    /** Pillars rise this many blocks above the plinth layer (inclusive). */
    private static final int PILLAR_HEIGHT = 8;

    private ObservatoryLayout() {
    }

    private static BlockState frame() {
        return TheTruthBlocks.CERTUS_FRAME.get().defaultBlockState();
    }

    private static BlockState core() {
        return TheTruthBlocks.MEASURER_CORE.get().defaultBlockState();
    }

    /** AE2 spatial pylon by registry id — decoration, never imported as a class. */
    private static BlockState pylon() {
        final Block resolved = BuiltInRegistries.BLOCK.get(
            ResourceLocation.parse("ae2:spatial_pylon"));
        return resolved.defaultBlockState().isAir() ? frame() : resolved.defaultBlockState();
    }

    /**
     * The block this installation places at the given world coordinate, or
     * null when the coordinate stays terrain. The deep platform top sits at
     * {@code y = -40} (CertusTerrainShape.OBSERVATORY_TOP), the structure
     * grows upward from there.
     */
    public static BlockState stateAt(final int x, final int y, final int z) {
        if (y == LAYOUT_BOTTOM_Y) {
            // Plinth under the core.
            if (Math.abs(x) <= PLINTH_HALF && Math.abs(z) <= PLINTH_HALF) {
                return frame();
            }
            // Ring wall with four gates (gates face the axes).
            if (isRingWall(x, z)) {
                return frame();
            }
        }
        if (y == LAYOUT_BOTTOM_Y + 1 && x == 0 && z == 0) {
            return core();
        }
        // Ring wall rises two more layers, gates stay open.
        if ((y == LAYOUT_BOTTOM_Y + 1 || y == LAYOUT_BOTTOM_Y + 2) && isRingWall(x, z)) {
            return frame();
        }
        // Four corner pylon columns.
        if (y <= LAYOUT_BOTTOM_Y + PILLAR_HEIGHT
            && Math.abs(x) == PILLAR_OFFSET && Math.abs(z) == PILLAR_OFFSET) {
            return pylon();
        }
        return null;
    }

    private static boolean isRingWall(final int x, final int z) {
        if (Math.abs(x) != RING_HALF && Math.abs(z) != RING_HALF) {
            return false;
        }
        if (!(Math.abs(x) <= RING_HALF && Math.abs(z) <= RING_HALF)) {
            return false;
        }
        // Four gates: on each side, the central GATE_HALF*2+1 columns stay open.
        if (Math.abs(x) == RING_HALF && Math.abs(z) <= GATE_HALF) {
            return false;
        }
        return !(Math.abs(z) == RING_HALF && Math.abs(x) <= GATE_HALF);
    }
}
