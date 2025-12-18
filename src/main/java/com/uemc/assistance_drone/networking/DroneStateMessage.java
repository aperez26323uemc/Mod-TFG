package com.uemc.assistance_drone.networking;

import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.entities.drone.DroneEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// 1. Usamos 'record' para definir los datos automáticamente
public record DroneStateMessage(int droneId, String state) implements CustomPacketPayload {

    // 2. Definimos el TIPO (Identificador único del paquete)
    public static final Type<DroneStateMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AssistanceDrone.MODID, "drone_state"));

    // 3. El StreamCodec (Sustituye a encode/decode manuales).
    // NeoForge serializa automáticamente el int (ID) y el String (Estado).
    public static final StreamCodec<ByteBuf, DroneStateMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DroneStateMessage::droneId,
            ByteBufCodecs.STRING_UTF8, DroneStateMessage::state,
            DroneStateMessage::new
    );

    @Override
    public Type<DroneStateMessage> type() {
        return TYPE;
    }

    // 4. La lógica de manejo (Qué pasa cuando llega al servidor)
    public static void handle(final DroneStateMessage message, final IPayloadContext context) {
        // Encolamos la tarea en el hilo principal del servidor
        context.enqueueWork(() -> {
            // Como enviamos al servidor, el sender es el jugador
            if (context.player() instanceof ServerPlayer player) {
                Entity entity = player.level().getEntity(message.droneId());

                // Verificamos que sea nuestro dron y que el jugador tenga permiso (distancia, dueño, etc.)
                if (entity instanceof DroneEntity drone) {
                    // Seguridad extra: ¿Es el dueño? (Opcional, pero recomendado)
                    if (player.getUUID().equals(drone.getOwnerUUID()) || player.isCreative()) {
                        drone.setState(message.state());
                    }
                }
            }
        });
    }
}