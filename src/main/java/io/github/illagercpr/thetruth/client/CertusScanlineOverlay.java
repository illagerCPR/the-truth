package io.github.illagercpr.thetruth.client;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.registry.TheTruthDimensions;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Certus scanline (M8, docs/00 §8 optional item): one horizontal line slowly
 * sweeping down the screen inside Certus — the world reads as a surface being
 * scanned. Low-information language: a single translucent band per frame,
 * no texture sampling, an 8 s period.
 *
 * <p>Implemented as a HUD layer rather than a sky-render hook: 1.21.1 offers
 * no sky-draw callback, and switching projection state mid level render is
 * not worth the risk (docs/02 M8 decision 4).
 */
@OnlyIn(Dist.CLIENT)
public final class CertusScanlineOverlay implements LayeredDraw.Layer {

    private static final long PERIOD_MS = 8_000;
    /** Band height in pixels; the line is a 2 px core inside a 6 px glow. */
    private static final int BAND_HEIGHT = 6;
    /** Peak alpha of the core line (very low — a whisper, not a feature). */
    private static final int CORE_ALPHA = 14;
    /** Peak alpha of the surrounding glow. */
    private static final int GLOW_ALPHA = 6;
    private static final int CORE_COLOR = 0x7FDFCF;

    /** Registry id of this GUI layer. */
    public static ResourceLocation layerId() {
        return TheTruth.id("certus_scanline");
    }

    @Override
    public void render(final GuiGraphics gui, final DeltaTracker deltaTracker) {
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        if (player.level().dimension() != TheTruthDimensions.CERTUS) {
            return;
        }
        final int height = gui.guiHeight();
        final int width = gui.guiWidth();
        // The sweep travels down the screen over the period, then wraps.
        final int y = (int) ((System.currentTimeMillis() % PERIOD_MS) * (height + BAND_HEIGHT) / PERIOD_MS);

        final int glow = (GLOW_ALPHA << 24) | CORE_COLOR;
        final int core = (CORE_ALPHA << 24) | CORE_COLOR;
        gui.fill(0, y - BAND_HEIGHT, width, y + BAND_HEIGHT, glow);
        gui.fill(0, y - 1, width, y + 1, core);
    }
}
