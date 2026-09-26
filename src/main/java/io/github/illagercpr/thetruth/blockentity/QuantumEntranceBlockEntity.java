package io.github.illagercpr.thetruth.blockentity;

import java.util.UUID;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.AECableType;
import io.github.illagercpr.thetruth.registry.TheTruthBlockEntities;
import io.github.illagercpr.thetruth.registry.TheTruthBlocks;
import io.github.illagercpr.thetruth.registry.TheTruthItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Quantum Entrance core (M4): the single BE of the 3x3 quantum ring. The
 * same block serves both sides of the bridge — in the Overworld it binds key
 * pairs and sends players to Certus, in Certus it works as the tether that
 * brings them home (docs/02 M4 design decision 5).
 *
 * <p>The AE2 node follows the M3 pattern: an in-world node created in
 * {@link #onLoad} (GridHelper.onFirstTick must NOT be used — it dereferences
 * getLevel() at registration time), and neighbour discovery only works because
 * the mod registers the IN_WORLD_GRID_NODE_HOST capability for this BE.
 */
public class QuantumEntranceBlockEntity extends BlockEntity implements IActionHost, IInWorldGridNodeHost {

    /** Constant AE/t draw while the node is part of a powered grid. */
    public static final double IDLE_POWER_USAGE = 4.0;

    private static final EntranceNodeListener NODE_LISTENER = new EntranceNodeListener();

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
        .setInWorldNode(true)
        .setIdlePowerUsage(IDLE_POWER_USAGE)
        .setVisualRepresentation(TheTruthItems.QUANTUM_ENTRANCE::get);

    /** Entanglement pair minted by this core; null until the binding ritual. */
    private UUID pairId;

    /** Certus position this core last sent a player to / heard a return from. */
    private BlockPos lastCertusPos;

    /** Cached multi-block verdict; recomputed when a neighbour changes. */
    private boolean structureFormed;
    private boolean structureDirty = true;

    public QuantumEntranceBlockEntity(final BlockPos pos, final BlockState state) {
        super(TheTruthBlockEntities.QUANTUM_ENTRANCE.get(), pos, state);
    }

    public IManagedGridNode getMainNode() {
        return mainNode;
    }

    /** True once the 3x3 ring is complete around this core. */
    public boolean isStructureFormed() {
        if (structureDirty) {
            structureFormed = RingStructure.isFormed(level, worldPosition);
            structureDirty = false;
        }
        return structureFormed;
    }

    /** Marks the cached structure verdict stale; called on neighbour changes. */
    public void markStructureDirty() {
        structureDirty = true;
        if (level != null && !level.isClientSide()) {
            setChanged();
        }
    }

    public UUID getPairId() {
        return pairId;
    }

    /** Mints a fresh entanglement pair id, replacing any previous binding. */
    public void rebindToNewPair() {
        pairId = UUID.randomUUID();
        setChanged();
    }

    public BlockPos getLastCertusPos() {
        return lastCertusPos;
    }

    public void setLastCertusPos(final BlockPos pos) {
        lastCertusPos = pos.immutable();
        setChanged();
    }

    @Override
    public IGridNode getActionableNode() {
        return mainNode.getNode();
    }

    /** Lets neighbour nodes discover this core and connect in-world. */
    @Override
    public IGridNode getGridNode(final Direction side) {
        return mainNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(final Direction side) {
        return AECableType.SMART;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // See CertusAnchorBlockEntity: the node joins the grid here, when the
        // level is guaranteed to be set. create() is idempotent and client-safe.
        if (level instanceof ServerLevel serverLevel) {
            mainNode.create(serverLevel, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        mainNode.destroy();
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
        if (pairId != null) {
            tag.putUUID("pair_id", pairId);
        }
        if (lastCertusPos != null) {
            tag.putLong("last_certus_pos", lastCertusPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
        pairId = tag.hasUUID("pair_id") ? tag.getUUID("pair_id") : null;
        lastCertusPos = tag.contains("last_certus_pos")
            ? BlockPos.of(tag.getLong("last_certus_pos"))
            : null;
        structureDirty = true;
    }

    /** Stateless listener; state changes only mark the BE dirty. */
    private static final class EntranceNodeListener implements IGridNodeListener<QuantumEntranceBlockEntity> {

        @Override
        public void onStateChanged(final QuantumEntranceBlockEntity owner, final IGridNode node,
                                   final IGridNodeListener.State state) {
            owner.setChanged();
        }

        @Override
        public void onSaveChanges(final QuantumEntranceBlockEntity owner, final IGridNode node) {
            owner.setChanged();
        }
    }

    /**
     * Ring geometry probe (package-private for GameTests). A formed ring is a
     * 3x3 vertical loop: the core sits at the bottom-center, the middle-center
     * cell stays open as the cavity, and the seven remaining cells hold Certus
     * Frames. Probed along the X axis first, then the Z axis.
     */
    static final class RingStructure {

        private RingStructure() {
        }

        static boolean isFormed(final Level level, final BlockPos core) {
            if (level == null) {
                return false;
            }
            return matches(level, core, X_FRAME_OFFSETS) || matches(level, core, Z_FRAME_OFFSETS);
        }

        private static boolean matches(final Level level, final BlockPos core, final BlockPos[] frameOffsets) {
            final BlockState frame = TheTruthBlocks.CERTUS_FRAME.get().defaultBlockState();
            for (final BlockPos offset : frameOffsets) {
                if (!level.getBlockState(core.offset(offset)).is(frame.getBlock())) {
                    return false;
                }
            }
            return true;
        }

        /** Frame offsets of a ring aligned along the X axis (dx, dy, dz). */
        static final BlockPos[] X_FRAME_OFFSETS = {
            new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0),
            new BlockPos(-1, 1, 0), new BlockPos(1, 1, 0),
            new BlockPos(-1, 2, 0), new BlockPos(0, 2, 0), new BlockPos(1, 2, 0)
        };

        /** Frame offsets of a ring aligned along the Z axis. */
        static final BlockPos[] Z_FRAME_OFFSETS = {
            new BlockPos(0, 0, -1), new BlockPos(0, 0, 1),
            new BlockPos(0, 1, -1), new BlockPos(0, 1, 1),
            new BlockPos(0, 2, -1), new BlockPos(0, 2, 0), new BlockPos(0, 2, 1)
        };
    }
}
