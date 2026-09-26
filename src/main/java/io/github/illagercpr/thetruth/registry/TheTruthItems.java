package io.github.illagercpr.thetruth.registry;

import io.github.illagercpr.thetruth.TheTruth;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Items of The Truth. M1 only carries the block item of Certus Stone. */
public final class TheTruthItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TheTruth.MOD_ID);

    public static final DeferredItem<BlockItem> CERTUS_STONE =
        ITEMS.registerSimpleBlockItem("certus_stone", TheTruthBlocks.CERTUS_STONE);

    private TheTruthItems() {
    }
}
