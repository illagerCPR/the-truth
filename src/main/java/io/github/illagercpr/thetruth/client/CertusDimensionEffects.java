package io.github.illagercpr.thetruth.client;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Dimension special effects of the Certus dimension type
 * ({@code "effects": "thetruth:certus"} in the dimension_type JSON).
 *
 * <p>The vanilla sky renderer passes the current fog brightness (0 = night,
 * 1 = day) to {@link #getBrightnessDependentFogColor}; because the server maps
 * the observation cycle onto the vanilla sky phases, that brightness already
 * follows the observation rhythm. We re-tint it onto the Certus palette:
 * an even cold-white haze while observed, near-total darkness when not.
 */
public final class CertusDimensionEffects extends DimensionSpecialEffects {

    /** Cold-white haze of the observed phase. */
    private static final Vec3 OBSERVED_FOG = new Vec3(0.74, 0.81, 0.90);
    /** Near-black of the unobserved phase. */
    private static final Vec3 UNOBSERVED_FOG = new Vec3(0.012, 0.016, 0.024);

    public CertusDimensionEffects() {
        super(192.0F, true, DimensionSpecialEffects.SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(final Vec3 fogColor, final float brightness) {
        double blend = Mth.clamp((brightness - 0.08) / 0.92, 0.0, 1.0);
        blend = blend * blend * (3.0 - 2.0 * blend);
        return new Vec3(
            UNOBSERVED_FOG.x + (OBSERVED_FOG.x - UNOBSERVED_FOG.x) * blend,
            UNOBSERVED_FOG.y + (OBSERVED_FOG.y - UNOBSERVED_FOG.y) * blend,
            UNOBSERVED_FOG.z + (OBSERVED_FOG.z - UNOBSERVED_FOG.z) * blend);
    }

    @Override
    public boolean isFoggyAt(final int x, final int y) {
        return false;
    }

    @Override
    public float[] getSunriseColor(final float timeOfDay, final float partialTicks) {
        // No warm sunrise: Certus has only cold transitions.
        return null;
    }
}
