package com.uemc.assistance_drone.networking;

import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.util.ModKeys;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-bound packet used to update the state of a drone entity.
 * <p>
 * Sent by the client when requesting a state change and validated
 * server-side to ensure ownership and permissions.
 */
public record DroneStateMessage(int droneId, String state)
        implements CustomPacketPayload {

    /** Unique packet identifier */
    public static final Type<DroneStateMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    AssistanceDrone.MODID,
                    ModKeys.STATE_NETWORK_MESSAGE_PATH
            ));

    /**
     * Codec for serializing the drone entity ID and target state.
     */
    public static final StreamCodec<ByteBuf, DroneStateMessage> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DroneStateMessage::droneId,
                    ByteBufCodecs.STRING_UTF8, DroneStateMessage::state,
                    DroneStateMessage::new
            );

    @Override
    public Type<DroneStateMessage> type() {
        return TYPE;
    }

    /**
     * Handles the packet on the server thread.
     * <p>
     * The state update is only applied if the sender is the drone owner
     * or has creative privileges.
     */
    public static void handle(
            final DroneStateMessage message,
            final IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            Entity entity = player.level().getEntity(message.droneId());
            if (!(entity instanceof DroneEntity drone)) {
                return;
            }

            if (player.isCreative()
                    || player.getUUID().equals(drone.getOwnerUUID())) {
                drone.setState(message.state());
            }
        });
    }
}
