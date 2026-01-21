package com.uemc.assistance_drone.datagen;

import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.entities.ModEntities;
import com.uemc.assistance_drone.items.ModItems;
import com.uemc.assistance_drone.util.ModKeys;
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
        addItem(ModItems.SITE_PLANNER, "Site Planner");

        // --- ENTITIES ---
        addEntityType(ModEntities.DRONE_ENTITY_TYPE, "Drone");

        // --- CREATIVE TABS ---
        add(ModKeys.GUI_CREATIVE_MODE_TAB_TITLE, "Assistance Drone");

        // --- INTERFAZ (GUI) ---
        // Claves personalizadas que usar en los menús
        add(ModKeys.GUI_DRONE_MENU_TITLE, "Drone");
        add(ModKeys.GUI_DRONE_MENU_MODES_LABEL, "Modes");
        add(ModKeys.GUI_DRONE_MENU_STORAGE_LABEL, "Storage");

        // Site Planner Translations
        add(ModKeys.GUI_SITE_PLANNER_START_SET, "Start position set!");
        add(ModKeys.GUI_SITE_PLANNER_NEW_START_SET, "New Start position set!");
        add(ModKeys.GUI_SITE_PLANNER_END_SET, "End position set! Volume: %s blocks");
        add(ModKeys.GUI_SITE_PLANNER_CLEARED, "Selection cleared!");
        add(ModKeys.GUI_SITE_PLANNER_CANCELLED, "Selection cancelled (Item changed).");
        add(ModKeys.GUI_SITE_PLANNER_ERROR_VOLUME, "§cError: Volume is too small (%s blocks). Minimum is 8.");

        // --- ESTADOS (GUI) ---
        // 1. IDLE
        add(ModKeys.getStateTitleKey(ModKeys.STATE_IDLE), "Idle");
        add(ModKeys.getStateDescKey(ModKeys.STATE_IDLE), "Drone will stay in position");

        // 2. FOLLOW
        add(ModKeys.getStateTitleKey(ModKeys.STATE_FOLLOW), "Follow");
        add(ModKeys.getStateDescKey(ModKeys.STATE_FOLLOW), "Drone will follow you");

        // 3. MINE
        add(ModKeys.getStateTitleKey(ModKeys.STATE_MINE), "Mine");
        add(ModKeys.getStateDescKey(ModKeys.STATE_MINE), "Drone will mine blocks in the area designated by the site planner");

        // 4. PICKUP
        add(ModKeys.getStateTitleKey(ModKeys.STATE_PICKUP), "Pickup");
        add(ModKeys.getStateDescKey(ModKeys.STATE_PICKUP), "Drone will collect items in the area designated by the site planner");
    }
}