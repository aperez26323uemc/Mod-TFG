package com.uemc.assistance_drone.entities;

import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.entities.drone.DroneEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AssistanceDrone.MODID);

    public static final Supplier<EntityType<DroneEntity>> DRONE_ENTITY_TYPE = ENTITY_TYPES.register(
            DroneEntity.NAME,
            DroneEntity.ENTITY_TYPE_SUPPLIER
    );
}