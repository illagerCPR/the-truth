package io.github.illagercpr.thetruth.registry;

import io.github.illagercpr.thetruth.TheTruth;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

/**
 * Identity keys of the three Certus layer biomes (M2). The biome JSONs
 * themselves are datapack files; biomes are assigned vertically by the custom
 * chunk generator instead of by a biome source.
 *
 * <ul>
 *   <li>{@code certus_debris} — surface layer "The Debris Belt" (y &ge; 80)</li>
 *   <li>{@code certus_sediment} — middle layer "The Sediment" (-16 &le; y &lt; 80)</li>
 *   <li>{@code certus_unobserved} — deep layer "The Uncollapsed" (y &lt; -16)</li>
 * </ul>
 */
public final class TheTruthBiomes {

    public static final ResourceKey<Biome> CERTUS_DEBRIS = key("certus_debris");
    public static final ResourceKey<Biome> CERTUS_SEDIMENT = key("certus_sediment");
    public static final ResourceKey<Biome> CERTUS_UNOBSERVED = key("certus_unobserved");

    private TheTruthBiomes() {
    }

    private static ResourceKey<Biome> key(final String path) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(TheTruth.MOD_ID, path));
    }
}
