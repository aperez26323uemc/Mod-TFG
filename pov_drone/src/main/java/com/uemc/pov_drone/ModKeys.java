package com.uemc.pov_drone;

public final class ModKeys {

    private ModKeys() {
    }

    public static final int TETHER_RADIUS_BLOCKS = 40;
    public static final int TETHER_WARNING_BLOCKS = 5;
    public static final int INTRO_MESSAGE_TICKS = 100;
    public static final double DRONE_SPEED_BLOCKS_PER_TICK = 0.45D;
    public static final int WARNING_MESSAGE_COOLDOWN_TICKS = 20;

    public static final String INPUT_PACKET = "pov_input";
    public static final String EXIT_PACKET = "pov_exit";
    public static final String SESSION_PACKET = "pov_session";

    public static final String ACTIONBAR_INTRO = "actionbar.pov_drone.intro";
    public static final String ACTIONBAR_TETHER_WARNING = "actionbar.pov_drone.tether_warning";
}
