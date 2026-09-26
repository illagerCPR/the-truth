package io.github.illagercpr.thetruth.event;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.registry.TheTruthDimensions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;

/**
 * Kill-switch insurance for the Certus dimension (M4, docs/00 section 5.2):
 *
 * <ul>
 *   <li>Player deaths in Certus drop "data remnants" instead of ordinary item
 *       entities — they never despawn and glow, so nothing is silently lost
 *       while the player respawns in the Overworld.</li>
 *   <li>Setting a spawn point inside Certus is refused (the dimension refuses
 *       to record you); the vanilla {@code bed_works:false} already explodes
 *       beds there, this closes the remaining sources.</li>
 * </ul>
 */
@EventBusSubscriber(modid = TheTruth.MOD_ID)
public final class TheTruthRemnantHandlers {

    private TheTruthRemnantHandlers() {
    }

    @SubscribeEvent
    public static void onLivingDrops(final LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().dimension() != TheTruthDimensions.CERTUS) {
            return;
        }
        for (final ItemEntity drop : event.getDrops()) {
            markAsDataRemnant(drop);
        }
    }

    /**
     * Turns a dropped item into a data remnant (public for GameTests). The
     * NeoForge unlimited-lifetime helper only rewinds {@code age} (a ~32 min
     * extension), so the despawn threshold itself is pinned to {@code MAX} as
     * well — nothing is silently lost while the player respawns in the
     * Overworld.
     */
    public static void markAsDataRemnant(final ItemEntity drop) {
        drop.setUnlimitedLifetime();
        drop.lifespan = Integer.MAX_VALUE;
        drop.setCustomName(Component.translatable("thetruth.message.entrance.remnant_name"));
        drop.setGlowingTag(true);
    }

    @SubscribeEvent
    public static void onPlayerSetSpawn(final PlayerSetSpawnEvent event) {
        if (event.getSpawnLevel() != TheTruthDimensions.CERTUS) {
            return;
        }
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) {
            player.displayClientMessage(
                Component.translatable("thetruth.message.entrance.spawn_denied"), true);
        }
    }
}
