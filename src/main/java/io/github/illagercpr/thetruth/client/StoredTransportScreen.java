package io.github.illagercpr.thetruth.client;

import java.util.function.BooleanSupplier;

import com.mojang.blaze3d.systems.RenderSystem;

import io.github.illagercpr.thetruth.TheTruth;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The "stored and transmitted" transition screen (M4): pure darkness with
 * fading noise and a status line, replacing the vanilla nether-portal haze
 * while the player crosses to or from Certus. Registered through NeoForge's
 * RegisterDimensionTransitionScreenEvent (see TheTruthClient).
 */
@OnlyIn(Dist.CLIENT)
public class StoredTransportScreen extends ReceivingLevelScreen {

    private static final ResourceLocation NOISE_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(TheTruth.MOD_ID, "textures/gui/uncertainty_noise.png");
    /** Noise atlas is a 4x4 grid of 64px tiles inside a 256x256 texture. */
    private static final int ATLAS_SIZE = 256;
    private static final int TILE_SIZE = 64;
    private static final int TILE_FRAMES = 4;

    private final Component statusLine;
    private final Component subLine;
    private int tickCount;

    private StoredTransportScreen(final BooleanSupplier stillLoading, final Reason reason,
                                  final Component statusLine, final Component subLine) {
        super(stillLoading, reason);
        this.statusLine = statusLine;
        this.subLine = subLine;
    }

    /** Factory for jumps INTO Certus: matter is being reconstituted. */
    public static StoredTransportScreen incoming(final BooleanSupplier stillLoading, final Reason reason) {
        return new StoredTransportScreen(stillLoading, reason,
            Component.translatable("thetruth.screen.entrance.incoming"),
            Component.translatable("thetruth.screen.entrance.sub"));
    }

    /** Factory for jumps OUT of Certus: the traveler is being serialized. */
    public static StoredTransportScreen outgoing(final BooleanSupplier stillLoading, final Reason reason) {
        return new StoredTransportScreen(stillLoading, reason,
            Component.translatable("thetruth.screen.entrance.outgoing"),
            Component.translatable("thetruth.screen.entrance.sub"));
    }

    @Override
    public void tick() {
        super.tick();
        tickCount++;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        // Pure dark void; the vanilla screen's background stays invisible.
        graphics.fill(0, 0, this.width, this.height, 0xFF04070C);

        final float appear = Mth.clamp(tickCount / 16.0F, 0.0F, 1.0F);

        // Drifting uncertainty noise, faint and stretched over the whole screen.
        final int frame = (tickCount / 3) % TILE_FRAMES;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.22F * appear);
        graphics.blit(NOISE_TEXTURE, 0, 0, this.width, this.height,
            0.0F, frame * TILE_SIZE, TILE_SIZE, TILE_SIZE, ATLAS_SIZE, ATLAS_SIZE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        final int fade = ((int) (appear * 255.0F) << 24) | 0x0087E8E0;
        final int dim = ((int) (appear * 255.0F * 0.6F) << 24) | 0x00546A78;
        graphics.drawCenteredString(this.font, statusLine, this.width / 2, this.height / 2 - 12, fade);
        graphics.drawCenteredString(this.font, subLine, this.width / 2, this.height / 2 + 10, dim);
    }
}
