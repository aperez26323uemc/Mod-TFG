package com.uemc.assistance_drone.entities.drone.goals;

import com.mojang.logging.LogUtils;
import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.items.SitePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;

public class DronePickupGoal extends Goal {
    private final DroneEntity drone;
    private final Predicate<String> activationCondition;
    private final Queue<ItemEntity> pickupQueue = new LinkedList<>();
    private ItemEntity currentTarget;
    private int checkCooldown = 0;

    public DronePickupGoal(DroneEntity drone, Predicate<String> activationCondition) {
        this.drone = drone;
        this.activationCondition = activationCondition;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!activationCondition.test(drone.getState())) return false;
        if (this.checkCooldown-- > 0) return false;

        this.checkCooldown = 10;
        return populatePickupQueue();
    }

    @Override
    public void start() {
        nextTarget();
    }

    @Override
    public void stop() {
        this.currentTarget = null;
        this.pickupQueue.clear();
        this.drone.getNavigation().stop();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DronePickupGoal.class);

    @Override
    public void tick() {
        if (!isValidTarget(currentTarget)) {
            nextTarget();
        }
        if (currentTarget == null) return;
        LOGGER.debug("DronePickupGoal target: {}", currentTarget);

        this.drone.getLookControl().setLookAt(this.currentTarget, 30.0F, 30.0F);

        // Movimiento
        if (this.drone.tickCount % 5 == 0) {
            this.drone.getNavigation().moveTo(this.currentTarget, 1.0D);
        }

        // Intento de recogida
        // Usamos una comprobación de distancia simple aquí antes de llamar a la lógica pesada
        if (this.drone.distanceToSqr(this.currentTarget) <= 2.25) { // 1.5 * 1.5
            // Llamamos a itemPickUp() que gestiona la recogida real
            boolean pickedUp = this.drone.getLogic().itemPickUp(); // DELEGADO

            if (pickedUp) {
                // Si recogimos algo, asumimos que fue el target actual (o uno cercano)
                // y pasamos al siguiente.
                nextTarget();
            } else {
                // Si estamos cerca pero no lo recogió, probablemente inventario lleno
                if (!this.drone.getLogic().hasInventorySpaceFor(currentTarget.getItem())) {
                    this.pickupQueue.clear(); // Abortar misión
                    this.currentTarget = null;
                }
            }
        }
    }

    private void nextTarget() {
        this.currentTarget = this.pickupQueue.poll();
        if (this.currentTarget != null) {
            this.drone.getNavigation().moveTo(this.currentTarget, 1.0D);
        }
    }

    private boolean populatePickupQueue() {
        this.pickupQueue.clear();
        ItemStack plannerStack = this.drone.getInventory().getStackInSlot(0);
        if (!SitePlanner.isConfigured(plannerStack)) return false;

        BlockPos start = SitePlanner.getStartPos(plannerStack);
        BlockPos end = SitePlanner.getEndPos(plannerStack);
        AABB searchArea = new AABB(start).minmax(new AABB(end)).expandTowards(1, 1, 1);

        List<ItemEntity> items = this.drone.level().getEntitiesOfClass(ItemEntity.class, searchArea);

        items.stream()
                .filter(this::isValidTarget)
                .filter(item -> this.drone.getLogic().hasInventorySpaceFor(item.getItem())) // DELEGADO
                .sorted(Comparator.comparingDouble(this.drone::distanceToSqr))
                .forEach(pickupQueue::add);

        return !pickupQueue.isEmpty();
    }

    private boolean isValidTarget(ItemEntity item) {
        return item != null && item.isAlive() && !item.getItem().isEmpty();
    }
}