package com.uemc.assistance_drone.entities.drone;

import com.uemc.assistance_drone.entities.drone.goals.DroneFollowGoal;
import com.uemc.assistance_drone.entities.drone.goals.DroneIdleGoal;
import com.uemc.assistance_drone.entities.drone.goals.IStateGoal;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class DroneGoalRegistry {
    // Mapea ID -> Constructor del Goal
    // Usamos LinkedHashMap para mantener el orden de los botones en la UI
    private static final Map<String, Function<DroneEntity, ? extends Goal>> GOALS = new LinkedHashMap<>();

    // Registro estático inicial
    static {
        register(DroneStateIds.IDLE, DroneIdleGoal::new);
        register(DroneStateIds.FOLLOW, DroneFollowGoal::new);
    }

    /**
     * Registra un nuevo estado/goal.
     * ¡Ideal para llamar desde un Mixin o desde otro mod!
     */
    public static void register(String id, Function<DroneEntity, ? extends Goal> factory) {
        GOALS.put(id, factory);
    }

    public static Collection<Function<DroneEntity, ? extends Goal>> getFactories() {
        return GOALS.values();
    }

    // Helper para obtener una instancia "dummy" o real para sacar datos de UI
    // Nota: Creamos una instancia temporal para leer el ID/Label si es necesario,
    // o simplemente confiamos en que el ID del mapa coincide.
    public static Map<String, Function<DroneEntity, ? extends Goal>> getEntries() {
        return GOALS;
    }
}