package io.github.illagercpr.thetruth.certus;

import io.github.illagercpr.thetruth.registry.TheTruthParticles;
import io.github.illagercpr.thetruth.registry.TheTruthSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side emitter of the "data stream" visuals (M8, docs/00 §8): whenever
 * matter migrates as data — dissolved into unformed matter, restored by the
 * solidifier, re-read as experience — a short pulse of cyan/white motes and a
 * scan-pulse sound mark the write.
 */
public final class DataStreamEffects {

    /** Motes per pulse at the source point. */
    private static final int DEFAULT_COUNT = 12;
    /** Pulse spread. */
    private static final double SPREAD = 0.35;

    private DataStreamEffects() {
    }

    /**
     * Emits the ingest pulse: a burst of data motes plus the scan-pulse sound.
     */
    public static void ingest(final ServerLevel level, final Vec3 pos) {
        ingestMotes(level, pos);
        ingestSound(level, pos);
    }

    /** Only the mote burst (callers that play their own sound). */
    public static void ingestMotes(final ServerLevel level, final Vec3 pos) {
        level.sendParticles(
            TheTruthParticles.DATA_STREAM.get(),
            pos.x, pos.y, pos.z,
            DEFAULT_COUNT, SPREAD, SPREAD, SPREAD, 0.08);
    }

    /** Only the scan-pulse sound. */
    public static void ingestSound(final ServerLevel level, final Vec3 pos) {
        level.playSound(null, pos.x, pos.y, pos.z,
            TheTruthSounds.DATA_INGEST.get(), SoundSource.AMBIENT, 0.9F, 1.0F);
    }

    /**
     * A single mote rising from an idle unformed-matter entity: the dropped
     * "unwritten data" keeps leaking data-stream light (docs/00 §8 — Certus
     * drops present as data streams, not ordinary item entities).
     */
    public static void idleMote(final ServerLevel level, final Vec3 pos) {
        level.sendParticles(
            TheTruthParticles.DATA_STREAM.get(),
            pos.x, pos.y + 0.2, pos.z,
            1, 0.1, 0.05, 0.1, 0.02);
    }
}
