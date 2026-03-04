package com.uemc.pov_drone.client;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.pov_drone.network.PovInputMessage;
import com.uemc.pov_drone.network.PovExitMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PovClientController {

    private static int controlledDroneId = -1;

    private static float droneYaw = 0.0F;
    private static float dronePitch = 0.0F;
    private static boolean droneRotationInitialized = false;

    private PovClientController() {
    }

    public static void start(int droneId) {
        controlledDroneId = droneId;
        droneRotationInitialized = false;
    }

    public static void stop() {
        controlledDroneId = -1;
        droneRotationInitialized = false;
    }

    public static boolean isActive() {
        return controlledDroneId >= 0;
    }

    public static int getControlledDroneId() {
        return controlledDroneId;
    }

    public static float getDroneYaw() {
        return droneYaw;
    }

    public static float getDronePitch() {
        return dronePitch;
    }

    /**
     * Called from MouseHandlerMixin instead of applying rotation to the player.
     * Sensitivity scaling already applied before calling this.
     */
    public static void applyMouseDelta(float deltaYaw, float deltaPitch) {
        droneYaw += (deltaYaw * 0.15F);
        dronePitch = Mth.clamp(dronePitch + (deltaPitch * 0.15F), -90.0F, 90.0F);
    }

    public static void sendInput(float strafe, float forward, float vertical) {
        PacketDistributor.sendToServer(
                new PovInputMessage(strafe, forward, vertical, droneYaw, dronePitch));
    }

    public static void requestExit() {
        PacketDistributor.sendToServer(new PovExitMessage());
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
            droneYaw = entity.getYRot();
            dronePitch = entity.getXRot();
            droneRotationInitialized = true;
        }

        entity.setYRot(droneYaw);
        entity.setXRot(dronePitch);

        mc.setCameraEntity(entity);
        return true;
    }
}