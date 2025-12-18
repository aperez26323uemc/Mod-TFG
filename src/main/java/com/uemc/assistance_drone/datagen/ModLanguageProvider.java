package com.uemc.assistance_drone.datagen;

import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.entities.ModEntities;
import com.uemc.assistance_drone.entities.drone.DroneStateIds;
import com.uemc.assistance_drone.items.ModItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class ModLanguageProvider extends LanguageProvider {

    public ModLanguageProvider(PackOutput output, String locale) {
        super(output, AssistanceDrone.MODID, locale);
    }

    @Override
    protected void addTranslations() {
        // --- ITEMS ---
        addItem(ModItems.DRONE_ITEM, "Drone");
        addItem(ModItems.BluePrint, "Blue Print");

        // --- ENTITIES ---
        addEntityType(ModEntities.DRONE_ENTITY_TYPE, "Drone");

        // --- CREATIVE TABS ---
        add("itemGroup.assistance_drone", "Assistance Drone");

        // --- INTERFAZ (GUI) ---
        // Claves personalizadas que usar en los menús
        add("gui.assistance_drone.drone_title", "Drone Inventory");

        // --- ESTADOS (GUI) ---
        add(DroneStateIds.IDLE, "IDLE");
        add(DroneStateIds.IDLE + "desc", "Drone will stay in position");
        add(DroneStateIds.FOLLOW, "FOLLOW");
        add(DroneStateIds.FOLLOW + "desc", "Drone will follow you");
    }
}