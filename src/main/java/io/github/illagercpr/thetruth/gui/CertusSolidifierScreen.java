package io.github.illagercpr.thetruth.gui;

import io.github.illagercpr.thetruth.TheTruth;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Certus Solidifier screen (M8): two slots, an AE power readout drawn in the
 * low-information cyan language (docs/00 §8). No progress bar — the machine
 * writes instantly.
 */
@OnlyIn(Dist.CLIENT)
public class CertusSolidifierScreen extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<CertusSolidifierMenu> {

    private static final ResourceLocation BACKGROUND = TheTruth.id("textures/gui/certus_solidifier.png");

    /** Power gauge geometry (right edge of the machine panel). */
    private static final int GAUGE_X = 152;
    private static final int GAUGE_Y = 17;
    private static final int GAUGE_HEIGHT = 54;

    public CertusSolidifierScreen(final CertusSolidifierMenu menu, final Inventory playerInventory,
                                  final Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
        this.titleLabelY = 5;
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(final GuiGraphics graphics, final float partialTick, final int mouseX, final int mouseY) {
        graphics.blit(BACKGROUND, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        this.renderPowerGauge(graphics);
    }

    private void renderPowerGauge(final GuiGraphics graphics) {
        final double stored = this.menu.getDisplayedStoredPower();
        // Reference scale: a fully charged 16 Mth cell (AE2 64k tier scale).
        final double scale = 1_000_000.0;
        final double fraction = Math.max(0.0, Math.min(1.0, stored / scale));
        if (this.menu.isNetworkPowered()) {
            final int filled = (int) (fraction * GAUGE_HEIGHT);
            graphics.fill(this.leftPos + GAUGE_X, this.topPos + GAUGE_Y + GAUGE_HEIGHT - filled,
                this.leftPos + GAUGE_X + 4, this.topPos + GAUGE_HEIGHT + GAUGE_Y,
                0xFF7FDFCF);
        }
        // Frame: hollow rectangle in the data-cyan outline colour.
        final int x = this.leftPos + GAUGE_X - 1;
        final int y = this.topPos + GAUGE_Y - 1;
        final int w = 6;
        final int h = GAUGE_HEIGHT + 2;
        graphics.fill(x, y, x + w, y + 1, 0xFF9AF3D4);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF9AF3D4);
        graphics.fill(x, y, x + 1, y + h, 0xFF9AF3D4);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFF9AF3D4);
    }

    @Override
    protected void renderLabels(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        final String ae = formatAe(this.menu.getDisplayedStoredPower());
        graphics.drawString(this.font, ae, this.leftPos + GAUGE_X - 2 - this.font.width(ae),
            this.topPos + GAUGE_Y + GAUGE_HEIGHT + 4, 0x40E0C8, false);
    }

    @Override
    public void renderTooltip(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        final int gaugeLeft = this.leftPos + GAUGE_X - 1;
        final int gaugeTop = this.topPos + GAUGE_Y - 1;
        final int gaugeBottom = gaugeTop + GAUGE_HEIGHT + 2;
        if (mouseX >= gaugeLeft && mouseX < gaugeLeft + 6
            && mouseY >= gaugeTop && mouseY < gaugeBottom) {
            graphics.renderTooltip(this.font,
                Component.translatable("thetruth.gui.solidifier.power",
                    formatAe(this.menu.getDisplayedStoredPower())), mouseX, mouseY);
        }
    }

    private static String formatAe(final double ae) {
        if (ae >= 10_000_000.0) {
            return String.format("%.1fM AE", ae / 1_000_000.0);
        }
        if (ae >= 10_000.0) {
            return String.format("%.1fk AE", ae / 1_000.0);
        }
        return String.format("%.0f AE", ae);
    }
}
