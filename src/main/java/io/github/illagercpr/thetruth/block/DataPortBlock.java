package io.github.illagercpr.thetruth.block;

import io.github.illagercpr.thetruth.blockentity.DataPortBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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

/**
 * The Data Port (M6 boss phase 3): a temporary socket The Last Measurer
 * exposes into the arena. Its measurement data can be siphoned through the
 * item capability (AE2 import bus is the intended tool) to overload the boss.
 * Drops nothing; the boss cleans its ports up on death.
 */
public class DataPortBlock extends Block implements EntityBlock {

    public DataPortBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .strength(1.5F, 6.0F)
            .sound(SoundType.AMETHYST)
            .noLootTable());
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new DataPortBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(final Level level, final BlockState state,
            final BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (serverLevel, pos, blockState, blockEntity) -> {
            if (blockEntity instanceof DataPortBlockEntity port
                && serverLevel instanceof ServerLevel server
                && type == io.github.illagercpr.thetruth.registry.TheTruthBlockEntities.DATA_PORT.get()) {
                DataPortBlockEntity.serverTick(server, pos, blockState, port);
            }
        };
    }
}
