package com.uemc.pov_drone.datagen;

import com.uemc.pov_drone.ModKeys;
import com.uemc.pov_drone.PovDrone;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class ModLanguageProvider extends LanguageProvider {

    public ModLanguageProvider(PackOutput output, String locale) {
        super(output, PovDrone.MODID, locale);
    }

    @Override
    protected void addTranslations() {
        add(ModKeys.ACTIONBAR_INTRO, "Press %1$s on your body to return");
        add(ModKeys.ACTIONBAR_TETHER_WARNING, "You can't go farther");
    }
}
