package com.uemc.pov_drone.network;

import com.uemc.pov_drone.ModKeys;
import com.uemc.pov_drone.PovDrone;
import com.uemc.pov_drone.client.PovClientController;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PovSessionMessage(boolean active, int droneId, int introTicks) implements CustomPacketPayload {

    public static final Type<PovSessionMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PovDrone.MODID, ModKeys.SESSION_PACKET));

    public static final StreamCodec<ByteBuf, PovSessionMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PovSessionMessage::active,
            ByteBufCodecs.VAR_INT, PovSessionMessage::droneId,
            ByteBufCodecs.VAR_INT, PovSessionMessage::introTicks,
            PovSessionMessage::new
    );

    @Override
    public Type<PovSessionMessage> type() {
        return TYPE;
    }

    public static void handle(PovSessionMessage message, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (message.active()) {
                PovClientController.start(message.droneId(), message.introTicks());
            } else {
                PovClientController.stop();
                if (mc.player != null) {
                    mc.setCameraEntity(mc.player);
                }
            }
        });
    }
}
