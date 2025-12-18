package com.uemc.assistance_drone.networking;

import com.uemc.assistance_drone.AssistanceDrone;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = AssistanceDrone.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModMessages {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
                DroneStateMessage.TYPE,
                DroneStateMessage.STREAM_CODEC,
                DroneStateMessage::handle
        );
    }
}