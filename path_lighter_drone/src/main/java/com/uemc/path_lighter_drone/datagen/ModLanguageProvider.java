package com.uemc.path_lighter_drone.datagen;

import com.uemc.path_lighter_drone.ModKeys;
import com.uemc.path_lighter_drone.PathLighterDrone;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class ModLanguageProvider extends LanguageProvider {

    public ModLanguageProvider(PackOutput output, String locale) {
        super(output, PathLighterDrone.MODID, locale);
    }

    @Override
    protected void addTranslations() {
        add(ModKeys.KEY_CATEGORY, "Assistance Drone");
        add(ModKeys.KEY_PATH_LIGHTER, "Dispatch path lighting");
        add(ModKeys.HUD_PATH_LIGHTER_HINT, "[Path Lighter] Release %s to queue path lighting");
    }
}
