package com.uemc.farmer_drone.datagen;

import com.uemc.farmer_drone.FarmerDrone;
import com.uemc.farmer_drone.ModKeys;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * Language provider for Farmer Drone addon translations.
 */
public class ModLanguageProvider extends LanguageProvider {

    public ModLanguageProvider(PackOutput output, String locale) {
        super(output, FarmerDrone.MODID, locale);
    }

    @Override
    protected void addTranslations() {
        add(ModKeys.getStateTitleKey(ModKeys.STATE_FARMER), "Farmer");
        add(ModKeys.getStateDescriptionKey(ModKeys.STATE_FARMER),
                "Drone farms crops in the area designated by the site planner");
    }
}
