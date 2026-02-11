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
 * Goal responsible for autonomous item pickup within the configured work area.
 * <p>
 * Maintains a bounded priority queue of nearby item entities sorted by distance.
 * Uses lightweight caching for inventory checks and periodic queue refreshes
 * to avoid excessive entity queries.
 *
 * @see DroneEntity
 * @see SitePlanner
 */
public class DronePickupGoal extends Goal {

    /* ------------------------------------------------------------ */
    /* Tuning                                                       */
    /* ------------------------------------------------------------ */

    private static final int MAX_TARGET_QUEUE_SIZE = 16;
    private static final double PICKUP_RANGE_SQUARED = 2.25;
    private static final int QUEUE_REFRESH_INTERVAL = 20;
    private static final int INVENTORY_CACHE_TICKS = 10;
    private static final int TARGET_TIMEOUT_TICKS = 200;
    private static final int ACTIVATION_COOLDOWN_TICKS = 100;

    /* ------------------------------------------------------------ */
    /* State                                                        */
    /* ------------------------------------------------------------ */

    private final DroneEntity drone;
    private final Predicate<String> activationCondition;

    private final Queue<ItemEntity> targetQueue = new LinkedList<>();

    private ItemEntity currentTarget;
    private ItemEntity previousTarget;

    private int activationCooldownTicks = 0;
    private int queueRefreshTicks = 0;

    private boolean cachedInventorySpace = true;
    private int inventoryCacheTicks = 0;

    private int targetTimeoutTicks = 0;

    public DronePickupGoal(DroneEntity drone, Predicate<String> activationCondition) {
        this.drone = drone;
        this.activationCondition = activationCondition;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /* ------------------------------------------------------------ */
    /* Lifecycle                                                    */
    /* ------------------------------------------------------------ */

    @Override
    public boolean canUse() {
        if (!activationCondition.test(drone.getState())) return false;
        if (activationCooldownTicks-- > 0) return false;

        activationCooldownTicks = ACTIVATION_COOLDOWN_TICKS;

        if (drone.getNavigation().isStuck()) return false;
        if (!hasInventorySpaceCached()) return false;

        return !targetQueue.isEmpty() || refreshTargetQueue();
    }

    @Override
    public void start() {
        queueRefreshTicks = QUEUE_REFRESH_INTERVAL;
        invalidateInventoryCache();
        selectNextTarget();
    }

    @Override
    public void stop() {
        currentTarget = null;
        targetQueue.clear();
        drone.getNavigation().stop();
        invalidateInventoryCache();
    }

    @Override
    public boolean canContinueToUse() {
        if (drone.getNavigation().isStuck()) return false;
        if (currentTarget == null) return false;
        if (!isValidTarget(currentTarget)) return false;

        // Countdown timeout
        if (targetTimeoutTicks-- <= 0) {
            if (currentTarget.equals(previousTarget)) return false;

            previousTarget = currentTarget;
            targetTimeoutTicks = TARGET_TIMEOUT_TICKS;
        }

        return hasInventorySpaceCached();
    }

    /* ------------------------------------------------------------ */
    /* Tick                                                         */
    /* ------------------------------------------------------------ */

    @Override
    public void tick() {

        if (inventoryCacheTicks > 0) {
            inventoryCacheTicks--;
        }

        if (!cachedInventorySpace) {
            targetQueue.clear();
            currentTarget = null;
            return;
        }

        if (!isValidTarget(currentTarget)) {
            selectNextTarget();
        }

        if (currentTarget == null) return;

        drone.getLookControl().setLookAt(currentTarget);

        if (drone.tickCount % 5 == 0) {
            drone.getLogic().executeMovement(currentTarget.position());
        }

        if (drone.distanceToSqr(currentTarget) <= PICKUP_RANGE_SQUARED) {
            boolean pickedUp = drone.getLogic().itemPickUp();

            if (pickedUp) {
                invalidateInventoryCache();
                selectNextTarget();
            } else if (!drone.getLogic()
                    .hasInventorySpaceFor(currentTarget.getItem())) {

                targetQueue.clear();
                currentTarget = null;
                cachedInventorySpace = false;
            }
        }

        // Queue refresh countdown
        if (queueRefreshTicks-- <= 0 && targetQueue.isEmpty()) {
            queueRefreshTicks = QUEUE_REFRESH_INTERVAL;
            refreshTargetQueue();
        }
    }

    /* ------------------------------------------------------------ */
    /* Target Selection                                             */
    /* ------------------------------------------------------------ */

    private void selectNextTarget() {
        currentTarget = null;

        while (!targetQueue.isEmpty()) {
            ItemEntity candidate = targetQueue.poll();
            if (isValidTarget(candidate)) {
                currentTarget = candidate;
                break;
            }
        }

        if (currentTarget != null) {
            targetTimeoutTicks = TARGET_TIMEOUT_TICKS;
            drone.getNavigation().moveTo(currentTarget, 1.0D);
        }
    }

    /**
     * Rebuilds the pickup queue from items inside the configured planner area.
     */
    private boolean refreshTargetQueue() {
        targetQueue.clear();

        ItemStack planner = drone.getInventory().getStackInSlot(0);
        if (!SitePlanner.isConfigured(planner)) return false;

        BlockPos start = SitePlanner.getStartPos(planner);
        BlockPos end = SitePlanner.getEndPos(planner);

        AABB searchArea =
                new AABB(start).minmax(new AABB(end))
                        .expandTowards(1, 1, 1);

        List<ItemEntity> items =
                drone.level().getEntitiesOfClass(
                        ItemEntity.class,
                        searchArea
                );

        if (items.isEmpty()) return false;

        items.sort(Comparator.comparingDouble(drone::distanceToSqr));

        int added = 0;
        for (ItemEntity item : items) {
            if (added >= MAX_TARGET_QUEUE_SIZE) break;
            if (!isValidTarget(item)) continue;
            if (!drone.getLogic()
                    .hasInventorySpaceFor(item.getItem())) continue;

            targetQueue.add(item);
            added++;
        }

        return !targetQueue.isEmpty();
    }

    private boolean isValidTarget(ItemEntity item) {
        return item != null
                && item.isAlive()
                && !item.getItem().isEmpty();
    }

    /* ------------------------------------------------------------ */
    /* Inventory Cache                                              */
    /* ------------------------------------------------------------ */

    private boolean hasInventorySpaceCached() {
        if (inventoryCacheTicks-- > 0) {
            return cachedInventorySpace;
        }

        cachedInventorySpace =
                drone.getLogic().hasAnyInventorySpace();

        inventoryCacheTicks = INVENTORY_CACHE_TICKS;
        return cachedInventorySpace;
    }

    private void invalidateInventoryCache() {
        inventoryCacheTicks = 0;
    }

    /* ------------------------------------------------------------ */

    @Override
    public boolean isInterruptable() {
        return !hasInventorySpaceCached();
    }
}
