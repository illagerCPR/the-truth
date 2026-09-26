package io.github.illagercpr.thetruth.uncertainty;

import io.github.illagercpr.thetruth.worldgen.CertusTerrainShape;

/**
 * Pure math of the three-tier instability curve (docs/00 section 4.2, parameters
 * from docs/02 M3 parameter table). Layer boundaries reuse the terrain layer
 * constants so terrain and mechanics can never drift apart.
 *
 * <p>Surface never accumulates (teaching-only); the middle layer accumulates
 * slowly towards a randomization threshold and is fully reversible; the deep
 * layer accumulates fast and opens a salvage window before anything is lost.
 * Nothing here deletes by itself — deletion is the manager's decision after an
 * announced, salvagable window.
 */
public final class UncertaintyCurve {

    /** Hard ceiling for the accumulated value. */
    public static final int CAP = 100;

    /** Surface layer accumulates nothing (pure teaching noise). */
    public static final int SURFACE_RATE_PER_SECOND = 0;
    /** Middle layer: slow accumulation. */
    public static final int MIDDLE_RATE_PER_SECOND = 1;
    /** Deep layer: fast accumulation (CAP in 20 s). */
    public static final int DEEP_RATE_PER_SECOND = 5;

    /** Middle layer: randomization kicks in at this value... */
    public static final int MIDDLE_RANDOMIZE_THRESHOLD = 60;
    /** ...and stacks recover once the player falls below this (hysteresis). */
    public static final int RECOVER_THRESHOLD = 50;

    /** Middle layer: while still over the threshold, re-randomize every 10 s. */
    public static final int RANDOMIZE_INTERVAL_TICKS = 200;

    /** Deep layer: value that opens the warning window. */
    public static final int DEEP_DELETE_THRESHOLD = 100;
    /** Deep layer: salvage window length, 80 ticks = 4 s (spec allows 3–5 s). */
    public static final int DEEP_WARNING_TICKS = 80;

    /** Decay per second while covered (also used when outside the dimension). */
    public static final int COVERED_DECAY_PER_SECOND = 4;
    /** Salvage outcome: surviving uncertainty after being saved in the window. */
    public static final int SALVAGE_UNCERTAINTY = CAP / 2;

    /** Certus strata relevant to the curve. */
    public enum Layer {
        SURFACE,
        MIDDLE,
        DEEP
    }

    /** Layer of a Certus column by block Y, mirroring the terrain geometry. */
    public static Layer layerOf(final int blockY) {
        if (blockY >= CertusTerrainShape.DEBRIS_LAYER_MIN_Y) {
            return Layer.SURFACE;
        }
        if (blockY >= CertusTerrainShape.UNOBSERVED_LAYER_MAX_Y) {
            return Layer.MIDDLE;
        }
        return Layer.DEEP;
    }

    /** Accumulation rate per second while uncovered. */
    public static int ratePerSecond(final Layer layer) {
        return switch (layer) {
            case SURFACE -> SURFACE_RATE_PER_SECOND;
            case MIDDLE -> MIDDLE_RATE_PER_SECOND;
            case DEEP -> DEEP_RATE_PER_SECOND;
        };
    }

    private UncertaintyCurve() {
    }
}
