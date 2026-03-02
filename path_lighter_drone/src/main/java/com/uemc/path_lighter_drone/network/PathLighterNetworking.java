package com.uemc.path_lighter_drone.network;

import com.uemc.path_lighter_drone.PathLighterDrone;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = PathLighterDrone.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class PathLighterNetworking {

    private PathLighterNetworking() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PathLighterDrone.MODID).playToServer(
                PathLighterRequestMessage.TYPE,
                PathLighterRequestMessage.STREAM_CODEC,
                PathLighterRequestMessage::handle
        );
    }
}
