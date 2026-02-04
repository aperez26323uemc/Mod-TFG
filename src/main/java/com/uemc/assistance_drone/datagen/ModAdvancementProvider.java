package com.uemc.assistance_drone.datagen;

import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.advancements.ModCriteriaTriggers;
import com.uemc.assistance_drone.items.ModItems;
import com.uemc.assistance_drone.util.ModKeys;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.advancements.AdvancementSubProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ModAdvancementProvider extends AdvancementProvider {

    public ModAdvancementProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, ExistingFileHelper existingFileHelper) {
        // Registramos nuestro generador interno
        super(output, lookupProvider, existingFileHelper, List.of(new ModAdvancements()));
    }

    private static final class ModAdvancements implements AdvancementProvider.AdvancementGenerator {

        @Override
        public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> saver, ExistingFileHelper existingFileHelper) {

            // --- LOGRO: DRONE HASTE ---
            Advancement.Builder.advancement()
                    .parent(AdvancementSubProvider.createPlaceholder("minecraft:nether/create_beacon")) // Padre externo (Vanilla)
                    .display(
                            new ItemStack(ModItems.DRONE_ITEM.get()), // Icono
                            Component.translatable(ModKeys.ADVANCEMENT_DRONE_HASTE_TITLE),
                            Component.translatable(ModKeys.ADVANCEMENT_DRONE_HASTE_DESCRIPTION),
                            null, // Fondo (null porque tiene padre)
                            AdvancementType.GOAL, // Frame: TASK, GOAL, CHALLENGE
                            true, // showToast
                            true, // announceToChat
                            true // hidden
                    )
                    // Añadimos el criterio usando tu Trigger registrado
                    .addCriterion("drone_buffed", ModCriteriaTriggers.DRONE_HASTE_MINING.get().createCriterion(
                            new PlayerTrigger.TriggerInstance(Optional.empty()) // Condición vacía {}
                    ))
                    // Guardamos con el ID: assistance_drone:drone_haste
                    .save(saver, ResourceLocation.fromNamespaceAndPath(AssistanceDrone.MODID, "drone_haste"), existingFileHelper);
        }
    }
}