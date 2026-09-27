package io.github.illagercpr.thetruth.coverage;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.registry.TheTruthDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The deep observatory's own deterministic field (M6, closes M3 登记项 5).
 * The vanished civilization's installation still runs: inside its square the
 * world is stable regardless of any network the player could reach. This is
 * the boss arena's guarantee — the fight never fights the instability curve.
 */
public final class ObservatoryField {

    /** Half-size of the field square (platform + one ring of slack). */
    public static final int FIELD_HALF_SIZE = 26;
    public static final int FIELD_BOTTOM_Y = -52;
    public static final int FIELD_TOP_EXCLUSIVE_Y = -36;

    private ObservatoryField() {
    }

    /**
     * Whether the position sits inside the observatory's own coverage. The
     * dimension key is an explicit parameter so the check stays a pure
     * function GameTests can drive.
     */
    public static boolean isCovered(final ResourceKey<Level> dimension, final BlockPos pos) {
        if (dimension != TheTruthDimensions.CERTUS) {
            return false;
        }
        if (Math.abs(pos.getX()) > FIELD_HALF_SIZE || Math.abs(pos.getZ()) > FIELD_HALF_SIZE) {
            return false;
        }
        return pos.getY() >= FIELD_BOTTOM_Y && pos.getY() < FIELD_TOP_EXCLUSIVE_Y;
    }

    /** Debug/UX label. */
    public static String describe() {
        return TheTruth.MOD_ID + ":observatory";
    }
}
