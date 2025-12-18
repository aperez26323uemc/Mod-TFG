package com.uemc.assistance_drone.entities.drone;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class DroneAiLogic {

    private final DroneEntity drone;

    public DroneAiLogic(DroneEntity drone) {
        this.drone = drone;
    }

    // --- MÉTODOS PÚBLICOS (La "Caja de Herramientas") ---

    public void executeMovement(Vec3 targetPos) {
        if (targetPos == null) return;

        double distSqr = drone.position().distanceToSqr(targetPos);

        // Si está lejos o bloqueado -> Navegación Inteligente
        if (distSqr > 2.0) {
            if (canSeeTarget(targetPos)) {
                // Camino libre -> Física Directa (Rebote)
                drone.getNavigation().stop();
                drone.getMoveControl().setWantedPosition(targetPos.x, targetPos.y, targetPos.z, 1.0);
            } else {
                // Obstáculo -> Pathfinding
                drone.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, 1.0);
            }
        } else {
            // Muy cerca -> Flotar con rebote suave
            drone.getNavigation().stop();
            drone.getMoveControl().setWantedPosition(targetPos.x, targetPos.y, targetPos.z, 1.0);
        }
    }

    public Vec3 getSafetyTarget(Player owner, double distance) {
        Vec3 dir = drone.position().subtract(owner.position());
        if (dir.lengthSqr() < 0.01) dir = new Vec3(1, 0, 0);
        dir = dir.normalize();

        Vec3 safetyPoint = owner.position().add(dir.scale(distance));
        return calculateIdealPosition(safetyPoint.x, owner.getEyeY(), safetyPoint.z);
    }

    public Vec3 calculateIdealPosition(double x, double y, double z) {
        double floorY = getFloorY(x, y, z);
        // Mínimo 1.5 bloques sobre el suelo
        if (y - floorY < 1.5) {
            y = floorY + 1.5;
        }
        return new Vec3(x, y, z);
    }

    // --- HELPERS PRIVADOS (Usados internamente por la caja de herramientas) ---

    private double getFloorY(double x, double y, double z) {
        Vec3 start = new Vec3(x, y, z);
        Vec3 end = new Vec3(x, y - 10.0, z);
        BlockHitResult result = drone.level().clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                drone
        ));
        if (result.getType() == HitResult.Type.BLOCK) {
            return result.getLocation().y;
        }
        return -500.0;
    }

    private boolean canSeeTarget(Vec3 target) {
        Vec3 start = new Vec3(drone.getX(), drone.getEyeY(), drone.getZ());
        BlockHitResult result = drone.level().clip(new ClipContext(
                start, target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                drone
        ));
        return result.getType() == HitResult.Type.MISS;
    }
}