package io.github.illagercpr.thetruth.endgame;

import appeng.api.storage.StorageCells;
import net.minecraft.world.item.ItemStack;

/**
 * Which items wear the tiny Certus mark after the endgame (docs/00 §9):
 * every ME storage cell. The truth set is whatever the AE2 handler registry
 * acknowledges — including third-party and our own Certus Cell — so the
 * visual gate and the mechanics share one source of truth
 * ({@link StorageCells#isCellHandled}).
 *
 * <p>{@link #ME_STORAGE_CELL_IDS} enumerates the AE2 19.2.17 cell items for
 * the client decoration registration (the event registers per-item). Spatial
 * storage cells are deliberately absent: they are not ME network storage
 * (verified docs/02 M4 — no spatial handler in {@code StorageCells}, they
 * never enter {@code getAvailableStacks()}), so they are not "ME storage
 * cells" in the docs/00 sense. Item ids verified against the AE2 19.2.17 jar.
 */
public final class MarkedStorageCells {

    /** AE2 19.2.17 ME storage cell items (jar-verified registry ids). */
    public static final String[] ME_STORAGE_CELL_IDS = {
        "item_storage_cell_1k",
        "item_storage_cell_4k",
        "item_storage_cell_16k",
        "item_storage_cell_64k",
        "item_storage_cell_256k",
        "fluid_storage_cell_1k",
        "fluid_storage_cell_4k",
        "fluid_storage_cell_16k",
        "fluid_storage_cell_64k",
        "fluid_storage_cell_256k",
        "portable_item_cell_1k",
        "portable_item_cell_4k",
        "portable_item_cell_16k",
        "portable_item_cell_64k",
        "portable_item_cell_256k",
        "portable_fluid_cell_1k",
        "portable_fluid_cell_4k",
        "portable_fluid_cell_16k",
        "portable_fluid_cell_64k",
        "portable_fluid_cell_256k",
        "creative_storage_cell",
    };

    /**
     * The mark gate: a non-empty stack the AE2 cell registry acknowledges.
     * Cheap by design (a handler-chain probe, no inventory instantiation).
     */
    public static boolean isMarkedCell(final ItemStack stack) {
        return !stack.isEmpty() && StorageCells.isCellHandled(stack);
    }

    private MarkedStorageCells() {
    }
}
