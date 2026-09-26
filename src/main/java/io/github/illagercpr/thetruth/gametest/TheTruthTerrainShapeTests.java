package io.github.illagercpr.thetruth.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.registry.TheTruthBiomes;
import io.github.illagercpr.thetruth.worldgen.CertusChunkGenerator;
import io.github.illagercpr.thetruth.worldgen.CertusTerrainShape;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M2 terrain-shape tests: the packed dimension JSON decodes into the custom
 * chunk generator, the grid-gap signature has straight edges and stays closed
 * around the boss observatory, and the layer spans keep their vertical bands.
 * {@link CertusTerrainShape} is a pure function, so the real generation
 * geometry is assertable here (unlike the M1 density-function terrain whose
 * interpolated nodes degrade outside a NoiseChunk).
 */
@GameTestHolder(TheTruth.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheTruthTerrainShapeTests {

    private TheTruthTerrainShapeTests() {
    }

    @GameTest(template = "smoke")
    public static void certusLevelStemResolvesCertusGenerator(final GameTestHelper helper) {
        // The GameTest server never instantiates datapack dimensions (FLAT
        // preset, see docs/02), so the LevelStem is absent from the loaded
        // registry. The JSON→codec chain is verified instead by decoding the
        // packed dimension JSON through DFU against the live registries.
        final RegistryAccess access = helper.getLevel().getServer().registryAccess();
        final RegistryOps<JsonElement> ops = access.createSerializationContext(JsonOps.INSTANCE);
        final InputStream packed = TheTruthTerrainShapeTests.class.getResourceAsStream(
            "/data/thetruth/dimension/certus.json");
        if (packed == null) {
            helper.fail("packed dimension/certus.json not found in mod resources");
            return;
        }
        final JsonElement json;
        try (InputStream in = packed) {
            json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (final IOException e) {
            helper.fail("failed to read packed dimension/certus.json: " + e);
            return;
        }
        final Optional<LevelStem> stem = LevelStem.CODEC.parse(ops, json).result();
        if (stem.isEmpty()) {
            helper.fail("dimension/certus.json failed to decode against live registries");
            return;
        }
        if (!(stem.get().generator() instanceof CertusChunkGenerator)) {
            helper.fail("Certus generator decoded as " + stem.get().generator());
            return;
        }
        if (!(stem.get().generator().getBiomeSource() instanceof FixedBiomeSource)) {
            helper.fail("Certus biome source should decode as a fixed source");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "smoke")
    public static void certusLayerBiomesLoad(final GameTestHelper helper) {
        final RegistryAccess access = helper.getLevel().getServer().registryAccess();
        for (final ResourceKey<Biome> key : List.of(
                TheTruthBiomes.CERTUS_DEBRIS, TheTruthBiomes.CERTUS_SEDIMENT, TheTruthBiomes.CERTUS_UNOBSERVED)) {
            if (access.registryOrThrow(Registries.BIOME).get(key) == null) {
                helper.fail("biome " + key.location() + " failed to load");
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "smoke")
    public static void certusGridGapSignature(final GameTestHelper helper) {
        final CertusTerrainShape shape = shape(helper);

        // Gap strips sit on the 48-block grid, 12 wide, with straight edges.
        // All probe columns are outside the observatory square (|x|,|z| <= 24),
        // which the grid deliberately bends around.
        if (!shape.isGridGap(48, 24)) {
            helper.fail("the x=48 strip must be a gap");
            return;
        }
        if (!shape.isGridGap(24, 48)) {
            helper.fail("the z=48 strip must be a gap");
            return;
        }
        if (shape.isGridGap(24, 24)) {
            helper.fail("(24,24) is terrain");
            return;
        }
        if (shape.isGridGap(71, 24)) {
            helper.fail("the last column before the wrap is terrain");
            return;
        }
        if (!shape.isGridGap(59, 24) || shape.isGridGap(60, 24)) {
            helper.fail("gap width must be exactly 12 (48+0..11 gap, 48+12 terrain)");
            return;
        }
        if (!shape.isGridGap(96, 72)) {
            helper.fail("the grid must wrap across chunk borders");
            return;
        }
        if (!shape.isGridGap(-48, 24)) {
            helper.fail("negative coordinates must wrap the same way");
            return;
        }

        // The grid bends around the boss observatory so the arena stays whole.
        if (shape.isGridGap(0, 0) || shape.isGridGap(0, 24) || shape.isGridGap(24, 0)) {
            helper.fail("the observatory must be exempt from the grid gaps");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "smoke")
    public static void certusTerrainLayerSpans(final GameTestHelper helper) {
        final CertusTerrainShape shape = shape(helper);

        // Inside shard and observatory (and exempt from the gap): three spans.
        final List<int[]> spans = shape.columnSpans(24, 24);
        if (spans.size() != 3) {
            helper.fail("(24,24) should carry shard + sediment + observatory spans, got " + spans.size());
            return;
        }
        if (spans.get(0)[0] != CertusTerrainShape.SHARD_BOTTOM || spans.get(0)[1] != CertusTerrainShape.SHARD_TOP) {
            helper.fail("shard span is " + Arrays.toString(spans.get(0)));
            return;
        }
        if (spans.get(1)[0] != -16 || spans.get(1)[1] < 48 || spans.get(1)[1] > 72) {
            helper.fail("sediment span out of band: " + Arrays.toString(spans.get(1)));
            return;
        }
        if (spans.get(2)[0] != CertusTerrainShape.OBSERVATORY_BOTTOM
                || spans.get(2)[1] != CertusTerrainShape.OBSERVATORY_TOP) {
            helper.fail("observatory span is " + Arrays.toString(spans.get(2)));
            return;
        }

        // A gap column (outside the observatory) is fully void.
        if (!shape.columnSpans(8, 40).isEmpty()) {
            helper.fail("(8,40) sits in a gap strip and must be void");
            return;
        }

        // Islands keep their vertical band and sit on solid columns only. Scan
        // several z lines far outside the shard: the low-frequency island mask
        // can stay above the threshold along any single line.
        boolean sawIsland = false;
        boolean sawBrokenColumn = false;
        for (final int z : new int[] {24, -160, 400}) {
            for (int x = 200; x <= 600 && !(sawIsland && sawBrokenColumn); x += 4) {
                if (shape.isGridGap(x, z) || shape.insideCentralShard(x, z)) {
                    continue;
                }
                final int[] island = shape.islandSpan(x, z);
                if (island != null) {
                    if (island[0] < CertusTerrainShape.DEBRIS_LAYER_MIN_Y || island[1] < 88 || island[1] > 200) {
                        helper.fail("island span out of band at " + x + "," + z + ": " + Arrays.toString(island));
                        return;
                    }
                    sawIsland = true;
                } else {
                    sawBrokenColumn = true;
                }
            }
        }
        if (!sawIsland) {
            helper.fail("no fragment island found in the sampled bands");
            return;
        }
        if (!sawBrokenColumn) {
            helper.fail("no broken (mask-void) column found; islands would not fragment");
            return;
        }
        helper.succeed();
    }

    private static CertusTerrainShape shape(final GameTestHelper helper) {
        final RegistryAccess access = helper.getLevel().getServer().registryAccess();
        final var noiseParameters = access.registryOrThrow(Registries.NOISE);
        final NormalNoise terrain = NormalNoise.create(
            RandomSource.create(42L), noiseParameters.get(CertusTerrainShape.CERTUS_TERRAIN_NOISE));
        final NormalNoise islands = NormalNoise.create(
            RandomSource.create(42L), noiseParameters.get(CertusTerrainShape.CERTUS_ISLANDS_NOISE));
        return new CertusTerrainShape(terrain, islands);
    }
}
