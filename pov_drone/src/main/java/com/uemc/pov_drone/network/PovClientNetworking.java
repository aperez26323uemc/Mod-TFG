package com.uemc.pov_drone.network;

import com.uemc.pov_drone.PovDrone;
import com.uemc.pov_drone.client.PovClientController;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = PovDrone.MODID, value = Dist.CLIENT)
public final class PovClientNetworking {

    private PovClientNetworking() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        PovNetworking.sessionHandler = PovClientNetworking::handleSession;
    }

    public static void handleSession(PovSessionMessage message, IPayloadContext context) {
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