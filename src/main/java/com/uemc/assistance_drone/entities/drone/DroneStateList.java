package com.uemc.assistance_drone.entities.drone;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;

/**
 * Clase para manejar el registro y validación de estados permitidos para DroneEntity.
 * Permite que los estados sean ampliables mediante mixins u otras extensiones.
 */
public class DroneStateList {
    // Conjunto de estados permitidos
    private final ArrayList<DroneState> allowedStates;
    private static final DroneStateList LIST = new DroneStateList();

    private DroneStateList() {
        allowedStates = new ArrayList<>();
    }

    /**
     * Registra un nuevo estado permitido para DroneEntity.
     *
     * @param state Nombre del nuevo estado.
     */
    public static void registerState(DroneState state) {
        if (state != null) {
            if (!LIST.contains(state.getStateName())) {
                LIST.add(state);
            }
        }
    }

    private void add(DroneState state) {
        allowedStates.add(state);
    }

    private boolean contains(String state) {
        for (DroneState allowedState : allowedStates) {
            if(allowedState.getStateName().equals(state)){
                return true;
            };
        }
        return false;
    }

    /**
     * Verifica si un estado está registrado y es válido.
     *
     * @param state Nombre del estado a verificar.
     * @return true si el estado está registrado, false de lo contrario.
     */
    public static boolean isValidState(String state) {
        return LIST.contains(state);
    }

    /**
     * Obtiene un conjunto inmutable de todos los estados permitidos.
     *
     * @return Conjunto de estados permitidos.
     */
    public static DroneStateList getAllowedStates() {
        return LIST;
    }

    public int size() {
        return allowedStates.size();
    }

    public DroneState get(int index) {
        return allowedStates.get(index);
    }
}
