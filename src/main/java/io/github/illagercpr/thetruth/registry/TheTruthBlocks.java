package io.github.illagercpr.thetruth.registry;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.block.CertusAnchorBlock;
import io.github.illagercpr.thetruth.block.CertusFrameBlock;
import io.github.illagercpr.thetruth.block.QuantumEntranceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Blocks of the Certus dimension. M1 registers the base terrain block only;
 * the three strata (debris belt / sediment / uncollapsed) get dedicated blocks in M2.
 */
public final class TheTruthBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TheTruth.MOD_ID);

    /**
     * Base stratum block of Certus. Deliberately keeps normal hardness and drops
     * itself for now; red line 3 (ME-only harvesting) is enforced on stratum
     * blocks in M5 via loot conditions, so keep {@code hardness >= 0}.
     */
    public static final DeferredBlock<Block> CERTUS_STONE = BLOCKS.registerSimpleBlock(
        "certus_stone",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_GRAY)
            .requiresCorrectToolForDrops()
            .strength(2.0F, 9.0F)
            .sound(SoundType.DEEPSLATE));

    /** Certus Anchor: projects the deterministic coverage field while powered. */
    public static final DeferredBlock<CertusAnchorBlock> CERTUS_ANCHOR =
        BLOCKS.register("certus_anchor", CertusAnchorBlock::new);

    /** M4 structural block of the 3x3 quantum ring (both dimensions). */
    public static final DeferredBlock<CertusFrameBlock> CERTUS_FRAME =
        BLOCKS.register("certus_frame", CertusFrameBlock::new);

    /** M4 bottom-center block of the quantum ring: binds keys, moves players. */
    public static final DeferredBlock<QuantumEntranceBlock> QUANTUM_ENTRANCE =
        BLOCKS.register("quantum_entrance", QuantumEntranceBlock::new);

    private TheTruthBlocks() {
    }
}
