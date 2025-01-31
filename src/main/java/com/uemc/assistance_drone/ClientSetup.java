package com.uemc.assistance_drone;

import com.mojang.logging.LogUtils;
import com.uemc.assistance_drone.entities.client.DroneEntityModel;
import com.uemc.assistance_drone.menus.client.DroneMenuScreen;
import com.uemc.assistance_drone.entities.ModEntities;
import com.uemc.assistance_drone.entities.client.DroneEntityRenderer;
import com.uemc.assistance_drone.menus.ModMenus;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.slf4j.Logger;


// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = AssistanceDrone.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        LOGGER.info("HELLO FROM CLIENT SETUP");
        LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DroneEntityModel.LAYER_LOCATION, DroneEntityModel::createBodyLayer);
    }
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DRONE_ENTITY_TYPE.get(), DroneEntityRenderer::new);
    }
    @SubscribeEvent
    public static void onRegisterMenuScreensEvent(RegisterMenuScreensEvent event) {
        event.register(ModMenus.DRONE_MENU.get(), DroneMenuScreen::new);
    }
}
