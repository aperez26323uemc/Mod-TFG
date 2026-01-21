package com.uemc.assistance_drone.entities.drone.goals;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.util.ModKeys;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class DroneIdleGoal extends Goal {
    private final DroneEntity drone;

    public DroneIdleGoal(DroneEntity drone) {
        this.drone = drone;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    public String getStateId() {
        return ModKeys.STATE_IDLE;
    }

    @Override
    public boolean canUse() {
        return getStateId().equals(drone.getState());
    }

    @Override
    public void tick() {
        Player owner = drone.getOwner();

        // Bobbing (Oscilación)
        double bobbing = Math.sin(drone.tickCount * 0.1) * 0.2;
        Vec3 targetPos;

        if (owner != null && drone.distanceToSqr(owner) < 64.0) {
            // Mirar al dueño si está cerca (usando la lógica compartida)
            targetPos = drone.getLogic().calculateIdealPosition(drone.getX(), owner.getEyeY(), drone.getZ())
                    .add(0, bobbing, 0);
        } else {
            // Flotar en el sitio
            Vec3 current = drone.getLogic().calculateIdealPosition(drone.getX(), drone.getY(), drone.getZ());
            targetPos = new Vec3(current.x, current.y + bobbing, current.z);
        }

        // Ejecutar movimiento usando la caja de herramientas
        drone.getLogic().executeMovement(targetPos);
    }
}