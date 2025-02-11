package com.uemc.assistance_drone.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record DroneStateMessage(int droneId, String newState) implements CustomPacketPayload {

    // Identificador único para nuestro payload.
    // Ajusta el namespace y path a tu mod y ruta.
    public static final CustomPacketPayload.Type<DroneStateMessage> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("assistance_drone", "drone_state"));

    // Definimos el codec de lectura/escritura
    // El orden en que aparezcan coincide con los parámetros del record.
    public static final StreamCodec<FriendlyByteBuf, DroneStateMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,       // Codifica/decodifica droneId como entero variable
            DroneStateMessage::droneId,  // Getter para droneId
            ByteBufCodecs.STRING_UTF8,   // Codifica/decodifica newState como String
            DroneStateMessage::newState, // Getter para newState
            DroneStateMessage::new       // Constructor final que recibe (droneId, newState)
    );

    @Override
    public CustomPacketPayload.@NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
