package io.github.illagercpr.thetruth.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.illagercpr.thetruth.registry.TheTruthBiomes;
import io.github.illagercpr.thetruth.registry.TheTruthBlocks;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.Util;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Custom chunk generator of the Certus dimension (M2).
 *
 * <p>Replaces the M1 {@code minecraft:noise} generator: the three-layer
 * geometry and the straight-edged grid gaps cannot be expressed with vanilla
 * density functions (no {@code mod}/{@code floor} nodes), and the layer
 * biomes are assigned vertically, which no vanilla biome source does.
 *
 * <p>Design notes:
 * <ul>
 *   <li>{@link #createBiomes} is overridden to fill each chunk section with
 *       the layer biome directly — the JSON {@code biome_source} is decoded
 *       (and kept as the fixed fallback) but never sampled.</li>
 *   <li>{@link #fillFromNoise} writes {@code certus_stone} spans straight into
 *       the chunk sections and maintains the two {@code *_WG} heightmaps, the
 *       same contract vanilla's doFill obeys (FINAL heightmaps only start at
 *       CARVERS — see AGENTS.md).</li>
 *   <li>No carvers, no surface rule, no mob generation: all Certus content is
 *       placed by the shape or by later milestones.</li>
 * </ul>
 */
public final class CertusChunkGenerator extends ChunkGenerator {

    public static final MapCodec<CertusChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
        .group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource))
        .apply(instance, CertusChunkGenerator::new));

    private volatile CertusTerrainShape cachedShape;
    private volatile RandomState shapeOwner;

    public CertusChunkGenerator(final BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // ------------------------------------------------------------------
    // Biomes: vertical layer assignment
    // ------------------------------------------------------------------

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(final RandomState randomState, final Blender blender,
            final StructureManager structureManager, final ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(Util.wrapThreadWithTaskName("thetruth_init_biomes", () -> {
            final Registry<Biome> biomes = structureManager.registryAccess().registryOrThrow(Registries.BIOME);
            final Holder<Biome> debris = biomes.getHolderOrThrow(TheTruthBiomes.CERTUS_DEBRIS);
            final Holder<Biome> sediment = biomes.getHolderOrThrow(TheTruthBiomes.CERTUS_SEDIMENT);
            final Holder<Biome> unobserved = biomes.getHolderOrThrow(TheTruthBiomes.CERTUS_UNOBSERVED);
            // Resolver receives quart coordinates: one quart step equals 4 blocks.
            chunk.fillBiomesFromNoise(
                (quartX, quartY, quartZ, sampler) -> biomeForLayer(quartY << 2, debris, sediment, unobserved),
                randomState.sampler());
            return chunk;
        }), Util.backgroundExecutor());
    }

    private static Holder<Biome> biomeForLayer(final int blockY, final Holder<Biome> debris,
            final Holder<Biome> sediment, final Holder<Biome> unobserved) {
        if (blockY >= CertusTerrainShape.DEBRIS_LAYER_MIN_Y) {
            return debris;
        }
        return blockY >= CertusTerrainShape.UNOBSERVED_LAYER_MAX_Y ? sediment : unobserved;
    }

    // ------------------------------------------------------------------
    // Terrain: solid spans from the shape
    // ------------------------------------------------------------------

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(final Blender blender, final RandomState randomState,
            final StructureManager structureManager, final ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(Util.wrapThreadWithTaskName("thetruth_fill_terrain", () -> {
            final CertusTerrainShape shape = shapeFor(randomState);
            final int baseX = chunk.getPos().getMinBlockX();
            final int baseZ = chunk.getPos().getMinBlockZ();
            final int minY = chunk.getMinBuildHeight();
            final int maxYExclusive = minY + chunk.getHeight();
            final Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
            final Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
            final BlockState stone = TheTruthBlocks.CERTUS_STONE.value().defaultBlockState();
            final BlockState residual = TheTruthBlocks.RESIDUAL_MATTER.value().defaultBlockState();

            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    final int worldX = baseX + localX;
                    final int worldZ = baseZ + localZ;
                    final List<int[]> spans = shape.columnSpans(worldX, worldZ);
                    for (int[] span : spans) {
                        final int bottom = Math.max(span[0], minY);
                        final int top = Math.min(span[1], maxYExclusive - 1);
                        for (int y = bottom; y <= top; y++) {
                            final LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                            section.setBlockState(localX, y & 15, localZ, stone, false);
                            oceanFloor.update(localX, y, localZ, stone);
                            worldSurface.update(localX, y, localZ, stone);
                        }
                    }
                    // M5: residual matter clusters on top of the sediment slab.
                    if (shape.isResidualClusterColumn(worldX, worldZ)) {
                        final int y = shape.residualClusterY(worldX, worldZ);
                        if (y >= minY && y < maxYExclusive) {
                            final LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                            section.setBlockState(localX, y & 15, localZ, residual, false);
                            oceanFloor.update(localX, y, localZ, residual);
                            worldSurface.update(localX, y, localZ, residual);
                        }
                    }
                    // M6: the deep observatory installation above the platform.
                    if (shape.insideObservatory(worldX, worldZ)) {
                        for (int y = ObservatoryLayout.LAYOUT_BOTTOM_Y;
                             y < ObservatoryLayout.LAYOUT_TOP_EXCLUSIVE_Y; y++) {
                            if (y < minY || y >= maxYExclusive) {
                                continue;
                            }
                            final BlockState structure = ObservatoryLayout.stateAt(worldX, y, worldZ);
                            if (structure != null) {
                                final LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                                section.setBlockState(localX, y & 15, localZ, structure, false);
                                oceanFloor.update(localX, y, localZ, structure);
                                worldSurface.update(localX, y, localZ, structure);
                            }
                        }
                    }
                }
            }
            return chunk;
        }), Util.backgroundExecutor());
    }

    /**
     * The shape binds two noise instances whose creation is somewhat costly;
     * {@link RandomState} is a per-level singleton, so the cache is keyed on
     * its identity (double-checked locking keeps parallel fill workers safe).
     */
    private CertusTerrainShape shapeFor(final RandomState randomState) {
        final CertusTerrainShape cached = this.cachedShape;
        if (cached != null && this.shapeOwner == randomState) {
            return cached;
        }
        synchronized (this) {
            if (this.cachedShape == null || this.shapeOwner != randomState) {
                this.cachedShape = CertusTerrainShape.create(randomState);
                this.shapeOwner = randomState;
            }
            return this.cachedShape;
        }
    }

    // ------------------------------------------------------------------
    // Empty hooks: no carvers, no surface rules, no natural mobs
    // ------------------------------------------------------------------

    @Override
    public void applyCarvers(final WorldGenRegion level, final long seed, final RandomState randomState,
            final BiomeManager biomeManager,
            final StructureManager structureManager, final ChunkAccess chunk, final GenerationStep.Carving step) {
        // Intentionally empty: the grid gaps are the only "carving" Certus has.
    }

    @Override
    public void buildSurface(final WorldGenRegion level, final StructureManager structureManager,
            final RandomState randomState, final ChunkAccess chunk) {
        // Intentionally empty: certus_stone is placed directly by fillFromNoise.
    }

    @Override
    public void spawnOriginalMobs(final WorldGenRegion level) {
        // Intentionally empty: Certus creatures arrive in M6.
    }

    // ------------------------------------------------------------------
    // Vertical metrics
    // ------------------------------------------------------------------

    @Override
    public int getGenDepth() {
        return 320;
    }

    @Override
    public int getSeaLevel() {
        // Sea level sits at the dimension floor so no fluid picker can flood
        // the void with lava (see the M1 lava-sea trap in AGENTS.md).
        return getMinY();
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(final int x, final int z, final Heightmap.Types type,
            final LevelHeightAccessor level, final RandomState randomState) {
        final List<int[]> spans = shapeFor(randomState).columnSpans(x, z);
        int top = level.getMinBuildHeight();
        for (int[] span : spans) {
            top = Math.max(top, span[1] + 1);
        }
        return top;
    }

    @Override
    public NoiseColumn getBaseColumn(final int x, final int z, final LevelHeightAccessor level,
            final RandomState randomState) {
        final List<int[]> spans = shapeFor(randomState).columnSpans(x, z);
        final BlockState[] states = new BlockState[level.getHeight()];
        for (int[] span : spans) {
            for (int y = Math.max(span[0], level.getMinBuildHeight());
                    y <= Math.min(span[1], level.getMaxBuildHeight() - 1); y++) {
                states[y - level.getMinBuildHeight()] = TheTruthBlocks.CERTUS_STONE.value().defaultBlockState();
            }
        }
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(final List<String> info, final RandomState randomState, final BlockPos pos) {
        info.add("Certus shape: grid gaps every " + CertusTerrainShape.GRID_CELL
            + " blocks (width " + CertusTerrainShape.GRID_GAP + "), layers debris/sediment/unobserved");
    }
}
