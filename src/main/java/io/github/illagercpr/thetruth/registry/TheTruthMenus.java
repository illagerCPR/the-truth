package io.github.illagercpr.thetruth.registry;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.gui.CertusSolidifierMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Menu types of The Truth (M8): the solidifier gains its player GUI
 * (docs/02 M5 decision 1 — "GUI deferred to M8"). The factory reads the
 * block position NeoForge's openMenu writes into the buffer, so both sides
 * resolve the same block entity.
 */
public final class TheTruthMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, TheTruth.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<CertusSolidifierMenu>> CERTUS_SOLIDIFIER =
        MENUS.register("certus_solidifier",
            () -> IMenuTypeExtension.create(CertusSolidifierMenu::fromNetwork));

    private TheTruthMenus() {
    }
}
