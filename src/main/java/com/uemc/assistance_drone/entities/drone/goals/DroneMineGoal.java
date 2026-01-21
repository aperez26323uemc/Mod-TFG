package com.uemc.assistance_drone.entities.drone.goals;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.EnumSet;
import java.util.function.Predicate;

public class DroneMineGoal extends Goal {
    private final DroneEntity drone;
    private final Predicate<String> activationCondition;

    // Flag para la atomicidad: Si es true, nadie puede interrumpirnos
    private boolean isBreakingBlock = false;
    private BlockPos targetBlock = null;

    public DroneMineGoal(DroneEntity drone, Predicate<String> activationCondition) {
        this.drone = drone;
        this.activationCondition = activationCondition;

        // SENIOR ARCHITECTURE:
        // Reclamamos MOVE (para ir al bloque) y LOOK (para mirarlo fijamente).
        // Al tener LOOK, el LookAtPlayerGoal (que es de menor prioridad) se desactivará automáticamente.
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // 1. Verificamos si el estado externo nos permite activarnos
        if (!activationCondition.test(drone.getState())) {
            return false;
        }

        // 2. Aquí iría tu lógica para buscar un bloque válido cerca
        // Por ahora simulamos que siempre encuentra uno si no tiene target
        // if (targetBlock == null) targetBlock = buscarBloque();
        // return targetBlock != null;

        return true; // Placeholder para que compile y arranque
    }

    @Override
    public void start() {
        // Iniciar navegación hacia el bloque
        // System.out.println("Drone: Iniciando secuencia de minería");
    }

    @Override
    public void tick() {
        // LÓGICA SIMULADA:
        // 1. Si estamos lejos -> Viajar. isBreakingBlock = false.
        // 2. Si llegamos -> isBreakingBlock = true. Romper bloque.

        // Mientras rompemos, forzamos la mirada al bloque
        if (targetBlock != null) {
            drone.getLookControl().setLookAt(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ());
        }

        // Simulación: Activamos el flag de "trabajo duro" aleatoriamente para probar la atomicidad
        // En la implementación real, esto sería true solo mientras dura la animación de romper
        this.isBreakingBlock = (drone.tickCount % 100) > 50;
    }

    @Override
    public void stop() {
        this.isBreakingBlock = false;
        this.targetBlock = null;
    }

    /**
     * EL TOQUE MAESTRO:
     * Si estamos en la fase crítica (rompiendo), devolvemos false.
     * Esto impide que el Goal de Recoger (Prioridad 1) nos interrumpa justo
     * antes de romper el bloque, aunque aparezca un ítem.
     */
    @Override
    public boolean isInterruptable() {
        return !this.isBreakingBlock;
    }

}
