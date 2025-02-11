package com.uemc.assistance_drone.entities.drone;

public class DroneState {

    private final String state;

    public DroneState(String state) {
        this.state = state;
    }

    public String getStateName() {
        return state;
    }
}
