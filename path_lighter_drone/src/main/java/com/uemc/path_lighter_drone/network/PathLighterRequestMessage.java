package com.uemc.path_lighter_drone.network;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.util.ModKeys;
import com.uemc.path_lighter_drone.PathLighterDrone;
import com.uemc.path_lighter_drone.planner.PathLighterPlanner;
import com.uemc.path_lighter_drone.planner.PathLighterTaskHolder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Comparator;
import java.util.List;

/**
 * Client request packet that dispatches one path-lighting task to a single follow drone.
 */
public record PathLighterRequestMessage() implements CustomPacketPayload {

    public static final Type<PathLighterRequestMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PathLighterDrone.MODID, com.uemc.path_lighter_drone.ModKeys.PATH_LIGHTER_PACKET));

    public static final StreamCodec<ByteBuf, PathLighterRequestMessage> STREAM_CODEC =
            StreamCodec.unit(new PathLighterRequestMessage());

    @Override
    public Type<PathLighterRequestMessage> type() {
        return TYPE;
    }

    public static void handle(PathLighterRequestMessage message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            List<DroneEntity> candidates = player.level().getEntitiesOfClass(DroneEntity.class,
                    player.getBoundingBox().inflate(96.0D),
                    drone -> player.getUUID().equals(drone.getOwnerUUID()) && ModKeys.STATE_FOLLOW.equals(drone.getState()));

            DroneEntity selected = candidates.stream()
                    .filter(drone -> drone instanceof PathLighterTaskHolder)
                    .min(Comparator
                            .comparingInt(drone -> ((PathLighterTaskHolder) drone).pathLighterDrone$pendingTasks())
                            .thenComparingDouble(player::distanceToSqr))
                    .orElse(null);

            if (selected == null) {
                return;
            }

            PathLighterPlanner.buildTask(player.level(), player, selected)
                    .ifPresent(task -> ((PathLighterTaskHolder) selected).pathLighterDrone$enqueueTask(task));
        });
    }
}
