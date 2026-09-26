package io.github.illagercpr.thetruth.block;

import io.github.illagercpr.thetruth.blockentity.CertusAnchorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * The Certus Anchor block. Static appearance for now; the block entity carries
 * the actual coverage field (M8 adds the polished visuals).
 */
public class CertusAnchorBlock extends Block implements EntityBlock {

    public CertusAnchorBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .strength(2.5F, 8.0F)
            .sound(SoundType.METAL));
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new CertusAnchorBlockEntity(pos, state);
    }
}
