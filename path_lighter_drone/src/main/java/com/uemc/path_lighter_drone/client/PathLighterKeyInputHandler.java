package com.uemc.path_lighter_drone.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.uemc.path_lighter_drone.ModKeys;
import com.uemc.path_lighter_drone.PathLighterDrone;
import com.uemc.path_lighter_drone.network.PathLighterRequestMessage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = PathLighterDrone.MODID, value = Dist.CLIENT)
public final class PathLighterKeyInputHandler {

    private static long lastReleaseMs;

    private PathLighterKeyInputHandler() {
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }

        if (event.getAction() != GLFW.GLFW_RELEASE) {
            return;
        }

        InputConstants.Key releasedKey = InputConstants.getKey(event.getKey(), event.getScanCode());
        if (!PathLighterKeyMappings.PATH_LIGHTER_KEY.isActiveAndMatches(releasedKey)) {
            return;
        }

        long now = Util.getMillis();
        if (now - lastReleaseMs < ModKeys.KEY_RELEASE_COOLDOWN_MS) {
            return;
        }

        lastReleaseMs = now;
        PacketDistributor.sendToServer(new PathLighterRequestMessage());
    }
}
