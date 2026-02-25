package com.uemc.farmer_drone.datagen;

import com.uemc.farmer_drone.FarmerDrone;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Data generation hooks for Farmer Drone addon.
 */
@EventBusSubscriber(modid = FarmerDrone.MODID)
public final class DataGenerators {

    private DataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();

        generator.addProvider(event.includeClient(), new ModLanguageProvider(output, "en_us"));
    }
}
