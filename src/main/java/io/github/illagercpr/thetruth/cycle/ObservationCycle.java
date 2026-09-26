package io.github.illagercpr.thetruth.cycle;

import net.minecraft.util.Mth;

/**
 * The observation cycle of the Certus dimension (docs/00 §2.2): the dimension
 * does not use the vanilla day/night loop but its own observation rhythm that
 * drives atmosphere, creature pacing and resource availability.
 *
 * <p><b>Timeline (one cycle = {@value #CYCLE_TICKS} ticks = 16 min):</b>
 * <pre>
 *   [0, 11400)      OBSERVED core      ≈ 9.5 min, bright cold-white sky
 *   [11400, 12700)  fading transition  dusk, sky drops into darkness
 *   [12700, 17900)  UNOBSERVED core    ≈ 6.5 min, quartz glow in darkness
 *   [17900, 19200)  rising transition  dawn back into the observed phase
 * </pre>
 *
 * <p><b>Driving the sky</b>: the Certus level's {@code dayTime} is overwritten
 * every server tick ({@code ObservationCycleTicker}) with
 * {@link #mapToSkyPhase}, a piecewise-linear map from the cycle tick to the
 * vanilla sky phase [0, 24000). That makes the vanilla sky renderer, sky
 * darkness and fog brightness follow the observation rhythm for free, with
 * smooth transitions at both phase boundaries. The pure functions here are
 * unit-tested in {@code TheTruthDimensionTests}.
 */
public final class ObservationCycle {

    /** Total length of one observation cycle, in ticks (16 minutes). */
    public static final int CYCLE_TICKS = 19200;
    /** Ticks of the observed core phase. */
    public static final int OBSERVED_CORE_END = 11400;
    /** Ticks of the observed→unobserved transition (dusk). */
    public static final int DUSK_END = 12700;
    /** Ticks of the unobserved core phase end (dawn starts here). */
    public static final int UNOBSERVED_CORE_END = 17900;

    /** Which side of the cycle a tick belongs to. */
    public enum Phase {
        OBSERVED,
        FADING,
        UNOBSERVED,
        RISING
    }

    private ObservationCycle() {
    }

    /** Normalizes any world time into a cycle tick [0, {@value #CYCLE_TICKS}). */
    public static long cycleTick(final long gameTime) {
        return Math.floorMod(gameTime, CYCLE_TICKS);
    }

    /** The phase of a cycle tick. */
    public static Phase phaseOf(final long cycleTick) {
        if (cycleTick < OBSERVED_CORE_END) {
            return Phase.OBSERVED;
        }
        if (cycleTick < DUSK_END) {
            return Phase.FADING;
        }
        return cycleTick < UNOBSERVED_CORE_END ? Phase.UNOBSERVED : Phase.RISING;
    }

    /**
     * Observation strength in [0, 1]: 1 = fully observed (bright), 0 = fully
     * unobserved (dark). Linear inside both transitions and smoothed by a
     * smoothstep so the endpoints have zero slope (no visible kink).
     */
    public static double observationBlend(final long cycleTick) {
        final Phase phase = phaseOf(cycleTick);
        return switch (phase) {
            case OBSERVED -> 1.0;
            case UNOBSERVED -> 0.0;
            case FADING -> 1.0 - smoothstep(fraction(cycleTick, OBSERVED_CORE_END, DUSK_END));
            case RISING -> smoothstep(fraction(cycleTick, UNOBSERVED_CORE_END, CYCLE_TICKS));
        };
    }

    /**
     * Maps a cycle tick to the vanilla sky phase [0, 24000) the renderer
     * understands, keeping the mapping continuous at every segment boundary
     * (including the wrap-around back to tick 0).
     *
     * <p>Segment layout (sky phase: 0 = sunrise, 12000 = dusk, 22000 = day):
     * <pre>
     *   [0, 11400)      → [1000, 11500)   observed core: high sun
     *   [11400, 12700)  → [11500, 14000)  dusk falls
     *   [12700, 17900)  → [14000, 21400)  night core
     *   [17900, 19200]  → [21400, 25000]  dawn rises (25000 mod 24000 = 1000)
     * </pre>
     */
    public static long mapToSkyPhase(final long cycleTick) {
        final long t = cycleTick;
        if (t < OBSERVED_CORE_END) {
            return lerpSegment(t, 0, OBSERVED_CORE_END, 1000, 11500);
        }
        if (t < DUSK_END) {
            return lerpSegment(t, OBSERVED_CORE_END, DUSK_END, 11500, 14000);
        }
        if (t < UNOBSERVED_CORE_END) {
            return lerpSegment(t, DUSK_END, UNOBSERVED_CORE_END, 14000, 21400);
        }
        return lerpSegment(t, UNOBSERVED_CORE_END, CYCLE_TICKS, 21400, 25000) % 24000L;
    }

    private static long lerpSegment(final long t, final long fromTick, final long toTick,
            final long fromPhase, final long toPhase) {
        final double fraction = fraction(t, fromTick, toTick);
        return Math.round(fromPhase + (toPhase - fromPhase) * fraction);
    }

    private static double fraction(final long t, final long fromTick, final long toTick) {
        return Mth.clamp((double) (t - fromTick) / (double) (toTick - fromTick), 0.0, 1.0);
    }

    private static double smoothstep(final double x) {
        return x * x * (3.0 - 2.0 * x);
    }
}
