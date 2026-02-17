package com.uemc.assistance_drone.menus;

import com.uemc.assistance_drone.AssistanceDrone;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, AssistanceDrone.MODID);

    public static final Supplier<MenuType<DroneMenu>> DRONE_MENU =
            MENU_TYPES.register(DroneMenu.ID, () -> IMenuTypeExtension.create(DroneMenu::new));
}
