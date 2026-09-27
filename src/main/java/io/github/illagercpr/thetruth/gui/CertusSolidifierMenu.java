package io.github.illagercpr.thetruth.gui;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.blockentity.CertusSolidifierBlockEntity;
import io.github.illagercpr.thetruth.registry.TheTruthItems;
import io.github.illagercpr.thetruth.registry.TheTruthMenus;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Certus Solidifier GUI (M8): one input slot, one take-only output slot and
 * the owning network's power readout. There is deliberately no progress bar —
 * the write is instantaneous (docs/02 M8 decision 5).
 *
 * <p>The menu keeps working only while its block entity exists; when the
 * block is broken mid-session, {@link #stillValid} ends the session.
 */
public class CertusSolidifierMenu extends AbstractContainerMenu {

    /** Machine slot coordinates inside the 176x166 GUI texture. */
    public static final int INPUT_X = 62;
    public static final int OUTPUT_X = 110;
    public static final int SLOT_Y = 34;

    /** Index of the "stored power" data value (AE, truncated to int). */
    public static final int DATA_STORED_POWER = 0;
    /** Index of the "network powered" flag. */
    public static final int DATA_POWERED = 1;

    private final CertusSolidifierBlockEntity blockEntity;
    private final ContainerData data;

    public CertusSolidifierMenu(final int containerId, final Inventory playerInventory,
                                final CertusSolidifierBlockEntity blockEntity, final ContainerData data) {
        super(TheTruthMenus.CERTUS_SOLIDIFIER.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        final IItemHandler items = blockEntity.getExternalHandler();
        this.addSlot(new SlotItemHandler(items, 0, INPUT_X, SLOT_Y) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        this.addSlot(new SlotItemHandler(items, 1, OUTPUT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(final ItemStack stack) {
                return false;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, 9 + row * 9 + column,
                    8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }

        this.addDataSlots(data);
    }

    /** Server-side construction from an open request. */
    public CertusSolidifierMenu(final int containerId, final Inventory playerInventory,
                                final CertusSolidifierBlockEntity blockEntity) {
        this(containerId, playerInventory, blockEntity, new SimpleContainerData(2));
        this.refreshPowerData();
    }

    /** Client-side construction: NeoForge wrote the block position into the buffer. */
    public static CertusSolidifierMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                                   final RegistryFriendlyByteBuf buffer) {
        final BlockPos pos = buffer.readBlockPos();
        final Level level = playerInventory.player.level();
        if (level.getBlockEntity(pos) instanceof CertusSolidifierBlockEntity blockEntity) {
            return new CertusSolidifierMenu(containerId, playerInventory, blockEntity,
                new SimpleContainerData(2));
        }
        return new CertusSolidifierMenu(containerId, playerInventory, null,
            new SimpleContainerData(2));
    }

    /** Reads the live network power into the data slots (server side only). */
    public void refreshPowerData() {
        if (this.blockEntity == null || this.blockEntity.getLevel() == null
            || this.blockEntity.getLevel().isClientSide()) {
            return;
        }
        final double stored = this.blockEntity.getStoredAEPower();
        this.data.set(DATA_STORED_POWER, (int) Math.min(Integer.MAX_VALUE, stored));
        this.data.set(DATA_POWERED, this.blockEntity.isNetworkPowered() ? 1 : 0);
    }

    @Override
    public void broadcastChanges() {
        // The readout stays live while the GUI is open; data slots sync to the client.
        this.refreshPowerData();
        super.broadcastChanges();
    }

    /** Readout for the screen — always the synced data value. */
    public double getDisplayedStoredPower() {
        return (double) this.data.get(DATA_STORED_POWER);
    }

    public boolean isNetworkPowered() {
        return this.data.get(DATA_POWERED) != 0;
    }

    @Override
    public boolean stillValid(final Player player) {
        return this.blockEntity != null && this.blockEntity.isUsableBy(player);
    }

    @Nullable
    public CertusSolidifierBlockEntity getBlockEntity() {
        return this.blockEntity;
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        final Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        final ItemStack stack = slot.getItem();
        final ItemStack original = stack.copy();
        final int machineSlots = 2;
        final int playerSlots = this.slots.size() - machineSlots;

        if (index < machineSlots) {
            // Machine → player.
            if (!this.moveItemStackTo(stack, machineSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(TheTruthItems.UNFORMED_MATTER.get())) {
            // Player → machine input (only unformed matter is accepted).
            if (!this.moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }
}
