package io.github.illagercpr.thetruth.entity;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.cycle.ObservationCycle;
import io.github.illagercpr.thetruth.registry.TheTruthEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * Attribute and spawn wiring for the Certus creatures (M6). Spawn predicates
 * encode the docs/00 section 6 pacing: Residue haunts the middle and unobserved
 * layers (thinner during the observed phase), Surveyors cruise the debris belt,
 * and the Unobserved only materializes while the world is unobserved.
 */
@EventBusSubscriber(modid = TheTruth.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class CertusSpawns {

    /** Y where the sediment layer is considered to begin (matches UncertaintyCurve). */
    private static final int MIDDLE_TOP_Y = 72;
    /** Y below which the deep layer begins. */
    private static final int DEEP_TOP_Y = -20;
    /** Y above which the debris belt is considered cruising ground. */
    private static final int SURFACE_BOTTOM_Y = 60;

    private CertusSpawns() {
    }

    @SubscribeEvent
    public static void onEntityAttributeCreation(final EntityAttributeCreationEvent event) {
        event.put(TheTruthEntities.RESIDUE.get(), ResidueEntity.createAttributes().build());
        event.put(TheTruthEntities.SURVEYOR.get(), SurveyorEntity.createAttributes().build());
        event.put(TheTruthEntities.UNOBSERVED.get(), UnobservedEntity.createAttributes().build());
        event.put(TheTruthEntities.LAST_MEASURER.get(), LastMeasurerEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onRegisterSpawnPlacements(final RegisterSpawnPlacementsEvent event) {
        event.register(TheTruthEntities.RESIDUE.get(),
            SpawnPlacementTypes.ON_GROUND, Heightmap.Types.WORLD_SURFACE_WG,
            CertusSpawns::residueSpawnRule, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(TheTruthEntities.SURVEYOR.get(),
            SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.WORLD_SURFACE_WG,
            CertusSpawns::surveyorSpawnRule, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(TheTruthEntities.UNOBSERVED.get(),
            SpawnPlacementTypes.ON_GROUND, Heightmap.Types.WORLD_SURFACE_WG,
            CertusSpawns::unobservedSpawnRule, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /** Middle and deep layers; the observed phase thins them out to a quarter. */
    private static boolean residueSpawnRule(final EntityType<? extends Monster> type,
                                            final net.minecraft.world.level.ServerLevelAccessor level,
                                            final MobSpawnType spawnType, final BlockPos pos,
                                            final RandomSource random) {
        if (pos.getY() > MIDDLE_TOP_Y) {
            return false;
        }
        final ObservationCycle.Phase phase = phaseOf(level, pos);
        if (phase == ObservationCycle.Phase.OBSERVED && random.nextInt(4) != 0) {
            return false;
        }
        return Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    /** Debris belt only: the mechanical eye cruises the sky above the shard. */
    private static boolean surveyorSpawnRule(final EntityType<? extends Monster> type,
                                             final net.minecraft.world.level.ServerLevelAccessor level,
                                             final MobSpawnType spawnType, final BlockPos pos,
                                             final RandomSource random) {
        if (pos.getY() < SURFACE_BOTTOM_Y) {
            return false;
        }
        return Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    /** Deep layer, unobserved phases only: it comes with the dark. */
    private static boolean unobservedSpawnRule(final EntityType<? extends Monster> type,
                                               final net.minecraft.world.level.ServerLevelAccessor level,
                                               final MobSpawnType spawnType, final BlockPos pos,
                                               final RandomSource random) {
        if (pos.getY() >= DEEP_TOP_Y) {
            return false;
        }
        final ObservationCycle.Phase phase = phaseOf(level, pos);
        if (phase == ObservationCycle.Phase.OBSERVED || phase == ObservationCycle.Phase.RISING) {
            return false;
        }
        return Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    private static ObservationCycle.Phase phaseOf(final LevelReader accessor, final BlockPos pos) {
        if (accessor instanceof ServerLevel serverLevel) {
            return ObservationCycle.phaseOf(ObservationCycle.cycleTick(serverLevel.getGameTime()));
        }
        return ObservationCycle.Phase.OBSERVED;
    }
}
