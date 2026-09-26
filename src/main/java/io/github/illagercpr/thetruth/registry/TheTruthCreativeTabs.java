package io.github.illagercpr.thetruth.registry;

import io.github.illagercpr.thetruth.TheTruth;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Creative tabs of The Truth. */
public final class TheTruthCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TheTruth.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.thetruth.main"))
            .icon(() -> new ItemStack(TheTruthItems.CERTUS_STONE.get()))
            .displayItems((parameters, output) -> output.accept(TheTruthItems.CERTUS_STONE.get()))
            .build());

    private TheTruthCreativeTabs() {
    }
}
