package io.github.illagercpr.thetruth.item;

import io.github.illagercpr.thetruth.endgame.EndgameState;
import io.github.illagercpr.thetruth.endgame.EndgameSync;
import javax.annotation.Nonnull;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Last Record (M6 narrative drop, M7 endgame trigger): the final record
 * of the civilization that became data. Reading it out in the Overworld —
 * the layer the world itself reads from — is the irreversible endgame act
 * (docs/00 §9): the world flips into its final state and never back.
 *
 * <p>The item is never consumed; a memory that changed the world does not
 * burn. Re-reads are acknowledged but change nothing.
 */
public class LastRecordItem extends Item {

    public LastRecordItem(final Properties properties) {
        super(properties);
    }

    /**
     * The truth can only be brought back in the Overworld: Certus is the
     * ledger, and a ledger cannot annotate itself. Pure so the GameTest can
     * assert the dimension gate (the Certus branch is unreachable in
     * GameTest — datapack dimensions never instantiate there, docs/02 M1).
     */
    public static boolean canBringBack(final ResourceKey<Level> dimension) {
        return dimension == Level.OVERWORLD;
    }

    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player,
                                                  @Nonnull final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (!canBringBack(level.dimension())) {
                player.displayClientMessage(
                    Component.translatable("thetruth.message.endgame.wrong_dimension"), true);
                return InteractionResultHolder.fail(stack);
            }
            final MinecraftServer server = level.getServer();
            final EndgameState state = EndgameState.get(server);
            if (state.markTruthBroughtBack()) {
                EndgameSync.broadcastOnUnlock(server);
                server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("thetruth.message.endgame.broadcast"), false);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 1.0F);
            } else {
                player.displayClientMessage(
                    Component.translatable("thetruth.message.endgame.already"), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
