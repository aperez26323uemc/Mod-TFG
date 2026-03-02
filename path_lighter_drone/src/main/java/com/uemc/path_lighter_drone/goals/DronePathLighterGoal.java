package com.uemc.path_lighter_drone.goals;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.util.ModKeys;
import com.uemc.path_lighter_drone.planner.PathLighterPlanner;
import com.uemc.path_lighter_drone.planner.PathLighterTaskHolder;
import com.uemc.path_lighter_drone.planner.PathLightingTask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Executes precomputed path-lighting tasks while drone state is follow.
 */
public class DronePathLighterGoal extends Goal {

    private final DroneEntity drone;
    private final PathLighterTaskHolder holder;

    private PathLightingTask currentTask;
    private int nodeIndex;
    private int nodeTimeout;

    public DronePathLighterGoal(DroneEntity drone) {
        this.drone = drone;
        this.holder = (PathLighterTaskHolder) drone;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return ModKeys.STATE_FOLLOW.equals(drone.getState()) && holder.pathLighterDrone$pendingTasks() > 0;
    }

    @Override
    public boolean canContinueToUse() {
        return ModKeys.STATE_FOLLOW.equals(drone.getState()) && currentTask != null && nodeIndex < currentTask.placements().size();
    }

    @Override
    public void start() {
        currentTask = holder.pathLighterDrone$peekTask();
        nodeIndex = 0;
        nodeTimeout = 0;
    }

    @Override
    public void stop() {
        drone.getNavigation().stop();
        currentTask = null;
        nodeIndex = 0;
        nodeTimeout = 0;
    }

    @Override
    public void tick() {
        if (currentTask == null || nodeIndex >= currentTask.placements().size()) {
            finishTask();
            return;
        }

        BlockPos target = currentTask.placements().get(nodeIndex);
        if (!drone.getLogic().isBlockAccessible(target)) {
            advanceNode();
            return;
        }

        Vec3 movement = new Vec3(target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D);
        drone.getLookControl().setLookAt(movement);
        drone.getLogic().executeMovement(movement);

        if (!drone.getLogic().isInRangeToInteract(target)) {
            if (++nodeTimeout >= com.uemc.path_lighter_drone.ModKeys.NODE_TIMEOUT_TICKS) {
                advanceNode();
            }
            return;
        }

        drone.getNavigation().stop();

        ItemStack candidate = findPlaceableLightBlock(target);
        if (candidate.isEmpty()) {
            advanceNode();
            return;
        }

        drone.getLogic().placeBlock(target, candidate);
        advanceNode();
    }

    private ItemStack findPlaceableLightBlock(BlockPos target) {
        for (int slot = 0; slot < drone.getInventory().getSlots(); slot++) {
            ItemStack stack = drone.getInventory().getStackInSlot(slot);
            if (PathLighterPlanner.isSuitableLightBlock(stack, drone.level(), target)
                    && drone.level().getBlockState(target).canBeReplaced()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private void advanceNode() {
        nodeIndex++;
        nodeTimeout = 0;
        if (currentTask != null && nodeIndex >= currentTask.placements().size()) {
            finishTask();
        }
    }

    private void finishTask() {
        holder.pathLighterDrone$pollTask();
        currentTask = holder.pathLighterDrone$peekTask();
        nodeIndex = 0;
        nodeTimeout = 0;
    }
}
