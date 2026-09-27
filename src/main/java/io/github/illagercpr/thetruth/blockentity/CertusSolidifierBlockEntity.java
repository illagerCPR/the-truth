package io.github.illagercpr.thetruth.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.AECableType;
import io.github.illagercpr.thetruth.registry.TheTruthBlockEntities;
import io.github.illagercpr.thetruth.registry.TheTruthDataComponents;
import io.github.illagercpr.thetruth.registry.TheTruthItems;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Certus Solidifier (M5, docs/00 §6.2): "writes data that fell out of the
 * mother ledger back in as a certain instance".
 *
 * <ul>
 *   <li>unformed matter carrying {@code unformed_content} → the original
 *       stack (restore path, the salvage loop of the instability curve);</li>
 *   <li>free unformed matter → one Certus Matrix (base material).</li>
 * </ul>
 *
 * <p>Each operation draws {@value #WORK_COST_AE} AE from the owning network
 * (red line 3: the writing step stays behind AE infrastructure). The machine
 * is GUI-less: items move through the exposed {@link IItemHandler} capability
 * or by hand (docs/02 M5 decision 1).
 */
public class CertusSolidifierBlockEntity extends BlockEntity implements IActionHost, IInWorldGridNodeHost {

    public static final double IDLE_POWER_USAGE = 2.0;
    public static final double WORK_COST_AE = 200.0;
    public static final int WORK_INTERVAL_TICKS = 20;

    private static final SolidifierNodeListener NODE_LISTENER = new SolidifierNodeListener();

    private final SolidifierStackHandler items = new SolidifierStackHandler();

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
        .setInWorldNode(true)
        .setIdlePowerUsage(IDLE_POWER_USAGE)
        .setVisualRepresentation(TheTruthItems.CERTUS_SOLIDIFIER::get);

    public CertusSolidifierBlockEntity(final BlockPos pos, final BlockState state) {
        super(TheTruthBlockEntities.CERTUS_SOLIDIFIER.get(), pos, state);
    }

    // ------------------------------------------------------------------ work

    public static void serverTick(final ServerLevel level, final BlockPos pos, final BlockState state,
                                  final CertusSolidifierBlockEntity blockEntity) {
        if (level.getGameTime() % WORK_INTERVAL_TICKS == 0) {
            blockEntity.tryWork();
        }
    }

    private void tryWork() {
        final ItemStack input = this.items.getStackInSlot(0);
        if (!input.is(TheTruthItems.UNFORMED_MATTER.get())) {
            return;
        }
        final ItemStack result = resolveResult(input);
        final ItemStack output = this.items.getStackInSlot(1);
        if (!output.isEmpty()
            && (!ItemStack.isSameItemSameComponents(output, result)
                || output.getCount() + result.getCount() > output.getMaxStackSize())) {
            return;
        }
        final IGrid grid = this.mainNode.getGrid();
        if (grid == null) {
            return;
        }
        final double available = grid.getEnergyService()
            .extractAEPower(WORK_COST_AE, Actionable.SIMULATE, PowerMultiplier.ONE);
        if (available + 0.001 < WORK_COST_AE) {
            return;
        }
        grid.getEnergyService().extractAEPower(WORK_COST_AE, Actionable.MODULATE, PowerMultiplier.ONE);

        input.shrink(1);
        if (output.isEmpty()) {
            this.items.setStackInSlot(1, result);
        } else {
            output.grow(result.getCount());
        }
        // M8: the write-back shows as a data-stream pulse (docs/00 §8).
        if (this.level instanceof ServerLevel serverLevel) {
            io.github.illagercpr.thetruth.certus.DataStreamEffects.ingest(
                serverLevel, Vec3.atCenterOf(this.worldPosition));
        }
    }

    /**
     * Restore path: hand back the recorded original stack (the randomization
     * marker is scrubbed); free matter becomes one Certus Matrix.
     */
    private static ItemStack resolveResult(final ItemStack unformed) {
        final ItemStack content = io.github.illagercpr.thetruth.item.UnformedMatterItem.getContent(unformed);
        if (content.isEmpty()) {
            return new ItemStack(TheTruthItems.CERTUS_MATRIX.get(), 1);
        }
        final ItemStack restored = content.copy();
        restored.remove(TheTruthDataComponents.UNCERTAIN_COUNT.get());
        return restored;
    }

    // -------------------------------------------------------------- by-hand

    /** Inserts a held stack into the input slot; returns the remainder. */
    public ItemStack insertInput(final ItemStack held) {
        if (!held.is(TheTruthItems.UNFORMED_MATTER.get())) {
            return held;
        }
        final ItemStack input = this.items.getStackInSlot(0);
        if (input.isEmpty()) {
            this.items.setStackInSlot(0, held.copy());
            return ItemStack.EMPTY;
        }
        return held;
    }

    public ItemStack extractOutput() {
        return takeSlot(1);
    }

    public ItemStack extractInput() {
        return takeSlot(0);
    }

    private ItemStack takeSlot(final int which) {
        final ItemStack stack = this.items.getStackInSlot(which);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        this.items.setStackInSlot(which, ItemStack.EMPTY);
        return stack;
    }

    /** Snapshot of both slots for drop-on-break. */
    public Container getDroppableInventory() {
        final SimpleContainer container = new SimpleContainer(2);
        container.setItem(0, this.items.getStackInSlot(0).copy());
        container.setItem(1, this.items.getStackInSlot(1).copy());
        return container;
    }

    // ----------------------------------------------------------- item slots

    private final class SolidifierStackHandler extends ItemStackHandler {

        private SolidifierStackHandler() {
            super(2);
        }

        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            return slot == 0 && stack.is(TheTruthItems.UNFORMED_MATTER.get());
        }

        @Override
        public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
            if (slot == 1) {
                return stack; // output slot is take-only
            }
            return super.insertItem(slot, stack, simulate);
        }

        @Override
        protected void onContentsChanged(final int slot) {
            setChanged();
        }
    }

    /** The capability handed to hoppers, AE2 buses and similar automation. */
    public IItemHandler getExternalHandler() {
        return this.items;
    }

    // ------------------------------------------------------------- GUI readout

    /** Stored AE power of the owning network, for the GUI readout (M8). */
    public double getStoredAEPower() {
        final IGrid grid = this.mainNode.getGrid();
        return grid == null ? 0.0 : grid.getEnergyService().getStoredPower();
    }

    /** Whether the owning network is powered, for the GUI readout (M8). */
    public boolean isNetworkPowered() {
        final IGrid grid = this.mainNode.getGrid();
        return grid != null && grid.getEnergyService().isNetworkPowered();
    }

    /** Standard "still usable" check for the GUI session. */
    public boolean isUsableBy(final Player player) {
        return !this.isRemoved()
            && player.distanceToSqr(this.worldPosition.getX() + 0.5,
                this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5) <= 64.0;
    }

    // ------------------------------------------------------------- AE2 node

    @Override
    public IGridNode getActionableNode() {
        return this.mainNode.getNode();
    }

    @Override
    public IGridNode getGridNode(@Nullable final Direction side) {
        return this.mainNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(@Nullable final Direction side) {
        return AECableType.SMART;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel) {
            // create() is idempotent and client-safe; see AGENTS.md (M3 pitfalls).
            this.mainNode.create(serverLevel, this.worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        this.mainNode.destroy();
    }

    // -------------------------------------------------------------- persistence

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", this.items.serializeNBT(registries));
        this.mainNode.saveToNBT(tag);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items.deserializeNBT(registries, tag.getCompound("items"));
        this.mainNode.loadFromNBT(tag);
    }

    /** Stateless listener; state changes only mark the BE dirty. */
    private static final class SolidifierNodeListener implements IGridNodeListener<CertusSolidifierBlockEntity> {

        @Override
        public void onStateChanged(final CertusSolidifierBlockEntity owner, final IGridNode node,
                                   final IGridNodeListener.State state) {
            owner.setChanged();
        }

        @Override
        public void onSaveChanges(final CertusSolidifierBlockEntity owner, final IGridNode node) {
            owner.setChanged();
        }
    }
}
