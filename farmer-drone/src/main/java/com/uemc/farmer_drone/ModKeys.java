package com.uemc.farmer_drone;

/**
 * Constants used by the Farmer Drone addon to avoid magic strings.
 */
public final class ModKeys {

    private ModKeys() {
    }

    public static final String STATE_FARMER = "farmer";

    private static final String STATE_TITLE_PREFIX = "gui.assistance_drone.state.title.";
    private static final String STATE_DESCRIPTION_PREFIX = "gui.assistance_drone.state.desc.";

    public static String getStateTitleKey(String stateId) {
        return STATE_TITLE_PREFIX + stateId;
    }

    public static String getStateDescriptionKey(String stateId) {
        return STATE_DESCRIPTION_PREFIX + stateId;
    }
}
