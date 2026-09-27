package io.github.illagercpr.thetruth.block;

import io.github.illagercpr.thetruth.blockentity.CertusSolidifierBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Certus Solidifier (M5): writes unformed data back into certain instances.
 * GUI-less machine (docs/02 M5 decision 1) — items go in and out through the
 * exposed item capability (hoppers / AE2 import-export buses) or by hand:
 * <ul>
 *   <li>right-click with an item: insert into the input slot;</li>
 *   <li>right-click empty-handed: take the output;</li>
 *   <li>sneak + right-click empty-handed: take the input back.</li>
 * </ul>
 */
public class CertusSolidifierBlock extends Block implements EntityBlock {

    public CertusSolidifierBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0F, 9.0F)
            .sound(SoundType.METAL));
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new CertusSolidifierBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(final Level level, final BlockState state,
            final BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (serverLevel, pos, blockState, blockEntity) -> {
            if (blockEntity instanceof CertusSolidifierBlockEntity solidifier
                && serverLevel instanceof ServerLevel server) {
                CertusSolidifierBlockEntity.serverTick(server, pos, blockState, solidifier);
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
            final Player player, final BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof CertusSolidifierBlockEntity solidifier && !level.isClientSide) {
            final ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (!held.isEmpty()) {
                final ItemStack remainder = solidifier.insertInput(held);
                player.setItemInHand(InteractionHand.MAIN_HAND, remainder);
                if (remainder.getCount() != held.getCount()) {
                    level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 1.2F);
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.FAIL;
            }
            final ItemStack taken = player.isShiftKeyDown()
                ? solidifier.extractInput()
                : solidifier.extractOutput();
            if (!taken.isEmpty()) {
                if (!player.getInventory().add(taken)) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, taken);
                }
                level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 0.7F, 1.0F);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
            final BlockState newState, final boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof CertusSolidifierBlockEntity solidifier) {
            Containers.dropContents(level, pos, solidifier.getDroppableInventory());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
