package com.uemc.assistance_drone.entities.drone.goals;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.EnumSet;
import java.util.function.Predicate;

public class DronePickupGoal extends Goal {
    private final DroneEntity drone;
    private final Predicate<String> activationCondition;

    public DronePickupGoal(DroneEntity drone, Predicate<String> activationCondition) {
        this.drone = drone;
        this.activationCondition = activationCondition;

        // También reclamamos la mirada. Si está recogiendo, mira al ítem, no al jugador.
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // 1. Chequeo de estado (Inyectado)
        if (!activationCondition.test(drone.getState())) {
            return false;
        }

        // 2. Chequeo de entorno: ¿Hay items cerca?
        // return !drone.level().getEntitiesOfClass(ItemEntity.class, ...).isEmpty();

        return false; // Placeholder: false por defecto para que no crashee hasta tener lógica real
    }

    @Override
    public void tick() {
        // Lógica de ir hacia el ítem y recogerlo
    }

}
