package com.uemc.assistance_drone.entities.drone.goals;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.util.ModKeys;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class DroneFollowGoal extends Goal {
    private final DroneEntity drone;

    public DroneFollowGoal(DroneEntity drone) {
        this.drone = drone;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    public String getStateId() {
        return ModKeys.STATE_FOLLOW;
    }

    @Override
    public boolean canUse() {
        return getStateId().equals(drone.getState()) && drone.getOwner() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse(); // Si pierde el dueño o cambia el estado, para.
    }

    @Override
    public void stop() {
        drone.getNavigation().stop();
    }

    @Override
    public void tick() {
        Player owner = drone.getOwner();
        if (owner == null) return;

        // Bobbing
        double bobbing = Math.sin(drone.tickCount * 0.1) * 0.2;

        // Usamos la lógica compleja delegada
        Vec3 targetPos = drone.getLogic().getSafetyTarget(owner, 2.5).add(0, bobbing, 0);

        drone.getLogic().executeMovement(targetPos);
    }
}