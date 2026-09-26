package io.github.illagercpr.thetruth.registry;

import com.mojang.serialization.Codec;
import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.transport.EntanglementPairData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item data components of The Truth.
 *
 * <p>M3 middle-layer randomization stores the pre-randomization stack size on
 * the affected stacks, so "loss" is always reversible once the player is covered
 * again (red line: never silently delete player property).
 */
public final class TheTruthDataComponents {

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, TheTruth.MOD_ID);

    /**
     * Original stack size recorded when the middle layer randomized a stack.
     * Absent on fully deterministic stacks.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> UNCERTAIN_COUNT =
        DATA_COMPONENTS.registerComponentType("uncertain_count",
            builder -> builder.persistent(Codec.intRange(1, 99))
                .networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * M4 entanglement pair data of an Entanglement Key: pair id plus the home
     * entrance core position. Absent on unbound (unpaired) keys.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<EntanglementPairData>> ENTANGLEMENT_PAIR =
        DATA_COMPONENTS.registerComponentType("entanglement_pair",
            builder -> builder.persistent(EntanglementPairData.CODEC)
                .networkSynchronized(EntanglementPairData.STREAM_CODEC));

    private TheTruthDataComponents() {
    }
}
