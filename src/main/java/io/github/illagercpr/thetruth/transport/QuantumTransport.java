package io.github.illagercpr.thetruth.transport;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import io.github.illagercpr.thetruth.blockentity.QuantumEntranceBlockEntity;
import io.github.illagercpr.thetruth.registry.TheTruthDataComponents;
import io.github.illagercpr.thetruth.registry.TheTruthDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

/**
 * The M4 transport ritual: binding key pairs and moving the player between the
 * Overworld and Certus ("stored and transmitted", not walking through a door).
 *
 * <p>Gate design (red line 1): the Overworld core requires a formed ring, a
 * powered ME network with spatial IO infrastructure (a spatial IO port plus a
 * valid pylon array), and energy for the jump. The Certus tether only requires
 * the formed ring and a bound key — going home must always work (kill-switch
 * insurance, docs/02 M4 decision 4).
 */
public final class QuantumTransport {

    /** One-time AE cost of minting a key pair at the Overworld core. */
    public static final int BINDING_COST_AE = 10_000;
    /** AE cost per outbound jump (Overworld -> Certus). The return is free. */
    public static final int OUTBOUND_COST_AE = 5_000;

    /**
     * Registry id of AE2's spatial IO port, the anchor device of the spatial
     * IO gate. Resolved by registry id because the block entity class is an
     * AE2 internal (only appeng.api may be imported).
     */
    private static final ResourceLocation SPATIAL_IO_PORT_ID =
        ResourceLocation.fromNamespaceAndPath("ae2", "spatial_io_port");

    /** Why a transport request was refused; {@code null} paths mean success. */
    public enum Failure {
        NOT_FORMED("thetruth.message.entrance.not_formed"),
        NO_NETWORK("thetruth.message.entrance.no_network"),
        NO_SPATIAL_IO("thetruth.message.entrance.no_spatial_io"),
        NO_POWER("thetruth.message.entrance.no_power"),
        NO_KEY("thetruth.message.entrance.no_key"),
        KEY_MISMATCH("thetruth.message.entrance.key_mismatch"),
        NO_ARRIVAL("thetruth.message.entrance.no_arrival"),
        NO_HOME_LEVEL("thetruth.message.entrance.no_home_level");

        public final String messageKey;

        Failure(final String messageKey) {
            this.messageKey = messageKey;
        }
    }

    private QuantumTransport() {
    }

    // ------------------------------------------------------------------
    // Binding ritual (Overworld core, unpaired key in hand)
    // ------------------------------------------------------------------

    /**
     * Validates the binding ritual without mutating anything. Returns null
     * when the pair may be minted.
     */
    @Nullable
    public static Failure validateBinding(final QuantumEntranceBlockEntity core, final ItemStack key,
                                          final Level level) {
        if (!core.isStructureFormed()) {
            return Failure.NOT_FORMED;
        }
        if (level.dimension() != Level.OVERWORLD) {
            // The pair is minted at the Overworld side only.
            return Failure.KEY_MISMATCH;
        }
        if (key.get(TheTruthDataComponents.ENTANGLEMENT_PAIR.get()) != null) {
            return Failure.KEY_MISMATCH;
        }
        final Failure networkGate = checkNetworkGate(core, BINDING_COST_AE);
        return networkGate;
    }

    /**
     * Runs the binding ritual: the core mints a pair id, the held key receives
     * the pair data, and a second key of the pair is handed to the player (one
     * stays at the core, one travels with the player, docs/00 section 5.1).
     * Rebinding overwrites the previous pair (recovery path for lost keys).
     */
    @Nullable
    public static Failure performBinding(final ServerPlayer player, final QuantumEntranceBlockEntity core,
                                         final ItemStack key) {
        final Failure failure = validateBinding(core, key, core.getLevel());
        if (failure != null) {
            return failure;
        }
        final IGrid grid = gridOf(core);
        if (grid == null) {
            return Failure.NO_NETWORK;
        }
        final double drawn = grid.getEnergyService()
            .extractAEPower(BINDING_COST_AE, Actionable.MODULATE, PowerMultiplier.ONE);
        if (drawn + 0.001 < BINDING_COST_AE) {
            return Failure.NO_POWER;
        }

        core.rebindToNewPair();
        final var pair = new EntanglementPairData(core.getPairId(),
            core.getLevel().dimension().location(), core.getBlockPos());
        key.set(TheTruthDataComponents.ENTANGLEMENT_PAIR.get(), pair);

        final ItemStack second = new ItemStack(key.getItem());
        second.set(TheTruthDataComponents.ENTANGLEMENT_PAIR.get(), pair);
        if (!player.getInventory().add(second)) {
            player.drop(second, false);
        }

        player.displayClientMessage(Component.translatable("thetruth.message.entrance.bound"), false);
        return null;
    }

    // ------------------------------------------------------------------
    // Outbound (Overworld core -> Certus)
    // ------------------------------------------------------------------

    /** Validates the outbound jump without spending energy or moving anyone. */
    @Nullable
    public static Failure validateOutbound(final QuantumEntranceBlockEntity core, final ItemStack key) {
        final var pair = key.get(TheTruthDataComponents.ENTANGLEMENT_PAIR.get());
        if (pair == null) {
            return Failure.NO_KEY;
        }
        if (core.getPairId() == null || !core.getPairId().equals(pair.pairId())) {
            return Failure.KEY_MISMATCH;
        }
        if (!core.isStructureFormed()) {
            return Failure.NOT_FORMED;
        }
        return checkNetworkGate(core, OUTBOUND_COST_AE);
    }

    /** Runs the outbound jump: validates, charges energy, and teleports. */
    @Nullable
    public static Failure performOutbound(final ServerPlayer player, final QuantumEntranceBlockEntity core,
                                          final ItemStack key) {
        final Failure failure = validateOutbound(core, key);
        if (failure != null) {
            return failure;
        }
        final IGrid grid = gridOf(core);
        if (grid == null) {
            return Failure.NO_NETWORK;
        }
        final double drawn = grid.getEnergyService()
            .extractAEPower(OUTBOUND_COST_AE, Actionable.MODULATE, PowerMultiplier.ONE);
        if (drawn + 0.001 < OUTBOUND_COST_AE) {
            return Failure.NO_POWER;
        }

        final ServerLevel certus = TheTruthDimensions.certusLevel(player.server);
        if (certus == null) {
            return Failure.NO_HOME_LEVEL;
        }
        final Vec3 arrival = resolveArrival(core, certus);
        if (arrival == null) {
            return Failure.NO_ARRIVAL;
        }

        final Entity arrived = player.changeDimension(new DimensionTransition(
            certus, arrival, Vec3.ZERO, player.getYRot(), 0.0F, DimensionTransition.PLAY_PORTAL_SOUND));
        if (arrived != player) {
            return Failure.NO_ARRIVAL;
        }
        core.setLastCertusPos(BlockPos.containing(arrival.x, arrival.y, arrival.z));
        player.displayClientMessage(Component.translatable("thetruth.message.entrance.transporting"), true);
        return null;
    }

    // ------------------------------------------------------------------
    // Return (Certus tether -> the key's home core)
    // ------------------------------------------------------------------

    /** Validates the return jump; the tether needs neither network nor power. */
    @Nullable
    public static Failure validateReturn(final QuantumEntranceBlockEntity tether, final ItemStack key) {
        if (key.get(TheTruthDataComponents.ENTANGLEMENT_PAIR.get()) == null) {
            return Failure.NO_KEY;
        }
        if (!tether.isStructureFormed()) {
            return Failure.NOT_FORMED;
        }
        return null;
    }

    /** Runs the return jump: validates and teleports home (always free). */
    @Nullable
    public static Failure performReturn(final ServerPlayer player, final QuantumEntranceBlockEntity tether,
                                        final ItemStack key) {
        final Failure failure = validateReturn(tether, key);
        if (failure != null) {
            return failure;
        }
        final var pair = key.get(TheTruthDataComponents.ENTANGLEMENT_PAIR.get());
        final ResourceKey<Level> homeKey = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
            pair.homeDimension());
        final ServerLevel home = player.server.getLevel(homeKey);
        if (home == null) {
            return Failure.NO_HOME_LEVEL;
        }

        final Vec3 arrival = resolveHomeArrival(home, pair.homePos());
        if (arrival == null) {
            return Failure.NO_ARRIVAL;
        }

        final Entity arrived = player.changeDimension(new DimensionTransition(
            home, arrival, Vec3.ZERO, player.getYRot(), 0.0F, DimensionTransition.PLAY_PORTAL_SOUND));
        if (arrived != player) {
            return Failure.NO_ARRIVAL;
        }
        // Remember which tether the player left from, so the next outbound jump
        // lands next to it (the home core may have been rebuilt elsewhere).
        if (home.getBlockEntity(pair.homePos()) instanceof QuantumEntranceBlockEntity homeCore) {
            homeCore.setLastCertusPos(tether.getBlockPos());
        }
        player.displayClientMessage(Component.translatable("thetruth.message.entrance.transporting"), true);
        return null;
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private static IGrid gridOf(final QuantumEntranceBlockEntity core) {
        if (core.getMainNode().getNode() == null) {
            return null;
        }
        return core.getMainNode().getNode().getGrid();
    }

    /**
     * The common Overworld-side gates: formed ring, powered network with
     * spatial IO infrastructure, and enough stored energy for {@code cost}.
     */
    @Nullable
    private static Failure checkNetworkGate(final QuantumEntranceBlockEntity core, final double cost) {
        final IGrid grid = gridOf(core);
        if (grid == null || !core.getMainNode().isActive()) {
            return Failure.NO_NETWORK;
        }
        if (!networkHasSpatialIoInfrastructure(grid)) {
            return Failure.NO_SPATIAL_IO;
        }
        final double available = grid.getEnergyService()
            .extractAEPower(cost, Actionable.SIMULATE, PowerMultiplier.ONE);
        if (available + 0.001 < cost) {
            return Failure.NO_POWER;
        }
        return null;
    }

    /**
     * The spatial IO gate (docs/02 M4 decision 3): the network must hold a
     * spatial IO port <em>and</em> a valid pylon array. Spatial storage cells
     * can never appear in network storage (AE2's {@code SpatialStorageCellItem}
     * implements {@code ISpatialStorageCell}, not {@code IMEStorageCell}, so a
     * drive refuses them) — the original storage-scan gate was therefore
     * unreachable in a real world and only passed against a test double.
     */
    private static boolean networkHasSpatialIoInfrastructure(final IGrid grid) {
        if (!grid.getSpatialService().isValidRegion()) {
            return false;
        }
        for (final IGridNode node : grid.getNodes()) {
            if (node.getOwner() instanceof BlockEntity be
                && BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).equals(SPATIAL_IO_PORT_ID)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Outbound arrival: the core's remembered Certus position when it has one,
     * otherwise a spiral search from the dimension origin. The standing height
     * is re-derived at arrival time so a rebuilt area cannot bury the player.
     */
    @Nullable
    private static Vec3 resolveArrival(final QuantumEntranceBlockEntity core, final ServerLevel certus) {
        BlockPos target = core.getLastCertusPos();
        if (target != null) {
            final double y = ArrivalLocator.findStandingY(certus, target.getX(), target.getZ());
            if (y >= 0.0) {
                return new Vec3(target.getX() + 0.5, y, target.getZ() + 0.5);
            }
        }
        final BlockPos found = ArrivalLocator.findArrivalColumn(certus,
            target != null ? target.getX() : 0, target != null ? target.getZ() : 0);
        if (found == null) {
            return null;
        }
        return new Vec3(found.getX() + 0.5, found.getY(), found.getZ() + 0.5);
    }

    /**
     * Return arrival: the ring cavity right above the remembered home core,
     * falling back to a safe column scan when that spot is blocked or the core
     * was torn down (the key is the credential, not the structure).
     */
    @Nullable
    private static Vec3 resolveHomeArrival(final ServerLevel home, final BlockPos homePos) {
        final BlockPos cavity = homePos.above();
        if (home.getBlockState(cavity).isAir() && home.getBlockState(cavity.above()).isAir()) {
            return new Vec3(homePos.getX() + 0.5, cavity.getY(), homePos.getZ() + 0.5);
        }
        final double y = ArrivalLocator.findStandingY(home, homePos.getX(), homePos.getZ());
        if (y >= 0.0) {
            return new Vec3(homePos.getX() + 0.5, y, homePos.getZ() + 0.5);
        }
        final BlockPos found = ArrivalLocator.findArrivalColumn(home, homePos.getX(), homePos.getZ());
        if (found == null) {
            return null;
        }
        return new Vec3(found.getX() + 0.5, found.getY(), found.getZ() + 0.5);
    }
}
