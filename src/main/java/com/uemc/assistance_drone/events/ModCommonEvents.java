package com.uemc.assistance_drone.events;

import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.entities.ModEntities;
import com.uemc.assistance_drone.entities.drone.DroneEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

@EventBusSubscriber(modid = AssistanceDrone.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModCommonEvents {

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.DRONE_ENTITY_TYPE.get(), DroneEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerEntity(
                Capabilities.ItemHandler.ENTITY,
                ModEntities.DRONE_ENTITY_TYPE.get(),
                (drone, context) -> drone.getInventory()
        );
    }
}