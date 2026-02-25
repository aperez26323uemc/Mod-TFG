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
            Predicate<DroneEntity> requirement
    ) {
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
    private static final Set<String> PICKUP_ACTIVE_STATES = new HashSet<>(
            Set.of(ModKeys.STATE_PICKUP, ModKeys.STATE_MINE)
    );

    public static void register(String id, int priority, Function<DroneEntity, Goal> factory, Predicate<DroneEntity> requirement) {
        REGISTRY.put(id, new StateDefinition(id, priority, factory, requirement));
    }

    public static void register(String id, int priority, Function<DroneEntity, Goal> factory) {
        register(id, priority, factory, d -> true);
    }

    public static Collection<StateDefinition> getDefinitions() { return REGISTRY.values(); }
    public static StateDefinition get(String id) { return REGISTRY.get(id); }

    /**
     * Allows addons to declare that their custom state should also
     * activate the built-in pickup goal.
     */
    public static void addPickupGoal(String stateId) {
        PICKUP_ACTIVE_STATES.add(stateId);
    }

    static {
        // IDLE & FOLLOW (Sin requisitos especiales)
        register(ModKeys.STATE_IDLE, 4, DroneIdleGoal::new);
        register(ModKeys.STATE_FOLLOW, 4, DroneFollowGoal::new);

        // PICKUP (Requiere Site Planner)
        register(ModKeys.STATE_PICKUP, 2,
                drone -> new DronePickupGoal(drone, PICKUP_ACTIVE_STATES::contains),
                DroneEntity::hasSitePlanner
        );

        // MINE (Requiere Site Planner)
        register(ModKeys.STATE_MINE, 3,
                drone -> new DroneMineGoal(drone, s -> s.equals(ModKeys.STATE_MINE)),
                DroneEntity::hasSitePlanner
        );
    }
}