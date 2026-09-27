package io.github.illagercpr.thetruth.storage;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import io.github.illagercpr.thetruth.item.CertusCellItem;
import net.minecraft.world.item.ItemStack;

/**
 * AE2 bridge that lets drives mount the Certus Cell (M5). Registered via
 * {@code StorageCells.addCellHandler} during mod construction — public API,
 * no mixin (docs/02 M5 tech notes).
 */
public final class CertusCellHandler implements ICellHandler {

    public static final CertusCellHandler INSTANCE = new CertusCellHandler();

    private CertusCellHandler() {
    }

    @Override
    public boolean isCell(final ItemStack stack) {
        return stack.getItem() instanceof CertusCellItem;
    }

    @Override
    public StorageCell getCellInventory(final ItemStack stack, final ISaveProvider saveProvider) {
        return stack.getItem() instanceof CertusCellItem ? new CertusCellInventory(stack, saveProvider) : null;
    }
}
