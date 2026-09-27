package io.github.illagercpr.thetruth.item;

import javax.annotation.Nonnull;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A Data Fragment (M6): experience that never got written back. Right-click
 * re-reads one fragment into yourself — the only "experience orb" the Certus
 * world still acknowledges (docs/02 M6, experience data-fication).
 */
public class DataFragmentItem extends Item {

    /** Experience points re-read per fragment (M8 rebalance target). */
    public static final int XP_PER_FRAGMENT = 8;

    public DataFragmentItem(final Properties properties) {
        super(properties);
    }

    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player,
                                                  @Nonnull final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.giveExperiencePoints(XP_PER_FRAGMENT);
            player.getCooldowns().addCooldown(this, 10);
            level.playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
