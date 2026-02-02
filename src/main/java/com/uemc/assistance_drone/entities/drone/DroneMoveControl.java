package com.uemc.assistance_drone.entities.drone;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

public class DroneMoveControl extends MoveControl {

    private final DroneEntity drone;

    // Velocidades máximas
    private static final double MAX_HORIZONTAL_SPEED = 0.5;
    private static final double MAX_VERTICAL_SPEED = 0.25;

    // Constantes PID (Física resbaladiza)
    private static final double K_P = 0.1;       // Fuerza de atracción
    private static final double BASE_K_D = 0.08; // Amortiguación (Damping) baja = Resbaladizo

    // Constantes de FRENADO
    private static final double BRAKING_K_D = 0.3;    // Frenado fuerte al llegar
    private static final double BRAKING_RADIUS = 3.0; // Distancia de inicio de frenado

    // Física
    private static final double MAX_ACCELERATION = 0.025; // Masa inercial

    // Configuración del Bobbing (Flotación)
    private static final double BOBBING_AMPLITUDE = 0.3; // Cuánto sube y baja (bloques)
    private static final double BOBBING_SPEED = 0.15;    // Qué tan rápido oscila

    public DroneMoveControl(DroneEntity drone) {
        super(drone);
        this.drone = drone;
    }

    @Override
    public void tick() {
        // 1. Calculamos el "Objetivo Real" incluyendo el Bobbing
        // Usamos Math.sin con el tiempo del drone para una oscilación suave y eterna
        double bobbingOffset = Math.sin(drone.tickCount * BOBBING_SPEED) * BOBBING_AMPLITUDE;

        // El targetY es la posición deseada por la IA + la oscilación
        double targetX = this.wantedX;
        double targetY = this.wantedY + bobbingOffset;
        double targetZ = this.wantedZ;

        // Si la operación no es mover, solo mantenemos posición actual + bobbing
        if (this.operation != Operation.MOVE_TO) {
            // Esto mantiene al drone flotando en el sitio si no tiene órdenes
            targetX = drone.getX();
            targetZ = drone.getZ();
            // Nota: No sobrescribimos targetY para que siga flotando en altura
        }

        Vec3 currentVel = drone.getDeltaMovement();

        double dx = targetX - drone.getX();
        double dy = targetY - drone.getY();
        double dz = targetZ - drone.getZ();

        double distSq = dx * dx + dy * dy + dz * dz;
        double dist = Math.sqrt(distSq);

        // --- FRENADO DINÁMICO (Tu lógica original, es buena) ---
        double currentKD = BASE_K_D;
        if (dist < BRAKING_RADIUS) {
            double factor = 1.0 - (dist / BRAKING_RADIUS);
            currentKD = Mth.lerp(factor, BASE_K_D, BRAKING_K_D);
        }

        // --- CONTROLADOR PD (Proporcional - Derivativo) ---
        // K_P * error (distancia) - K_D * velocidad
        double ax = (K_P * dx - currentKD * 1.5 * currentVel.x);
        double ay = (K_P * dy - currentKD * currentVel.y); // Eje Y suele necesitar menos amortiguación extra
        double az = (K_P * dz - currentKD * 1.5 * currentVel.z);

        // Limitar la aceleración (Simula la masa/inercia del motor)
        Vec3 acceleration = new Vec3(ax, ay, az);
        if (acceleration.length() > MAX_ACCELERATION) {
            acceleration = acceleration.normalize().scale(MAX_ACCELERATION);
        }

        // Aplicar nueva velocidad
        Vec3 newVel = currentVel.add(acceleration);

        // Clamp final de seguridad
        double finalX = Mth.clamp(newVel.x, -MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED);
        double finalY = Mth.clamp(newVel.y, -MAX_VERTICAL_SPEED, MAX_VERTICAL_SPEED);
        double finalZ = Mth.clamp(newVel.z, -MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED);

        drone.setDeltaMovement(finalX, finalY, finalZ);

        // --- ROTACIÓN ---
        if (dx * dx + dz * dz > 0.01) {
            float targetYaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90.0F;
            drone.setYRot(this.rotlerp(drone.getYRot(), targetYaw, 5.0F));
            drone.setYBodyRot(drone.getYRot());
        }
    }
}