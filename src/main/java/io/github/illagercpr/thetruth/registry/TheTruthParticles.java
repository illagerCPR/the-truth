package io.github.illagercpr.thetruth.registry;

import io.github.illagercpr.thetruth.TheTruth;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Particles of The Truth (M8, docs/00 §8 data-fied visuals): the Certus data
 * stream — cyan/white square motes in the visual language of AE2 ingestion,
 * emitted whenever matter migrates as data (dissolve into unformed matter,
 * solidifier restore, experience return).
 */
public final class TheTruthParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
        DeferredRegister.create(Registries.PARTICLE_TYPE, TheTruth.MOD_ID);

    /** Cyan/white data motes drifting upward; no per-instance options needed. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DATA_STREAM =
        PARTICLES.register("data_stream",
            () -> new SimpleParticleType(false));

    private TheTruthParticles() {
    }
}
