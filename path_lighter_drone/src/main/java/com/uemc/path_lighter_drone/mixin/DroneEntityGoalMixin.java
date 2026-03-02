package com.uemc.path_lighter_drone.mixin;

import com.uemc.path_lighter_drone.goals.DronePathLighterGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = com.uemc.assistance_drone.entities.drone.DroneEntity.class, remap = false)
public abstract class DroneEntityGoalMixin {

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void path_lighter_drone$registerGoal(CallbackInfo ci) {
        com.uemc.assistance_drone.entities.drone.DroneEntity self =
                (com.uemc.assistance_drone.entities.drone.DroneEntity) (Object) this;
        self.goalSelector.addGoal(3, new DronePathLighterGoal(self));
    }
}
