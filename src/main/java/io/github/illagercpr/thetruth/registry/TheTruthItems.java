package io.github.illagercpr.thetruth.registry;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.item.EntanglementKeyItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Items of The Truth. M1 only carries the block item of Certus Stone. */
public final class TheTruthItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TheTruth.MOD_ID);

    public static final DeferredItem<BlockItem> CERTUS_STONE =
        ITEMS.registerSimpleBlockItem("certus_stone", TheTruthBlocks.CERTUS_STONE);

    public static final DeferredItem<BlockItem> CERTUS_ANCHOR =
        ITEMS.registerSimpleBlockItem("certus_anchor", TheTruthBlocks.CERTUS_ANCHOR);

    public static final DeferredItem<BlockItem> CERTUS_FRAME =
        ITEMS.registerSimpleBlockItem("certus_frame", TheTruthBlocks.CERTUS_FRAME);

    public static final DeferredItem<BlockItem> QUANTUM_ENTRANCE =
        ITEMS.registerSimpleBlockItem("quantum_entrance", TheTruthBlocks.QUANTUM_ENTRANCE);

    /** M4 red-line-1 credential: blank until the Overworld core binds a pair. */
    public static final DeferredItem<EntanglementKeyItem> ENTANGLEMENT_KEY =
        ITEMS.register("entanglement_key", () -> new EntanglementKeyItem(
            new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));

    private TheTruthItems() {
    }
}
