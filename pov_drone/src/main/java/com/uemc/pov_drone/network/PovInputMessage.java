package com.uemc.pov_drone.network;

import com.uemc.pov_drone.ModKeys;
import com.uemc.pov_drone.PovDrone;
import com.uemc.pov_drone.session.PovSessionManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PovInputMessage(float strafe, float forward, float vertical, float yaw, float pitch)
        implements CustomPacketPayload {

    public static final Type<PovInputMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PovDrone.MODID, ModKeys.INPUT_PACKET));

    public static final StreamCodec<ByteBuf, PovInputMessage> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, PovInputMessage::strafe,
                    ByteBufCodecs.FLOAT, PovInputMessage::forward,
                    ByteBufCodecs.FLOAT, PovInputMessage::vertical,
                    ByteBufCodecs.FLOAT, PovInputMessage::yaw,
                    ByteBufCodecs.FLOAT, PovInputMessage::pitch,
                    PovInputMessage::new
            );

    @Override
    public Type<PovInputMessage> type() {
        return TYPE;
    }

    public static void handle(PovInputMessage message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PovSessionManager.updateInput(player, message);
            }
        });
    }
}
