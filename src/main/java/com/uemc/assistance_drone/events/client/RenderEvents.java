package com.uemc.assistance_drone.events.client;

import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.entities.ModEntities;
import com.uemc.assistance_drone.entities.client.DroneEntityModel;
import com.uemc.assistance_drone.entities.client.DroneEntityRenderer;
import com.uemc.assistance_drone.menus.ModMenus;
import com.uemc.assistance_drone.menus.client.DroneMenuScreen;
import com.uemc.assistance_drone.util.ModKeys;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;


@EventBusSubscriber(modid = AssistanceDrone.MODID, value = Dist.CLIENT)
public class RenderEvents {

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

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(AssistanceDrone.MODID, ModKeys.SITE_PLANNER_START_MARKER_MODEL_PATH)));

        event.register(ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(AssistanceDrone.MODID, ModKeys.SITE_PLANNER_END_MARKER_MODEL_PATH)));
    }
}
