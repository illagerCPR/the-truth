package io.github.illagercpr.thetruth.blockentity;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.AECableType;
import io.github.illagercpr.thetruth.coverage.UmbilicalNetwork;
import io.github.illagercpr.thetruth.registry.TheTruthBlockEntities;
import io.github.illagercpr.thetruth.registry.TheTruthItems;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Umbilical Anchor (M5): the dimension-side endpoint of an umbilical cord.
 * Paired via a bound Entanglement Key (the quantum-entanglement credential,
 * docs/02 M5 decision 5), it
 *
 * <ul>
 *   <li>projects the paired Overworld network's deterministic coverage into
 *       Certus ({@link UmbilicalNetwork#isPosCovered});</li>
 *   <li>keeps the Overworld endpoint's chunk loaded with a FORCED chunk ticket
 *       while it runs — the "persistent cross-dimension loading" deferred from
 *       M4 (docs/02 M4 decision 3). One direction suffices: the Certus side is
 *       only relevant while a player is here, and a player here already loads
 *       its chunk.</li>
 * </ul>
 */
public class UmbilicalAnchorBlockEntity extends BlockEntity implements IActionHost, IInWorldGridNodeHost {

    public static final double IDLE_POWER_USAGE = 32.0;

    private static final UmbilicalNodeListener NODE_LISTENER = new UmbilicalNodeListener();

    @Nullable
    private UUID pairId;
    /**
     * The forced ticket this anchor currently holds, recorded as the exact
     * (dimension, chunk) it was issued against. Release must never depend on
     * the peer lookup: once the peer is destroyed it is gone from the
     * registry, and a peer-driven release would leak the ticket forever
     * (permanently force-loading the peer chunk — runClient 2026-09-27).
     */
    @Nullable
    private ResourceKey<Level> ticketDimension;
    @Nullable
    private ChunkPos ticketChunk;

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
        .setInWorldNode(true)
        .setIdlePowerUsage(IDLE_POWER_USAGE)
        .setVisualRepresentation(TheTruthItems.UMBILICAL_ANCHOR::get);

    public UmbilicalAnchorBlockEntity(final BlockPos pos, final BlockState state) {
        super(TheTruthBlockEntities.UMBILICAL_ANCHOR.get(), pos, state);
    }

    // ------------------------------------------------------------------ pair

    @Nullable
    public UUID getPairId() {
        return this.pairId;
    }

    public boolean hasPair() {
        return this.pairId != null;
    }

    /** Binds this anchor to a pair id; the caller has passed {@link UmbilicalNetwork#canBind}. */
    public void bindPair(final UUID pairId) {
        this.pairId = pairId;
        setChanged();
        if (this.level instanceof ServerLevel serverLevel) {
            UmbilicalNetwork.register(this);
        }
    }

    public boolean isNodeActive() {
        return this.mainNode.isReady() && this.mainNode.isActive();
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel) {
            this.mainNode.create(serverLevel, this.worldPosition);
            if (this.pairId != null) {
                UmbilicalNetwork.register(this);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        this.mainNode.destroy();
        if (this.level instanceof ServerLevel serverLevel) {
            // Release before unregistering: the recorded ticket target is
            // self-sufficient, but this order also reads clearest.
            releaseTicket();
            UmbilicalNetwork.unregister(this);
        }
    }

    // --------------------------------------------------- forced chunk loading

    /**
     * Runs every work tick: hold a FORCED ticket on the peer chunk while the
     * cord is up, release it otherwise. addRegionTicket is idempotent for the
     * same position, so re-issuing is harmless.
     */
    public void maintainTicket() {
        if (this.level == null || !UmbilicalNetwork.isFieldActive(this)) {
            releaseTicket();
            return;
        }
        final UmbilicalAnchorBlockEntity peer = UmbilicalNetwork.peerOf(this);
        if (peer == null || peer.getLevel() == this.level) {
            releaseTicket();
            return;
        }
        if (peer.getLevel() instanceof ServerLevel peerLevel) {
            final ChunkPos peerChunk = new ChunkPos(peer.getBlockPos());
            peerLevel.getChunkSource().addRegionTicket(TicketType.FORCED, peerChunk, 2, peerChunk);
            this.ticketDimension = peerLevel.dimension();
            this.ticketChunk = peerChunk;
        }
    }

    /**
     * Releases the ticket recorded on this anchor, if any. The target comes
     * from the record, not from the peer registry — the peer may already be
     * destroyed (and unregistered) by the time this runs.
     */
    private void releaseTicket() {
        if (this.ticketChunk != null && this.ticketDimension != null
            && this.level instanceof ServerLevel serverLevel) {
            final ServerLevel peerLevel = serverLevel.getServer().getLevel(this.ticketDimension);
            if (peerLevel != null) {
                peerLevel.getChunkSource().removeRegionTicket(
                    TicketType.FORCED, this.ticketChunk, 2, this.ticketChunk);
            }
        }
        this.ticketDimension = null;
        this.ticketChunk = null;
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

    // -------------------------------------------------------------- persistence

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.pairId != null) {
            tag.putUUID("pair_id", this.pairId);
        }
        this.mainNode.saveToNBT(tag);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.pairId = tag.hasUUID("pair_id") ? tag.getUUID("pair_id") : null;
        this.mainNode.loadFromNBT(tag);
    }

    private static final class UmbilicalNodeListener implements IGridNodeListener<UmbilicalAnchorBlockEntity> {

        @Override
        public void onStateChanged(final UmbilicalAnchorBlockEntity owner, final IGridNode node,
                                   final IGridNodeListener.State state) {
            owner.setChanged();
        }

        @Override
        public void onSaveChanges(final UmbilicalAnchorBlockEntity owner, final IGridNode node) {
            owner.setChanged();
        }
    }
}
