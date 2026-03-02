package com.uemc.path_lighter_drone;

/**
 * Constants for identifiers, translations and algorithm tunables.
 */
public final class ModKeys {

    private ModKeys() {
    }

    public static final int PATH_RADIUS_BLOCKS = 48;
    public static final int FIRST_NODE_DISTANCE = 3;
    public static final int NODE_SPACING = 7;
    public static final int NODE_LIGHT_THRESHOLD = 5;
    public static final int SUITABLE_LIGHT_EMISSION = 14;
    public static final int NODE_DEVIATION_RADIUS = 5;
    public static final long KEY_RELEASE_COOLDOWN_MS = 1_000L;
    public static final int NODE_TIMEOUT_TICKS = 1200;
    public static final int WALL_HEIGHT_BLOCKS = 4;

    public static final String PATH_LIGHTER_PACKET = "path_lighter_request";

    public static final String KEY_CATEGORY = "key.categories.path_lighter_drone";
    public static final String KEY_PATH_LIGHTER = "key.path_lighter_drone.path_lighter";
    public static final String HUD_PATH_LIGHTER_HINT = "hud.path_lighter_drone.follow_hint";
}
