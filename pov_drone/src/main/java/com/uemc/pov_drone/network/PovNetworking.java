package com.uemc.pov_drone.network;

import com.uemc.pov_drone.PovDrone;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = PovDrone.MODID)
public final class PovNetworking {

    private PovNetworking() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(PovDrone.MODID);
        registrar.playToServer(PovInputMessage.TYPE, PovInputMessage.STREAM_CODEC, PovInputMessage::handle);
        registrar.playToServer(PovExitMessage.TYPE, PovExitMessage.STREAM_CODEC, (message, context) -> PovExitMessage.handle(context));
        registrar.playToClient(PovSessionMessage.TYPE, PovSessionMessage.STREAM_CODEC, PovSessionMessage::handle);
    }
}
