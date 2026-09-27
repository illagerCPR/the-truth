package io.github.illagercpr.thetruth.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import io.github.illagercpr.thetruth.registry.TheTruthDataComponents;
import io.github.illagercpr.thetruth.registry.TheTruthItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * In-memory inventory of a Certus Cell (M5). The ledger lives in the host
 * {@link ItemStack}'s {@code certus_cell_content} data component, so contents
 * survive being taken off the network — "unformed, not lost" (docs/00 §6.2).
 *
 * <p>Byte accounting (docs/02 M5, simplified model): every type costs
 * {@value #BYTES_PER_TYPE} bytes plus one byte per {@code amountPerByte}
 * units, capped at {@value #BYTES}/{@value #TOTAL_TYPES}. Unlike AE2's own
 * cells this is our own accounting; the numbers are initial values for M8.
 */
public final class CertusCellInventory implements StorageCell {

    public static final int BYTES = 1_048_576;
    public static final int TOTAL_TYPES = 256;
    public static final int BYTES_PER_TYPE = 8;
    public static final double IDLE_DRAIN = 2.0;

    private final ItemStack host;
    private final ISaveProvider saveProvider;
    private final KeyCounter contents = new KeyCounter();

    public CertusCellInventory(final ItemStack host, final ISaveProvider saveProvider) {
        this.host = host;
        this.saveProvider = saveProvider;
        final List<GenericStack> stored =
            host.get(TheTruthDataComponents.CERTUS_CELL_CONTENT.get());
        if (stored != null) {
            for (final GenericStack entry : stored) {
                if (entry != null && entry.amount() > 0) {
                    this.contents.add(entry.what(), entry.amount());
                }
            }
        }
    }

    // ------------------------------------------------------------ capacity

    private static long amountBytes(final long amount, final int perByte) {
        return (amount + perByte - 1) / perByte;
    }

    /** Bytes currently used: per type a fixed header plus its amount bytes. */
    public long usedBytes() {
        long total = 0;
        for (final var entry : contents) {
            final AEKey key = entry.getKey();
            total += BYTES_PER_TYPE + amountBytes(entry.getLongValue(), key.getType().getAmountPerByte());
        }
        return total;
    }

    private long maxFittableAmount(final AEKey what) {
        final long current = contents.get(what);
        if (current == 0 && contents.size() >= TOTAL_TYPES) {
            return 0;
        }
        long free = BYTES - usedBytes();
        if (current == 0) {
            free -= BYTES_PER_TYPE;
        }
        if (free <= 0) {
            return 0;
        }
        return free * what.getType().getAmountPerByte();
    }

    // ------------------------------------------------- MEStorage contract

    @Override
    public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        if (!(what instanceof AEItemKey itemKey)) {
            return 0;
        }
        if (itemKey.is(TheTruthItems.CERTUS_CELL.get())) {
            return 0; // no recursive paging (belt and braces beside IBasicCellItem#isBlackListed)
        }
        final long fittable = Math.min(amount, maxFittableAmount(what));
        if (fittable <= 0) {
            return 0;
        }
        if (mode == Actionable.MODULATE) {
            this.contents.add(what, fittable);
            writeBack();
        }
        return fittable;
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        final long current = this.contents.get(what);
        final long extracted = Math.min(amount, current);
        if (extracted <= 0 || mode == Actionable.SIMULATE) {
            return extracted;
        }
        this.contents.remove(what, extracted);
        if (this.contents.get(what) == 0) {
            this.contents.remove(what);
        }
        writeBack();
        return extracted;
    }

    @Override
    public void getAvailableStacks(final KeyCounter out) {
        out.addAll(this.contents);
    }

    @Override
    public Component getDescription() {
        return Component.translatable("item.thetruth.certus_cell");
    }

    // ------------------------------------------------- StorageCell contract

    @Override
    public CellState getStatus() {
        if (this.contents.isEmpty()) {
            return CellState.EMPTY;
        }
        if (this.contents.size() >= TOTAL_TYPES) {
            return CellState.TYPES_FULL;
        }
        return usedBytes() >= BYTES ? CellState.FULL : CellState.NOT_EMPTY;
    }

    @Override
    public double getIdleDrain() {
        return IDLE_DRAIN;
    }

    /** A Certus Cell never fits inside another storage cell (no recursive paging). */
    @Override
    public boolean canFitInsideCell() {
        return false;
    }

    /** Writes the ledger back into the host item (also called on every change). */
    @Override
    public void persist() {
        writeBack();
    }

    // ------------------------------------------------------------- helpers

    private void writeBack() {
        final List<GenericStack> list = new ArrayList<>(this.contents.size());
        for (final var entry : this.contents) {
            list.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }
        this.host.set(TheTruthDataComponents.CERTUS_CELL_CONTENT.get(), list);
        if (this.saveProvider != null) {
            this.saveProvider.saveChanges();
        }
    }

    /** Content types currently recorded (server side; used by tests/debug). */
    public int storedTypes() {
        return this.contents.size();
    }

    /** Builds a cell inventory for an item stack (test/diagnostic helper). */
    public static CertusCellInventory of(final ItemStack host) {
        return new CertusCellInventory(host, null);
    }

    /** True when the host item stack is a Certus Cell item. */
    public static boolean isCertusCell(final ItemStack stack) {
        return stack.getItem() == TheTruthItems.CERTUS_CELL.get();
    }
}
