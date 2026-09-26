package io.github.illagercpr.thetruth.gametest;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.registry.TheTruthBlocks;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M1 dimension skeleton tests.
 *
 * <p>Two engine facts shape what can be asserted here (both recorded in
 * docs/02-开发计划.md): vanilla {@code GameTestServer.create} hard-codes the FLAT
 * world preset and discards datapack LevelStem JSON, so a datapack dimension can
 * never be instantiated inside a GameTest server; and {@code interpolated} /
 * {@code flat_cache} density nodes degrade to 0 when a density function is
 * sampled directly with {@link DensityFunction.SinglePointContext} outside a
 * NoiseChunk. The density assertions therefore verify the arithmetic shell of
 * the density tree, while a direct NormalNoise sample proves the island-mask
 * noise actually varies (the real void gaps of the terrain). Seeing the actual
 * fragment islands stays a manual runClient acceptance step.
 */
@GameTestHolder(TheTruth.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheTruthDimensionTests {

    private static final ResourceKey<DimensionType> CERTUS_DIMENSION_TYPE = key(Registries.DIMENSION_TYPE, "certus");
    private static final ResourceKey<Biome> CERTUS_DEBRIS_BIOME = key(Registries.BIOME, "certus_debris");
    private static final ResourceKey<NoiseGeneratorSettings> CERTUS_NOISE_SETTINGS = key(Registries.NOISE_SETTINGS, "certus");
    private static final ResourceKey<NormalNoise.NoiseParameters> CERTUS_ISLANDS_NOISE = key(Registries.NOISE, "certus_islands");

    private TheTruthDimensionTests() {
    }

    @GameTest(template = "smoke")
    public static void certusWorldgenRegistriesLoaded(final GameTestHelper helper) {
        final RegistryAccess access = helper.getLevel().getServer().registryAccess();

        final DimensionType dimensionType = access.registryOrThrow(Registries.DIMENSION_TYPE).get(CERTUS_DIMENSION_TYPE);
        if (dimensionType == null) {
            helper.fail("dimension_type thetruth:certus failed to load");
            return;
        }
        if (dimensionType.minY() != -64 || dimensionType.height() != 320 || dimensionType.logicalHeight() != 320) {
            helper.fail("Unexpected Certus bounds: minY=" + dimensionType.minY()
                + " height=" + dimensionType.height() + " logicalHeight=" + dimensionType.logicalHeight());
            return;
        }

        final Biome biome = access.registryOrThrow(Registries.BIOME).get(CERTUS_DEBRIS_BIOME);
        if (biome == null) {
            helper.fail("biome thetruth:certus_debris failed to load");
            return;
        }

        final NoiseGeneratorSettings noiseSettings =
            access.registryOrThrow(Registries.NOISE_SETTINGS).get(CERTUS_NOISE_SETTINGS);
        if (noiseSettings == null) {
            helper.fail("noise_settings thetruth:certus failed to load");
            return;
        }
        if (!noiseSettings.defaultBlock().is(TheTruthBlocks.CERTUS_STONE.getKey())) {
            helper.fail("Certus default block is not certus_stone: " + noiseSettings.defaultBlock());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "smoke")
    public static void certusDensityProfile(final GameTestHelper helper) {
        final RegistryAccess access = helper.getLevel().getServer().registryAccess();
        final NoiseGeneratorSettings settings =
            access.registryOrThrow(Registries.NOISE_SETTINGS).get(CERTUS_NOISE_SETTINGS);
        if (settings == null) {
            helper.fail("noise_settings thetruth:certus failed to load");
            return;
        }
        final NoiseRouter router = settings.noiseRouter();

        // Below the rise band the density is multiplied by exactly 0 → empty.
        if (sample(router, 8, -60, 8) > 0.0) {
            helper.fail("Certus below-void at (8,-60,8) is not empty");
            return;
        }
        // Above the top-bias band the shell term pulls the density to -3 → empty.
        if (!(sample(router, 8, 300, 8) < 0.0)) {
            helper.fail("Certus sky at (8,300,8) is not empty");
            return;
        }
        // Mid shell: with both noise nodes degraded to 0 the stub value is
        // rise(1.0) * (clamp(0.15)*(2+0) + bias(0)) = 0.30 → solid; this proves
        // the density tree keeps its mid-band positive.
        if (!(sample(router, 8, 64, 8) > 0.0)) {
            helper.fail("Certus core at (8,64,8) has non-positive stub density: "
                + sample(router, 8, 64, 8));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "smoke")
    public static void certusIslandNoiseVaries(final GameTestHelper helper) {
        final RegistryAccess access = helper.getLevel().getServer().registryAccess();
        final NormalNoise.NoiseParameters islands = access.registryOrThrow(Registries.NOISE).get(CERTUS_ISLANDS_NOISE);
        if (islands == null) {
            helper.fail("noise thetruth:certus_islands failed to load");
            return;
        }
        // Fixed seed keeps the assertion deterministic; the mask threshold sits
        // near zero, so any meaningful amplitude produces both solid columns and
        // void gaps during real generation.
        final NormalNoise noise = NormalNoise.create(RandomSource.create(0L), islands);
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int x = -256; x <= 256; x += 8) {
            final double value = noise.getValue(x, 0, 0);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        if (max - min < 0.1) {
            helper.fail("Island mask noise is near-constant (range " + (max - min)
                + "); real terrain would not fragment");
            return;
        }
        helper.succeed();
    }

    private static double sample(final NoiseRouter router, final int x, final int y, final int z) {
        return router.finalDensity().compute(new DensityFunction.SinglePointContext(x, y, z));
    }

    private static <T> ResourceKey<T> key(final ResourceKey<? extends net.minecraft.core.Registry<T>> registryKey,
                                          final String path) {
        return ResourceKey.create(registryKey, ResourceLocation.fromNamespaceAndPath(TheTruth.MOD_ID, path));
    }
}
