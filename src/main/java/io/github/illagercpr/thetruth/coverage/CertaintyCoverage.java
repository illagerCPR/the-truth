package io.github.illagercpr.thetruth.coverage;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.blockentity.CertusAnchorBlockEntity;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;

/**
 * Deterministic-coverage verdict (red line 2, M3). A player in Certus is
 * covered when either
 *
 * <ul>
 * <li>they stand inside an active {@link CertusAnchorBlockEntity} field, or</li>
 * <li>they stand inside the range of an active AE2 wireless access point
 *     ({@code isActive()} and distance &le; {@code getRange()}), reusing AE2's
 *     own wireless-coverage semantics (docs/01 section 2.3).</li>
 * </ul>
 *
 * <p>Anchors self-register per level on load and unregister when removed, so
 * the anchor check is O(#anchors). Access points cannot be enumerated from the
 * AE2 API, so the check scans loaded chunk block entities around the player
 * (±{@value #ACCESS_POINT_SCAN_CHUNK_RADIUS} chunks, covering ranges up to 64
 * blocks) and never forces chunk loads. The umbilical anchor (dimension-side
 * network extension) joins this verdict in M5.
 */
public final class CertaintyCoverage {

    /** Scan radius in chunks around the player for AE2 wireless access points. */
    public static final int ACCESS_POINT_SCAN_CHUNK_RADIUS = 4;

    private static final Map<ResourceKey<Level>, Set<CertusAnchorBlockEntity>> ACTIVE_ANCHORS =
        new ConcurrentHashMap<>();

    /** True when the position is inside any coverage source of the given level. */
    public static boolean isPosCovered(final Level level, final BlockPos pos) {
        if (withinAnchorField(level, pos)) {
            return true;
        }
        return level instanceof ServerLevel serverLevel && nearActiveAccessPoint(serverLevel, pos).isPresent();
    }

    /** Coverage verdict for a player (positional; the caller handles layers/creative). */
    public static boolean isPlayerCovered(final ServerPlayer player) {
        return isPosCovered(player.level(), player.blockPosition());
    }

    /**
     * Human-readable source of the covering field ("anchor@x,y,z" /
     * "wireless_access_point@x,y,z"), or "none". Debug/UX helper.
     */
    public static String describeCoverage(final ServerPlayer player) {
        final Level level = player.level();
        final BlockPos pos = player.blockPosition();
        for (final CertusAnchorBlockEntity anchor : anchorsOf(level)) {
            if (anchor.isFieldActive() && distanceSq(anchor.getBlockPos(), pos) <= rangeSq(anchor)) {
                return "anchor@" + anchor.getBlockPos().toShortString();
            }
        }
        if (level instanceof ServerLevel serverLevel) {
            final Optional<BlockPos> accessPoint = nearActiveAccessPoint(serverLevel, pos);
            if (accessPoint.isPresent()) {
                return "wireless_access_point@" + accessPoint.get().toShortString();
            }
        }
        return "none";
    }

    /** Anchor field test, kept level-typed so GameTests can call it directly. */
    public static boolean withinAnchorField(final Level level, final BlockPos pos) {
        for (final CertusAnchorBlockEntity anchor : anchorsOf(level)) {
            if (anchor.isFieldActive() && distanceSq(anchor.getBlockPos(), pos) <= rangeSq(anchor)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Nearest active wireless access point covering {@code pos}, or empty.
     * Scans already-loaded chunks only ({@code requireChunk = false}).
     */
    public static Optional<BlockPos> nearActiveAccessPoint(final ServerLevel level, final BlockPos pos) {
        final int centerX = pos.getX() >> 4;
        final int centerZ = pos.getZ() >> 4;
        for (int dx = -ACCESS_POINT_SCAN_CHUNK_RADIUS; dx <= ACCESS_POINT_SCAN_CHUNK_RADIUS; dx++) {
            for (int dz = -ACCESS_POINT_SCAN_CHUNK_RADIUS; dz <= ACCESS_POINT_SCAN_CHUNK_RADIUS; dz++) {
                if (level.getChunkSource().getChunkNow(centerX + dx, centerZ + dz) instanceof LevelChunk chunk) {
                    for (final var entry : chunk.getBlockEntities().entrySet()) {
                        if (entry.getValue() instanceof IWirelessAccessPoint accessPoint
                            && accessPoint.isActive()
                            && accessPoint.getLocation().isInWorld(level)
                            && distanceSq(accessPoint.getLocation().getPos(), pos)
                                <= rangeSq(accessPoint.getRange())) {
                            return Optional.of(entry.getKey());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- registry

    public static void registerAnchor(final ServerLevel level, final CertusAnchorBlockEntity anchor) {
        anchorsOf(level).add(anchor);
        TheTruth.LOGGER.debug("Certus anchor registered at {} ({})", anchor.getBlockPos(), level.dimension());
    }

    public static void unregisterAnchor(final ServerLevel level, final CertusAnchorBlockEntity anchor) {
        anchorsOf(level).remove(anchor);
    }

    private static Set<CertusAnchorBlockEntity> anchorsOf(final Level level) {
        return ACTIVE_ANCHORS.computeIfAbsent(level.dimension(), key -> ConcurrentHashMap.newKeySet());
    }

    private static double distanceSq(final BlockPos a, final BlockPos b) {
        final double dx = a.getX() - b.getX();
        final double dy = a.getY() - b.getY();
        final double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static double rangeSq(final CertusAnchorBlockEntity anchor) {
        final double range = anchor.fieldRange();
        return range * range;
    }

    private static double rangeSq(final double range) {
        return range * range;
    }

    private CertaintyCoverage() {
    }
}
