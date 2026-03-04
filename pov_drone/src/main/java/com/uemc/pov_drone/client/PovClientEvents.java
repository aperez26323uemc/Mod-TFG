package com.uemc.pov_drone.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.uemc.pov_drone.ModKeys;
import com.uemc.pov_drone.PovDrone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
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
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = PovDrone.MODID, value = Dist.CLIENT)
public final class PovClientEvents {

    private static final RenderType XRAY_LINES_RENDER_TYPE = RenderType.create(
            "pov_tether_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(java.util.OptionalDouble.of(2.0D)))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false)
    );

    private static final RenderType XRAY_FILL_RENDER_TYPE = RenderType.create(
            "pov_tether_fill",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false)
    );

    private PovClientEvents() {
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !PovClientController.isActive()) {
            return;
        }
        mc.options.keyAttack.consumeClick();

        if (!PovClientController.tryBindCamera()) {
            return;
        }

        // Intro hint — displayed here so we can embed the real keybind name.
        // The server intentionally does NOT display this; it has no access to
        // client key bindings (fix for dedicated-server crash).
        if (PovClientController.getIntroTicks() > 0) {
            mc.player.displayClientMessage(
                    Component.translatable(
                            ModKeys.ACTIONBAR_INTRO,
                            mc.options.keyAttack.getTranslatedKeyMessage()),
                    true);
            PovClientController.decrementIntroTicks();
        }

        float forward  = axis(mc.options.keyUp.isDown(),    mc.options.keyDown.isDown());
        float strafe   = axis(mc.options.keyLeft.isDown(),  mc.options.keyRight.isDown());
        float vertical = axis(mc.options.keyJump.isDown(),  mc.options.keyShift.isDown());

        PovClientController.sendInput(strafe, forward, vertical);
    }

    // -------------------------------------------------------------------------
    // Input firewall — raw events only cancel; NO game logic here.
    // -------------------------------------------------------------------------

    /**
     * Firewall for raw mouse-button events.
     * Cancels every button press/release while in POV mode so that vanilla
     * never sees them and cannot trigger block-break or item-use actions.
     */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!PovClientController.isActive() || mc.player == null || mc.screen != null) {
            return;
        }
        if (mc.options.keyAttack.matchesMouse(event.getButton())) {
            return;
        }

        event.setCanceled(true);
    }

    private static float axis(boolean positive, boolean negative) {
        return positive == negative ? 0.0F : (positive ? 1.0F : -1.0F);
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!PovClientController.isActive()) {
            return;
        }
        if (event.isAttack()) {
            PovClientController.tryExitByRaycast();
        }
        // Cancel all interaction mappings (attack, use, pick-block) so the
        // player body cannot interact with the world while piloting the drone.
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (PovClientController.isActive()) {
            event.setCanceled(true);
        }
    }

    // -------------------------------------------------------------------------
    // HUD / rendering
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void hideHud(RenderGuiLayerEvent.Pre event) {
        if (!PovClientController.isActive()) {
            return;
        }
        String name = event.getName().toString();
        if (!name.contains("debug")
                && !name.contains("title")
                && !name.contains("crosshair")
                && !name.contains("sleep_overlay")
                && !name.contains("demo_overlay")
                && !name.contains("overlay_message")
                && !name.contains("chat")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void hideHand(RenderHandEvent event) {
        if (PovClientController.isActive()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void renderTether(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || !PovClientController.isActive()) {
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
                anchor.x - ModKeys.TETHER_RADIUS_BLOCKS - 1,
                anchor.y - ModKeys.TETHER_RADIUS_BLOCKS - 1,
                anchor.z - ModKeys.TETHER_RADIUS_BLOCKS - 1,
                anchor.x + ModKeys.TETHER_RADIUS_BLOCKS + 1,
                anchor.y + ModKeys.TETHER_RADIUS_BLOCKS + 1,
                anchor.z + ModKeys.TETHER_RADIUS_BLOCKS + 1
        ).move(-cam.x, -cam.y, -cam.z);

        RenderSystem.enableBlend();

        PoseStack poseStack = event.getPoseStack();
        VertexConsumer fillBuffer = mc.renderBuffers().bufferSource().getBuffer(XRAY_FILL_RENDER_TYPE);
        renderFilledBox(poseStack, fillBuffer, box);

        VertexConsumer lineBuffer = mc.renderBuffers().bufferSource().getBuffer(XRAY_LINES_RENDER_TYPE);
        LevelRenderer.renderLineBox(poseStack, lineBuffer, box, 1.0F, 0.2F, 0.2F, 0.7F);

        RenderSystem.disableBlend();
    }

    private static void renderFilledBox(PoseStack poseStack, VertexConsumer buffer, AABB box) {
        float r = 1.0F;
        float g = 0.2F;
        float b = 0.2F;
        float a = 0.08F;
        Matrix4f matrix = poseStack.last().pose();
        float minX = (float) box.minX, minY = (float) box.minY, minZ = (float) box.minZ;
        float maxX = (float) box.maxX, maxY = (float) box.maxY, maxZ = (float) box.maxZ;
        addQuad(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, r, g, b, a);
        addQuad(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        addQuad(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, r, g, b, a);
        addQuad(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        addQuad(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        addQuad(buffer, matrix, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ, r, g, b, a);
    }

    private static void addQuad(VertexConsumer buffer, Matrix4f matrix,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float x4, float y4, float z4,
                                float r, float g, float b, float a) {
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a);
        buffer.addVertex(matrix, x4, y4, z4).setColor(r, g, b, a);
    }

    // -------------------------------------------------------------------------
    // Disconnect
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft mc = Minecraft.getInstance();
        PovClientController.stop();
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
    }
}