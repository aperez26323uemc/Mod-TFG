package com.uemc.assistance_drone.events.client;

import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.items.ModItems;
import com.uemc.assistance_drone.items.SitePlanner;
import com.uemc.assistance_drone.networking.SitePlannerActionMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Detects scroll input on configured SitePlanner and sends manipulation commands to server.
 */
@EventBusSubscriber(modid = AssistanceDrone.MODID, value = Dist.CLIENT)
public class SitePlannerScrollHandler {

    private static final double MIN_SCROLL_THRESHOLD = 0.01;

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null || mc.level == null) return;

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.is(ModItems.SITE_PLANNER.get())) return;
        if (!SitePlanner.isConfigured(mainHand)) return;

        double scrollDelta = event.getScrollDeltaY();
        if (Math.abs(scrollDelta) < MIN_SCROLL_THRESHOLD) return;

        int scrollAmount = scrollDelta > 0 ? 1 : -1;

        long window = mc.getWindow().getWindow();
        boolean shiftPressed = isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT) ||
                isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean ctrlPressed = isKeyPressed(window, GLFW.GLFW_KEY_LEFT_CONTROL) ||
                isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_CONTROL);

        if (!shiftPressed) return;

        Direction direction = SitePlanner.getPlayerLookDirection(player);
        SitePlannerActionMessage.Action action = ctrlPressed
                ? SitePlannerActionMessage.Action.MOVE
                : SitePlannerActionMessage.Action.RESIZE;

        PacketDistributor.sendToServer(new SitePlannerActionMessage(
                action,
                direction,
                scrollAmount
        ));

        event.setCanceled(true);
    }

    private static boolean isKeyPressed(long window, int key) {
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }
}