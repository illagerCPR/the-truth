package io.github.illagercpr.thetruth.coverage;

import io.github.illagercpr.thetruth.blockentity.UmbilicalAnchorBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Per-server registry of umbilical anchor pairs (M5). Two anchors bound to the
 * same entanglement pair id — one per dimension — form an umbilical cord; the
 * Certus-side anchor projects the Overworld network's deterministic coverage
 * into Certus and keeps the Overworld endpoint chunk loaded (docs/02 M5).
 *
 * <p>Limits (docs/00 §4.3 "数量受限"): at most {@value #MAX_ACTIVE_PAIRS}
 * active pairs per server and {@value #MAX_ANCHORS_PER_PAIR} anchors per pair,
 * never two in the same dimension. The global pair count is checked here at
 * binding time; it is per-server global state, so it is verified by hand (the
 * GameTest cross-plot trap, docs/02 M5 decision 9).
 */
public final class UmbilicalNetwork {

    public static final int MAX_ACTIVE_PAIRS = 2;
    public static final int MAX_ANCHORS_PER_PAIR = 2;

    /** Coverage radius in blocks (matches the Certus Anchor field). */
    public static final double FIELD_RANGE = 16.0;

    private static final Map<UUID, Set<UmbilicalAnchorBlockEntity>> PAIRS = new ConcurrentHashMap<>();

    /**
     * Why an anchor in {@code dimension} may or may not bind to
     * {@code pairId}. A fresh pair needs a free global pair slot; an existing
     * pair needs a free anchor slot and no anchor yet in that dimension.
     * Note a single-ended pair (one anchor bound, its peer not yet placed)
     * already counts against the global pair budget — the binding exists.
     */
    public enum BindVerdict {
        OK,
        /** This pair already has both of its anchors (one per dimension). */
        PAIR_FULL,
        /** The server already holds the maximum number of bound pairs. */
        GLOBAL_LIMIT
    }

    public static BindVerdict checkBind(final UUID pairId, final Level dimension) {
        final Set<UmbilicalAnchorBlockEntity> anchors = liveAnchors(pairId);
        if (anchors == null) {
            return PAIRS.size() < MAX_ACTIVE_PAIRS ? BindVerdict.OK : BindVerdict.GLOBAL_LIMIT;
        }
        if (anchors.size() >= MAX_ANCHORS_PER_PAIR) {
            return BindVerdict.PAIR_FULL;
        }
        return anchors.stream().noneMatch(
            anchor -> anchor.getLevel() != null && anchor.getLevel().dimension() == dimension.dimension())
                ? BindVerdict.OK
                : BindVerdict.PAIR_FULL;
    }

    /** Convenience wrapper for callers that only need a yes/no verdict. */
    public static boolean canBind(final UUID pairId, final Level dimension) {
        return checkBind(pairId, dimension) == BindVerdict.OK;
    }

    /** The pair's anchor set with already-destroyed entries filtered out. */
    @Nullable
    private static Set<UmbilicalAnchorBlockEntity> liveAnchors(final UUID pairId) {
        final Set<UmbilicalAnchorBlockEntity> anchors = PAIRS.get(pairId);
        if (anchors == null) {
            return null;
        }
        anchors.removeIf(UmbilicalAnchorBlockEntity::isRemoved);
        if (anchors.isEmpty()) {
            PAIRS.remove(pairId);
            return null;
        }
        return anchors;
    }

    public static void register(final UmbilicalAnchorBlockEntity anchor) {
        if (anchor.getPairId() == null) {
            return;
        }
        PAIRS.computeIfAbsent(anchor.getPairId(), id -> ConcurrentHashMap.newKeySet()).add(anchor);
    }

    public static void unregister(final UmbilicalAnchorBlockEntity anchor) {
        if (anchor.getPairId() == null) {
            // Unbound anchors are never in the registry; a null key would
            // throw inside ConcurrentHashMap.get and blow up the caller —
            // including vanilla's block removal / chunk unload paths
            // (ghost blocks, runClient 2026-09-27 + GameTest shutdown log).
            return;
        }
        final Set<UmbilicalAnchorBlockEntity> anchors = PAIRS.get(anchor.getPairId());
        if (anchors != null) {
            anchors.remove(anchor);
            if (anchors.isEmpty()) {
                PAIRS.remove(anchor.getPairId());
            }
        }
    }

    /** The other anchor of this pair (loaded thanks to the forced ticket), or null. */
    public static UmbilicalAnchorBlockEntity peerOf(final UmbilicalAnchorBlockEntity self) {
        if (self.getPairId() == null) {
            return null;
        }
        final Set<UmbilicalAnchorBlockEntity> anchors = PAIRS.get(self.getPairId());
        if (anchors == null) {
            return null;
        }
        for (final UmbilicalAnchorBlockEntity anchor : anchors) {
            if (anchor != self && anchor.getLevel() != null) {
                return anchor;
            }
        }
        return null;
    }

    /** Active count: anchors online, powered, paired and with a live peer. */
    public static boolean isFieldActive(final UmbilicalAnchorBlockEntity anchor) {
        return anchor.hasPair() && anchor.isNodeActive() && peerOf(anchor) != null;
    }

    /** Umbilical coverage verdict for one position (coverage-package check). */
    public static boolean isPosCovered(final Level level, final BlockPos pos) {
        final double rangeSq = FIELD_RANGE * FIELD_RANGE;
        for (final Set<UmbilicalAnchorBlockEntity> anchors : PAIRS.values()) {
            for (final UmbilicalAnchorBlockEntity anchor : anchors) {
                if (anchor.getLevel() == level && isFieldActive(anchor)
                    && anchor.getBlockPos().distSqr(pos) <= rangeSq) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Number of distinct active pair ids (debug readings). */
    public static int activePairCount() {
        int count = 0;
        for (final Set<UmbilicalAnchorBlockEntity> anchors : PAIRS.values()) {
            for (final UmbilicalAnchorBlockEntity anchor : anchors) {
                if (isFieldActive(anchor)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    /** Flat view over every registered anchor (drop data-fication, M6). */
    public static Iterable<UmbilicalAnchorBlockEntity> allAnchors() {
        final List<UmbilicalAnchorBlockEntity> all = new ArrayList<>();
        for (final Set<UmbilicalAnchorBlockEntity> anchors : PAIRS.values()) {
            all.addAll(anchors);
        }
        return all;
    }

    private UmbilicalNetwork() {
    }
}
