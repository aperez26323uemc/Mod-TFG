package com.uemc.assistance_drone.entities.drone.goals;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.items.SitePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.function.Predicate;

/**
 * Goal for automatic item pickup for drones within a configured work area.
 * <p>
 * Processes items in batches, maintains a pickup queue sorted by distance,
 * and uses caching strategies to minimize expensive operations like entity queries
 * and inventory space validation.
 * </p>
 *
 * @see DroneEntity
 * @see SitePlanner
 */
public class DronePickupGoal extends Goal {
    private static final int MAX_QUEUE_SIZE = 16;
    private static final int SCAN_COOLDOWN = 20;
    private static final int SPACE_CACHE_DURATION = 10;
    private static final double PICKUP_RANGE_SQ = 2.25;

    private final DroneEntity drone;
    private final Predicate<String> activationCondition;
    private final Queue<ItemEntity> pickupQueue = new LinkedList<>();

    private ItemEntity currentTarget;
    private int checkCooldown = 0;
    private int queueRefreshCooldown = 0;
    private boolean cachedHasSpace = true;
    private int spaceCacheTicks = 0;

    /**
     * Constructs a new pickup goal for the specified drone.
     *
     * @param drone the drone entity that will execute this goal
     * @param activationCondition predicate that determines when this goal can activate based on drone state.
     */
    public DronePickupGoal(DroneEntity drone, Predicate<String> activationCondition) {
        this.drone = drone;
        this.activationCondition = activationCondition;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!activationCondition.test(drone.getState())) return false;
        if (this.checkCooldown-- > 0) return false;

        this.checkCooldown = 100;

        if (drone.getNavigation().isStuck()) return false;
        if (!hasInventorySpaceCached()) return false;

        if (pickupQueue.isEmpty()) {
            return populatePickupQueue();
        }
        return true;
    }

    @Override
    public void start() {
        this.queueRefreshCooldown = SCAN_COOLDOWN;
        invalidateSpaceCache();
        nextTarget();
    }

    @Override
    public void stop() {
        this.currentTarget = null;
        this.pickupQueue.clear();
        this.drone.getNavigation().stop();
        invalidateSpaceCache();
    }

    @Override
    public boolean canContinueToUse() {
        if (drone.getNavigation().isStuck()) return false;
        if (this.currentTarget == null) return false;
        if (!currentTarget.isAlive() || currentTarget.getItem().isEmpty()) return false;

        return hasInventorySpaceCached();
    }

    @Override
    public void tick() {
        updateSpaceCache();

        if (!cachedHasSpace) {
            this.pickupQueue.clear();
            this.currentTarget = null;
            return;
        }

        if (!isValidTargetQuick(currentTarget)) {
            nextTarget();
        }
        if (currentTarget == null) return;

        this.drone.getLookControl().setLookAt(currentTarget);

        if (this.drone.tickCount % 5 == 0) {
            this.drone.getNavigation().moveTo(this.currentTarget, 1.0D);
        }

        if (this.drone.distanceToSqr(this.currentTarget) <= PICKUP_RANGE_SQ) {
            boolean pickedUp = this.drone.getLogic().itemPickUp();

            if (pickedUp) {
                invalidateSpaceCache();
                nextTarget();
            } else {
                if (!this.drone.getLogic().hasInventorySpaceFor(currentTarget.getItem())) {
                    this.pickupQueue.clear();
                    this.currentTarget = null;
                    cachedHasSpace = false;
                }
            }
        }

        if (--queueRefreshCooldown <= 0 && pickupQueue.isEmpty()) {
            queueRefreshCooldown = SCAN_COOLDOWN;
            populatePickupQueue();
        }
    }

    /**
     * Goes to a next valid target in the queue, discarding invalid items.
     */
    private void nextTarget() {
        this.currentTarget = null;

        while (!pickupQueue.isEmpty()) {
            ItemEntity candidate = pickupQueue.poll();
            if (isValidTargetQuick(candidate)) {
                this.currentTarget = candidate;
                break;
            }
        }

        if (this.currentTarget != null) {
            this.drone.getNavigation().moveTo(this.currentTarget, 1.0D);
        }
    }

    /**
     * Scans the configured work area for items and populates the pickup queue.
     * Items get sorted by distance and limited to {@link #MAX_QUEUE_SIZE}.
     *
     * @return true if it finds any valid items, false otherwise
     */
    private boolean populatePickupQueue() {
        this.pickupQueue.clear();

        ItemStack planner = this.drone.getInventory().getStackInSlot(0);
        if (!SitePlanner.isConfigured(planner)) return false;

        BlockPos start = SitePlanner.getStartPos(planner);
        BlockPos end = SitePlanner.getEndPos(planner);
        AABB searchArea = new AABB(start).minmax(new AABB(end)).expandTowards(1, 1, 1);

        List<ItemEntity> items = this.drone.level().getEntitiesOfClass(ItemEntity.class, searchArea);
        if (items.isEmpty()) return false;

        items.sort(Comparator.comparingDouble(this.drone::distanceToSqr));

        int added = 0;
        for (ItemEntity item : items) {
            if (added >= MAX_QUEUE_SIZE) break;
            if (!isValidTargetQuick(item)) continue;
            if (!this.drone.getLogic().hasInventorySpaceFor(item.getItem())) continue;

            pickupQueue.add(item);
            added++;
        }

        return !pickupQueue.isEmpty();
    }

    /**
     * Fast validation that checks only essential item state.
     *
     * @param item the item entity to validate
     * @return true if the item alive and has a valid stack
     */
    private boolean isValidTargetQuick(ItemEntity item) {
        return item != null
                && item.isAlive()
                && !item.getItem().isEmpty();
    }

    /**
     * Checks inventory space using a time-based cache to reduce expensive validation calls.
     *
     * @return true if the drone has inventory space available
     */
    private boolean hasInventorySpaceCached() {
        if (spaceCacheTicks > 0) {
            return cachedHasSpace;
        }

        cachedHasSpace = this.drone.getLogic().hasAnyInventorySpace();
        spaceCacheTicks = SPACE_CACHE_DURATION;
        return cachedHasSpace;
    }

    /**
     * Decrements the space cache timer if active.
     */
    private void updateSpaceCache() {
        if (spaceCacheTicks > 0) {
            spaceCacheTicks--;
        }
    }

    /**
     * Forces the inventory space cache to expire on the next check.
     */
    private void invalidateSpaceCache() {
        spaceCacheTicks = 0;
    }

    @Override
    public boolean isInterruptable() {
        return !hasInventorySpaceCached();
    }
}