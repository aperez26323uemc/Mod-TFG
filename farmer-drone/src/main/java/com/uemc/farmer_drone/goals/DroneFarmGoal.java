package com.uemc.farmer_drone.goals;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.items.SitePlanner;
import com.uemc.farmer_drone.cache.FarmAreaCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Goal that makes the drone behave as an autonomous farmer.
 *
 * <p>Behaviour order:</p>
 * <ol>
 *     <li>Harvest mature crops.</li>
 *     <li>Plant compatible seeds on empty farmland.</li>
 * </ol>
 *
 * <p>The target discovery is delegated to {@link FarmAreaCache} and executed in an amortized way
 * to preserve server TPS in large Site Planner volumes.</p>
 */
public class DroneFarmGoal extends Goal {

    private static final int RECHECK_COOLDOWN_TICKS = 10;

    private final DroneEntity drone;
    private final Predicate<String> activationCondition;
    private final FarmAreaCache farmCache = new FarmAreaCache();

    private BlockPos activeTarget;
    private TargetType activeType;
    private int recheckCooldown;

    public DroneFarmGoal(DroneEntity drone, Predicate<String> activationCondition) {
        this.drone = drone;
        this.activationCondition = activationCondition;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!activationCondition.test(drone.getState())) {
            return false;
        }

        ItemStack planner = drone.getInventory().getStackInSlot(0);
        if (!SitePlanner.isConfigured(planner)) {
            return false;
        }

        long time = drone.level().getGameTime();
        farmCache.tick(drone.level(), planner, time);

        return farmCache.hasHarvestTargets() || (hasPlantableSeedInInventory() && farmCache.hasPlantTargets());
    }

    @Override
    public boolean canContinueToUse() {
        return activationCondition.test(drone.getState())
                && SitePlanner.isConfigured(drone.getInventory().getStackInSlot(0));
    }

    @Override
    public void start() {
        activeTarget = null;
        activeType = null;
        recheckCooldown = 0;
    }

    @Override
    public void stop() {
        activeTarget = null;
        activeType = null;
        drone.getLogic().resetMiningState();
        drone.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (drone.level().isClientSide) {
            return;
        }

        long time = drone.level().getGameTime();
        ItemStack planner = drone.getInventory().getStackInSlot(0);
        farmCache.tick(drone.level(), planner, time);

        if (recheckCooldown-- > 0) {
            return;
        }

        if (activeTarget == null || activeType == null) {
            assignNextTarget(time);
            if (activeTarget == null) {
                recheckCooldown = RECHECK_COOLDOWN_TICKS;
                return;
            }
        }

        BlockPos interactionPos = getInteractionPos(activeTarget, activeType);
        if (!drone.getLogic().isBlockAccessible(interactionPos)) {
            farmCache.markInaccessible(activeTarget, time);
            clearTarget();
            recheckCooldown = 1;
            return;
        }

        Vec3 navigationAnchor = getCompactFarmAnchor(interactionPos);
        drone.getLookControl().setLookAt(Vec3.atCenterOf(interactionPos));
        drone.getLogic().executeMovement(navigationAnchor);

        if (!drone.getLogic().isInRangeToInteract(interactionPos)) {
            return;
        }

        drone.getNavigation().stop();

        if (activeType == TargetType.HARVEST) {
            boolean mined = drone.getLogic().mineBlock(activeTarget);
            if (mined) {
                drone.getLogic().itemPickUp();
                farmCache.markDirty(activeTarget);
                clearTarget();
            }
            return;
        }

        ItemStack seedStack = findPlantableSeed(activeTarget);
        if (seedStack.isEmpty()) {
            farmCache.markDirty(activeTarget);
            clearTarget();
            recheckCooldown = RECHECK_COOLDOWN_TICKS;
            return;
        }

        boolean planted = drone.getLogic().placeBlock(activeTarget.above(), seedStack);
        if (planted) {
            farmCache.markDirty(activeTarget);
            clearTarget();
        } else {
            farmCache.markInaccessible(activeTarget, time);
            clearTarget();
        }
    }

    private void assignNextTarget(long gameTime) {
        Optional<BlockPos> harvest = farmCache.pollNearestHarvestTarget(drone.blockPosition(), gameTime);
        if (harvest.isPresent()) {
            this.activeTarget = harvest.get();
            this.activeType = TargetType.HARVEST;
            return;
        }

        if (!hasPlantableSeedInInventory()) {
            clearTarget();
            return;
        }

        Optional<BlockPos> plant = farmCache.pollNearestPlantTarget(drone.blockPosition(), gameTime);
        if (plant.isPresent() && !findPlantableSeed(plant.get()).isEmpty()) {
            this.activeTarget = plant.get();
            this.activeType = TargetType.PLANT;
            return;
        }

        clearTarget();
    }

    private Vec3 getCompactFarmAnchor(BlockPos interactionPos) {
        BlockPos below = interactionPos.below();
        if (drone.level().getBlockState(interactionPos.above()).canBeReplaced()) {
            return new Vec3(interactionPos.getX() + 0.5D, interactionPos.getY() + 0.65D, interactionPos.getZ() + 0.5D);
        }

        return new Vec3(below.getX() + 0.5D, below.getY() + 0.55D, below.getZ() + 0.5D);
    }

    private BlockPos getInteractionPos(BlockPos targetPos, TargetType targetType) {
        return targetType == TargetType.PLANT ? targetPos.above() : targetPos;
    }

    private boolean hasPlantableSeedInInventory() {
        for (int slot = 1; slot < drone.getInventory().getSlots(); slot++) {
            ItemStack stack = drone.getInventory().getStackInSlot(slot);
            if (isSeedLikeItem(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSeedLikeItem(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof net.neoforged.neoforge.common.IPlantable plantable)) {
            return false;
        }

        return plantable.getPlantType(drone.level(), drone.blockPosition())
                == net.neoforged.neoforge.common.PlantType.CROP;
    }

    private ItemStack findPlantableSeed(BlockPos farmlandPos) {
        for (int slot = 1; slot < drone.getInventory().getSlots(); slot++) {
            ItemStack stack = drone.getInventory().getStackInSlot(slot);
            if (FarmAreaCache.canPlantCropSeedOn(drone.level(), farmlandPos, stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private void clearTarget() {
        activeTarget = null;
        activeType = null;
    }

    private enum TargetType {
        HARVEST,
        PLANT
    }
}
