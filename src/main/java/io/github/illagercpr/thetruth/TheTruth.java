package io.github.illagercpr.thetruth;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * The Truth — an Applied Energistics 2 endgame expansion.
 *
 * <p>M0 scaffold: mod entry point only. The Certus dimension and its mechanics
 * arrive in later milestones (see docs/02-开发计划.md).
 */
@Mod(TheTruth.MOD_ID)
public final class TheTruth {

    public static final String MOD_ID = "thetruth";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TheTruth(final IEventBus modEventBus, final ModContainer modContainer) {
        LOGGER.info("The Truth {} initialized", modContainer.getModInfo().getVersion());
    }
}
