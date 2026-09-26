package io.github.illagercpr.thetruth.block;

import io.github.illagercpr.thetruth.blockentity.QuantumEntranceBlockEntity;
import io.github.illagercpr.thetruth.item.EntanglementKeyItem;
import io.github.illagercpr.thetruth.transport.QuantumTransport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Quantum Entrance (M4): bottom-center block of the 3x3 quantum ring. Right
 * click behaviour depends on which side the ring stands on — Overworld cores
 * bind unpaired keys and send their pair holders to Certus, Certus cores bring
 * bound keys home (docs/02 M4).
 */
public class QuantumEntranceBlock extends Block implements EntityBlock {

    public QuantumEntranceBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .strength(3.0F, 9.0F)
            .sound(SoundType.METAL));
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new QuantumEntranceBlockEntity(pos, state);
    }

    @Override
    protected void neighborChanged(final BlockState state, final Level level, final BlockPos pos,
                                   final Block neighborBlock, final BlockPos fromPos, final boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, fromPos, isMoving);
        if (level.getBlockEntity(pos) instanceof QuantumEntranceBlockEntity core) {
            core.markStructureDirty();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
            && level.getBlockEntity(pos) instanceof QuantumEntranceBlockEntity core) {
            handleUse(serverPlayer, core);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Routes the right click: an unpaired key on an Overworld core starts the
     * binding ritual, a paired key jumps outbound from the Overworld and home
     * from Certus. Any refusal answers with its own actionbar message.
     */
    private static void handleUse(final ServerPlayer player, final QuantumEntranceBlockEntity core) {
        final ItemStack key = findKey(player);
        if (key.isEmpty()) {
            player.displayClientMessage(
                Component.translatable(QuantumTransport.Failure.NO_KEY.messageKey), true);
            return;
        }
        final boolean overworldSide = core.getLevel() != null && core.getLevel().dimension() == Level.OVERWORLD;
        final QuantumTransport.Failure failure;
        if (overworldSide && !key.has(io.github.illagercpr.thetruth.registry.TheTruthDataComponents.ENTANGLEMENT_PAIR.get())) {
            failure = QuantumTransport.performBinding(player, core, key);
        } else if (overworldSide) {
            failure = QuantumTransport.performOutbound(player, core, key);
        } else {
            failure = QuantumTransport.performReturn(player, core, key);
        }
        if (failure != null) {
            player.displayClientMessage(Component.translatable(failure.messageKey), true);
        } else {
            player.getCooldowns().addCooldown(key.getItem(), 40);
        }
    }

    /** The entanglement key in the main hand, else the off hand. */
    private static ItemStack findKey(final Player player) {
        final ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof EntanglementKeyItem) {
            return main;
        }
        final ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof EntanglementKeyItem) {
            return off;
        }
        return ItemStack.EMPTY;
    }
}
