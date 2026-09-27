package io.github.illagercpr.thetruth.item;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.IBasicCellItem;
import io.github.illagercpr.thetruth.storage.CertusCellInventory;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Certus Cell (M5): a huge-capacity ME storage cell whose contents are a
 * "pagination" of the Certus storage layer (docs/00 §6.2). Offline / detached
 * from a network the contents are <b>unformed</b> — invisible, but never lost;
 * reattaching restores them (AE2's own mount semantics). Presentation: a
 * constant enchantment glint plus the unformed tooltip note.
 */
public class CertusCellItem extends Item implements IBasicCellItem {

    public CertusCellItem(final Properties properties) {
        super(properties);
    }

    // -------------------------------------------------------- IBasicCellItem

    @Override
    public AEKeyType getKeyType() {
        return AEKeyType.items();
    }

    @Override
    public int getBytes(final ItemStack stack) {
        return CertusCellInventory.BYTES;
    }

    @Override
    public int getBytesPerType(final ItemStack stack) {
        return CertusCellInventory.BYTES_PER_TYPE;
    }

    @Override
    public int getTotalTypes(final ItemStack stack) {
        return CertusCellInventory.TOTAL_TYPES;
    }

    @Override
    public double getIdleDrain() {
        return CertusCellInventory.IDLE_DRAIN;
    }

    @Override
    public boolean isBlackListed(final ItemStack stack, final AEKey key) {
        // No recursive paging: a Certus Cell never goes into a Certus Cell.
        return key instanceof AEItemKey itemKey && itemKey.is(this);
    }

    @Override
    public boolean storableInStorageCell() {
        return false;
    }

    // ------------------------------------------------ ICellWorkbenchItem

    @Override
    public boolean isEditable(final ItemStack stack) {
        return false;
    }

    @Override
    public FuzzyMode getFuzzyMode(final ItemStack stack) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(final ItemStack stack, final FuzzyMode mode) {
        // Not editable; no partition config in M5.
    }

    // ------------------------------------------------------------ presentation

    /** All Certus Cells shimmer: their ledger is data, never ordinary matter. */
    @Override
    public boolean isFoil(final ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        addCellInformationToTooltip(stack, tooltip);
    }

    @Override
    public void addCellInformationToTooltip(final ItemStack stack, final List<Component> tooltip) {
        tooltip.add(Component.translatable("item.thetruth.certus_cell.tooltip.unformed")
            .withStyle(ChatFormatting.AQUA));
    }
}
