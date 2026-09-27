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
 * geometric quartz-glow mark in the top-right of its icon — a detail only
 * players who went through the endgame can read.
 *
 * <p>Driven by the client mirror of the server flag ({@link EndgameSync}) and
 * the shared gate ({@link MarkedStorageCells#isMarkedCell}). Pure geometry:
 * no texture asset until M8 art.
 */
@OnlyIn(Dist.CLIENT)
public final class CertusItemDecorations {

    /** Quartz-glow aqua of the mark frame and its bright core. */
    private static final int MARK_COLOR = 0xFF7FDFCF;
    private static final int MARK_CORE = 0xFFE9FFF8;

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
     * A 3x3 hollow square with a bright core at the icon's top-right corner.
     * Returns false (nothing rendered) while the world is unmarked.
     */
    private static boolean renderMark(final GuiGraphics graphics, final Font font,
                                      final ItemStack stack, final int xOffset, final int yOffset) {
        if (!EndgameSync.clientTruthBroughtBack || !MarkedStorageCells.isMarkedCell(stack)) {
            return false;
        }
        final int x = xOffset + 12;
        final int y = yOffset + 2;
        graphics.fill(x, y, x + 3, y + 1, MARK_COLOR);
        graphics.fill(x, y + 2, x + 3, y + 3, MARK_COLOR);
        graphics.fill(x, y + 1, x + 1, y + 2, MARK_COLOR);
        graphics.fill(x + 2, y + 1, x + 3, y + 2, MARK_COLOR);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, MARK_CORE);
        return true;
    }
}
