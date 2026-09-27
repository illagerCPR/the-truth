package io.github.illagercpr.thetruth.uncertainty;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.coverage.CertaintyCoverage;
import io.github.illagercpr.thetruth.registry.TheTruthAttachments;
import io.github.illagercpr.thetruth.registry.TheTruthDimensions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server-side driver of the instability curve (M3). Settles once per second per
 * player; the state source is the synced {@link UncertaintyData} attachment, so
 * the loop is re-entrant and survives restarts.
 *
 * <p>Red-line guarantees implemented here:
 * <ul>
 * <li>covered players never accumulate ({@code rate -> 0}) and decay instead;</li>
 * <li>the deep layer never deletes without the announced, salvagable window;</li>
 * <li>middle-layer randomization records original sizes and is reversible;</li>
 * <li>creative/spectator players are exempt (docs/02 risk table).</li>
 * </ul>
 */
@EventBusSubscriber(modid = TheTruth.MOD_ID)
public final class UncertaintyManager {

    /** Settle interval in ticks (all rates are per second). */
    public static final int SETTLE_INTERVAL_TICKS = 20;

    private UncertaintyManager() {
    }

    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % SETTLE_INTERVAL_TICKS != 0) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        settleSecond(player);
    }

    private static void settleSecond(final ServerPlayer player) {
        final UncertaintyData current = player.getData(TheTruthAttachments.UNCERTAINTY.get());
        final boolean inCertus = player.level().dimension() == TheTruthDimensions.CERTUS;
        final boolean covered = inCertus && CertaintyCoverage.isPlayerCovered(player);
        final UncertaintyCurve.Layer layer = UncertaintyCurve.layerOf(player.blockPosition().getY());

        UncertaintyData next = current.withCovered(covered);

        if (!inCertus) {
            // Material is only unstable inside Certus; decay and cancel any
            // open window (the player physically left the world).
            if (current.warningTicksLeft() > 0) {
                next = next.withWarningTicks(0);
                message(player, "thetruth.message.warning_cancelled", true);
            }
            if (next.uncertainty() > 0) {
                next = next.withUncertainty(decay(next.uncertainty()));
            }
        } else if (covered) {
            if (next.warningTicksLeft() > 0) {
                // Salvage: window closed with coverage restored.
                next = next.withWarningTicks(0).withUncertainty(UncertaintyCurve.SALVAGE_UNCERTAINTY);
                final Inventory inventory = player.getInventory();
                StackUncertainty.restore(inventory);
                sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F);
                message(player, "thetruth.message.salvaged", true);
            } else if (next.uncertainty() < UncertaintyCurve.RECOVER_THRESHOLD
                && StackUncertainty.hasIndeterminateStacks(inventory(player))) {
                // Back under the threshold: sizes settle back to their records.
                StackUncertainty.restore(inventory(player));
                sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F);
                message(player, "thetruth.message.restored", true);
            }
            if (next.uncertainty() > 0) {
                next = next.withUncertainty(decay(next.uncertainty()));
            }
        } else {
            // Uncovered inside Certus.
            if (next.warningTicksLeft() > 0) {
                final int left = next.warningTicksLeft() - SETTLE_INTERVAL_TICKS;
                if (left <= 0) {
                    next = next.withWarningTicks(0).withUncertainty(0);
                    // M5: the salvage window expired — matter leaves as
                    // unformed data at the player's feet (never despawns).
                    StackUncertainty.dissolveToUnformed(inventory(player), player.level());
                    sound(player, SoundEvents.WARDEN_SONIC_BOOM, 1.0F);
                    message(player, "thetruth.message.deleted", false);
                } else {
                    next = next.withWarningTicks(left);
                    sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.5F);
                    message(player, "thetruth.message.warning_tick",
                        Component.literal(String.valueOf(ceilSeconds(left))), true);
                }
            } else {
                final int rate = UncertaintyCurve.ratePerSecond(layer);
                if (rate > 0 && next.uncertainty() < UncertaintyCurve.CAP) {
                    next = next.withUncertainty(Math.min(UncertaintyCurve.CAP, next.uncertainty() + rate));
                }
                if (layer == UncertaintyCurve.Layer.MIDDLE
                    && next.uncertainty() >= UncertaintyCurve.MIDDLE_RANDOMIZE_THRESHOLD
                    && player.level().getGameTime() >= next.nextRandomizeAt()) {
                    StackUncertainty.randomize(inventory(player), player.getRandom());
                    // M5: on top of the size shuffle, some stacks degrade into
                    // unformed matter (restorable via the Certus Solidifier).
                    StackUncertainty.convertSomeToUnformed(inventory(player), player.getRandom());
                    next = next.withNextRandomizeAt(
                        player.level().getGameTime() + UncertaintyCurve.RANDOMIZE_INTERVAL_TICKS);
                    sound(player, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F);
                    message(player, "thetruth.message.randomized", true);
                }
                if (layer == UncertaintyCurve.Layer.DEEP
                    && next.uncertainty() >= UncertaintyCurve.DEEP_DELETE_THRESHOLD) {
                    next = next.withWarningTicks(UncertaintyCurve.DEEP_WARNING_TICKS);
                    sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.5F);
                    message(player, "thetruth.message.warning_open", true);
                }
            }
        }

        if (next.differsExceptCovered(current) || next.covered() != current.covered()) {
            player.setData(TheTruthAttachments.UNCERTAINTY.get(), next);
        }
    }

    private static Inventory inventory(final ServerPlayer player) {
        return player.getInventory();
    }

    private static int decay(final int value) {
        return Math.max(0, value - UncertaintyCurve.COVERED_DECAY_PER_SECOND);
    }

    private static int ceilSeconds(final int ticks) {
        return (ticks + SETTLE_INTERVAL_TICKS - 1) / SETTLE_INTERVAL_TICKS;
    }

    private static void message(final ServerPlayer player, final String key, final boolean actionBar) {
        player.displayClientMessage(Component.translatable(key), actionBar);
    }

    private static void message(final ServerPlayer player, final String key, final Component argument,
                                final boolean actionBar) {
        player.displayClientMessage(Component.translatable(key, argument), actionBar);
    }

    private static void sound(final ServerPlayer player, final net.minecraft.sounds.SoundEvent event,
                              final float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), event, SoundSource.PLAYERS,
            1.0F, pitch);
    }
}
