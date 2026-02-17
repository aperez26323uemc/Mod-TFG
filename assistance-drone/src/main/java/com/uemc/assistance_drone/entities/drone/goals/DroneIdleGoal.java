package com.uemc.assistance_drone.entities.drone.goals;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.util.ModKeys;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class DroneIdleGoal extends Goal {
    private final DroneEntity drone;
    private int calculationDelay = 0;
    private Vec3 cachedTargetPos;

    public DroneIdleGoal(DroneEntity drone) {
        this.drone = drone;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return ModKeys.STATE_IDLE.equals(drone.getState());
    }

    @Override
    public void tick() {
        calculationDelay--;

        // Solo recalculamos la posición ideal cada 10 ticks (0.5 segundos)
        // O si no tenemos una posición guardada
        if (calculationDelay <= 0 || cachedTargetPos == null) {
            Player owner = drone.getOwner();

            if (owner != null && drone.distanceToSqr(owner) < 64.0) {
                // Mirar al dueño: calculamos posición una vez
                cachedTargetPos = drone.getLogic().calculateIdealPosition(drone.getX(), owner.getEyeY(), drone.getZ());
            } else {
                // Flotar: calculamos posición una vez
                cachedTargetPos = drone.getLogic().calculateIdealPosition(drone.getX(), drone.getY(), drone.getZ());
            }
            calculationDelay = 10;
        }

        // Solo ejecutamos movimiento si estamos lejos del objetivo para evitar vibraciones (jitter)
        if (drone.position().distanceToSqr(cachedTargetPos) > 0.3) {
            drone.getLogic().executeMovement(cachedTargetPos);
        } else {
            // Si ya estamos ahí, simplemente bajamos la velocidad para que no parezca "congelado"
            drone.setDeltaMovement(drone.getDeltaMovement().scale(0.9));
        }
    }
}