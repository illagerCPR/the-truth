package io.github.illagercpr.thetruth.worldgen;

import io.github.illagercpr.thetruth.TheTruth;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * Deterministic terrain shape of the Certus dimension (M2 rewrite of the M1
 * density-function terrain).
 *
 * <p>Geometry (initial values from docs/00-世界观创意方案.md §2.3):
 *
 * <ul>
 *   <li><b>Grid gaps</b> — vertical void strips on a fixed world grid. This is
 *       the dimension's visual signature: "this world was edited, not
 *       destroyed". Straight edges, not caves.</li>
 *   <li><b>Surface layer "Debris Belt" (y &ge; 80)</b> — the central planetary
 *       shard near the origin plus noise-driven floating fragment islands
 *       outside it.</li>
 *   <li><b>Middle layer "The Sediment" (-16 &le; y &lt; 80)</b> — a continuous
 *       compressed slab with a gently undulating top.</li>
 *   <li><b>Deep layer "The Uncollapsed" (y &lt; -16)</b> — void except the
 *       observatory platform reserved for the M6 boss arena; the grid gaps
 *       bend around it so the arena stays whole.</li>
 * </ul>
 *
 * <p>The shape is a pure function of world coordinates: every column can be
 * resolved to solid spans without a NoiseChunk, which keeps it unit-testable
 * (see {@code TheTruthDimensionTests}) and makes straight-cut edges trivial.
 */
public final class CertusTerrainShape {

    // ---- Grid gaps: void strips every GRID_CELL blocks, GRID_GAP wide. ----
    public static final int GRID_CELL = 48;
    public static final int GRID_GAP = 12;

    // ---- Vertical layer boundaries (also used for biome assignment). ----
    public static final int DEBRIS_LAYER_MIN_Y = 80;
    public static final int UNOBSERVED_LAYER_MAX_Y = -16;

    // ---- Central planetary shard (surface layer anchor and arrival point). ----
    public static final int SHARD_HALF_SIZE = 96;
    public static final int SHARD_BOTTOM = 105;
    public static final int SHARD_TOP = 132;

    // ---- Observatory platform in the unobserved layer (M6 boss arena seed). ----
    public static final int OBSERVATORY_HALF_SIZE = 24;
    public static final int OBSERVATORY_BOTTOM = -48;
    public static final int OBSERVATORY_TOP = -40;

    // ---- Floating fragment islands. ----
    private static final double ISLAND_MASK_GAIN = 8.0;
    private static final double ISLAND_MASK_BIAS = 0.05;
    private static final double ISLAND_MASK_THRESHOLD = 0.25;
    private static final int ISLAND_BASE_TOP = 128;
    private static final int ISLAND_DETAIL_AMPLITUDE = 12;
    private static final int ISLAND_TOP_MIN = 88;
    private static final int ISLAND_TOP_MAX = 200;
    private static final int ISLAND_MIN_THICKNESS = 8;
    private static final int ISLAND_MAX_EXTRA_THICKNESS = 24;

    // ---- Sediment slab. ----
    private static final int SEDIMENT_BOTTOM = UNOBSERVED_LAYER_MAX_Y;
    private static final int SEDIMENT_BASE_TOP = 56;
    private static final int SEDIMENT_DETAIL_AMPLITUDE = 10;
    private static final int SEDIMENT_TOP_MIN = 48;
    private static final int SEDIMENT_TOP_MAX = 72;

    public static final ResourceKey<NormalNoise.NoiseParameters> CERTUS_TERRAIN_NOISE = noiseKey("certus_terrain");
    public static final ResourceKey<NormalNoise.NoiseParameters> CERTUS_ISLANDS_NOISE = noiseKey("certus_islands");

    private final NormalNoise terrainNoise;
    private final NormalNoise islandNoise;

    /** Public so GameTests can build a shape from fixed-seed noises directly. */
    public CertusTerrainShape(final NormalNoise terrainNoise, final NormalNoise islandNoise) {
        this.terrainNoise = terrainNoise;
        this.islandNoise = islandNoise;
    }

    /** Builds the shape from the world's {@link RandomState}, reusing the datapack noise parameters of M1. */
    public static CertusTerrainShape create(final RandomState randomState) {
        return new CertusTerrainShape(
            randomState.getOrCreateNoise(CERTUS_TERRAIN_NOISE),
            randomState.getOrCreateNoise(CERTUS_ISLANDS_NOISE));
    }

    /**
     * A column is void when it falls inside either strip of the world grid,
     * except inside the observatory platform which the grid bends around so
     * the boss arena stays whole.
     */
    public boolean isGridGap(final int x, final int z) {
        return !insideObservatory(x, z)
            && (Math.floorMod(x, GRID_CELL) < GRID_GAP || Math.floorMod(z, GRID_CELL) < GRID_GAP);
    }

    /** Inside the central planetary shard square. */
    public boolean insideCentralShard(final int x, final int z) {
        return Math.abs(x) <= SHARD_HALF_SIZE && Math.abs(z) <= SHARD_HALF_SIZE;
    }

    /** Inside the observatory platform square in the unobserved layer. */
    public boolean insideObservatory(final int x, final int z) {
        return Math.abs(x) <= OBSERVATORY_HALF_SIZE && Math.abs(z) <= OBSERVATORY_HALF_SIZE;
    }

    /** Island mask (0..1): sharpened large-scale island noise, mirroring the M1 mask ×8+0.05. */
    public double islandMask(final int x, final int z) {
        return Mth.clamp(this.islandNoise.getValue(x, 0, z) * ISLAND_MASK_GAIN + ISLAND_MASK_BIAS, 0.0, 1.0);
    }

    /** Fragment island solid span for a column, or null when the mask breaks the column apart. */
    public int[] islandSpan(final int x, final int z) {
        final double mask = islandMask(x, z);
        if (mask < ISLAND_MASK_THRESHOLD) {
            return null;
        }
        final double detail = Mth.clamp(this.terrainNoise.getValue(x, 0, z), -2.0, 2.0) / 2.0;
        final int top = Mth.clamp(
            ISLAND_BASE_TOP + (int) Math.round(detail * ISLAND_DETAIL_AMPLITUDE), ISLAND_TOP_MIN, ISLAND_TOP_MAX);
        final int thickness = ISLAND_MIN_THICKNESS + (int) Math.round(mask * ISLAND_MAX_EXTRA_THICKNESS);
        final int bottom = Math.max(DEBRIS_LAYER_MIN_Y, top - thickness);
        return bottom > top ? null : new int[] {bottom, top};
    }

    /** Top of the compressed sediment slab for a column. */
    public int sedimentTop(final int x, final int z) {
        // Sample the terrain noise far from the island band (y offset) so the
        // two layers decorrelate while reusing one noise instance.
        final double detail = Mth.clamp(this.terrainNoise.getValue(x, 1024.0, z), -2.0, 2.0) / 2.0;
        return Mth.clamp(
            SEDIMENT_BASE_TOP + (int) Math.round(detail * SEDIMENT_DETAIL_AMPLITUDE),
            SEDIMENT_TOP_MIN, SEDIMENT_TOP_MAX);
    }

    /**
     * All solid spans of a column as {@code [bottom, top]} pairs in world Y,
     * ordered surface layer first. Empty when the column sits in a grid gap.
     */
    public List<int[]> columnSpans(final int x, final int z) {
        final List<int[]> spans = new ArrayList<>(3);
        if (isGridGap(x, z)) {
            return spans;
        }
        if (insideCentralShard(x, z)) {
            spans.add(new int[] {SHARD_BOTTOM, SHARD_TOP});
        } else {
            final int[] island = islandSpan(x, z);
            if (island != null) {
                spans.add(island);
            }
        }
        spans.add(new int[] {SEDIMENT_BOTTOM, sedimentTop(x, z)});
        if (insideObservatory(x, z)) {
            spans.add(new int[] {OBSERVATORY_BOTTOM, OBSERVATORY_TOP});
        }
        return spans;
    }

    private static ResourceKey<NormalNoise.NoiseParameters> noiseKey(final String path) {
        return ResourceKey.create(
            Registries.NOISE, ResourceLocation.fromNamespaceAndPath(TheTruth.MOD_ID, path));
    }
}
