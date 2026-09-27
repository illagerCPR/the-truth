package io.github.illagercpr.thetruth.registry;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.blockentity.CertusAnchorBlockEntity;
import io.github.illagercpr.thetruth.blockentity.CertusSolidifierBlockEntity;
import io.github.illagercpr.thetruth.blockentity.QuantumEntranceBlockEntity;
import io.github.illagercpr.thetruth.blockentity.UmbilicalAnchorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block entity types of The Truth. */
public final class TheTruthBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TheTruth.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CertusAnchorBlockEntity>> CERTUS_ANCHOR =
        BLOCK_ENTITIES.register("certus_anchor",
            () -> BlockEntityType.Builder.of(CertusAnchorBlockEntity::new, TheTruthBlocks.CERTUS_ANCHOR.get())
                .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuantumEntranceBlockEntity>> QUANTUM_ENTRANCE =
        BLOCK_ENTITIES.register("quantum_entrance",
            () -> BlockEntityType.Builder.of(QuantumEntranceBlockEntity::new, TheTruthBlocks.QUANTUM_ENTRANCE.get())
                .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CertusSolidifierBlockEntity>> CERTUS_SOLIDIFIER =
        BLOCK_ENTITIES.register("certus_solidifier",
            () -> BlockEntityType.Builder.of(
                CertusSolidifierBlockEntity::new, TheTruthBlocks.CERTUS_SOLIDIFIER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UmbilicalAnchorBlockEntity>> UMBILICAL_ANCHOR =
        BLOCK_ENTITIES.register("umbilical_anchor",
            () -> BlockEntityType.Builder.of(
                UmbilicalAnchorBlockEntity::new, TheTruthBlocks.UMBILICAL_ANCHOR.get()).build(null));

    private TheTruthBlockEntities() {
    }
}
