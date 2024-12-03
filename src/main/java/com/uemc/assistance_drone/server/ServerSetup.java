package com.uemc.assistance_drone.server;

import com.mojang.logging.LogUtils;
import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.entities.ModEntities;
import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.entities.drone.Moves;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = AssistanceDrone.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.DEDICATED_SERVER)
public class ServerSetup {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
