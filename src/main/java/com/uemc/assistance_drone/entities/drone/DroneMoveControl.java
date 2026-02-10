package com.uemc.assistance_drone.entities.drone;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/**
 * Custom movement controller for drone entities implementing physics-based flight.
 * <p>
 * This controller uses a PD (Proportional-Derivative) control system to create
 * smooth, realistic drone movement with the following characteristics:
 * <ul>
 *   <li><b>Inertial movement</b> - Gradual acceleration and deceleration</li>
 *   <li><b>Dynamic braking</b> - Increased damping when approaching target</li>
 *   <li><b>Hovering oscillation</b> - Sinusoidal bobbing motion for visual realism</li>
 * </ul>
 * </p>
 *
 * <h3>Control System Parameters</h3>
 * <p>
 * The PD controller calculates acceleration as:<br>
 * {@code a = K_P * error - K_D * velocity}
 * </p>
 * <ul>
 *   <li>{@code K_P} - Proportional gain controlling attraction force</li>
 *   <li>{@code K_D} - Derivative gain controlling damping (resistance to velocity)</li>
 *   <li>{@code error} - Distance vector to target position</li>
 * </ul>
 *
 * <h3>Dynamic Braking</h3>
 * <p>
 * When within {@value BRAKING_RADIUS} blocks of target, damping increases linearly
 * from {@value BASE_K_D} to {@value BRAKING_K_D} to prevent overshooting.
 * </p>
 *
 * @see DroneEntity
 */
public class DroneMoveControl extends MoveControl {

    private final DroneEntity drone;

    /** Maximum horizontal movement speed in blocks per tick */
    private static final double MAX_HORIZONTAL_SPEED = 0.5;

    /** Maximum vertical movement speed in blocks per tick */
    private static final double MAX_VERTICAL_SPEED = 0.25;

    /** Proportional gain - controls attraction force to target */
    private static final double K_P = 0.1;

    /** Base derivative gain - controls velocity damping during normal flight */
    private static final double BASE_K_D = 0.08;

    /** Braking derivative gain - high damping when approaching target */
    private static final double BRAKING_K_D = 0.3;

    /** Distance at which braking begins (blocks) */
    private static final double BRAKING_RADIUS = 3.0;

    /** Maximum acceleration per tick - simulates mass/motor limitations */
    private static final double MAX_ACCELERATION = 0.025;

    /** Vertical displacement amplitude for hovering oscillation (blocks) */
    private static final double BOBBING_AMPLITUDE = 0.3;

    /** Angular frequency of hovering oscillation (radians per tick) */
    private static final double BOBBING_SPEED = 0.15;

    /**
     * Constructs a new move controller for the specified drone.
     *
     * @param drone the drone entity to control
     */
    public DroneMoveControl(DroneEntity drone) {
        super(drone);
        this.drone = drone;
    }

    /**
     * Updates the drone's velocity each tick using PD control with dynamic braking.
     * <p>
     * The method performs the following steps:
     * <ol>
     *   <li>Calculate target position including bobbing offset</li>
     *   <li>Compute error vector and distance to target</li>
     *   <li>Apply dynamic braking if within braking radius</li>
     *   <li>Calculate PD-controlled acceleration</li>
     *   <li>Limit acceleration and update velocity</li>
     *   <li>Update drone rotation to face movement direction</li>
     * </ol>
     * </p>
     */
    @Override
    public void tick() {
        Vec3 targetPosition = calculateTargetWithBobbing();
        Vec3 currentVelocity = drone.getDeltaMovement();

        Vec3 errorVector = calculateErrorVector(targetPosition);
        double distance = errorVector.length();

        double dampingFactor = calculateDampingFactor(distance);
        Vec3 acceleration = calculatePDAcceleration(errorVector, currentVelocity, dampingFactor);
        acceleration = limitAcceleration(acceleration);

        Vec3 newVelocity = currentVelocity.add(acceleration);
        applyVelocity(newVelocity);

        updateRotation(errorVector);
    }

    /**
     * Calculates the target position including hovering oscillation.
     * <p>
     * If the operation is {@link Operation#MOVE_TO}, returns the desired position
     * plus a sinusoidal vertical offset. Otherwise, maintains current horizontal
     * position while hovering.
     * </p>
     *
     * @return target position with bobbing applied
     */
    private Vec3 calculateTargetWithBobbing() {
        double bobbingOffset = Math.sin(drone.tickCount * BOBBING_SPEED) * BOBBING_AMPLITUDE;

        if (this.operation == Operation.MOVE_TO) {
            return new Vec3(this.wantedX, this.wantedY + bobbingOffset, this.wantedZ);
        } else {
            return new Vec3(drone.getX(), drone.getY() + bobbingOffset, drone.getZ());
        }
    }

    /**
     * Calculates the error vector from current to target position.
     *
     * @param target the target position
     * @return vector pointing from current position to target
     */
    private Vec3 calculateErrorVector(Vec3 target) {
        return new Vec3(
                target.x - drone.getX(),
                target.y - drone.getY(),
                target.z - drone.getZ()
        );
    }

    /**
     * Calculates the damping factor using dynamic braking.
     * <p>
     * Damping linearly interpolates from {@value BASE_K_D} to {@value BRAKING_K_D}
     * as distance decreases from {@value BRAKING_RADIUS} to 0.
     * </p>
     *
     * @param distance current distance to target
     * @return damping coefficient for PD controller
     */
    private double calculateDampingFactor(double distance) {
        if (distance < BRAKING_RADIUS) {
            double brakingFactor = 1.0 - (distance / BRAKING_RADIUS);
            return Mth.lerp(brakingFactor, BASE_K_D, BRAKING_K_D);
        }
        return BASE_K_D;
    }

    /**
     * Calculates acceleration using PD control law.
     * <p>
     * The acceleration is computed as:<br>
     * {@code a = K_P * error - K_D * velocity}
     * </p>
     * <p>
     * Horizontal axes (X and Z) receive additional damping (1.5x) to prevent
     * lateral oscillation, while vertical axis (Y) uses standard damping.
     * </p>
     *
     * @param error vector from current to target position
     * @param velocity current velocity vector
     * @param dampingFactor base damping coefficient (K_D)
     * @return acceleration vector
     */
    private Vec3 calculatePDAcceleration(Vec3 error, Vec3 velocity, double dampingFactor) {
        double ax = K_P * error.x - dampingFactor * 1.5 * velocity.x;
        double ay = K_P * error.y - dampingFactor * velocity.y;
        double az = K_P * error.z - dampingFactor * 1.5 * velocity.z;

        return new Vec3(ax, ay, az);
    }

    /**
     * Limits acceleration magnitude to simulate motor constraints.
     *
     * @param acceleration unconstrained acceleration vector
     * @return acceleration vector clamped to {@value MAX_ACCELERATION}
     */
    private Vec3 limitAcceleration(Vec3 acceleration) {
        double magnitude = acceleration.length();
        if (magnitude > MAX_ACCELERATION) {
            return acceleration.normalize().scale(MAX_ACCELERATION);
        }
        return acceleration;
    }

    /**
     * Applies velocity to the drone with axis-specific limits.
     * <p>
     * Horizontal velocity is clamped to ±{@value MAX_HORIZONTAL_SPEED}<br>
     * Vertical velocity is clamped to ±{@value MAX_VERTICAL_SPEED}
     * </p>
     *
     * @param velocity the velocity to apply
     */
    private void applyVelocity(Vec3 velocity) {
        double x = Mth.clamp(velocity.x, -MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED);
        double y = Mth.clamp(velocity.y, -MAX_VERTICAL_SPEED, MAX_VERTICAL_SPEED);
        double z = Mth.clamp(velocity.z, -MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED);

        drone.setDeltaMovement(x, y, z);
    }

    /**
     * Updates the drone's yaw rotation to face the movement direction.
     * <p>
     * Rotation is only updated when horizontal movement exceeds a threshold
     * to prevent erratic rotation when stationary.
     * </p>
     *
     * @param error error vector used to determine facing direction
     */
    private void updateRotation(Vec3 error) {
        if (error.x * error.x + error.z * error.z > 0.01) {
            float targetYaw = (float) (Mth.atan2(error.z, error.x) * (180F / Math.PI)) - 90.0F;
            drone.setYRot(this.rotlerp(drone.getYRot(), targetYaw, 5.0F));
            drone.setYBodyRot(drone.getYRot());
        }
    }
}