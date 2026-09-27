package io.github.illagercpr.thetruth.registry;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.entity.LastMeasurerEntity;
import io.github.illagercpr.thetruth.entity.ResidueEntity;
import io.github.illagercpr.thetruth.entity.SurveyorEntity;
import io.github.illagercpr.thetruth.entity.UnobservedEntity;
import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Creatures of the Certus dimension (M6, docs/00 section 7): the Residue that
 * was not deleted cleanly, the patrolling Surveyor that "determines" players
 * by scanning them, the Unobserved that only moves unseen, and the boss —
 * The Last Measurer. The boss is spawned by the Measurer Core structure block,
 * never by natural spawning.
 */
public final class TheTruthEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, TheTruth.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ResidueEntity>> RESIDUE =
        register("residue", ResidueEntity::new, MobCategory.MONSTER, 0.85F, 0.85F);

    public static final DeferredHolder<EntityType<?>, EntityType<SurveyorEntity>> SURVEYOR =
        register("surveyor", SurveyorEntity::new, MobCategory.MONSTER, 0.9F, 0.9F);

    public static final DeferredHolder<EntityType<?>, EntityType<UnobservedEntity>> UNOBSERVED =
        register("unobserved", UnobservedEntity::new, MobCategory.MONSTER, 0.7F, 2.1F);

    public static final DeferredHolder<EntityType<?>, EntityType<LastMeasurerEntity>> LAST_MEASURER =
        register("last_measurer", LastMeasurerEntity::new, MobCategory.MONSTER, 1.6F, 2.6F);

    private static <T extends net.minecraft.world.entity.Entity> DeferredHolder<EntityType<?>, EntityType<T>> register(
            final String name, final EntityType.EntityFactory<T> factory, final MobCategory category,
            final float width, final float height) {
        return ENTITY_TYPES.register(name,
            () -> EntityType.Builder.of(factory, category)
                .sized(width, height)
                .clientTrackingRange(10)
                .build(name));
    }

    private TheTruthEntities() {
    }
}
