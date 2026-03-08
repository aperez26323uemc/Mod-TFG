package com.uemc.assistance_drone.events.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.items.ModItems;
import com.uemc.assistance_drone.items.SitePlanner;
import com.uemc.assistance_drone.util.ModKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * Renders control hints when a configured SitePlanner is held.
 */
@EventBusSubscriber(modid = AssistanceDrone.MODID, value = Dist.CLIENT)
public class SitePlannerHudOverlay {

    private static final int HUD_X = 10;
    private static final int HUD_Y = 100;
    private static final int LINE_HEIGHT = 12;
    private static final int TITLE_SPACING = 3;

    private static final int COLOR_TITLE = 0xFFD700;
    private static final int COLOR_VALUE = 0xFFFFFF;

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiLayerEvent.Post event) {
        if (!event.getName().toString().equals("minecraft:hotbar")) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null) return;

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.is(ModItems.SITE_PLANNER.get())) return;
        if (!SitePlanner.isConfigured(mainHand)) return;

        renderControlHints(event.getGuiGraphics(), mc);
    }

    private static void renderControlHints(GuiGraphics guiGraphics, Minecraft mc) {
        RenderSystem.enableBlend();

        int y = HUD_Y;

        guiGraphics.drawString(
                mc.font,
                Component.translatable(ModKeys.HUD_SITE_PLANNER_TITLE),
                HUD_X,
                y,
                COLOR_TITLE,
                true
        );
        y += LINE_HEIGHT + TITLE_SPACING;

        guiGraphics.drawString(
                mc.font,
                Component.translatable(ModKeys.HUD_SITE_PLANNER_RESIZE),
                HUD_X,
                y,
                COLOR_VALUE,
                true
        );
        y += LINE_HEIGHT;

        guiGraphics.drawString(
                mc.font,
                Component.translatable(ModKeys.HUD_SITE_PLANNER_MOVE),
                HUD_X,
                y,
                COLOR_VALUE,
                true
        );

        RenderSystem.disableBlend();
    }
}