package com.uemc.assistance_drone.entities.drone;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/**
 * Custom movement controller implementing smooth, physics-inspired drone flight.
 * <p>
 * Uses a PD (Proportional–Derivative) control model with inertial movement,
 * dynamic braking near the target and subtle hovering oscillation for visual realism.
 *
 * @see DroneEntity
 */
public class DroneMoveControl extends MoveControl {

    private final DroneEntity drone;

    /* ------------------------------------------------------------ */
    /* Tuning Parameters                                            */
    /* ------------------------------------------------------------ */

    /** Maximum horizontal speed (blocks/tick) */
    private static final double MAX_HORIZONTAL_SPEED = 0.5;

    /** Maximum vertical speed (blocks/tick) */
    private static final double MAX_VERTICAL_SPEED = 0.25;

    /** Proportional gain (attraction force to target) */
    private static final double K_P = 0.1;

    /** Base derivative gain (velocity damping) */
    private static final double BASE_K_D = 0.08;

    /** Increased damping when approaching target */
    private static final double BRAKING_K_D = 0.3;

    /** Distance at which braking starts (blocks) */
    private static final double BRAKING_RADIUS = 3.0;

    /** Maximum acceleration per tick */
    private static final double MAX_ACCELERATION = 0.025;

    /** Vertical hover oscillation amplitude (blocks) */
    private static final double BOBBING_AMPLITUDE = 0.3;

    /** Hover oscillation speed (radians/tick) */
    private static final double BOBBING_SPEED = 0.15;

    public DroneMoveControl(DroneEntity drone) {
        super(drone);
        this.drone = drone;
    }

    @Override
    public void tick() {
        Vec3 target = calculateTargetWithBobbing();
        Vec3 velocity = drone.getDeltaMovement();

        Vec3 error = calculateErrorVector(target);
        double distance = error.length();

        double damping = calculateDampingFactor(distance);
        Vec3 acceleration = calculatePDAcceleration(error, velocity, damping);
        acceleration = limitAcceleration(acceleration);

        applyVelocity(velocity.add(acceleration));
        updateRotation(error);
    }

    /* ------------------------------------------------------------ */
    /* Target & Error                                               */
    /* ------------------------------------------------------------ */

    private Vec3 calculateTargetWithBobbing() {
        double bobbing = Math.sin(drone.tickCount * BOBBING_SPEED) * BOBBING_AMPLITUDE;

        if (this.operation == Operation.MOVE_TO) {
            return new Vec3(this.wantedX, this.wantedY + bobbing, this.wantedZ);
        }
        return new Vec3(drone.getX(), drone.getY() + bobbing, drone.getZ());
    }

    private Vec3 calculateErrorVector(Vec3 target) {
        return new Vec3(
                target.x - drone.getX(),
                target.y - drone.getY(),
                target.z - drone.getZ()
        );
    }

    /* ------------------------------------------------------------ */
    /* Control Law                                                  */
    /* ------------------------------------------------------------ */

    private double calculateDampingFactor(double distance) {
        if (distance < BRAKING_RADIUS) {
            double factor = 1.0 - (distance / BRAKING_RADIUS);
            return Mth.lerp(factor, BASE_K_D, BRAKING_K_D);
        }
        return BASE_K_D;
    }

    /**
     * PD control: a = Kp * error − Kd * velocity.
     * <p>
     * Horizontal axes receive extra damping to reduce lateral oscillation.
     */
    private Vec3 calculatePDAcceleration(Vec3 error, Vec3 velocity, double damping) {
        double ax = K_P * error.x - damping * 1.5 * velocity.x;
        double ay = K_P * error.y - damping * velocity.y;
        double az = K_P * error.z - damping * 1.5 * velocity.z;

        return new Vec3(ax, ay, az);
    }

    private Vec3 limitAcceleration(Vec3 acceleration) {
        double magnitude = acceleration.length();
        if (magnitude > MAX_ACCELERATION) {
            return acceleration.normalize().scale(MAX_ACCELERATION);
        }
        return acceleration;
    }

    /* ------------------------------------------------------------ */
    /* Velocity & Rotation                                         */
    /* ------------------------------------------------------------ */

    private void applyVelocity(Vec3 velocity) {
        double x = Mth.clamp(velocity.x, -MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED);
        double y = Mth.clamp(velocity.y, -MAX_VERTICAL_SPEED, MAX_VERTICAL_SPEED);
        double z = Mth.clamp(velocity.z, -MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED);

        drone.setDeltaMovement(x, y, z);
    }

    private void updateRotation(Vec3 error) {
        if (error.x * error.x + error.z * error.z > 0.01) {
            float yaw = (float) (Mth.atan2(error.z, error.x) * (180F / Math.PI)) - 90.0F;
            drone.setYRot(this.rotlerp(drone.getYRot(), yaw, 5.0F));
            drone.setYBodyRot(drone.getYRot());
        }
    }
}
