package com.uemc.path_lighter_drone.planner;

public interface PathLighterTaskHolder {
    int pathLighterDrone$pendingTasks();

    void pathLighterDrone$enqueueTask(PathLightingTask task);

    PathLightingTask pathLighterDrone$peekTask();

    void pathLighterDrone$pollTask();
}
