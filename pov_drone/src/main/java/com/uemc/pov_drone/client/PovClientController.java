package com.uemc.pov_drone.client;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.pov_drone.ModKeys;
import com.uemc.pov_drone.network.PovExitMessage;
import com.uemc.pov_drone.network.PovInputMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

public final class PovClientController {

    private static int controlledDroneId = -1;

    private static float droneYaw   = 0.0F;
    private static float dronePitch = 0.0F;
    private static boolean droneRotationInitialized = false;

    private static int introTicks = 0;

    private PovClientController() {
    }

    // -------------------------------------------------------------------------
    // Session lifecycle
    // -------------------------------------------------------------------------

    /** Called when the server confirms a session has started. */
    public static void start(int droneId, int introTicksFromServer) {
        controlledDroneId        = droneId;
        introTicks               = introTicksFromServer;
        droneRotationInitialized = false;
        Minecraft.getInstance().options.keyAttack.setDown(false);
    }

    public static void stop() {
        controlledDroneId        = -1;
        introTicks               = 0;
        droneRotationInitialized = false;
    }

    public static boolean isActive() {
        return controlledDroneId >= 0;
    }

    // -------------------------------------------------------------------------
    // Intro hint
    // -------------------------------------------------------------------------

    public static int getIntroTicks() {
        return introTicks;
    }

    public static void decrementIntroTicks() {
        if (introTicks > 0) {
            introTicks--;
        }
    }

    // -------------------------------------------------------------------------
    // Camera / rotation
    // -------------------------------------------------------------------------

    /**
     * Called from MouseHandlerMixin instead of applying rotation to the player.
     * Sensitivity scaling is already applied by vanilla before this is called.
     */
    public static void applyMouseDelta(float deltaYaw, float deltaPitch) {
        droneYaw += deltaYaw   * 0.15F;
        dronePitch = Mth.clamp(dronePitch + deltaPitch * 0.15F, -90.0F, 90.0F);
    }

    // -------------------------------------------------------------------------
    // Network
    // -------------------------------------------------------------------------

    public static void sendInput(float strafe, float forward, float vertical) {
        PacketDistributor.sendToServer(
                new PovInputMessage(strafe, forward, vertical, droneYaw, dronePitch));
    }

    public static void requestExit() {
        PacketDistributor.sendToServer(new PovExitMessage());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Raycasts from the drone's eye toward the locally-tracked look direction.
     * Sends an exit packet if the player's body AABB is hit.
     * No-op when the drone entity is absent from the client level.
     */
    public static void tryExitByRaycast() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Entity drone = mc.level.getEntity(controlledDroneId);
        if (drone == null) {
            return;
        }
        Vec3 eyePos = drone.getEyePosition();
        Vec3 look   = Vec3.directionFromRotation(dronePitch, droneYaw);
        Vec3 endPos = eyePos.add(look.scale(mc.player.entityInteractionRange()));
        AABB playerBox = mc.player.getBoundingBox().inflate(ModKeys.PLAYER_AABB_INFLATE);
        Optional<Vec3> hit = playerBox.clip(eyePos, endPos);
        if (hit.isPresent()) {
            requestExit();
        }
    }

    /**
     * Tries to bind the camera to the controlled drone.
     * On first call initialises droneYaw/dronePitch from the entity so the
     * view does not snap.
     *
     * @return true if the drone is still valid and the camera was bound.
     */
    public static boolean tryBindCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return false;
        }
        Entity entity = mc.level.getEntity(controlledDroneId);
        if (!(entity instanceof DroneEntity)) {
            stop();
            mc.setCameraEntity(mc.player);
            return false;
        }

        if (!droneRotationInitialized) {
            droneYaw   = entity.getYRot();
            dronePitch = entity.getXRot();
            droneRotationInitialized = true;
        }

        entity.yRotO = droneYaw;
        entity.xRotO = dronePitch;
        entity.setYRot(droneYaw);
        entity.setXRot(dronePitch);
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = droneYaw;
        }

        mc.setCameraEntity(entity);
        return true;
    }
}