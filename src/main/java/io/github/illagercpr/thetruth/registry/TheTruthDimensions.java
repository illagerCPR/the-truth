package io.github.illagercpr.thetruth.registry;

import io.github.illagercpr.thetruth.TheTruth;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Dimension identity of the Certus dimension. The dimension itself is provided
 * as a datapack JSON (data/thetruth/dimension/certus.json), so only the
 * {@link ResourceKey} is defined here.
 */
public final class TheTruthDimensions {

    public static final ResourceKey<Level> CERTUS = ResourceKey.create(
        Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath(TheTruth.MOD_ID, "certus"));

    /** Looks up the loaded Certus dimension, or null when it failed to load. */
    public static ServerLevel certusLevel(final MinecraftServer server) {
        return server.getLevel(CERTUS);
    }

    private TheTruthDimensions() {
    }
}
