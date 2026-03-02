package com.uemc.path_lighter_drone.mixin;

import com.uemc.path_lighter_drone.planner.PathLighterTaskHolder;
import com.uemc.path_lighter_drone.planner.PathLightingTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayDeque;
import java.util.Deque;

@Mixin(value = com.uemc.assistance_drone.entities.drone.DroneEntity.class, remap = false)
public abstract class DroneEntityMixin implements PathLighterTaskHolder {

    @Unique
    private final Deque<PathLightingTask> pathLighterDrone$taskQueue = new ArrayDeque<>();

    @Override
    public int pathLighterDrone$pendingTasks() {
        return pathLighterDrone$taskQueue.size();
    }

    @Override
    public void pathLighterDrone$enqueueTask(PathLightingTask task) {
        if (!task.isEmpty()) {
            pathLighterDrone$taskQueue.addLast(task);
        }
    }

    @Override
    public PathLightingTask pathLighterDrone$peekTask() {
        return pathLighterDrone$taskQueue.peekFirst();
    }

    @Override
    public void pathLighterDrone$pollTask() {
        pathLighterDrone$taskQueue.pollFirst();
    }
}
