package io.github.illagercpr.thetruth.client;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.endgame.EndgameSync;
import io.github.illagercpr.thetruth.endgame.MarkedStorageCells;
import io.github.illagercpr.thetruth.registry.TheTruthItems;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

/**
 * The Certus mark on ME storage cells (M7, docs/00 §9): after the truth has
 * been brought back, every acknowledged ME storage cell renders a small
 * quartz-glow mark in the top-right of its icon — a detail only players who
 * went through the endgame can read. M8 replaces the geometric fill with the
 * dedicated mark texture ({@code textures/item/certus_mark.png}).
 *
 * <p>Driven by the client mirror of the server flag ({@link EndgameSync}) and
 * the shared gate ({@link MarkedStorageCells#isMarkedCell}).
 */
@OnlyIn(Dist.CLIENT)
public final class CertusItemDecorations {

    /** The mark texture: a 4x4 hollow square with a bright core and glow ring. */
    private static final ResourceLocation MARK_TEXTURE = TheTruth.id("textures/item/certus_mark.png");

    private CertusItemDecorations() {
    }

    /** Mod-bus hook: registers the mark decorator on every listed cell item. */
    public static void onRegisterItemDecorations(final RegisterItemDecorationsEvent event) {
        final IItemDecorator decorator = CertusItemDecorations::renderMark;
        for (final String id : MarkedStorageCells.ME_STORAGE_CELL_IDS) {
            final Item item = cellItem(id);
            if (item != null) {
                event.register(item, decorator);
            }
        }
        event.register(TheTruthItems.CERTUS_CELL.get(), decorator);
    }

    /** Resolves an AE2 item id, tolerating a renamed cell (logged, skipped). */
    private static Item cellItem(final String id) {
        final Item item = BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath("ae2", id));
        if (item.getDefaultInstance().isEmpty()) {
            TheTruth.LOGGER.warn("Marked cell id {} did not resolve; the mark skips it", id);
            return null;
        }
        return item;
    }

    /**
     * Blits the mark texture over the icon's top-right corner. Returns false
     * (nothing rendered) while the world is unmarked.
     */
    private static boolean renderMark(final GuiGraphics graphics, final Font font,
                                      final ItemStack stack, final int xOffset, final int yOffset) {
        if (!EndgameSync.clientTruthBroughtBack || !MarkedStorageCells.isMarkedCell(stack)) {
            return false;
        }
        graphics.blit(MARK_TEXTURE, xOffset + 8, yOffset, 0, 0, 8, 8, 8, 8);
        return true;
    }
}
