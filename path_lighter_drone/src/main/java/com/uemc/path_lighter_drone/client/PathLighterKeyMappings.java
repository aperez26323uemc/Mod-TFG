package com.uemc.path_lighter_drone.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.uemc.path_lighter_drone.ModKeys;
import com.uemc.path_lighter_drone.PathLighterDrone;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = PathLighterDrone.MODID, value = Dist.CLIENT)
public final class PathLighterKeyMappings {

    private PathLighterKeyMappings() {
    }

    public static final KeyMapping PATH_LIGHTER_KEY = new KeyMapping(
            ModKeys.KEY_PATH_LIGHTER,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_CONTROL,
            ModKeys.KEY_CATEGORY
    );

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(PATH_LIGHTER_KEY);
    }
}
