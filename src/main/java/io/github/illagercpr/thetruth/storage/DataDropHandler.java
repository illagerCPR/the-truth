package io.github.illagercpr.thetruth.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.registry.TheTruthDimensions;
import io.github.illagercpr.thetruth.blockentity.CertusAnchorBlockEntity;
import io.github.illagercpr.thetruth.coverage.CertaintyCoverage;
import io.github.illagercpr.thetruth.coverage.ObservatoryField;
import io.github.illagercpr.thetruth.coverage.UmbilicalNetwork;
import io.github.illagercpr.thetruth.registry.TheTruthItems;
import io.github.illagercpr.thetruth.uncertainty.StackUncertainty;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Drop data-fication (M6, docs/02 M6): inside Certus, drops are data flowing
 * back to the storage layer, not loose matter.
 *
 * <ul>
 * <li><b>Covered position with a reachable network</b> — the drop is written
 *     straight into that network ("re-written into the ledger"); anything the
 *     network refuses stays as a normal entity.</li>
 * <li><b>Covered position without a network</b> (the observatory field, for
 *     example) — data is stable here, drops stay as they are; boss loot stays
 *     pickable.</li>
 * <li><b>Uncovered position</b> — the drop dissolves into unformed matter that
 *     remembers the original ("data never written back"); the Certus
 *     Solidifier restores it. Never a silent loss (red line).</li>
 * </ul>
 *
 * <p>Experience inside Certus follows the same law: death experience never
 * spawns orbs, it condenses into data fragments (see
 * {@link #onLivingExperienceDrop}).
 */
@EventBusSubscriber(modid = TheTruth.MOD_ID)
public final class DataDropHandler {

    /** Experience points condensed per data fragment (matches the item's use value). */
    public static final int XP_PER_FRAGMENT = DataFragmentXp.VALUE;

    private DataDropHandler() {
    }

    @SubscribeEvent
    public static void onBlockDrops(final BlockDropsEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != TheTruthDimensions.CERTUS) {
            return;
        }
        processDrops(level, event.getPos(), event.getDrops());
    }

    @SubscribeEvent
    public static void onLivingDrops(final LivingDropsEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)
            || level.dimension() != TheTruthDimensions.CERTUS) {
            return;
        }
        processDrops(level, event.getEntity().blockPosition(), event.getDrops());
    }

    /** Death experience inside Certus condenses into data fragments, not orbs. */
    @SubscribeEvent
    public static void onLivingExperienceDrop(final LivingExperienceDropEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
            || player.level().dimension() != TheTruthDimensions.CERTUS) {
            return;
        }
        final int xp = event.getDroppedExperience();
        if (xp <= 0) {
            return;
        }
        event.setDroppedExperience(0);
        final int fragments = fragmentsFor(xp);
        for (int i = 0; i < fragments; i++) {
            final ItemEntity fragment = new ItemEntity(player.level(),
                player.getX(), player.getY() + 0.5D, player.getZ(),
                new ItemStack(TheTruthItems.DATA_FRAGMENT.get()));
            fragment.lifespan = Integer.MAX_VALUE;
            player.level().addFreshEntity(fragment);
        }
    }

    /** Fragment count for an experience amount; ceiling so no xp is lost. */
    public static int fragmentsFor(final int xpPoints) {
        return (xpPoints + XP_PER_FRAGMENT - 1) / XP_PER_FRAGMENT;
    }

    /**
     * The verdict for one drop batch at one position. Public for GameTests:
     * covered + network → written into storage; covered without network →
     * untouched; uncovered → dissolved into unformed matter.
     */
    public static void processDrops(final ServerLevel level, final BlockPos pos,
                                    final Collection<ItemEntity> drops) {
        if (drops.isEmpty()) {
            return;
        }
        if (!CertaintyCoverage.isPosCovered(level, pos)) {
            dissolveAll(level, pos, drops);
            return;
        }
        final MEStorage network = coveringNetwork(level, pos);
        if (network == null) {
            return; // stable but unconnected: keep the entities
        }
        writeToNetwork(network, drops);
    }

    /** Public for GameTests: the uncovered branch is the recoverable-dissolve contract. */
    public static void dissolveAll(final ServerLevel level, final BlockPos pos,
                                    final Collection<ItemEntity> drops) {
        final List<ItemStack> originals = new ArrayList<>();
        for (final ItemEntity drop : drops) {
            originals.add(drop.getItem());
        }
        drops.clear();
        for (final ItemStack original : originals) {
            if (original.isEmpty()) {
                continue;
            }
            if (!StackUncertainty.isConvertible(original)) {
                // Credentials and already-data items survive as themselves.
                final ItemEntity entity = new ItemEntity(level,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, original.copy());
                entity.lifespan = Integer.MAX_VALUE;
                level.addFreshEntity(entity);
                continue;
            }
            // One unformed item per original piece: each remembers its source.
            for (int i = 0; i < original.getCount(); i++) {
                final ItemStack unformed = StackUncertainty.toUnformed(original);
                final ItemEntity entity = new ItemEntity(level,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, unformed);
                entity.lifespan = Integer.MAX_VALUE;
                level.addFreshEntity(entity);
            }
        }
    }

    private static void writeToNetwork(final MEStorage network, final Collection<ItemEntity> drops) {
        final Iterator<ItemEntity> iterator = drops.iterator();
        while (iterator.hasNext()) {
            final ItemEntity drop = iterator.next();
            final ItemStack stack = drop.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            final long accepted = network.insert(AEItemKey.of(stack), stack.getCount(),
                Actionable.MODULATE, IActionSource.empty());
            if (accepted >= stack.getCount()) {
                iterator.remove();
            } else if (accepted > 0) {
                stack.shrink((int) accepted);
            }
        }
    }

    /**
     * The network behind the coverage at {@code pos}, or null when the
     * coverage comes from a field without storage (observatory) or nothing
     * covers the position at all.
     */
    public static MEStorage coveringNetwork(final ServerLevel level, final BlockPos pos) {
        for (final CertusAnchorBlockEntity anchor : CertaintyCoverage.activeAnchorsOf(level)) {
            if (anchor.isFieldActive()
                && anchor.getBlockPos().distSqr(pos) <= anchor.fieldRange() * anchor.fieldRange()) {
                return storageOf(anchor);
            }
        }
        for (final io.github.illagercpr.thetruth.blockentity.UmbilicalAnchorBlockEntity umbilical
                : UmbilicalNetwork.allAnchors()) {
            if (umbilical.getLevel() == level && UmbilicalNetwork.isFieldActive(umbilical)
                && umbilical.getBlockPos().distSqr(pos)
                    <= UmbilicalNetwork.FIELD_RANGE * UmbilicalNetwork.FIELD_RANGE) {
                return storageOf(umbilical);
            }
        }
        // Wireless access points keep their own grid; expose it on coverage.
        return CertaintyCoverage.nearActiveAccessPoint(level, pos)
            .flatMap(accessPointPos -> {
                if (level.getBlockEntity(accessPointPos)
                    instanceof appeng.api.implementations.blockentities.IWirelessAccessPoint accessPoint) {
                    return java.util.Optional.ofNullable(accessPoint.getGrid())
                        .map(grid -> grid.getStorageService().getInventory());
                }
                return java.util.Optional.<MEStorage>empty();
            })
            .orElse(null);
    }

    private static MEStorage storageOf(final net.minecraft.world.level.block.entity.BlockEntity host) {
        if (host instanceof appeng.api.networking.IInWorldGridNodeHost nodeHost
            && nodeHost.getGridNode(null) != null) {
            return nodeHost.getGridNode(null).getGrid().getStorageService().getInventory();
        }
        return null;
    }

    /** Holder for the XP value so the item class stays client-agnostic. */
    private static final class DataFragmentXp {
        private static final int VALUE = io.github.illagercpr.thetruth.item.DataFragmentItem.XP_PER_FRAGMENT;
    }
}
