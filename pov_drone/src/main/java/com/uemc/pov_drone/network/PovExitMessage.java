package com.uemc.pov_drone.network;

import com.uemc.pov_drone.ModKeys;
import com.uemc.pov_drone.PovDrone;
import com.uemc.pov_drone.session.PovSessionManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record PovExitMessage() implements CustomPacketPayload {

    public static final Type<PovExitMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PovDrone.MODID, ModKeys.EXIT_PACKET));

    public static final StreamCodec<ByteBuf, PovExitMessage> STREAM_CODEC =
            StreamCodec.unit(new PovExitMessage());

    @Override
    public @NotNull Type<PovExitMessage> type() {
        return TYPE;
    }

    public static void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // The client already validated the hit against the player body
                // (AABB.clip from the drone's POV), so we trust it here.
                PovSessionManager.stop(player, false);
            }
        });
    }
}