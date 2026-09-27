package io.github.illagercpr.thetruth.registry;

import com.mojang.serialization.Codec;
import appeng.api.stacks.GenericStack;
import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.transport.EntanglementPairData;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
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

    /**
     * M5 unformed matter content: the original stack an uncertain item degraded
     * into unformed matter, kept so the Certus Solidifier can write it back
     * ("re-serialize into a certain instance"). Present only on unformed matter
     * that used to be a player item; free (mined) unformed matter has none.
     * Wrapped in {@link ItemContainerContents} — data component values must be
     * immutable with equals/hashCode, which bare {@code ItemStack} is not.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> UNFORMED_CONTENT =
        DATA_COMPONENTS.registerComponentType("unformed_content",
            builder -> builder.persistent(ItemContainerContents.CODEC)
                .networkSynchronized(ItemContainerContents.STREAM_CODEC));

    /**
     * M5 Certus Cell contents. Persisted but deliberately NOT network
     * synchronized: "unformed" means the ledger is invisible while offline, and
     * the tooltip never reads it on the client side.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<GenericStack>>> CERTUS_CELL_CONTENT =
        DATA_COMPONENTS.registerComponentType("certus_cell_content",
            builder -> builder.persistent(GenericStack.FAULT_TOLERANT_NULLABLE_LIST_CODEC));

    private TheTruthDataComponents() {
    }
}
