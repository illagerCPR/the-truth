package io.github.illagercpr.thetruth.block;

import io.github.illagercpr.thetruth.blockentity.MeasurerCoreBlockEntity;
import io.github.illagercpr.thetruth.registry.TheTruthBlockEntities;
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
 * The Measurer Core (M6): the heart of the deep observatory and the boss
 * wake trigger. Structure-placed, drops nothing (it is a remnant of the
 * observatory installation, not loot) and survives the boss fight to allow
 * rematches.
 */
public class MeasurerCoreBlock extends Block implements EntityBlock {

    public MeasurerCoreBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK)
            .strength(6.0F, 1200.0F)
            .sound(SoundType.METAL)
            .noLootTable());
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new MeasurerCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(final Level level, final BlockState state,
            final BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (serverLevel, pos, blockState, blockEntity) -> {
            if (blockEntity instanceof MeasurerCoreBlockEntity core
                && serverLevel instanceof ServerLevel server
                && type == TheTruthBlockEntities.MEASURER_CORE.get()) {
                MeasurerCoreBlockEntity.serverTick(server, pos, blockState, core);
            }
        };
    }
}
