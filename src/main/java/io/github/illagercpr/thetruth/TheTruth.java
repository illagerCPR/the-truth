package io.github.illagercpr.thetruth;

import com.mojang.logging.LogUtils;
import io.github.illagercpr.thetruth.client.TheTruthClient;
import io.github.illagercpr.thetruth.registry.TheTruthBlocks;
import io.github.illagercpr.thetruth.registry.TheTruthChunkGenerators;
import io.github.illagercpr.thetruth.registry.TheTruthCreativeTabs;
import io.github.illagercpr.thetruth.registry.TheTruthItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.slf4j.Logger;

/**
 * The Truth — an Applied Energistics 2 endgame expansion.
 *
 * <p>M2: custom Certus chunk generator (three layers + grid gaps), vertical
 * layer biomes and the observation cycle state machine (see docs/02-开发计划.md).
 */
@Mod(TheTruth.MOD_ID)
public final class TheTruth {

    public static final String MOD_ID = "thetruth";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TheTruth(final IEventBus modEventBus, final ModContainer modContainer) {
        LOGGER.info("The Truth {} initialized", modContainer.getModInfo().getVersion());

        TheTruthBlocks.BLOCKS.register(modEventBus);
        TheTruthItems.ITEMS.register(modEventBus);
        TheTruthCreativeTabs.TABS.register(modEventBus);
        TheTruthChunkGenerators.GENERATORS.register(modEventBus);

        if (FMLEnvironment.dist.isClient()) {
            // Client-only classes must stay unloaded on dedicated servers.
            modEventBus.addListener(
                RegisterDimensionSpecialEffectsEvent.class,
                TheTruthClient::onRegisterDimensionSpecialEffects);
        }
    }
}
