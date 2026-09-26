package io.github.illagercpr.thetruth.client;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.registry.TheTruthAttachments;
import io.github.illagercpr.thetruth.registry.TheTruthDimensions;
import io.github.illagercpr.thetruth.uncertainty.UncertaintyData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Uncertainty noise overlay (M3 warning layer): a tiling grain of faint white
 * specks over the world. Intensity follows the synced uncertainty value —
 * barely visible while uncovered on the surface (teaching), stronger with the
 * value, and a violent flicker while the deep-layer salvage window is open.
 * Covered players see nothing. Uses the synced uncertainty attachment directly;
 * no extra packet is needed.
 */
@OnlyIn(Dist.CLIENT)
public final class CertusUncertaintyOverlay implements LayeredDraw.Layer {

    private static final ResourceLocation NOISE_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(TheTruth.MOD_ID, "textures/gui/uncertainty_noise.png");

    /** Source texture is 4x4 noise tiles of this size; the tile index shuffles to flicker. */
    private static final int TILE = 64;
    private static final int ATLAS = TILE * 4;

    /** Flicker step in ms; each step picks another noise tile. */
    private static final long FLICKER_STEP_MS = 60;

    /** Base visibility while uncovered (teaching noise on the surface). */
    private static final float BASE_ALPHA = 0.10F;
    /** Extra visibility per unit of accumulated uncertainty. */
    private static final float LOAD_ALPHA = 0.30F;
    /** Warning window: violent flicker band. */
    private static final float WARNING_ALPHA_LOW = 0.35F;
    private static final float WARNING_ALPHA_HIGH = 0.75F;

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
        final UncertaintyData data = player.getData(TheTruthAttachments.UNCERTAINTY.get());
        if (data.covered() && data.warningTicksLeft() <= 0) {
            return;
        }

        final long now = Util.getMillis();
        final float alpha;
        if (data.warningTicksLeft() > 0) {
            alpha = WARNING_ALPHA_LOW + (WARNING_ALPHA_HIGH - WARNING_ALPHA_LOW) * jitter(now);
        } else {
            alpha = BASE_ALPHA + LOAD_ALPHA * (data.uncertainty() / (float) 100);
        }

        final int tileIndex = (int) ((now / FLICKER_STEP_MS) % 16);
        final int u = (tileIndex % 4) * TILE;
        final int v = (tileIndex / 4) * TILE;

        renderNoise(gui, u, v, alpha, gui.guiWidth(), gui.guiHeight());
    }

    private static void renderNoise(final GuiGraphics gui, final int u, final int v, final float alpha,
                                    final int width, final int height) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        for (int x = 0; x < width; x += TILE) {
            for (int y = 0; y < height; y += TILE) {
                gui.blit(NOISE_TEXTURE, x, y, u, v, TILE, TILE, ATLAS, ATLAS);
            }
        }
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    /** Cheap deterministic jitter in [0,1) driving the flicker depth. */
    private static float jitter(final long now) {
        final long step = now / FLICKER_STEP_MS;
        return (step * 31 % 97) / 97.0F;
    }

    /** Registration id for the layer, kept next to the layer itself. */
    public static ResourceLocation layerId() {
        return ResourceLocation.fromNamespaceAndPath(TheTruth.MOD_ID, "uncertainty_noise");
    }
}
