package com.uemc.assistance_drone.entities.drone.mixins;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DroneEntity.class)
public abstract class DroneHoverMixin extends Entity{

    public DroneHoverMixin(EntityType<?> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    public void move(CallbackInfo ci) {
        //this.setDeltaMovement(0, Math.sin(this.tickCount * 0.1) * 0.05, 0); // Doesn't work
    }
}
