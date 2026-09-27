package io.github.illagercpr.thetruth.block;

import io.github.illagercpr.thetruth.blockentity.UmbilicalAnchorBlockEntity;
import io.github.illagercpr.thetruth.coverage.UmbilicalNetwork;
import io.github.illagercpr.thetruth.registry.TheTruthDataComponents;
import io.github.illagercpr.thetruth.registry.TheTruthItems;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Umbilical Anchor (M5). Binding ritual: right-click with a <b>bound</b>
 * Entanglement Key — the anchor records the key's pair id; two anchors sharing
 * a pair id in different dimensions form the cord (docs/02 M5 decision 5).
 * Right-click empty-handed prints a status readout.
 */
public class UmbilicalAnchorBlock extends Block implements EntityBlock {

    public UmbilicalAnchorBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_GRAY)
            .strength(3.0F, 9.0F)
            .sound(SoundType.METAL));
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new UmbilicalAnchorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(final Level level, final BlockState state,
            final BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (serverLevel, pos, blockState, blockEntity) -> {
            if (blockEntity instanceof UmbilicalAnchorBlockEntity anchor) {
                anchor.maintainTicket();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
            final Player player, final BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof UmbilicalAnchorBlockEntity anchor) || level.isClientSide) {
            return InteractionResult.PASS;
        }
        final ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.is(TheTruthItems.ENTANGLEMENT_KEY.get())) {
            final var pair = held.get(TheTruthDataComponents.ENTANGLEMENT_PAIR.get());
            if (pair == null) {
                player.displayClientMessage(
                    Component.translatable("thetruth.message.umbilical.key_unbound"), true);
                return InteractionResult.FAIL;
            }
            if (anchor.hasPair()) {
                player.displayClientMessage(
                    Component.translatable("thetruth.message.umbilical.already_bound"), true);
                return InteractionResult.FAIL;
            }
            final UmbilicalNetwork.BindVerdict verdict = UmbilicalNetwork.checkBind(pair.pairId(), level);
            if (verdict != UmbilicalNetwork.BindVerdict.OK) {
                player.displayClientMessage(
                    Component.translatable(verdict == UmbilicalNetwork.BindVerdict.PAIR_FULL
                        ? "thetruth.message.umbilical.limit_pair"
                        : "thetruth.message.umbilical.limit_global"), true);
                return InteractionResult.FAIL;
            }
            anchor.bindPair(pair.pairId());
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.8F);
            player.displayClientMessage(
                Component.translatable("thetruth.message.umbilical.bound"), true);
            return InteractionResult.SUCCESS;
        }
        // Status readout for any other interaction.
        final boolean active = UmbilicalNetwork.isFieldActive(anchor);
        player.displayClientMessage(
            Component.translatable(anchor.hasPair()
                ? (active ? "thetruth.message.umbilical.status_active" : "thetruth.message.umbilical.status_idle")
                : "thetruth.message.umbilical.status_unbound"), true);
        return InteractionResult.SUCCESS;
    }
}
