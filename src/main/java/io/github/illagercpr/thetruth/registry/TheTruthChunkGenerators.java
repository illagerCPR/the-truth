package io.github.illagercpr.thetruth.registry;

import com.mojang.serialization.MapCodec;
import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.worldgen.CertusChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry of the Certus chunk generator codecs. The generator is referenced
 * by data/thetruth/dimension/certus.json as {@code {"type": "thetruth:certus"}}.
 */
public final class TheTruthChunkGenerators {

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
        DeferredRegister.create(Registries.CHUNK_GENERATOR, TheTruth.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<CertusChunkGenerator>> CERTUS =
        GENERATORS.register("certus", () -> CertusChunkGenerator.CODEC);

    private TheTruthChunkGenerators() {
    }
}
