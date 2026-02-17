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

        // --- SUBTITLES ---
        add(ModKeys.SUBTITLE_FLYING_KEY, "Drone humming");

        // --- INTERFAZ (GUI) ---
        // Claves personalizadas que usar en los menús
        add(ModKeys.GUI_DRONE_MENU_MODES_LABEL, "Modes");
        add(ModKeys.GUI_DRONE_MENU_STORAGE_LABEL, "Storage");

        // Site Planner Translations
        add(ModKeys.GUI_SITE_PLANNER_START_SET, "Start position set!");
        add(ModKeys.GUI_SITE_PLANNER_END_SET, "End position set! Volume: %s blocks");
        add(ModKeys.GUI_SITE_PLANNER_CLEARED, "Selection cleared!");
        add(ModKeys.GUI_SITE_PLANNER_ERROR_VOLUME_SMALL,
                "Too small! Minimum volume is %s. Your selection is %s");
        add(ModKeys.GUI_SITE_PLANNER_ERROR_MAX_SIZE,
                "Too big! No side can be larger than %s blocks. Your selection: %s×%s×%s");
        add(ModKeys.GUI_SITE_PLANNER_SLOT_HINT, "Place configured Site Planner here");
        // Site Planner Advanced Controls
        add(ModKeys.GUI_SITE_PLANNER_RESIZED, "Resized: %s×%s×%s (%s blocks)");
        add(ModKeys.GUI_SITE_PLANNER_MOVED, "Moved %s by %s blocks");
        add(ModKeys.GUI_SITE_PLANNER_ERROR_RESIZE_LIMIT, "Cannot resize: would exceed limits!");
        // HUD
        add(ModKeys.HUD_SITE_PLANNER_TITLE, "§6§lSite Planner Controls");
        add(ModKeys.HUD_SITE_PLANNER_RESIZE, "§7Shift + Scroll: §fResize");
        add(ModKeys.HUD_SITE_PLANNER_MOVE, "§7Ctrl + Shift + Scroll: §fMove");

        // Tooltips
        add(ModKeys.TOOLTIP_SITE_PLANNER_NO_SELECTION, "No area selected");
        add(ModKeys.TOOLTIP_SITE_PLANNER_INCOMPLETE, "Waiting for End position...");
        add(ModKeys.TOOLTIP_SITE_PLANNER_START, "Start: [%s, %s, %s]");
        add(ModKeys.TOOLTIP_SITE_PLANNER_END, "End: [%s, %s, %s]");
        add(ModKeys.TOOLTIP_SITE_PLANNER_VOLUME, "Total Volume: %s blocks");

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

        // --- ADVANCEMENTS ---
        add(ModKeys.ADVANCEMENT_DRONE_HASTE_TITLE, "Hastening Drones!");
        add(ModKeys.ADVANCEMENT_DRONE_HASTE_DESCRIPTION, "Empower a Assistance Drone with a Haste Beacon");
    }
}