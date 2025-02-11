package com.uemc.assistance_drone.network;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public class ModPayloads {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        // Establecemos la versión de nuestro protocolo: "1".
        PayloadRegistrar registrar = event.registrar("1");

        // Si solo quieres un handler en el servidor, podrías usar:
        registrar.playToServer(
            DroneStateMessage.TYPE,
            DroneStateMessage.STREAM_CODEC,
            ServerPayloadHandler::handleDroneStateOnMain
        );
    }

    // Handler en el servidor
    public static class ServerPayloadHandler {
        /**
         * Llega un DroneStateMessage enviado desde el cliente.
         * Se llama en el main thread por defecto (puedes configurarlo en el registrar).
         */
        public static void handleDroneStateOnMain(final DroneStateMessage data, final IPayloadContext context) {
            // Validamos que el que manda sea un jugador
            if (!(context.player() instanceof ServerPlayer player)) return;

            ServerLevel level = player.serverLevel();
            Entity entity = level.getEntity(data.droneId());

            // Verifica que la entidad exista y sea un DroneEntity
            if (entity instanceof DroneEntity drone) {
                // AQUI actualizamos el estado del dron a nivel de servidor
                drone.setState(data.newState());
            }
        }
    }
}