package io.github.illagercpr.thetruth.registry;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.item.CertusCellItem;
import io.github.illagercpr.thetruth.item.EntanglementKeyItem;
import io.github.illagercpr.thetruth.item.UnformedMatterItem;
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

    public static final DeferredItem<BlockItem> RESIDUAL_MATTER =
        ITEMS.registerSimpleBlockItem("residual_matter", TheTruthBlocks.RESIDUAL_MATTER);

    public static final DeferredItem<BlockItem> CERTUS_SOLIDIFIER =
        ITEMS.registerSimpleBlockItem("certus_solidifier", TheTruthBlocks.CERTUS_SOLIDIFIER);

    public static final DeferredItem<BlockItem> UMBILICAL_ANCHOR =
        ITEMS.registerSimpleBlockItem("umbilical_anchor", TheTruthBlocks.UMBILICAL_ANCHOR);

    /** M4 red-line-1 credential: blank until the Overworld core binds a pair. */
    public static final DeferredItem<EntanglementKeyItem> ENTANGLEMENT_KEY =
        ITEMS.register("entanglement_key", () -> new EntanglementKeyItem(
            new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));

    /**
     * M5 unformed matter: mined from residual clusters or degraded from player
     * items; glows like all "unwritten data". One stack carries one content
     * group (see {@code UNFORMED_CONTENT}).
     */
    public static final DeferredItem<UnformedMatterItem> UNFORMED_MATTER =
        ITEMS.register("unformed_matter", () -> new UnformedMatterItem(
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** M5 Certus Matrix: the base material the solidifier writes free data into. */
    public static final DeferredItem<Item> CERTUS_MATRIX =
        ITEMS.register("certus_matrix", () -> new Item(new Item.Properties()));

    /** M5 Certus Core: boss drop (M6); the crafting keystone of the Certus Cell. */
    public static final DeferredItem<Item> CERTUS_CORE =
        ITEMS.register("certus_core", () -> new Item(new Item.Properties().rarity(Rarity.EPIC)));

    /** M5 Certus Cell: huge-capacity ME storage whose content is "unformed" while offline. */
    public static final DeferredItem<CertusCellItem> CERTUS_CELL =
        ITEMS.register("certus_cell", () -> new CertusCellItem(
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    private TheTruthItems() {
    }
}
