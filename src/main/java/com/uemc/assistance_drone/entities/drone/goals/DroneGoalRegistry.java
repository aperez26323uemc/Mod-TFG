package com.uemc.assistance_drone.entities.drone.goals;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.util.ModKeys;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class DroneGoalRegistry {

    public record StateDefinition(
            String id,
            int priority,
            Function<DroneEntity, Goal> factory,
            // NUEVO: Predicado de validación (¿Puedo activar este estado?)
            Predicate<DroneEntity> requirement
    ) {
        // Constructor compacto para estados sin requisitos (Idle, Follow)
        public StateDefinition(String id, int priority, Function<DroneEntity, Goal> factory) {
            this(id, priority, factory, d -> true);
        }

        public Component getLabel() {
            return Component.translatable(ModKeys.getStateTitleKey(this.id));
        }

        public Component getTooltip() {
            return Component.translatable(ModKeys.getStateDescKey(this.id));
        }

        // Helper para chequear requisitos
        public boolean isAvailable(DroneEntity drone) {
            return requirement.test(drone);
        }
    }

    private static final Map<String, StateDefinition> REGISTRY = new LinkedHashMap<>();

    public static void register(String id, int priority, Function<DroneEntity, Goal> factory, Predicate<DroneEntity> requirement) {
        REGISTRY.put(id, new StateDefinition(id, priority, factory, requirement));
    }

    public static void register(String id, int priority, Function<DroneEntity, Goal> factory) {
        register(id, priority, factory, d -> true);
    }

    // ... getters (getDefinitions, get) iguales ...
    public static Collection<StateDefinition> getDefinitions() { return REGISTRY.values(); }
    public static StateDefinition get(String id) { return REGISTRY.get(id); }

    static {
        // IDLE & FOLLOW (Sin requisitos especiales)
        register(ModKeys.STATE_IDLE, 4, DroneIdleGoal::new);
        register(ModKeys.STATE_FOLLOW, 3, DroneFollowGoal::new);

        // PICKUP (Requiere Site Planner)
        register(ModKeys.STATE_PICKUP, 1,
                drone -> new DronePickupGoal(drone, s -> s.equals(ModKeys.STATE_PICKUP) || s.equals(ModKeys.STATE_MINE)),
                DroneEntity::hasSitePlanner
        );

        // MINE (Requiere Site Planner)
        // Definimos la regla AQUÍ. El "qué" y el "por qué" viven juntos.
        register(ModKeys.STATE_MINE, 2,
                drone -> new DroneMineGoal(drone, s -> s.equals(ModKeys.STATE_MINE)),
                DroneEntity::hasSitePlanner
        );
    }
}