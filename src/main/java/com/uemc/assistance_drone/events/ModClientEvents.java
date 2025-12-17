package com.uemc.assistance_drone.events;

import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.entities.ModEntities;
import com.uemc.assistance_drone.entities.client.DroneEntityModel;
import com.uemc.assistance_drone.entities.client.DroneEntityRenderer;
import com.uemc.assistance_drone.menus.ModMenus;
import com.uemc.assistance_drone.menus.client.DroneMenuScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;


// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = AssistanceDrone.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModClientEvents {

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
