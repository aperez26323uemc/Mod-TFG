package com.uemc.assistance_drone.entities.drone;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

public class DroneMoveControl extends MoveControl {
    private final DroneEntity drone;

    private static final double MAX_HORIZONTAL_SPEED = 0.5;
    private static final double MAX_VERTICAL_SPEED = 0.25;

    // Constantes BASE
    private static final double K_P = 0.1;
    private static final double BASE_K_D = 0.08; // Amortiguación normal (vuelo libre)

    // Constantes de FRENADO
    private static final double BRAKING_K_D = 0.3; // Amortiguación fuerte (llegando)
    private static final double BRAKING_RADIUS = 3.0; // Distancia donde empieza a frenar

    // Tu valor preferido :)
    private static final double MAX_ACCELERATION = 0.025;

    public DroneMoveControl(DroneEntity drone) {
        super(drone);
        this.drone = drone;
    }

    @Override
    public void tick() {
        Vec3 currentVel = drone.getDeltaMovement();
        double targetX, targetY, targetZ;

        if (this.operation == Operation.MOVE_TO) {
            targetX = this.wantedX;
            targetY = this.wantedY;
            targetZ = this.wantedZ;
        } else {
            targetX = drone.getX();
            targetY = drone.getY();
            targetZ = drone.getZ();
        }

        double dx = targetX - drone.getX();
        double dy = targetY - drone.getY();
        double dz = targetZ - drone.getZ();

        double distSqr = dx*dx + dy*dy + dz*dz;
        double dist = Math.sqrt(distSqr);

        // --- FRENADO DINÁMICO ---
        // Si estamos cerca, aumentamos K_D para matar la inercia
        double currentKD = BASE_K_D;

        if (dist < BRAKING_RADIUS) {
            // Interpolamos linealmente entre BASE_K_D y BRAKING_K_D
            // Si dist=3 -> usa 0.08
            // Si dist=0 -> usa 0.3
            double factor = 1.0 - (dist / BRAKING_RADIUS);
            currentKD = Mth.lerp(factor, BASE_K_D, BRAKING_K_D);
        }

        // PID con el K_D dinámico
        double ax = (K_P * dx - currentKD * 1.5 * currentVel.x);
        double ay = (K_P * dy - currentKD * currentVel.y);
        double az = (K_P * dz - currentKD * 1.5 * currentVel.z);

        // Limitar Aceleración (Masa)
        Vec3 acceleration = new Vec3(ax, ay, az);
        double accLength = acceleration.length();
        if (accLength > MAX_ACCELERATION) {
            acceleration = acceleration.normalize().scale(MAX_ACCELERATION);
        }

        Vec3 newVel = currentVel.add(acceleration);

        // Limitar Velocidad Máxima
        double finalX = Mth.clamp(newVel.x, -MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED);
        double finalY = Mth.clamp(newVel.y, -MAX_VERTICAL_SPEED, MAX_VERTICAL_SPEED);
        double finalZ = Mth.clamp(newVel.z, -MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED);

        drone.setDeltaMovement(finalX, finalY, finalZ);

        if (dx * dx + dz * dz > 0.01) {
            float targetYaw = (float) (Mth.atan2(dz, dx) * (180F / (float) Math.PI)) - 90.0F;
            drone.setYRot(this.rotlerp(drone.getYRot(), targetYaw, 5.0F));
            drone.setYBodyRot(drone.getYRot());
        }
    }
}