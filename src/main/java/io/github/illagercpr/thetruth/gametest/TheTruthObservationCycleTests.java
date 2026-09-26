package io.github.illagercpr.thetruth.gametest;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.cycle.ObservationCycle;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M2 observation-cycle tests: phase boundaries, monotonic blending inside the
 * transitions and a continuous, wrap-around-consistent sky-phase mapping. The
 * mapping is what the server pushes into the level's {@code dayTime} every
 * tick, so its continuity is what "the transition looks smooth" means at the
 * engine level; the visual rendering stays a manual runClient step.
 */
@GameTestHolder(TheTruth.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheTruthObservationCycleTests {

    private TheTruthObservationCycleTests() {
    }

    @GameTest(template = "smoke")
    public static void observationCyclePhases(final GameTestHelper helper) {
        if (ObservationCycle.phaseOf(0) != ObservationCycle.Phase.OBSERVED
                || ObservationCycle.phaseOf(11399) != ObservationCycle.Phase.OBSERVED) {
            helper.fail("the observed core must span [0, 11400)");
            return;
        }
        if (ObservationCycle.phaseOf(11400) != ObservationCycle.Phase.FADING
                || ObservationCycle.phaseOf(12699) != ObservationCycle.Phase.FADING) {
            helper.fail("the fading transition must span [11400, 12700)");
            return;
        }
        if (ObservationCycle.phaseOf(12700) != ObservationCycle.Phase.UNOBSERVED
                || ObservationCycle.phaseOf(17899) != ObservationCycle.Phase.UNOBSERVED) {
            helper.fail("the unobserved core must span [12700, 17900)");
            return;
        }
        if (ObservationCycle.phaseOf(17900) != ObservationCycle.Phase.RISING
                || ObservationCycle.phaseOf(19199) != ObservationCycle.Phase.RISING) {
            helper.fail("the rising transition must span [17900, 19200)");
            return;
        }
        if (ObservationCycle.cycleTick(19200) != 0 || ObservationCycle.cycleTick(-1) != 19199) {
            helper.fail("cycle ticks must wrap into [0, 19200)");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "smoke")
    public static void observationBlendIsMonotonic(final GameTestHelper helper) {
        if (ObservationCycle.observationBlend(0) != 1.0
                || ObservationCycle.observationBlend(11399) != 1.0) {
            helper.fail("the observed core must blend to 1.0");
            return;
        }
        if (ObservationCycle.observationBlend(12700) != 0.0
                || ObservationCycle.observationBlend(17899) != 0.0) {
            helper.fail("the unobserved core must blend to 0.0");
            return;
        }
        double previous = ObservationCycle.observationBlend(11400);
        if (previous != 1.0) {
            helper.fail("the fading transition must start at 1.0");
            return;
        }
        for (long tick = 11500; tick < 12700; tick += 100) {
            final double blend = ObservationCycle.observationBlend(tick);
            if (blend > previous) {
                helper.fail("fading blend must decrease: " + previous + " -> " + blend + " at " + tick);
                return;
            }
            previous = blend;
        }
        // Inside the transition the smoothstep only approaches the endpoints;
        // exact 0.0 is reached once the unobserved core starts.
        if (!(previous < 0.02) || ObservationCycle.observationBlend(12700) != 0.0) {
            helper.fail("the fading transition must converge to 0.0 at the unobserved core");
            return;
        }
        previous = ObservationCycle.observationBlend(17900);
        for (long tick = 18000; tick < 19200; tick += 100) {
            final double blend = ObservationCycle.observationBlend(tick);
            if (blend < previous) {
                helper.fail("rising blend must increase: " + previous + " -> " + blend + " at " + tick);
                return;
            }
            previous = blend;
        }
        if (!(previous > 0.98) || ObservationCycle.observationBlend(19200) != 1.0) {
            helper.fail("the rising transition must converge to 1.0 (wrapping into the observed core)");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "smoke")
    public static void observationSkyPhaseMappingIsContinuous(final GameTestHelper helper) {
        long previous = ObservationCycle.mapToSkyPhase(0);
        if (previous < 1000 || previous > 11500) {
            helper.fail("cycle start must map into the bright observed band, got " + previous);
            return;
        }
        if (ObservationCycle.mapToSkyPhase(12000) < 11500
                || ObservationCycle.mapToSkyPhase(12000) > 14000) {
            helper.fail("dusk tick 12000 must map into the dusk band");
            return;
        }
        if (ObservationCycle.mapToSkyPhase(14000) < 14000
                || ObservationCycle.mapToSkyPhase(14000) > 21400) {
            helper.fail("night tick 14000 must map into the night band");
            return;
        }
        if (ObservationCycle.mapToSkyPhase(18000) < 21400) {
            helper.fail("dawn tick 18000 must map at or after the night end");
            return;
        }
        // Per-tick continuity across the whole cycle: a mapping the server
        // pushes into dayTime must never jump between consecutive ticks.
        // Distance is measured on the 24000 ring, the mapping legitimately
        // wraps at dawn.
        for (long tick = 1; tick < ObservationCycle.CYCLE_TICKS; tick++) {
            final long current = ObservationCycle.mapToSkyPhase(tick);
            long step = Math.abs(current - previous);
            step = Math.min(step, 24000 - step);
            if (step > 8) {
                helper.fail("sky phase jumped between " + previous + " and " + current + " at tick " + tick);
                return;
            }
            previous = current;
        }
        // The wrap-around: the last tick must continue into tick 0 modulo 24000.
        final long last = ObservationCycle.mapToSkyPhase(ObservationCycle.CYCLE_TICKS - 1);
        final long first = ObservationCycle.mapToSkyPhase(0);
        long wrapDistance = Math.abs(last - first);
        wrapDistance = Math.min(wrapDistance, 24000 - wrapDistance);
        if (wrapDistance > 8) {
            helper.fail("the cycle must wrap continuously: last " + last + " vs first " + first);
            return;
        }
        helper.succeed();
    }
}
