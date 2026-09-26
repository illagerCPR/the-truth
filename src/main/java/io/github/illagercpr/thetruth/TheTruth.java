package io.github.illagercpr.thetruth;

import com.mojang.logging.LogUtils;
import io.github.illagercpr.thetruth.client.TheTruthClient;
import io.github.illagercpr.thetruth.registry.TheTruthAttachments;
import io.github.illagercpr.thetruth.registry.TheTruthBlockEntities;
import io.github.illagercpr.thetruth.registry.TheTruthBlocks;
import io.github.illagercpr.thetruth.registry.TheTruthChunkGenerators;
import io.github.illagercpr.thetruth.registry.TheTruthCreativeTabs;
import io.github.illagercpr.thetruth.registry.TheTruthDataComponents;
import io.github.illagercpr.thetruth.registry.TheTruthItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionTransitionScreenEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.slf4j.Logger;

/**
 * The Truth — an Applied Energistics 2 endgame expansion.
 *
 * <p>M3: deterministic coverage (anchor field + AE2 access points) and the
 * three-tier uncertainty curve (see docs/02-开发计划.md).
 */
@Mod(TheTruth.MOD_ID)
public final class TheTruth {

    public static final String MOD_ID = "thetruth";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TheTruth(final IEventBus modEventBus, final ModContainer modContainer) {
        LOGGER.info("The Truth {} initialized", modContainer.getModInfo().getVersion());

        TheTruthBlocks.BLOCKS.register(modEventBus);
        TheTruthItems.ITEMS.register(modEventBus);
        TheTruthBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        TheTruthCreativeTabs.TABS.register(modEventBus);
        TheTruthChunkGenerators.GENERATORS.register(modEventBus);
        TheTruthDataComponents.DATA_COMPONENTS.register(modEventBus);
        TheTruthAttachments.ATTACHMENTS.register(modEventBus);

        // AE2 discovers in-world grid node hosts through this capability; without
        // registering it, neighbouring AE2 devices can never connect to the anchor.
        modEventBus.addListener(RegisterCapabilitiesEvent.class, TheTruth::onRegisterCapabilities);

        if (FMLEnvironment.dist.isClient()) {
            // Client-only classes must stay unloaded on dedicated servers.
            modEventBus.addListener(
                RegisterDimensionSpecialEffectsEvent.class,
                TheTruthClient::onRegisterDimensionSpecialEffects);
            modEventBus.addListener(
                RegisterGuiLayersEvent.class,
                TheTruthClient::onRegisterGuiLayers);
            modEventBus.addListener(
                RegisterDimensionTransitionScreenEvent.class,
                TheTruthClient::onRegisterDimensionTransitionScreens);
        }
    }

    private static void onRegisterCapabilities(final RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST,
            TheTruthBlockEntities.CERTUS_ANCHOR.get(),
            (blockEntity, side) -> blockEntity);
        // M4: the quantum entrance core joins ME networks the same way.
        event.registerBlockEntity(
            appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST,
            TheTruthBlockEntities.QUANTUM_ENTRANCE.get(),
            (blockEntity, side) -> blockEntity);
    }
}
