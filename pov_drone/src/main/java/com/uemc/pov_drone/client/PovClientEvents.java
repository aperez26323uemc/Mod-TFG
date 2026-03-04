package com.uemc.pov_drone.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.uemc.pov_drone.ModKeys;
import com.uemc.pov_drone.PovDrone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = PovDrone.MODID, value = Dist.CLIENT)
public final class PovClientEvents {

    private PovClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !PovClientController.isActive()) {
            return;
        }
        if (!PovClientController.tryBindCamera()) {
            return;
        }

        float forward = axis(mc.options.keyUp.isDown(), mc.options.keyDown.isDown());
        float strafe = axis(mc.options.keyLeft.isDown(), mc.options.keyRight.isDown());
        float vertical = axis(mc.options.keyJump.isDown(), mc.options.keyShift.isDown());
        PovClientController.sendInput(strafe, forward, vertical, mc.player.getYRot(), mc.player.getXRot());
    }

    private static float axis(boolean positive, boolean negative) {
        return positive == negative ? 0.0F : (positive ? 1.0F : -1.0F);
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!PovClientController.isActive() || mc.player == null || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit && hit.getEntity() == mc.player) {
            PovClientController.requestExit();
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void hideHud(RenderGuiLayerEvent.Pre event) {
        if (!PovClientController.isActive()) {
            return;
        }
        String name = event.getName().toString();
        if (!name.contains("debug")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void renderTether(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !PovClientController.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        Entity camera = mc.getCameraEntity();
        if (camera == null) {
            return;
        }

        Vec3 anchor = mc.player.position();
        double distanceToBoundary = ModKeys.TETHER_RADIUS_BLOCKS - Math.max(
                Math.max(Math.abs(camera.getX() - anchor.x), Math.abs(camera.getY() - anchor.y)),
                Math.abs(camera.getZ() - anchor.z)
        );
        if (distanceToBoundary > ModKeys.TETHER_WARNING_BLOCKS) {
            return;
        }

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        AABB box = new AABB(
                anchor.x - ModKeys.TETHER_RADIUS_BLOCKS,
                anchor.y - ModKeys.TETHER_RADIUS_BLOCKS,
                anchor.z - ModKeys.TETHER_RADIUS_BLOCKS,
                anchor.x + ModKeys.TETHER_RADIUS_BLOCKS,
                anchor.y + ModKeys.TETHER_RADIUS_BLOCKS,
                anchor.z + ModKeys.TETHER_RADIUS_BLOCKS
        ).move(-cam.x, -cam.y, -cam.z);

        RenderSystem.enableBlend();
        LevelRenderer.renderLineBox(event.getPoseStack(), mc.renderBuffers().bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.lines()), box, 1.0F, 0.2F, 0.2F, 0.8F);
        RenderSystem.disableBlend();
    }

    @SubscribeEvent
    public static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft mc = Minecraft.getInstance();
        PovClientController.stop();
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
    }
}
