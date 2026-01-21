package com.uemc.assistance_drone.datagen;

import com.uemc.assistance_drone.AssistanceDrone;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = AssistanceDrone.MODID)
public class DataGenerators {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        // 1. Idiomas
        generator.addProvider(event.includeClient(), new ModLanguageProvider(packOutput, "en_us"));

        // 2. Recetas
        generator.addProvider(event.includeServer(), new ModRecipeProvider(packOutput, lookupProvider));

        // 3. Sonidos
        generator.addProvider(event.includeClient(), new ModSoundProvider(packOutput, event.getExistingFileHelper()));
    }
}