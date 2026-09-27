package io.github.illagercpr.thetruth.gametest;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.blockentity.UmbilicalAnchorBlockEntity;
import io.github.illagercpr.thetruth.endgame.EndgameState;
import io.github.illagercpr.thetruth.endgame.MarkedStorageCells;
import io.github.illagercpr.thetruth.item.LastRecordItem;
import io.github.illagercpr.thetruth.registry.TheTruthBlocks;
import io.github.illagercpr.thetruth.registry.TheTruthDataComponents;
import io.github.illagercpr.thetruth.registry.TheTruthDimensions;
import io.github.illagercpr.thetruth.registry.TheTruthItems;
import io.github.illagercpr.thetruth.transport.EntanglementPairData;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M7 mechanics tests: the irreversible endgame marker (SavedData round trip,
 * one-way flip) and the Last Record read-out ritual. The umbilical gate and
 * the dimension gate are asserted as pure functions, so the tests never
 * depend on the shared flag's order — the integration test unlocks first
 * (idempotent) and then walks the block-interaction path.
 */
@GameTestHolder(TheTruth.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheTruthEndgameTests {

    private static final BlockPos ANCHOR_POS = new BlockPos(1, 1, 1);

    private TheTruthEndgameTests() {
    }

    private static void check(final GameTestHelper helper, final boolean condition, final String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    // ------------------------------------------------------- the read-out

    @GameTest(template = "smoke")
    public static void endgameUnlocksViaLastRecordInOverworld(final GameTestHelper helper) {
        final ServerPlayer mock = helper.makeMockServerPlayerInLevel();
        final ItemStack record = new ItemStack(TheTruthItems.LAST_RECORD.get());
        helper.startSequence()
            .thenExecute(() -> {
                check(helper, LastRecordItem.canBringBack(helper.getLevel().dimension()),
                    "the GameTest world is the Overworld, so the record must be readable");
                final InteractionResultHolder<ItemStack> result =
                    record.use(helper.getLevel(), mock, InteractionHand.MAIN_HAND);
                check(helper, result.getResult().consumesAction(),
                    "reading the record must succeed");
                check(helper, EndgameState.isUnlocked(helper.getLevel().getServer()),
                    "the world must be flipped after the read-out");
            })
            .thenSucceed();
    }

    @GameTest(template = "smoke")
    public static void endgameUnlockIsIrreversibleAndIdempotent(final GameTestHelper helper) {
        final EndgameState state = EndgameState.get(helper.getLevel().getServer());
        // Order-independent: make sure this save is flipped, then probe again.
        state.markTruthBroughtBack();
        check(helper, !state.markTruthBroughtBack(),
            "a second flip must be a no-op, not another first flip");
        check(helper, state.isTruthBroughtBack(),
            "the marker must stay set");
        // No code path writes the flag back: the only setter is one-way.
        check(helper, EndgameState.isUnlocked(helper.getLevel().getServer()),
            "the world must remain flipped");
        helper.succeed();
    }

    @GameTest(template = "smoke")
    public static void endgameStatePersistsThroughSaveLoadRoundtrip(final GameTestHelper helper) {
        final HolderLookup.Provider access = helper.getLevel().registryAccess();
        // A fresh state is the unmarked world.
        final EndgameState fresh = new EndgameState();
        check(helper, !fresh.isTruthBroughtBack(),
            "a save that never saw the endgame must stay unmarked");
        // The flag survives a save/load round trip.
        fresh.markTruthBroughtBack();
        final CompoundTag saved = fresh.save(new CompoundTag(), access);
        check(helper, EndgameState.load(saved, access).isTruthBroughtBack(),
            "the flipped flag must persist through save/load");
        // ...and a save without the flag loads as unmarked.
        check(helper, !EndgameState.load(new CompoundTag(), access).isTruthBroughtBack(),
            "a save without the marker must load unmarked");
        helper.succeed();
    }

    @GameTest(template = "smoke")
    public static void lastRecordCanOnlyBeBroughtBackFromOverworld(final GameTestHelper helper) {
        check(helper, LastRecordItem.canBringBack(Level.OVERWORLD),
            "the Overworld must accept the read-out");
        check(helper, !LastRecordItem.canBringBack(TheTruthDimensions.CERTUS),
            "Certus is the ledger — it cannot read the truth out into itself");
        check(helper, !LastRecordItem.canBringBack(Level.NETHER),
            "no other dimension may shortcut the ritual");
        helper.succeed();
    }

    // ---------------------------------------------------- the umbilical gate

    @GameTest(template = "smoke")
    public static void umbilicalBindingGateRespectsDimensionAndEndgame(final GameTestHelper helper) {
        check(helper, EndgameState.allowsUmbilicalBinding(false, TheTruthDimensions.CERTUS),
            "Certus may always host an anchor (dimension-side gameplay)");
        check(helper, EndgameState.allowsUmbilicalBinding(true, TheTruthDimensions.CERTUS),
            "the endgame must not revoke Certus-side anchoring");
        check(helper, !EndgameState.allowsUmbilicalBinding(false, Level.OVERWORLD),
            "before the endgame the Overworld refuses the cord");
        check(helper, EndgameState.allowsUmbilicalBinding(true, Level.OVERWORLD),
            "after the endgame the Overworld acknowledges the cord");
        check(helper, !EndgameState.allowsUmbilicalBinding(false, Level.NETHER),
            "no third dimension may shortcut the gate");
        helper.succeed();
    }

    @GameTest(template = "smoke")
    public static void umbilicalOverworldBindViaBlockAfterEndgame(final GameTestHelper helper) {
        helper.setBlock(ANCHOR_POS, TheTruthBlocks.UMBILICAL_ANCHOR.get());
        final ServerPlayer mock = helper.makeMockServerPlayerInLevel();
        final UUID pairId = UUID.randomUUID();
        final ItemStack key = new ItemStack(TheTruthItems.ENTANGLEMENT_KEY.get());
        key.set(TheTruthDataComponents.ENTANGLEMENT_PAIR.get(), new EntanglementPairData(
            pairId, ResourceLocation.withDefaultNamespace("overworld"),
            helper.absolutePos(ANCHOR_POS)));
        helper.startSequence()
            .thenExecute(() -> {
                // Idempotent unlock keeps this test order-independent.
                EndgameState.get(helper.getLevel().getServer()).markTruthBroughtBack();
                mock.setItemInHand(InteractionHand.MAIN_HAND, key);
                helper.useBlock(ANCHOR_POS, mock);
            })
            .thenExecute(() -> {
                final UmbilicalAnchorBlockEntity anchor =
                    (UmbilicalAnchorBlockEntity) helper.getBlockEntity(ANCHOR_POS);
                check(helper, anchor.hasPair() && pairId.equals(anchor.getPairId()),
                    "the block-interaction path must bind the pair after the endgame");
            })
            .thenSucceed();
    }

    // ----------------------------------------------------- the cell mark set

    @GameTest(template = "smoke")
    public static void ae2MeStorageCellsAcknowledgeMark(final GameTestHelper helper) {
        // Every listed ME storage cell must exist and be acknowledged by the
        // AE2 handler registry — the mark gate's single source of truth.
        for (final String id : MarkedStorageCells.ME_STORAGE_CELL_IDS) {
            final var item = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("ae2", id));
            check(helper, !item.getDefaultInstance().isEmpty(), "ae2:" + id + " must exist");
            check(helper, appeng.api.storage.StorageCells.isCellHandled(item.getDefaultInstance()),
                "ae2:" + id + " must be acknowledged as an ME storage cell");
        }
        check(helper, appeng.api.storage.StorageCells.isCellHandled(
                new ItemStack(TheTruthItems.CERTUS_CELL.get())),
            "the Certus Cell must be acknowledged as an ME storage cell");
        // Spatial cells are not ME network storage (docs/02 M4); the mark list
        // deliberately excludes them. If AE2 ever changes this, revisit.
        check(helper, !appeng.api.storage.StorageCells.isCellHandled(
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("ae2", "spatial_storage_cell_2"))
                    .getDefaultInstance()),
            "a spatial cell must not be acknowledged as ME network storage");
        helper.succeed();
    }
}
