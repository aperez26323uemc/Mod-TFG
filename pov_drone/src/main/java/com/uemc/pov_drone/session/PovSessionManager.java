package com.uemc.pov_drone.session;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.pov_drone.ModKeys;
import com.uemc.pov_drone.network.PovInputMessage;
import com.uemc.pov_drone.network.PovSessionMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PovSessionManager {

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private PovSessionManager() {
    }

    public static boolean hasSession(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    public static void tryStart(ServerPlayer player, DroneEntity drone) {
        if (hasSession(player) || !player.getUUID().equals(drone.getOwnerUUID())) {
            return;
        }
        Session session = new Session(
                drone.getId(),
                player.position(),
                player.getYRot(),
                player.getXRot(),
                player.getYHeadRot()
        );
        SESSIONS.put(player.getUUID(), session);
        drone.setNoAi(true);
        PacketDistributor.sendToPlayer(player,
                new PovSessionMessage(true, drone.getId(), ModKeys.INTRO_MESSAGE_TICKS));
    }

    public static void updateInput(ServerPlayer player, PovInputMessage input) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) {
            session.input = input;
        }
    }

    public static void tick(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }

        Entity entity = player.level().getEntity(session.droneId);
        if (!(entity instanceof DroneEntity drone) || !drone.isAlive()) {
            stop(player, false);
            return;
        }

        // Keep the player body frozen in place.
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.setYRot(session.bodyYaw);
        player.setXRot(session.bodyPitch);
        player.setYHeadRot(session.bodyHeadYaw);
        player.setYBodyRot(session.bodyYaw);

        // Compute drone velocity from the latest client input.
        PovInputMessage input = session.input;
        Vec3 look    = Vec3.directionFromRotation(input.pitch(), input.yaw());
        Vec3 forward = new Vec3(look.x, 0.0D, look.z).normalize();
        if (forward.lengthSqr() < 1.0E-4D) {
            forward = Vec3.ZERO;
        }
        Vec3 left = new Vec3(forward.z, 0.0D, -forward.x);

        Vec3 velocity = forward.scale(input.forward())
                .add(left.scale(input.strafe()))
                .add(0.0D, input.vertical(), 0.0D)
                .normalize()
                .scale(ModKeys.DRONE_SPEED_BLOCKS_PER_TICK);

        // Clamp desired position to the tether.
        Vec3 anchor  = session.anchor;
        Vec3 desired = drone.position().add(velocity);
        Vec3 clamped = new Vec3(
                Mth.clamp(desired.x, anchor.x - ModKeys.TETHER_RADIUS_BLOCKS, anchor.x + ModKeys.TETHER_RADIUS_BLOCKS),
                Mth.clamp(desired.y, anchor.y - ModKeys.TETHER_RADIUS_BLOCKS, anchor.y + ModKeys.TETHER_RADIUS_BLOCKS),
                Mth.clamp(desired.z, anchor.z - ModKeys.TETHER_RADIUS_BLOCKS, anchor.z + ModKeys.TETHER_RADIUS_BLOCKS)
        );

        Vec3 step = clamped.subtract(drone.position());
        drone.setYRot(input.yaw());
        drone.setXRot(input.pitch());
        drone.setYHeadRot(input.yaw());
        drone.setDeltaMovement(step);
        drone.move(MoverType.SELF, step);

        double warningDistance = distanceToBoundary(clamped, anchor);
        if (warningDistance <= ModKeys.TETHER_WARNING_BLOCKS
                && player.tickCount - session.lastWarningTick >= ModKeys.WARNING_MESSAGE_COOLDOWN_TICKS) {
            player.displayClientMessage(
                    Component.translatable(ModKeys.ACTIONBAR_TETHER_WARNING), true);
            session.lastWarningTick = player.tickCount;
        }
    }

    private static double distanceToBoundary(Vec3 position, Vec3 anchor) {
        double dx = ModKeys.TETHER_RADIUS_BLOCKS - Math.abs(position.x - anchor.x);
        double dy = ModKeys.TETHER_RADIUS_BLOCKS - Math.abs(position.y - anchor.y);
        double dz = ModKeys.TETHER_RADIUS_BLOCKS - Math.abs(position.z - anchor.z);
        return Math.min(dx, Math.min(dy, dz));
    }

    public static void stop(ServerPlayer player, boolean setFollow) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return;
        }
        Entity entity = player.level().getEntity(session.droneId);
        if (entity instanceof DroneEntity drone) {
            drone.setNoAi(false);
            if (setFollow) {
                drone.setState(com.uemc.assistance_drone.util.ModKeys.STATE_FOLLOW);
            }
        }
        PacketDistributor.sendToPlayer(player, new PovSessionMessage(false, 0, 0));
    }

    public static void onBodyDamaged(ServerPlayer player) {
        if (hasSession(player)) {
            stop(player, true);
        }
    }

    // -------------------------------------------------------------------------
    // Session state
    // -------------------------------------------------------------------------

    private static final class Session {
        private final int   droneId;
        private final Vec3  anchor;
        private final float bodyYaw;
        private final float bodyPitch;
        private final float bodyHeadYaw;
        private PovInputMessage input = new PovInputMessage(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        private int lastWarningTick;

        private Session(int droneId, Vec3 anchor,
                        float bodyYaw, float bodyPitch, float bodyHeadYaw) {
            this.droneId      = droneId;
            this.anchor       = anchor;
            this.bodyYaw      = bodyYaw;
            this.bodyPitch    = bodyPitch;
            this.bodyHeadYaw  = bodyHeadYaw;
        }
    }
}