package com.uemc.assistance_drone.entities.drone;

import com.uemc.assistance_drone.entities.drone.goals.*;
import com.uemc.assistance_drone.util.ModKeys;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.*;
import java.util.function.Function;

public class DroneGoalRegistry {
    // RECORD: Contenedor de datos inmutable. Define qué es un "Estado".
    public record StateDefinition(
            String id,
            int priority, // Prioridad en la IA (menor número = mayor prioridad)
            Function<DroneEntity, Goal> factory // Cómo fabricar el goal
    ) {
        // Helper para obtener la etiqueta de traducción estandarizada
        public Component getLabel() {
            return Component.translatable(ModKeys.getStateTitleKey(this.id));
        }

        public Component getTooltip() {
            return Component.translatable(ModKeys.getStateDescKey(this.id));
        }
    }

    // Mapa ordenado para mantener consistencia, aunque ordenaremos por prioridad al registrar
    private static final Map<String, StateDefinition> REGISTRY = new LinkedHashMap<>();

    public static void register(String id, int priority, Function<DroneEntity, Goal> factory) {
        REGISTRY.put(id, new StateDefinition(id, priority, factory));
    }

    public static Collection<StateDefinition> getDefinitions() {
        return REGISTRY.values();
    }

    public static StateDefinition get(String id) {
        return REGISTRY.get(id);
    }

    static {
        // IDLE: Prioridad 4 (Baja/Default)
        register(ModKeys.STATE_IDLE, 4, DroneIdleGoal::new);

        // FOLLOW: Prioridad 3 (Media)
        register(ModKeys.STATE_FOLLOW, 3, DroneFollowGoal::new);

        // MINE: Prioridad 2 (Alta)
        register(ModKeys.STATE_MINE, 2, drone ->
                new DroneMineGoal(drone, s -> s.equals(ModKeys.STATE_MINE)));

        // PICKUP: Prioridad 1 (Muy Alta)
        // Lógica: Se activa si estado es PICKUP o MINE
        register(ModKeys.STATE_PICKUP, 1, drone ->
                new DronePickupGoal(drone, s -> s.equals(ModKeys.STATE_PICKUP) || s.equals(ModKeys.STATE_MINE)));
    }
}