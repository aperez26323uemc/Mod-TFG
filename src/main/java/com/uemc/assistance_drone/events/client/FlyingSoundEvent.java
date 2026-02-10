package com.uemc.assistance_drone.events.client;

import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.entities.drone.client.DroneFlyingSound;
import com.uemc.assistance_drone.entities.drone.DroneEntity;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = AssistanceDrone.MODID, value = Dist.CLIENT)
public class FlyingSoundEvent {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof DroneEntity drone && event.getLevel().isClientSide()) {
            Minecraft.getInstance().getSoundManager().play(new DroneFlyingSound(drone));
        }
    }
}
