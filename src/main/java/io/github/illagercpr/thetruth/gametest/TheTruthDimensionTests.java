package io.github.illagercpr.thetruth.gametest;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.registry.TheTruthBlocks;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M1 dimension skeleton tests.
 *
 * <p>Vanilla {@code GameTestServer.create} hard-codes the FLAT world preset and
 * discards datapack LevelStem JSON, so a datapack dimension can never be
 * instantiated inside a GameTest server. The mechanics are therefore verified
 * one layer down: the datapack worldgen registries must parse and expose the
 * Certus entries, and the density function must sample solid core / floating
 * fringe / void exactly as designed. Entering the dimension ("see terrain,
 * re-enter stably") stays a manual runClient acceptance step.
 */
@GameTestHolder(TheTruth.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheTruthDimensionTests {

    private static final ResourceKey<DimensionType> CERTUS_DIMENSION_TYPE = key(Registries.DIMENSION_TYPE, "certus");
    private static final ResourceKey<Biome> CERTUS_DEBRIS_BIOME = key(Registries.BIOME, "certus_debris");
    private static final ResourceKey<NoiseGeneratorSettings> CERTUS_NOISE_SETTINGS = key(Registries.NOISE_SETTINGS, "certus");

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

        // Core shell (y 16..112): shell weight 1.0, min density = 2.0 - 1.0 > 0 → always solid.
        if (!(sample(router, 8, 64, 8) > 0.0)) {
            helper.fail("Certus core at (8,64,8) is not solid");
            return;
        }
        // Floating fringe (fall band): shell 0.78125 at y=140 keeps density > 0.
        if (!(sample(router, 8, 140, 8) > 0.0)) {
            helper.fail("Certus fringe at (8,140,8) is not solid");
            return;
        }
        // Void below the rise band and above the fall band: shell is exactly 0.
        if (Math.abs(sample(router, 8, -60, 8)) > 1.0E-9) {
            helper.fail("Certus below-void at (8,-60,8) is not empty: " + sample(router, 8, -60, 8));
            return;
        }
        if (Math.abs(sample(router, 8, 300, 8)) > 1.0E-9) {
            helper.fail("Certus sky at (8,300,8) is not empty");
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
