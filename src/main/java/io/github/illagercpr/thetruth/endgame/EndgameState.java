package io.github.illagercpr.thetruth.endgame;

import io.github.illagercpr.thetruth.registry.TheTruthDimensions;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The irreversible endgame marker of the world (M7). Once the player brings
 * the Last Record back to the Overworld and reads it out, the world flips
 * into its final state and stays there (docs/00 §9):
 *
 * <ul>
 *   <li>every ME storage cell wears the tiny Certus mark,</li>
 *   <li>umbilical anchors are acknowledged in the Overworld (and any other
 *       dimension that is not Certus itself),</li>
 *   <li>the flag is persisted in the Overworld's data storage and has no code
 *       path back — the truth cannot be un-brought.</li>
 * </ul>
 *
 * <p>A save that never went through the endgame keeps the flag absent: the
 * default state is the unmarked world.
 */
public final class EndgameState extends SavedData {

    /** SavedData key inside the Overworld's data folder. */
    public static final String DATA_NAME = "thetruth_endgame";

    private static final String TAG_TRUTH_BROUGHT_BACK = "truth_brought_back";

    private boolean truthBroughtBack;

    /** Fresh, unflipped state — a world that has not seen the endgame. */
    public EndgameState() {
    }

    /** Loads the persisted state (missing key = never flipped). */
    public static EndgameState load(final CompoundTag tag, final HolderLookup.Provider provider) {
        final EndgameState state = new EndgameState();
        state.truthBroughtBack = tag.getBoolean(TAG_TRUTH_BROUGHT_BACK);
        return state;
    }

    /** Returns the per-server state, stored in the Overworld's data storage. */
    public static EndgameState get(final MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(EndgameState::new, EndgameState::load), DATA_NAME);
    }

    /** Whether the endgame has happened in this save. */
    public static boolean isUnlocked(final MinecraftServer server) {
        return get(server).truthBroughtBack;
    }

    /**
     * Flips the world into its final state. Returns {@code true} only for the
     * first call — afterwards the marker is a no-op, and nothing in the code
     * base ever writes {@code false} back.
     *
     * @return whether this call was the irreversible first flip
     */
    public boolean markTruthBroughtBack() {
        if (truthBroughtBack) {
            return false;
        }
        truthBroughtBack = true;
        setDirty();
        return true;
    }

    public boolean isTruthBroughtBack() {
        return truthBroughtBack;
    }

    /**
     * The umbilical gate: Certus may always host an anchor (dimension-side
     * gameplay, docs/02 M5), but any other dimension must first have gone
     * through the endgame. Pure so the GameTest can assert all branches
     * without touching the shared flag.
     */
    public static boolean allowsUmbilicalBinding(final boolean endgameUnlocked,
                                                 final ResourceKey<Level> dimension) {
        return dimension == TheTruthDimensions.CERTUS || endgameUnlocked;
    }

    /** Server-side wrapper reading the saved flag. */
    public static boolean allowsUmbilicalBinding(final Level dimension) {
        final MinecraftServer server = dimension.getServer();
        return allowsUmbilicalBinding(server != null && isUnlocked(server), dimension.dimension());
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider provider) {
        tag.putBoolean(TAG_TRUTH_BROUGHT_BACK, truthBroughtBack);
        return tag;
    }
}
