package com.uemc.farmer_drone.mixin;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.entities.drone.goals.DroneGoalRegistry;
import com.uemc.farmer_drone.ModKeys;
import com.uemc.farmer_drone.goals.DroneFarmGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers Farmer Drone goal after base goals are loaded.
 */
@Mixin(value = DroneGoalRegistry.class, remap = false)
public abstract class DroneGoalRegistryMixin {

    private static final int FARMER_GOAL_PRIORITY = 2;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void farmer_drone$registerGoal(CallbackInfo ci) {
        DroneGoalRegistry.register(
                ModKeys.STATE_FARMER,
                FARMER_GOAL_PRIORITY,
                drone -> new DroneFarmGoal(drone, state -> ModKeys.STATE_FARMER.equals(state)),
                DroneEntity::hasSitePlanner
        );
    }
}
