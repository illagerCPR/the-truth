package io.github.illagercpr.thetruth.cycle;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.registry.TheTruthDimensions;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Server-side driver of the observation cycle: every Certus level tick the
 * level's {@code dayTime} is overwritten with {@link ObservationCycle#mapToSkyPhase}
 * of the level's {@code gameTime}, so the vanilla sky, sky darkness and fog
 * brightness follow the observation rhythm. {@code gameTime} keeps counting
 * independently, which makes the mapping stateless and re-entrant.
 */
/** bus() 默认即为 GAME 总线（该属性已标记废弃，显式传值会触发 removal 警告）。 */
@EventBusSubscriber(modid = TheTruth.MOD_ID)
public final class ObservationCycleTicker {

    private ObservationCycleTicker() {
    }

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == TheTruthDimensions.CERTUS) {
            level.setDayTime(ObservationCycle.mapToSkyPhase(ObservationCycle.cycleTick(level.getGameTime())));
        }
    }
}
