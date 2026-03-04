package com.uemc.pov_drone.client;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.pov_drone.ModKeys;
import com.uemc.pov_drone.network.PovInputMessage;
import com.uemc.pov_drone.network.PovExitMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PovClientController {

    private static int controlledDroneId = -1;
    private static int introTicks;

    private PovClientController() {
    }

    public static void start(int droneId, int messageTicks) {
        controlledDroneId = droneId;
        introTicks = messageTicks;
    }

    public static void stop() {
        controlledDroneId = -1;
        introTicks = 0;
    }

    public static boolean isActive() {
        return controlledDroneId >= 0;
    }

    public static int getControlledDroneId() {
        return controlledDroneId;
    }

    public static void sendInput(float strafe, float forward, float vertical, float yaw, float pitch) {
        PacketDistributor.sendToServer(new PovInputMessage(strafe, forward, vertical, yaw, pitch));
    }

    public static void requestExit() {
        PacketDistributor.sendToServer(new PovExitMessage());
    }

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
        mc.setCameraEntity(entity);
        return true;
    }
}
