package io.github.illagercpr.thetruth.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.util.AECableType;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import io.github.illagercpr.thetruth.coverage.CertaintyCoverage;
import io.github.illagercpr.thetruth.registry.TheTruthBlockEntities;
import io.github.illagercpr.thetruth.registry.TheTruthItems;

/**
 * The Certus Anchor (M3): a grid-connected device that projects a deterministic
 * coverage field while it is powered and online. The field is the fixed-counter
 * of red line 2 (docs/00 section 4.3); it deliberately requires an ME network
 * for energy, so "bringing the network into the dimension" stays the real game.
 *
 * <p>The BE registers itself into the per-level anchor registry so coverage
 * checks are O(#anchors) instead of a block entity scan.
 */
public class CertusAnchorBlockEntity extends BlockEntity implements IActionHost, IInWorldGridNodeHost {

    /** Coverage radius in blocks (docs/02 M3 initial value). */
    public static final double FIELD_RANGE = 16.0;
    /** Constant AE/t draw while the node is part of a powered grid. */
    public static final double IDLE_POWER_USAGE = 16.0;

    private static final AnchorNodeListener NODE_LISTENER = new AnchorNodeListener();

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
        // MUST be an in-world node: otherwise no neighbour discovery happens
        // and powered AE2 devices next to the anchor never form a grid.
        .setInWorldNode(true)
        .setIdlePowerUsage(IDLE_POWER_USAGE)
        // No REQUIRE_CHANNEL: any powered network may feed the field. The grid
        // itself remains the gate (red line 1 keeps the dimension gated).
        .setVisualRepresentation(TheTruthItems.CERTUS_ANCHOR::get);

    public CertusAnchorBlockEntity(final BlockPos pos, final BlockState state) {
        super(TheTruthBlockEntities.CERTUS_ANCHOR.get(), pos, state);
    }

    public IManagedGridNode getMainNode() {
        return mainNode;
    }

    /** True while the anchor projects its field (node online and powered). */
    public boolean isFieldActive() {
        return mainNode.isReady() && mainNode.isActive();
    }

    /**
     * Field state as known on this side (M8): the client never runs the node,
     * so the active flag is mirrored through the update tag for the field-edge
     * renderer. The server always evaluates the live node.
     */
    public boolean isFieldActiveForRender() {
        return level != null && level.isClientSide() ? this.clientFieldActive : isFieldActive();
    }

    /** Mirrors the active flag to the client via the block entity update. */
    private void syncFieldActive() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("field_active", isFieldActive());
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>
        getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(final CompoundTag tag, final HolderLookup.Provider registries) {
        this.clientFieldActive = tag.getBoolean("field_active");
    }

    /** Coverage radius of this anchor's field, in blocks. */
    public double fieldRange() {
        return FIELD_RANGE;
    }

    /** Client mirror of {@link #isFieldActive()} (synced via the update tag). */
    private boolean clientFieldActive;

    @Override
    public IGridNode getActionableNode() {
        return mainNode.getNode();
    }

    /** Lets neighbour nodes discover this anchor and connect in-world. */
    @Override
    public IGridNode getGridNode(final net.minecraft.core.Direction side) {
        return mainNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(final net.minecraft.core.Direction side) {
        return AECableType.SMART;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // The node joins the grid here, when the level is guaranteed to be set.
        // create() is client-safe (no-ops on the client) and idempotent across
        // chunk reloads. Do NOT defer this via GridHelper.onFirstTick: that
        // helper dereferences getLevel() at registration time.
        if (level instanceof ServerLevel serverLevel) {
            mainNode.create(serverLevel, worldPosition);
            CertaintyCoverage.registerAnchor(serverLevel, this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        mainNode.destroy();
        if (level instanceof ServerLevel serverLevel) {
            CertaintyCoverage.unregisterAnchor(serverLevel, this);
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
    }

    /** Stateless listener; state changes mark the BE dirty and re-sync the flag. */
    private static final class AnchorNodeListener implements IGridNodeListener<CertusAnchorBlockEntity> {

        @Override
        public void onStateChanged(final CertusAnchorBlockEntity owner, final IGridNode node,
                                   final IGridNodeListener.State state) {
            owner.syncFieldActive();
        }

        @Override
        public void onSaveChanges(final CertusAnchorBlockEntity owner, final IGridNode node) {
            owner.setChanged();
        }
    }
}
