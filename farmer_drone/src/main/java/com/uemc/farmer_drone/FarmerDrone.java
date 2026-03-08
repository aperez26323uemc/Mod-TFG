package com.uemc.farmer_drone;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Entry-point for the Farmer Drone addon.
 */
@Mod(FarmerDrone.MODID)
public class FarmerDrone {

    public static final String MODID = "farmer_drone";

    public FarmerDrone(IEventBus modEventBus, ModContainer modContainer) {
        // Intentionally empty: registration is performed via mixins and static event subscribers.
    }
}
