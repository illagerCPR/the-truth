package io.github.illagercpr.thetruth.client;

import io.github.illagercpr.thetruth.TheTruth;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

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
}
