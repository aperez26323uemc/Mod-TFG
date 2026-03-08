package com.uemc.pov_drone.network;

import com.uemc.pov_drone.PovDrone;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.function.BiConsumer;

@EventBusSubscriber(modid = PovDrone.MODID)
public final class PovNetworking {

    private PovNetworking() {}

    // Replaced by PovClientNetworking during FMLClientSetupEvent.
    // The no-op default is fine: the server never receives playToClient packets.
    static volatile BiConsumer<PovSessionMessage, IPayloadContext> sessionHandler = (msg, ctx) -> {};

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(PovDrone.MODID);
        registrar.playToServer(PovInputMessage.TYPE,   PovInputMessage.STREAM_CODEC,   PovInputMessage::handle);
        registrar.playToServer(PovExitMessage.TYPE,    PovExitMessage.STREAM_CODEC,    (msg, ctx) -> PovExitMessage.handle(ctx));
        registrar.playToClient(PovSessionMessage.TYPE, PovSessionMessage.STREAM_CODEC, (msg, ctx) -> sessionHandler.accept(msg, ctx));
    }
}