package io.github.illagercpr.thetruth.client;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.client.render.CertusCreatureRenderers;
import io.github.illagercpr.thetruth.registry.TheTruthDimensions;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionTransitionScreenEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Client-only bootstrap. Referenced from the mod constructor behind a
 * {@code dist.isClient()} guard so dedicated servers never load this class.
 */
@OnlyIn(Dist.CLIENT)
public final class TheTruthClient {

    private TheTruthClient() {
    }

    public static void onRegisterDimensionSpecialEffects(final RegisterDimensionSpecialEffectsEvent event) {
        event.register(
            ResourceLocation.fromNamespaceAndPath(TheTruth.MOD_ID, "certus"), new CertusDimensionEffects());
    }

    public static void onRegisterGuiLayers(final RegisterGuiLayersEvent event) {
        event.registerAboveAll(CertusUncertaintyOverlay.layerId(), new CertusUncertaintyOverlay());
    }

    /** M4: the "stored and transmitted" cross-dimension transition screen. */
    public static void onRegisterDimensionTransitionScreens(final RegisterDimensionTransitionScreenEvent event) {
        event.registerIncomingEffect(TheTruthDimensions.CERTUS, StoredTransportScreen::incoming);
        event.registerOutgoingEffect(TheTruthDimensions.CERTUS, StoredTransportScreen::outgoing);
    }

    /** M6: models and renderers of the Certus creatures. */
    public static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        CertusCreatureRenderers.onRegisterRenderers(event);
    }

    public static void onRegisterLayerDefinitions(final EntityRenderersEvent.RegisterLayerDefinitions event) {
        CertusCreatureRenderers.onRegisterLayerDefinitions(event);
    }

    /** M7: the Certus mark on ME storage cells after the endgame. */
    public static void onRegisterItemDecorations(
            final net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent event) {
        CertusItemDecorations.onRegisterItemDecorations(event);
    }
}
