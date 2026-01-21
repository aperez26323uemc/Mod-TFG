package com.uemc.assistance_drone.util;

public class ModKeys {
    private ModKeys() {}

    // ====================
    //  Mod IDs (Recursos internos)
    // ====================
    public static final String CREATIVE_MODE_TAB_KEY = "assistance_drone_tab";
    public static final String DRONE_ENTITY_KEY = "drone";
    public static final String DRONE_ITEM_KEY = "drone_item";
    public static final String DRONE_MENU_KEY = "drone_menu";
    public static final String DRONE_FLYING_SOUND_KEY = "flying_sound";
    public static final String SITE_PLANNER_ITEM_KEY = "site_planner";
    public static final String SITE_PLANNER_START_POS_KEY = "start_pos";
    public static final String SITE_PLANNER_END_POS_KEY = "end_pos";
    public static final String STATE_NETWORK_MESSAGE_PATH = "state_network_message";

    // ====================
    //  Estados Lógicos (NBT / Network)
    // ====================
    public static final String STATE_IDLE = "idle";
    public static final String STATE_FOLLOW = "follow";
    public static final String STATE_MINE = "mine";
    public static final String STATE_PICKUP = "pickup";

    // ====================
    //  Translation Keys (Para en_us.json)
    // ====================

    // GUI General
    public static final String GUI_CREATIVE_MODE_TAB_TITLE = "itemGroup." + CREATIVE_MODE_TAB_KEY; // Standard Minecraft format
    public static final String GUI_DRONE_MENU_TITLE = "gui.assistance_drone.drone_title";
    public static final String GUI_DRONE_MENU_MODES_LABEL = "gui.assistance_drone.modes_label";
    public static final String GUI_DRONE_MENU_STORAGE_LABEL = "gui.assistance_drone.storage_label";

    // Site Planner Messages
    public static final String GUI_SITE_PLANNER_START_SET = "gui.assistance_drone.site_planner.start_set";
    public static final String GUI_SITE_PLANNER_NEW_START_SET = "gui.assistance_drone.site_planner.new_start_set";
    public static final String GUI_SITE_PLANNER_END_SET = "gui.assistance_drone.site_planner.end_set";
    public static final String GUI_SITE_PLANNER_CLEARED = "gui.assistance_drone.site_planner.selection_cleared";
    public static final String GUI_SITE_PLANNER_CANCELLED = "gui.assistance_drone.site_planner.selection_cancelled";
    public static final String GUI_SITE_PLANNER_ERROR_VOLUME = "gui.assistance_drone.site_planner.error.volume_too_small";

    // Prefijos para Estados (Privados, se acceden vía helper)
    private static final String GUI_STATE_TITLE_PREFIX = "gui.assistance_drone.state.title.";
    private static final String GUI_STATE_DESCRIPTION_PREFIX = "gui.assistance_drone.state.desc.";

    // ====================
    //  Helpers
    // ====================
    /** Returns: gui.assistance_drone.state.title.{stateId} */
    public static String getStateTitleKey(String stateId) {
        return GUI_STATE_TITLE_PREFIX + stateId;
    }

    /** Returns: gui.assistance_drone.state.desc.{stateId} */
    public static String getStateDescKey(String stateId) {
        return GUI_STATE_DESCRIPTION_PREFIX + stateId;
    }
}