package com.uemc.assistance_drone.entities.drone.mixins;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DroneEntity.class)
public abstract class DroneFollowMixin extends Entity {
    @Shadow public abstract String getState();

    public DroneFollowMixin(EntityType<?> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }
/*
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onConstruct(EntityType<?> type, Level level, CallbackInfo ci) {
        //DroneStateList.registerState(new DroneState("FOLLOW"));
    }

    @Inject(method = "stateManager", at = @At("TAIL"))
    private void onStateManager(CallbackInfo ci) {
        if (this.getState().equals("")) {

        }
    }
*/
}