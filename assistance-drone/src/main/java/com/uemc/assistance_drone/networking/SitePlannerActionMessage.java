package com.uemc.assistance_drone.networking;

import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.items.ModItems;
import com.uemc.assistance_drone.items.SitePlanner;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server packet for SitePlanner manipulation actions.
 */
public record SitePlannerActionMessage(Action action, Direction direction, int amount)
        implements CustomPacketPayload {

    public static final Type<SitePlannerActionMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    AssistanceDrone.MODID,
                    "site_planner_action"
            ));

    public static final StreamCodec<ByteBuf, SitePlannerActionMessage> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.idMapper(id -> Action.values()[id], Action::ordinal), SitePlannerActionMessage::action,
                    Direction.STREAM_CODEC, SitePlannerActionMessage::direction,
                    ByteBufCodecs.VAR_INT, SitePlannerActionMessage::amount,
                    SitePlannerActionMessage::new
            );

    @Override
    public Type<SitePlannerActionMessage> type() {
        return TYPE;
    }

    public static void handle(
            final SitePlannerActionMessage message,
            final IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            ItemStack mainHand = player.getMainHandItem();
            if (!mainHand.is(ModItems.SITE_PLANNER.get())) {
                return;
            }

            if (!SitePlanner.isConfigured(mainHand)) {
                return;
            }

            switch (message.action) {
                case RESIZE -> SitePlanner.resizeSelection(
                        mainHand,
                        player,
                        message.direction,
                        message.amount
                );
                case MOVE -> SitePlanner.moveSelection(
                        mainHand,
                        player,
                        message.direction,
                        message.amount
                );
            }
        });
    }

    public enum Action {
        RESIZE,
        MOVE
    }
}