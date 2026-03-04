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
        add(ModKeys.ACTIONBAR_INTRO, "Haz click izquierdo en tu cuerpo para volver");
        add(ModKeys.ACTIONBAR_TETHER_WARNING, "Límite de señal alcanzado");
    }
}
