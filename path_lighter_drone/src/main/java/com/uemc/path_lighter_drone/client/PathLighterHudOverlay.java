package com.uemc.path_lighter_drone.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.util.ModKeys;
import com.uemc.path_lighter_drone.PathLighterDrone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

@EventBusSubscriber(modid = PathLighterDrone.MODID, value = Dist.CLIENT)
public final class PathLighterHudOverlay {

    private static final int HUD_X = 10;
    private static final int HUD_Y = 140;
    private static final int COLOR_VALUE = 0xFFFFFF;

    private PathLighterHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiLayerEvent.Post event) {
        if (!event.getName().toString().equals("minecraft:hotbar")) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.options.keyShift.isDown()) {
            return;
        }

        boolean hasFollowDrone = !mc.level.getEntitiesOfClass(DroneEntity.class,
                        mc.player.getBoundingBox().inflate(96.0D),
                        drone -> mc.player.getUUID().equals(drone.getOwnerUUID()) && ModKeys.STATE_FOLLOW.equals(drone.getState()))
                .isEmpty();
        if (!hasFollowDrone) {
            return;
        }

        String keyName = PathLighterKeyMappings.PATH_LIGHTER_KEY.getTranslatedKeyMessage().getString();
        Component line = Component.translatable(com.uemc.path_lighter_drone.ModKeys.HUD_PATH_LIGHTER_HINT, keyName);

        RenderSystem.enableBlend();
        GuiGraphics gui = event.getGuiGraphics();
        gui.drawString(mc.font, line, HUD_X, HUD_Y, COLOR_VALUE, true);
        RenderSystem.disableBlend();
    }
}
