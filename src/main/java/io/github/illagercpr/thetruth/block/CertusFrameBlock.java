package io.github.illagercpr.thetruth.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Certus Frame (M4): the structural block of the 3x3 quantum ring. Pure
 * geometry — every behaviour (pairing, power, transport) lives in the
 * QuantumEntranceBlockEntity at the ring's bottom-center.
 */
public class CertusFrameBlock extends Block {

    public CertusFrameBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_GRAY)
            .requiresCorrectToolForDrops()
            .strength(3.0F, 9.0F)
            .sound(SoundType.METAL));
    }
}
